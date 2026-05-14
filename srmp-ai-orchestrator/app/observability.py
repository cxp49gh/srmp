import json
import os
import time
import uuid
from collections import deque
from copy import deepcopy
from pathlib import Path
from threading import Lock
from typing import Any, Deque, Dict, List, Optional

from .config import settings
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
    """轻量级运行时审计缓冲区。

    Phase50.10 在 Phase50.9 的可回放内存审计基础上，增加可选 JSONL 持久化、
    启动恢复、导出脱敏和容量保护。默认仍然只用内存，避免给生产环境额外写盘。
    """

    def __init__(
        self,
        max_records: Optional[int] = None,
        persist_enabled: Optional[bool] = None,
        persist_path: Optional[str] = None,
        load_on_start: Optional[bool] = None,
    ) -> None:
        self.max_records = max(1, int(max_records or settings.audit_max_records or 200))
        self.persist_enabled = bool(settings.audit_persist_enabled if persist_enabled is None else persist_enabled)
        self.persist_path = persist_path or settings.audit_persist_path
        self.load_on_start = bool(settings.audit_load_on_start if load_on_start is None else load_on_start)
        self.max_persist_bytes = max(1024 * 1024, int(settings.audit_max_persist_bytes or 20 * 1024 * 1024))
        self._records: Deque[Dict[str, Any]] = deque(maxlen=self.max_records)
        self._lock = Lock()
        self._started_at_ms = _now_ms()
        self._total = 0
        self._success = 0
        self._failed = 0
        self._fallback_like = 0
        self._total_cost_ms = 0
        self._loaded_from_disk = 0
        self._persist_written = 0
        self._persist_error: Optional[str] = None
        self._last_persist_ms: Optional[int] = None
        if self.persist_enabled and self.load_on_start:
            self._load_existing_records()

    def record_success(self, request: MapAiAgentRequest, response: MapAiAgentResponse, tenant_id: Optional[str], trace_id: Optional[str], cost_ms: int) -> Dict[str, Any]:
        trace = response.trace or {}
        data = response.data or {}
        action_result = _model_or_dict(getattr(response, "actionResult", None))
        action = getattr(response, "action", None)
        if not action and isinstance(data, dict):
            action = data.get("action")
        graph_name = data.get("graphName") if isinstance(data, dict) else None
        if not graph_name and isinstance(trace, dict):
            graph_name = trace.get("graphName")
        steps = trace.get("steps") if isinstance(trace, dict) else None
        tool_results = response.toolResults or []
        tool_total = data.get("toolTotalCount") if isinstance(data, dict) else len(tool_results)
        tool_success = data.get("toolSuccessCount") if isinstance(data, dict) else sum(1 for item in tool_results if item.get("success"))
        adaptive_planning = _adaptive_planning_from_response(response)
        adaptive_added_tools = _adaptive_added_tools(adaptive_planning)
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
            "action": action,
            "graphName": graph_name,
            "actionResultType": action_result.get("type"),
            "actionResultStatus": action_result.get("status"),
            "writeBlocked": bool(data.get("writeBlocked") or data.get("writeConfirmation") is False) if isinstance(data, dict) else False,
            "needsConfirmation": action_result.get("status") == "NEEDS_CONFIRMATION",
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
            "adaptivePlanningStatus": adaptive_planning.get("status"),
            "adaptivePlanningEnabled": adaptive_planning.get("enabled"),
            "adaptiveAddedToolCount": len(adaptive_added_tools),
            "adaptiveAddedToolNames": adaptive_added_tools,
            "adaptivePlanningReason": adaptive_planning.get("reason"),
            "stepCount": len(steps) if isinstance(steps, list) else 0,
            "steps": _compact_value(steps if isinstance(steps, list) else []),
            "toolResults": _compact_tool_results(tool_results),
            "requestPayload": _request_payload(request),
            "responsePreview": _compact_response(response),
            "fallbackLike": (tool_total or 0) > 0 and (tool_success or 0) == 0,
            "replayable": True,
        }
        self._append(record)
        return deepcopy(record)

    def record_failure(self, request: Optional[MapAiAgentRequest], tenant_id: Optional[str], trace_id: Optional[str], cost_ms: int, error: Exception) -> Dict[str, Any]:
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
        return deepcopy(record)

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
                "actionBuckets": _bucket(records, "action", max_items=12),
                "graphBuckets": _bucket(records, "graphName", max_items=12),
                "writeBlockedCount": sum(1 for item in records if item.get("writeBlocked")),
                "needsConfirmationCount": sum(1 for item in records if item.get("needsConfirmation")),
                "routeBuckets": _bucket(records, "routeCode", max_items=8),
                "toolBuckets": _tool_buckets(records),
                "adaptivePlanning": _adaptive_summary(records),
                "persistence": self.persistence_status(compact=True),
            }

    def recent(self, limit: int = 20, status: Optional[str] = None) -> List[Dict[str, Any]]:
        safe_limit = max(1, min(int(limit or 20), 100))
        normalized_status = (status or "").strip().upper()
        with self._lock:
            records = list(self._records)
        if normalized_status:
            records = [item for item in records if item.get("status") == normalized_status]
        return deepcopy(list(reversed(records[-safe_limit:])))

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
            "persistence": self.persistence_status(),
            "redaction": {"enabled": bool(settings.audit_redact_enabled), "keys": list(settings.audit_redact_keys or [])},
            "note": "Runtime 诊断快照。Phase50.11 支持配置快照、健康解释和条件清理；JSONL 持久化默认仍为内存模式。",
        }

    def persistence_status(self, compact: bool = False) -> Dict[str, Any]:
        path = self.persist_path
        size = None
        exists = False
        if path:
            try:
                file_path = Path(path)
                exists = file_path.exists()
                size = file_path.stat().st_size if exists else 0
            except Exception as exc:  # noqa: BLE001
                self._persist_error = str(exc)[:300]
        data = {
            "enabled": self.persist_enabled,
            "path": path,
            "exists": exists,
            "sizeBytes": size,
            "maxBytes": self.max_persist_bytes,
            "loadOnStart": self.load_on_start,
            "loadedFromDisk": self._loaded_from_disk,
            "written": self._persist_written,
            "lastPersistAtMs": self._last_persist_ms,
            "lastError": self._persist_error,
        }
        if compact:
            return {k: data[k] for k in ["enabled", "exists", "sizeBytes", "loadedFromDisk", "written", "lastError"]}
        return data

    def clear(self) -> Dict[str, Any]:
        with self._lock:
            size = len(self._records)
            self._records.clear()
            self._reset_counters_locked(started_at_ms=_now_ms())
            self._loaded_from_disk = 0
        truncated = False
        if self.persist_enabled and self.persist_path:
            try:
                Path(self.persist_path).parent.mkdir(parents=True, exist_ok=True)
                Path(self.persist_path).write_text("", encoding="utf-8")
                truncated = True
                self._persist_error = None
            except Exception as exc:  # noqa: BLE001
                self._persist_error = str(exc)[:300]
        return {"cleared": size, "persistFileTruncated": truncated, "status": "OK"}

    def prune(
        self,
        status: Optional[str] = None,
        before_ms: Optional[int] = None,
        retain_latest: Optional[int] = None,
        include_persist: bool = True,
    ) -> Dict[str, Any]:
        """按条件清理审计记录。

        Phase50.11 新增更安全的清理能力：默认保留最近 N 条，避免一键清空后
        丢失最近问题现场；如开启 JSONL 持久化，可同步重写持久化文件。
        """
        normalized_status = (status or "").strip().upper()
        safe_retain = max(0, int(retain_latest if retain_latest is not None else settings.audit_prune_default_retain_latest or 20))
        cutoff = int(before_ms) if before_ms is not None else None
        with self._lock:
            records = list(self._records)
            original_size = len(records)
            protected_start = max(0, original_size - safe_retain) if safe_retain else original_size
            kept: List[Dict[str, Any]] = []
            removed: List[Dict[str, Any]] = []
            for idx, item in enumerate(records):
                if idx >= protected_start:
                    kept.append(item)
                    continue
                matched = True
                if normalized_status:
                    matched = matched and str(item.get("status") or "").upper() == normalized_status
                if cutoff is not None:
                    matched = matched and int(item.get("ts") or 0) < cutoff
                if not normalized_status and cutoff is None:
                    matched = True
                if matched:
                    removed.append(item)
                else:
                    kept.append(item)
            self._records = deque(kept[-self.max_records :], maxlen=self.max_records)
            self._recount_locked()
            remaining = list(self._records)
        persist_rewritten = False
        if include_persist and self.persist_enabled and self.persist_path:
            persist_rewritten = self._rewrite_persist_file_with_records(remaining)
        return {
            "status": "OK",
            "removed": len(removed),
            "remaining": len(remaining),
            "retainLatest": safe_retain,
            "filterStatus": normalized_status or None,
            "beforeMs": cutoff,
            "persistFileRewritten": persist_rewritten,
            "persistence": self.persistence_status(compact=True),
        }

    def _append(self, record: Dict[str, Any]) -> None:
        with self._lock:
            self._records.append(record)
            self._count_record(record)
        if self.persist_enabled:
            self._persist_append(record)

    def _reset_counters_locked(self, started_at_ms: Optional[int] = None) -> None:
        self._total = 0
        self._success = 0
        self._failed = 0
        self._fallback_like = 0
        self._total_cost_ms = 0
        if started_at_ms is not None:
            self._started_at_ms = started_at_ms

    def _recount_locked(self) -> None:
        self._reset_counters_locked()
        for item in list(self._records):
            self._count_record(item)

    def _count_record(self, record: Dict[str, Any]) -> None:
        self._total += 1
        if record.get("status") == "SUCCESS":
            self._success += 1
        else:
            self._failed += 1
        if record.get("fallbackLike"):
            self._fallback_like += 1
        self._total_cost_ms += int(record.get("costMs") or 0)

    def _load_existing_records(self) -> None:
        if not self.persist_path:
            return
        path = Path(self.persist_path)
        if not path.exists() or not path.is_file():
            return
        try:
            if path.stat().st_size > self.max_persist_bytes:
                self._persist_error = "persist file exceeds max bytes; skip startup load"
                return
            loaded: List[Dict[str, Any]] = []
            with path.open("r", encoding="utf-8") as fh:
                for line in fh:
                    line = line.strip()
                    if not line:
                        continue
                    item = json.loads(line)
                    if isinstance(item, dict):
                        loaded.append(item)
            with self._lock:
                for item in loaded[-self.max_records :]:
                    self._records.append(item)
                    self._count_record(item)
                self._loaded_from_disk = len(self._records)
            self._persist_error = None
        except Exception as exc:  # noqa: BLE001
            self._persist_error = str(exc)[:300]

    def _persist_append(self, record: Dict[str, Any]) -> None:
        if not self.persist_path:
            return
        try:
            path = Path(self.persist_path)
            path.parent.mkdir(parents=True, exist_ok=True)
            if path.exists() and path.stat().st_size > self.max_persist_bytes:
                self._rewrite_persist_file()
            with path.open("a", encoding="utf-8") as fh:
                fh.write(json.dumps(record, ensure_ascii=False, default=str) + "\n")
            self._persist_written += 1
            self._last_persist_ms = _now_ms()
            self._persist_error = None
        except Exception as exc:  # noqa: BLE001
            self._persist_error = str(exc)[:300]

    def _rewrite_persist_file(self) -> None:
        with self._lock:
            records = list(self._records)
        self._rewrite_persist_file_with_records(records)

    def _rewrite_persist_file_with_records(self, records: List[Dict[str, Any]]) -> bool:
        if not self.persist_path:
            return False
        try:
            path = Path(self.persist_path)
            path.parent.mkdir(parents=True, exist_ok=True)
            tmp = path.with_suffix(path.suffix + ".tmp")
            with tmp.open("w", encoding="utf-8") as fh:
                for item in records[-self.max_records :]:
                    fh.write(json.dumps(item, ensure_ascii=False, default=str) + "\n")
            os.replace(str(tmp), str(path))
            self._last_persist_ms = _now_ms()
            self._persist_error = None
            return True
        except Exception as exc:  # noqa: BLE001
            self._persist_error = str(exc)[:300]
            return False


