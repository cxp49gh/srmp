import time
import uuid
from collections import deque
from copy import deepcopy
from threading import Lock
from typing import Any, Deque, Dict, List, Optional

from .schemas import MapAiAgentRequest, MapAiAgentResponse


MAX_STRING_LENGTH = 1200
MAX_LIST_ITEMS = 80
MAX_DICT_KEYS = 120
MAX_COMPACT_DEPTH = 8


def _now_ms() -> int:
    return int(time.time() * 1000)


def _safe_len(value: Optional[str]) -> int:
    return len(value or "")


def _tenant_from_request(request: Optional[MapAiAgentRequest]) -> Optional[str]:
    if request and request.mapContext and request.mapContext.tenantId:
        return request.mapContext.tenantId
    return None


def _route_from_request(request: Optional[MapAiAgentRequest]) -> Optional[str]:
    if request and request.mapContext and request.mapContext.routeCode:
        return request.mapContext.routeCode
    return None


def _mode_from_request(request: Optional[MapAiAgentRequest]) -> Optional[str]:
    if request and request.mapContext and request.mapContext.mode:
        return request.mapContext.mode
    return None


class RuntimeAuditStore:
    """轻量级内存观测缓冲区。

    这里只做运行时排障和联调用，不替代 Java ai_trace_log/ai_trace_step 持久化。
    容器重启后数据会清空，这正好避免把敏感上下文长期留在编排服务里。
    Phase50.9 增加了 requestPayload 快照，支持对最近一次调用做 Plan/只读执行回放。
    """

    def __init__(self, max_records: int = 200) -> None:
        self.max_records = max_records
        self._records: Deque[Dict[str, Any]] = deque(maxlen=max_records)
        self._lock = Lock()
        self._started_at_ms = _now_ms()
        self._total = 0
        self._success = 0
        self._failed = 0
        self._fallback_like = 0
        self._total_cost_ms = 0

    def record_success(
        self,
        request: MapAiAgentRequest,
        response: MapAiAgentResponse,
        tenant_id: Optional[str],
        trace_id: Optional[str],
        cost_ms: int,
    ) -> Dict[str, Any]:
        trace = response.trace or {}
        data = response.data or {}
        steps = trace.get("steps") if isinstance(trace, dict) else None
        tool_results = response.toolResults or []
        tool_total = data.get("toolTotalCount") if isinstance(data, dict) else len(tool_results)
        tool_success = data.get("toolSuccessCount") if isinstance(data, dict) else sum(1 for item in tool_results if item.get("success"))
        record = {
            "id": str(uuid.uuid4()),
            "ts": _now_ms(),
            "status": "SUCCESS",
            "tenantId": tenant_id or _tenant_from_request(request) or "default",
            "traceId": trace_id or trace.get("traceId"),
            "messagePreview": (request.message or "")[:120],
            "messageLength": _safe_len(request.message),
            "mode": response.mode,
            "intent": response.intent,
            "mapMode": _mode_from_request(request),
            "routeCode": _route_from_request(request),
            "answerLength": _safe_len(response.answer),
            "costMs": cost_ms,
            "engine": trace.get("engine") if isinstance(trace, dict) else None,
            "langgraphAvailable": data.get("langgraphAvailable") if isinstance(data, dict) else None,
            "toolTotalCount": tool_total,
            "toolSuccessCount": tool_success,
            "toolFailedCount": data.get("toolFailedCount") if isinstance(data, dict) else sum(1 for item in tool_results if not item.get("success")),
            "sourceCount": data.get("sourceCount") if isinstance(data, dict) else len(response.sources or []),
            "stepCount": len(steps) if isinstance(steps, list) else 0,
            "steps": steps if isinstance(steps, list) else [],
            "toolResults": _compact_tool_results(tool_results),
            "requestPayload": _request_payload(request),
            "responsePreview": _compact_response(response),
            "fallbackLike": (tool_total or 0) > 0 and (tool_success or 0) == 0,
            "replayable": True,
        }
        self._append(record)
        return record

    def record_failure(
        self,
        request: Optional[MapAiAgentRequest],
        tenant_id: Optional[str],
        trace_id: Optional[str],
        cost_ms: int,
        error: Exception,
    ) -> Dict[str, Any]:
        record = {
            "id": str(uuid.uuid4()),
            "ts": _now_ms(),
            "status": "FAILED",
            "tenantId": tenant_id or _tenant_from_request(request) or "default",
            "traceId": trace_id,
            "messagePreview": ((request.message if request else None) or "")[:120],
            "messageLength": _safe_len(request.message if request else None),
            "mapMode": _mode_from_request(request),
            "routeCode": _route_from_request(request),
            "costMs": cost_ms,
            "errorType": error.__class__.__name__,
            "errorMessage": str(error)[:500],
            "requestPayload": _request_payload(request),
            "fallbackLike": True,
            "replayable": request is not None,
        }
        self._append(record)
        return record

    def summary(self) -> Dict[str, Any]:
        with self._lock:
            avg_cost = int(self._total_cost_ms / self._total) if self._total else 0
            records = list(self._records)
            recent = records[-10:]
            return {
                "status": "UP",
                "startedAtMs": self._started_at_ms,
                "uptimeMs": _now_ms() - self._started_at_ms,
                "maxRecords": self.max_records,
                "recordCount": len(self._records),
                "replayableCount": sum(1 for item in records if item.get("replayable")),
                "total": self._total,
                "success": self._success,
                "failed": self._failed,
                "fallbackLike": self._fallback_like,
                "successRate": round(float(self._success) / float(self._total), 4) if self._total else None,
                "avgCostMs": avg_cost,
                "lastStatus": recent[-1].get("status") if recent else None,
                "lastTraceId": recent[-1].get("traceId") if recent else None,
                "recentIntents": [item.get("intent") for item in recent if item.get("intent")],
                "recentFailedTools": _recent_failed_tools(recent),
                "intentBuckets": _bucket(records, "intent"),
                "routeBuckets": _bucket(records, "routeCode", max_items=8),
                "toolBuckets": _tool_buckets(records),
            }

    def recent(self, limit: int = 20, status: Optional[str] = None) -> List[Dict[str, Any]]:
        safe_limit = max(1, min(int(limit or 20), 100))
        normalized_status = (status or "").strip().upper()
        with self._lock:
            records = list(self._records)
        if normalized_status:
            records = [item for item in records if item.get("status") == normalized_status]
        return list(reversed(records[-safe_limit:]))

    def get(self, record_id: str) -> Optional[Dict[str, Any]]:
        target = (record_id or "").strip()
        if not target:
            return None
        with self._lock:
            records = list(self._records)
        for item in reversed(records):
            if item.get("id") == target or item.get("traceId") == target:
                return deepcopy(item)
        return None

    def export_bundle(self, limit: int = 30, status: Optional[str] = None) -> Dict[str, Any]:
        return {
            "generatedAtMs": _now_ms(),
            "summary": self.summary(),
            "recent": self.recent(limit=limit, status=status),
            "note": "Runtime 内存诊断快照，仅用于联调；容器重启后历史会清空。",
        }

    def clear(self) -> Dict[str, Any]:
        with self._lock:
            size = len(self._records)
            self._records.clear()
            self._total = 0
            self._success = 0
            self._failed = 0
            self._fallback_like = 0
            self._total_cost_ms = 0
            self._started_at_ms = _now_ms()
        return {"cleared": size, "status": "OK"}

    def _append(self, record: Dict[str, Any]) -> None:
        with self._lock:
            self._records.append(record)
            self._total += 1
            if record.get("status") == "SUCCESS":
                self._success += 1
            else:
                self._failed += 1
            if record.get("fallbackLike"):
                self._fallback_like += 1
            self._total_cost_ms += int(record.get("costMs") or 0)


