import time
import uuid
from collections import deque
from threading import Lock
from typing import Any, Deque, Dict, List, Optional

from .schemas import MapAiAgentRequest, MapAiAgentResponse


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
            "toolTotalCount": data.get("toolTotalCount") if isinstance(data, dict) else len(tool_results),
            "toolSuccessCount": data.get("toolSuccessCount") if isinstance(data, dict) else sum(1 for item in tool_results if item.get("success")),
            "stepCount": len(steps) if isinstance(steps, list) else 0,
            "steps": steps if isinstance(steps, list) else [],
            "toolResults": _compact_tool_results(tool_results),
            "fallbackLike": False,
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
            "fallbackLike": True,
        }
        self._append(record)
        return record

    def summary(self) -> Dict[str, Any]:
        with self._lock:
            avg_cost = int(self._total_cost_ms / self._total) if self._total else 0
            recent = list(self._records)[-10:]
            return {
                "status": "UP",
                "startedAtMs": self._started_at_ms,
                "uptimeMs": _now_ms() - self._started_at_ms,
                "maxRecords": self.max_records,
                "recordCount": len(self._records),
                "total": self._total,
                "success": self._success,
                "failed": self._failed,
                "fallbackLike": self._fallback_like,
                "successRate": round(float(self._success) / float(self._total), 4) if self._total else None,
                "avgCostMs": avg_cost,
                "lastStatus": recent[-1].get("status") if recent else None,
                "lastTraceId": recent[-1].get("traceId") if recent else None,
                "recentIntents": [item.get("intent") for item in recent if item.get("intent")],
            }

    def recent(self, limit: int = 20, status: Optional[str] = None) -> List[Dict[str, Any]]:
        safe_limit = max(1, min(int(limit or 20), 100))
        normalized_status = (status or "").strip().upper()
        with self._lock:
            records = list(self._records)
        if normalized_status:
            records = [item for item in records if item.get("status") == normalized_status]
        return list(reversed(records[-safe_limit:]))

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


runtime_audit_store = RuntimeAuditStore()