def _request_payload(request: Optional[MapAiAgentRequest]) -> Optional[Dict[str, Any]]:
    if not request:
        return None
    try:
        return _compact_value(request.model_dump(exclude_none=True))
    except Exception:  # noqa: BLE001
        return None


def _compact_response(response: MapAiAgentResponse) -> Dict[str, Any]:
    action_result = _model_or_dict(getattr(response, "actionResult", None))
    return {
        "answerPreview": (response.answer or "")[:500],
        "mode": response.mode,
        "intent": response.intent,
        "action": getattr(response, "action", None),
        "actionResult": _compact_value(action_result),
        "sourceCount": len(response.sources or []),
        "toolResultCount": len(response.toolResults or []),
        "data": _compact_value(response.data or {}, depth=0),
    }


def _adaptive_planning_from_response(response: MapAiAgentResponse) -> Dict[str, Any]:
    data = response.data or {}
    trace = response.trace or {}
    adaptive = data.get("adaptivePlanning") if isinstance(data, dict) else None
    if not isinstance(adaptive, dict) and isinstance(trace, dict):
        adaptive = trace.get("adaptivePlanning")
    return adaptive if isinstance(adaptive, dict) else {}


def _adaptive_added_tools(adaptive: Dict[str, Any]) -> List[str]:
    values = adaptive.get("addedToolNames")
    if not isinstance(values, list):
        return []
    result: List[str] = []
    for item in values:
        text = str(item or "").strip()
        if text and text not in result:
            result.append(text)
    return result


