import hashlib
import json
import time
from typing import Any, Dict, List, Optional


DEFAULT_EVIDENCE_TTL_SECONDS = 1800


class EvidenceSnapshotStore:
    def __init__(self, ttl_seconds: int = DEFAULT_EVIDENCE_TTL_SECONDS, max_records: int = 200) -> None:
        self.ttl_seconds = max(1, int(ttl_seconds or DEFAULT_EVIDENCE_TTL_SECONDS))
        self.max_records = max(1, int(max_records or 200))
        self._records: Dict[str, Dict[str, Any]] = {}

    def create(
        self,
        *,
        analysis_trace_id: str,
        capability_id: str,
        map_context: Dict[str, Any],
        tool_results: List[Dict[str, Any]],
        sources: List[Dict[str, Any]],
        evidence: Dict[str, Any],
        now_ms: Optional[int] = None,
    ) -> Dict[str, Any]:
        created_at_ms = int(now_ms if now_ms is not None else time.time() * 1000)
        fingerprint = build_scope_fingerprint(map_context)
        raw_id = f"{analysis_trace_id}:{fingerprint}:{created_at_ms}"
        snapshot_id = "evs_" + hashlib.sha256(raw_id.encode("utf-8")).hexdigest()[:24]
        record = {
            "evidenceSnapshotId": snapshot_id,
            "analysisTraceId": analysis_trace_id,
            "capabilityId": capability_id,
            "scopeFingerprint": fingerprint,
            "scope": build_scope(map_context),
            "toolResults": list(tool_results or []),
            "sources": list(sources or []),
            "evidence": dict(evidence or {}),
            "createdAtMs": created_at_ms,
            "ttlSeconds": self.ttl_seconds,
        }
        self._records[snapshot_id] = record
        self._trim(created_at_ms)
        return dict(record)

    def get(self, snapshot_id: str, map_context: Dict[str, Any], now_ms: Optional[int] = None) -> Optional[Dict[str, Any]]:
        record = self._records.get(str(snapshot_id or ""))
        if not record:
            return None
        current_ms = int(now_ms if now_ms is not None else time.time() * 1000)
        if current_ms - int(record.get("createdAtMs") or 0) > self.ttl_seconds * 1000:
            return None
        if record.get("scopeFingerprint") != build_scope_fingerprint(map_context):
            return None
        return dict(record)

    def _trim(self, now_ms: int) -> None:
        expired = [
            key
            for key, value in self._records.items()
            if now_ms - int(value.get("createdAtMs") or 0) > self.ttl_seconds * 1000
        ]
        for key in expired:
            self._records.pop(key, None)
        if len(self._records) <= self.max_records:
            return
        ordered = sorted(self._records.items(), key=lambda item: int(item[1].get("createdAtMs") or 0))
        for key, _value in ordered[: len(self._records) - self.max_records]:
            self._records.pop(key, None)


def build_scope_fingerprint(map_context: Dict[str, Any]) -> str:
    canonical = json.dumps(build_scope(map_context), ensure_ascii=True, sort_keys=True, separators=(",", ":"))
    return "sha256:" + hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:16]


def build_scope(map_context: Dict[str, Any]) -> Dict[str, Any]:
    ctx = map_context if isinstance(map_context, dict) else {}
    extra = ctx.get("extra") if isinstance(ctx.get("extra"), dict) else {}
    raw_context = extra.get("rawContext") if isinstance(extra.get("rawContext"), dict) else {}
    query = raw_context.get("query") if isinstance(raw_context.get("query"), dict) else {}
    obj = ctx.get("mapObject") if isinstance(ctx.get("mapObject"), dict) else {}
    geometry = ctx.get("geometry") or ctx.get("regionGeometry")
    return compact(
        {
            "projectId": first_present(extra.get("projectId"), query.get("projectId"), query.get("project_id")),
            "mode": ctx.get("mode"),
            "objectType": first_present(obj.get("objectType"), obj.get("object_type")),
            "objectId": first_present(obj.get("objectId"), obj.get("object_id"), obj.get("id")),
            "routeCode": first_present(obj.get("routeCode"), obj.get("route_code"), ctx.get("routeCode"), ctx.get("route_code")),
            "startStake": first_present(obj.get("startStake"), obj.get("start_stake"), obj.get("stakeStart"), obj.get("stake_start")),
            "endStake": first_present(obj.get("endStake"), obj.get("end_stake"), obj.get("stakeEnd"), obj.get("stake_end")),
            "geometry": geometry,
            "metric": first_present(query.get("indexCode"), query.get("index_code"), extra.get("indexCode"), extra.get("index_code")),
            "selectedLayers": ctx.get("selectedLayers"),
            "grade": query.get("grade"),
            "sectionTier": first_present(query.get("sectionTier"), query.get("section_tier")),
        }
    )


def first_present(*values: Any) -> Any:
    for value in values:
        if value not in (None, "", [], {}):
            return value
    return None


def compact(value: Dict[str, Any]) -> Dict[str, Any]:
    return {key: item for key, item in value.items() if item not in (None, "", [], {})}
