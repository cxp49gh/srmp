import time
from collections import OrderedDict
from copy import deepcopy
from threading import Lock
from typing import Any, Dict, Optional


def now_ms() -> int:
    return int(time.time() * 1000)


class LiveTraceStore:
    def __init__(self, max_records: int = 200) -> None:
        self.max_records = max(1, int(max_records or 200))
        self._records = OrderedDict()
        self._lock = Lock()

    def start_trace(self, trace_id: str, action: str = "", graph_name: str = "") -> Dict[str, Any]:
        trace_id = self._safe_trace_id(trace_id)
        ts = now_ms()
        with self._lock:
            record = self._records.get(trace_id) or {
                "traceId": trace_id,
                "steps": [],
                "toolSummary": {"planned": 0, "completed": 0, "success": 0, "failed": 0},
                "sourceSummary": {"business": 0, "knowledge": 0, "outline": 0},
                "answerMeta": {},
                "error": "",
                "startedAtMs": ts,
            }
            record.update({
                "status": "RUNNING",
                "action": action or record.get("action") or "",
                "graphName": graph_name or record.get("graphName") or "",
                "currentStep": record.get("currentStep"),
                "updatedAtMs": ts,
                "costMs": max(0, ts - int(record.get("startedAtMs") or ts)),
            })
            self._records[trace_id] = record
            self._records.move_to_end(trace_id)
            self._prune_locked()
            return deepcopy(record)

    def start_step(self, trace_id: str, name: str, label: str = "", data: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        trace_id = self._safe_trace_id(trace_id)
        ts = now_ms()
        with self._lock:
            record = self._ensure_record_locked(trace_id)
            record["status"] = "RUNNING"
            record["currentStep"] = {
                "name": name,
                "label": label or name,
                "status": "RUNNING",
                "startedAtMs": ts,
                "elapsedMs": 0,
                "data": data or {},
            }
            self._touch_locked(record, ts)
            return deepcopy(record)

    def complete_step(self, trace_id: str, step: Dict[str, Any]) -> Dict[str, Any]:
        trace_id = self._safe_trace_id(trace_id)
        ts = now_ms()
        item = dict(step or {})
        name = str(item.get("name") or item.get("node") or "unknown")
        item["name"] = name
        item["label"] = str(item.get("label") or item.get("message") or name)
        item["status"] = str(item.get("status") or "SUCCESS").upper()
        item["elapsedMs"] = int(item.get("elapsedMs") or item.get("costMs") or 0)
        with self._lock:
            record = self._ensure_record_locked(trace_id)
            record.setdefault("steps", []).append(item)
            record["currentStep"] = None
            self._merge_step_summaries_locked(record, item)
            self._touch_locked(record, ts)
            return deepcopy(record)

    def fail_step(self, trace_id: str, name: str, error: str) -> Dict[str, Any]:
        step = {
            "name": name,
            "label": name,
            "status": "FAILED",
            "elapsedMs": 0,
            "error": error,
            "errorMessage": error,
            "data": {},
        }
        self.complete_step(trace_id, step)
        with self._lock:
            record = self._ensure_record_locked(self._safe_trace_id(trace_id))
            record["status"] = "FAILED"
            record["error"] = error
            self._touch_locked(record, now_ms())
            return deepcopy(record)

    def complete_trace(
        self,
        trace_id: str,
        status: str = "SUCCESS",
        answer_meta: Optional[Dict[str, Any]] = None,
        trace: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        trace_id = self._safe_trace_id(trace_id)
        ts = now_ms()
        with self._lock:
            record = self._ensure_record_locked(trace_id)
            record["status"] = status
            record["currentStep"] = None
            if answer_meta:
                record["answerMeta"] = dict(answer_meta)
            if trace:
                record["finalTrace"] = deepcopy(trace)
                if isinstance(trace.get("steps"), list):
                    record["steps"] = deepcopy(trace.get("steps"))
            self._touch_locked(record, ts)
            return deepcopy(record)

    def get(self, trace_id: str) -> Optional[Dict[str, Any]]:
        trace_id = self._safe_trace_id(trace_id)
        with self._lock:
            record = self._records.get(trace_id)
            if not record:
                return None
            snapshot = deepcopy(record)
        self._refresh_current_step(snapshot)
        return snapshot

    def not_found(self, trace_id: str) -> Dict[str, Any]:
        return {
            "traceId": self._safe_trace_id(trace_id),
            "status": "NOT_FOUND",
            "currentStep": None,
            "steps": [],
            "toolSummary": {"planned": 0, "completed": 0, "success": 0, "failed": 0},
            "sourceSummary": {"business": 0, "knowledge": 0, "outline": 0},
            "answerMeta": {},
            "error": "",
            "startedAtMs": None,
            "updatedAtMs": now_ms(),
            "costMs": 0,
        }

    def _safe_trace_id(self, trace_id: str) -> str:
        return str(trace_id or "").strip()

    def _ensure_record_locked(self, trace_id: str) -> Dict[str, Any]:
        record = self._records.get(trace_id)
        if not record:
            ts = now_ms()
            record = {
                "traceId": trace_id,
                "status": "RUNNING",
                "action": "",
                "graphName": "",
                "currentStep": None,
                "steps": [],
                "toolSummary": {"planned": 0, "completed": 0, "success": 0, "failed": 0},
                "sourceSummary": {"business": 0, "knowledge": 0, "outline": 0},
                "answerMeta": {},
                "error": "",
                "startedAtMs": ts,
                "updatedAtMs": ts,
                "costMs": 0,
            }
            self._records[trace_id] = record
        return record

    def _touch_locked(self, record: Dict[str, Any], ts: int) -> None:
        record["updatedAtMs"] = ts
        started = int(record.get("startedAtMs") or ts)
        record["costMs"] = max(0, ts - started)
        trace_id = record.get("traceId")
        if trace_id:
            self._records.move_to_end(trace_id)
        self._prune_locked()

    def _prune_locked(self) -> None:
        while len(self._records) > self.max_records:
            self._records.popitem(last=False)

    def _merge_step_summaries_locked(self, record: Dict[str, Any], step: Dict[str, Any]) -> None:
        data = step.get("data") if isinstance(step.get("data"), dict) else {}
        name = str(step.get("name") or "")
        if name in {"tool_plan", "tool_planning"}:
            tools = data.get("tools")
            if isinstance(tools, list):
                record["toolSummary"]["planned"] = len(tools)
        if name == "tool_execute":
            record["toolSummary"]["completed"] = int(step.get("count") or record["toolSummary"].get("completed") or 0)
            record["toolSummary"]["success"] = int(data.get("success") or 0)
            record["toolSummary"]["failed"] = int(data.get("failed") or 0)
        if name == "evidence_fuse":
            record["sourceSummary"]["business"] = int(data.get("businessHitCount") or 0)
            record["sourceSummary"]["knowledge"] = int(data.get("knowledgeHitCount") or 0)

    def _refresh_current_step(self, snapshot: Dict[str, Any]) -> None:
        current = snapshot.get("currentStep")
        if not isinstance(current, dict) or not current.get("startedAtMs"):
            return
        current["elapsedMs"] = max(0, now_ms() - int(current.get("startedAtMs") or now_ms()))