def _adaptive_summary(records: List[Dict[str, Any]]) -> Dict[str, Any]:
    status_buckets: Dict[str, int] = {}
    tool_buckets: Dict[str, int] = {}
    executed = 0
    skipped = 0
    disabled = 0
    added_tool_count = 0
    for record in records or []:
        status = str(record.get("adaptivePlanningStatus") or "").strip()
        if status:
            status_buckets[status] = status_buckets.get(status, 0) + 1
            if status == "EXECUTED":
                executed += 1
            elif status.startswith("SKIPPED_"):
                skipped += 1
            elif status == "DISABLED":
                disabled += 1
        tools = record.get("adaptiveAddedToolNames")
        if not isinstance(tools, list):
            tools = []
        added_tool_count += len(tools)
        for tool_name in tools:
            name = str(tool_name or "").strip()
            if name:
                tool_buckets[name] = tool_buckets.get(name, 0) + 1
    return {
        "executedCount": executed,
        "skippedCount": skipped,
        "disabledCount": disabled,
        "addedToolCount": added_tool_count,
        "statusBuckets": dict(sorted(status_buckets.items(), key=lambda item: item[1], reverse=True)),
        "addedToolBuckets": dict(sorted(tool_buckets.items(), key=lambda item: item[1], reverse=True)),
    }