def _request_payload(request: Optional[MapAiAgentRequest]) -> Optional[Dict[str, Any]]:
    if not request:
        return None
    try:
        return _compact_value(request.model_dump(exclude_none=True))
    except Exception:  # noqa: BLE001
        return None


def _compact_response(response: MapAiAgentResponse) -> Dict[str, Any]:
    return {
        "answerPreview": (response.answer or "")[:500],
        "mode": response.mode,
        "intent": response.intent,
        "sourceCount": len(response.sources or []),
        "toolResultCount": len(response.toolResults or []),
        "data": _compact_value(response.data or {}, depth=0),
    }


def _compact_value(value: Any, depth: int = 0) -> Any:
    if depth > MAX_COMPACT_DEPTH:
        return "..."
    if value is None or isinstance(value, (bool, int, float)):
        return value
    if isinstance(value, str):
        return value if len(value) <= MAX_STRING_LENGTH else value[:MAX_STRING_LENGTH] + "..."
    if isinstance(value, list):
        result = [_compact_value(item, depth + 1) for item in value[:MAX_LIST_ITEMS]]
        if len(value) > MAX_LIST_ITEMS:
            result.append({"truncatedItems": len(value) - MAX_LIST_ITEMS})
        return result
    if isinstance(value, dict):
        result: Dict[str, Any] = {}
        for index, (key, item) in enumerate(value.items()):
            if index >= MAX_DICT_KEYS:
                result["__truncatedKeys"] = len(value) - MAX_DICT_KEYS
                break
            result[str(key)] = _compact_value(item, depth + 1)
        return result
    if hasattr(value, "model_dump"):
        return _compact_value(value.model_dump(exclude_none=True), depth + 1)
    return str(value)[:MAX_STRING_LENGTH]


def _compact_tool_results(tool_results: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    compact: List[Dict[str, Any]] = []
    for item in tool_results:
        if not isinstance(item, dict):
            continue
        compact.append(
            {
                "toolName": item.get("toolName"),
                "success": item.get("success"),
                "summary": item.get("summary"),
                "count": item.get("count"),
                "costMs": item.get("costMs"),
                "errorMessage": (item.get("errorMessage") or "")[:300] if item.get("errorMessage") else None,
            }
        )
    return compact


def _recent_failed_tools(records: List[Dict[str, Any]]) -> Dict[str, int]:
    result: Dict[str, int] = {}
    for record in records or []:
        for item in record.get("toolResults") or []:
            if isinstance(item, dict) and item.get("success") is False:
                name = item.get("toolName") or "UNKNOWN"
                result[name] = result.get(name, 0) + 1
    return result


def _bucket(records: List[Dict[str, Any]], key: str, max_items: int = 10) -> Dict[str, int]:
    result: Dict[str, int] = {}
    for record in records or []:
        value = record.get(key)
        if value in (None, ""):
            continue
        name = str(value)
        result[name] = result.get(name, 0) + 1
    return dict(sorted(result.items(), key=lambda item: item[1], reverse=True)[:max_items])


def _tool_buckets(records: List[Dict[str, Any]]) -> Dict[str, Dict[str, int]]:
    result: Dict[str, Dict[str, int]] = {}
    for record in records or []:
        for item in record.get("toolResults") or []:
            if not isinstance(item, dict):
                continue
            name = str(item.get("toolName") or "UNKNOWN")
            bucket = result.setdefault(name, {"total": 0, "success": 0, "failed": 0})
            bucket["total"] += 1
            if item.get("success") is True:
                bucket["success"] += 1
            elif item.get("success") is False:
                bucket["failed"] += 1
    return result


runtime_audit_store = RuntimeAuditStore()