def _model_or_dict(value: Any) -> Dict[str, Any]:
    if value is None:
        return {}
    if isinstance(value, dict):
        return value
    if hasattr(value, "model_dump"):
        return value.model_dump(exclude_none=True)
    if hasattr(value, "dict"):
        return value.dict(exclude_none=True)
    return {}


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
            normalized_key = str(key)
            if _should_redact_key(normalized_key):
                result[normalized_key] = "***REDACTED***"
            else:
                result[normalized_key] = _compact_value(item, depth + 1)
        return result
    if hasattr(value, "model_dump"):
        return _compact_value(value.model_dump(exclude_none=True), depth + 1)
    return str(value)[:MAX_STRING_LENGTH]


def _should_redact_key(key: str) -> bool:
    if not settings.audit_redact_enabled:
        return False
    normalized = (key or "").strip().lower().replace("-", "_")
    if not normalized:
        return False
    for item in settings.audit_redact_keys or []:
        token = (item or "").strip().lower().replace("-", "_")
        if token and token in normalized:
            return True
    return False


def _compact_tool_results(tool_results: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    compact: List[Dict[str, Any]] = []
    for item in tool_results:
        if not isinstance(item, dict):
            continue
        compact.append({
            "toolName": item.get("toolName"),
            "success": item.get("success"),
            "summary": item.get("summary"),
            "count": item.get("count"),
            "costMs": item.get("costMs"),
            "errorMessage": (item.get("errorMessage") or "")[:300] if item.get("errorMessage") else None,
        })
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
