import time
from typing import Any, Dict, List, Optional, Tuple

import httpx

from .config import settings
from .schemas import MapAiAgentRequest, ToolCall, ToolResult
from .source_binding import normalize_source

WRITE_HINTS = ("save", "delete", "update", "create", "archive")
OK_CODES = {"0", "200", "SUCCESS", "success", "OK", "ok"}


class JavaToolGateway:
    def __init__(self) -> None:
        timeout = httpx.Timeout(
            connect=settings.java_connect_timeout_seconds,
            read=settings.java_read_timeout_seconds,
            write=30.0,
            pool=30.0,
        )
        self.client = httpx.AsyncClient(timeout=timeout, trust_env=False)

    async def close(self) -> None:
        await self.client.aclose()

    async def list_tools(self, tenant_id: Optional[str] = None) -> Dict[str, Any]:
        headers = self._headers(tenant_id=tenant_id, trace_id=None)
        url = settings.java_base_url + settings.java_tool_list_path
        start = time.perf_counter()
        try:
            response = await self.client.get(url, headers=headers)
            response.raise_for_status()
            body = response.json()
            data, api_error = _unwrap_r(body)
            if api_error:
                return {
                    "ok": False,
                    "javaBaseUrl": settings.java_base_url,
                    "path": settings.java_tool_list_path,
                    "costMs": int((time.perf_counter() - start) * 1000),
                    "error": api_error,
                    "raw": body,
                }
            return {
                "ok": True,
                "javaBaseUrl": settings.java_base_url,
                "path": settings.java_tool_list_path,
                "costMs": int((time.perf_counter() - start) * 1000),
                "data": data,
                "raw": body,
            }
        except Exception as exc:  # noqa: BLE001
            return {
                "ok": False,
                "javaBaseUrl": settings.java_base_url,
                "path": settings.java_tool_list_path,
                "costMs": int((time.perf_counter() - start) * 1000),
                "error": _http_error_message(exc),
            }

    async def inspect_contract(self, tenant_id: Optional[str] = None) -> Dict[str, Any]:
        """对比 Runtime 白名单与 Java Tool Gateway 实际清单，快速定位工具契约不闭合。"""
        tools_response = await self.list_tools(tenant_id=tenant_id)
        java_tools = _extract_tool_names(tools_response.get("data") if isinstance(tools_response, dict) else tools_response)
        allowed = list(settings.allowed_tools or [])
        missing_in_java = [name for name in allowed if name not in java_tools]
        blocked_by_runtime = [name for name in java_tools if allowed and name not in allowed]
        write_like_java = [name for name in java_tools if self._is_write_tool(name)]
        write_blocked = [name for name in write_like_java if not settings.allow_write_tools]
        ok = bool(tools_response.get("ok", True)) and not missing_in_java
        return {
            "ok": ok,
            "javaBaseUrl": settings.java_base_url,
            "toolListPath": settings.java_tool_list_path,
            "toolExecutePath": settings.java_tool_execute_path,
            "strategyVersion": settings.strategy_version,
            "readOnly": not settings.allow_write_tools,
            "javaToolCount": len(java_tools),
            "javaTools": java_tools,
            "runtimeAllowedToolCount": len(allowed),
            "runtimeAllowedTools": allowed,
            "missingInJava": missing_in_java,
            "blockedByRuntimeWhitelist": blocked_by_runtime,
            "writeLikeJavaTools": write_like_java,
            "writeBlocked": write_blocked,
            "listTools": tools_response,
        }

    async def execute_tool(
        self,
        call: ToolCall,
        request: MapAiAgentRequest,
        tenant_id: Optional[str],
        trace_id: Optional[str],
    ) -> ToolResult:
        start = time.perf_counter()
        if not self._is_allowed(call.toolName):
            return ToolResult(
                toolName=call.toolName,
                success=False,
                summary="工具未在 LangGraph 白名单中开放",
                errorMessage="tool not allowed by SRMP_LANGGRAPH_ALLOWED_TOOLS",
                costMs=0,
            )
        if self._is_write_tool(call.toolName) and not settings.allow_write_tools:
            return ToolResult(
                toolName=call.toolName,
                success=False,
                summary="写操作工具未开放，请通过前端人工确认流程执行",
                errorMessage="write tool blocked",
                costMs=0,
            )

        map_context = request.mapContext.model_dump(exclude_none=True) if request.mapContext else {}
        payload: Dict[str, Any] = {
            "toolName": call.toolName,
            "tenantId": tenant_id or map_context.get("tenantId") or "default",
            "traceId": trace_id,
            "userQuestion": request.message,
            "mapContext": map_context,
            "options": request.options or {},
            "args": call.args or {},
        }
        headers = self._headers(tenant_id=payload["tenantId"], trace_id=trace_id)
        try:
            url = settings.java_base_url + settings.java_tool_execute_path
            response = await self.client.post(url, json=payload, headers=headers)
            response.raise_for_status()
            body = response.json()
            data, api_error = _unwrap_r(body)
            if api_error:
                return ToolResult(
                    toolName=call.toolName,
                    success=False,
                    summary="Java Tool Gateway 返回失败",
                    errorMessage=api_error,
                    data={"raw": body},
                    costMs=int((time.perf_counter() - start) * 1000),
                )
            if isinstance(data, dict):
                data.setdefault("toolName", call.toolName)
                data.setdefault("costMs", int((time.perf_counter() - start) * 1000))
                if data.get("error") and not data.get("errorMessage"):
                    data["errorMessage"] = str(data.get("error"))
                return ToolResult(**data)
            return ToolResult(
                toolName=call.toolName,
                success=False,
                summary="Java Tool Gateway 返回格式不符合预期",
                errorMessage=str(body)[:500],
                costMs=int((time.perf_counter() - start) * 1000),
            )
        except Exception as exc:  # noqa: BLE001
            return ToolResult(
                toolName=call.toolName,
                success=False,
                summary="调用 Java Tool Gateway 失败",
                errorMessage=_http_error_message(exc),
                data={
                    "javaBaseUrl": settings.java_base_url,
                    "path": settings.java_tool_execute_path,
                    "toolName": call.toolName,
                },
                costMs=int((time.perf_counter() - start) * 1000),
            )

    def _headers(self, tenant_id: Optional[str], trace_id: Optional[str]) -> Dict[str, str]:
        headers = {"Content-Type": "application/json"}
        safe_tenant_id = _safe_header_value(tenant_id)
        safe_trace_id = _safe_header_value(trace_id)
        if safe_tenant_id:
            headers["X-Tenant-Id"] = safe_tenant_id
        if safe_trace_id:
            headers["X-AI-Trace-Id"] = safe_trace_id
        return headers

    def _is_allowed(self, tool_name: str) -> bool:
        return not settings.allowed_tools or tool_name in settings.allowed_tools

    def _is_write_tool(self, tool_name: str) -> bool:
        lower = (tool_name or "").lower()
        return lower.startswith("solution.save") or lower.startswith("task.") or any(hint in lower for hint in WRITE_HINTS)


def _extract_tool_names(data: Any) -> List[str]:
    if isinstance(data, dict):
        for key in ("data", "list", "items", "records", "tools"):
            value = data.get(key)
            if isinstance(value, list):
                return _extract_tool_names(value)
        return []
    if not isinstance(data, list):
        return []
    names: List[str] = []
    for item in data:
        name = None
        if isinstance(item, dict):
            name = item.get("name") or item.get("toolName") or item.get("tool")
        elif isinstance(item, str):
            name = item
        if name:
            names.append(str(name))
    return sorted(set(names))


def _unwrap_r(body: Any) -> Tuple[Any, Optional[str]]:
    """兼容 Java R/ApiResult 包装，返回 (data, error)。"""
    if isinstance(body, dict) and ("code" in body or "message" in body) and "data" in body:
        code = body.get("code")
        message = body.get("message")
        if code is not None and str(code) not in OK_CODES:
            return None, str(message or code)
        return body.get("data"), None
    return body, None


def _safe_header_value(value: Optional[str]) -> str:
    if value is None:
        return ""
    text = str(value).replace("\r", "").replace("\n", "").strip()
    return text.encode("ascii", "ignore").decode("ascii")


def _http_error_message(exc: Exception) -> str:
    if isinstance(exc, httpx.HTTPStatusError):
        response = exc.response
        text = ""
        try:
            text = response.text[:500]
        except Exception:  # noqa: BLE001
            text = ""
        return f"HTTP {response.status_code} {response.reason_phrase}: {text}"
    return str(exc)


BUSINESS_SOURCE_TOOL_TYPES = {
    "gis.queryDiseases": "DISEASE",
    "gis.queryDiseasesByStakeRange": "DISEASE",
    "gis.queryAssessmentResults": "ASSESSMENT_RESULT",
    "gis.queryNearbyObjects": "",
    "gis.queryRegionSummary": "MAP_REGION",
}


def extract_knowledge_sources(tool_results: List[ToolResult]) -> List[Dict[str, Any]]:
    for result in tool_results:
        if result.toolName != "knowledge.retrieve" or not result.success:
            continue
        data = result.data
        candidates = _extract_source_candidates(data)
        if candidates:
            return [
                normalize_source(
                    _knowledge_source(hit, idx),
                    origin="EXPLICIT_METADATA",
                )
                for idx, hit in enumerate(candidates)
                if isinstance(hit, dict)
            ]
    return []


def extract_business_sources(tool_results: List[ToolResult], limit_per_tool: int = 3) -> List[Dict[str, Any]]:
    sources: List[Dict[str, Any]] = []
    for result in tool_results or []:
        if not result.success or result.toolName not in BUSINESS_SOURCE_TOOL_TYPES:
            continue
        default_object_type = BUSINESS_SOURCE_TOOL_TYPES.get(result.toolName, "")
        candidates = _extract_source_candidates(result.data)
        query_scope = _extract_query_scope(result.data)
        for idx, item in enumerate(candidates[: max(1, limit_per_tool)]):
            if not isinstance(item, dict):
                continue
            sources.append(
                normalize_source(
                    _business_source_candidate(
                        item,
                        query_scope,
                        result,
                        default_object_type,
                        idx,
                    ),
                    origin="BUSINESS_QUERY",
                )
            )
        if candidates:
            continue
        if result.toolName == "gis.queryRegionSummary":
            sources.append(
                normalize_source(
                    _region_summary_candidate(result),
                    origin="BUSINESS_QUERY",
                )
            )
        if query_scope:
            sources.append(
                normalize_source(
                    _business_scope_candidate(
                        query_scope,
                        result,
                        default_object_type,
                    ),
                    origin="BUSINESS_QUERY",
                )
            )
    return _dedupe_sources(sources)


def merge_sources(*groups: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    merged: List[Dict[str, Any]] = []
    for group in groups:
        merged.extend([item for item in group or [] if isinstance(item, dict)])
    return _dedupe_sources(merged)


def _extract_source_candidates(data: Any) -> List[Dict[str, Any]]:
    if isinstance(data, list):
        return [item for item in data if isinstance(item, dict)]
    if not isinstance(data, dict):
        return []
    grouped: List[Dict[str, Any]] = []
    for key in ("diseases", "assessments"):
        value = data.get(key)
        if isinstance(value, list):
            grouped.extend([item for item in value if isinstance(item, dict)])
    if grouped:
        return grouped
    for key in ("hits", "list", "records", "items", "sources"):
        value = data.get(key)
        if isinstance(value, list):
            return [item for item in value if isinstance(item, dict)]
    nested = data.get("data")
    if isinstance(nested, dict):
        return _extract_source_candidates(nested)
    if isinstance(nested, list):
        return [item for item in nested if isinstance(item, dict)]
    return []


def _knowledge_source(hit: Dict[str, Any], idx: int) -> Dict[str, Any]:
    title = _first(hit, "title", "documentTitle", "docTitle", "name", "sourceName") or f"知识片段 {idx + 1}"
    chunk_id = _first(hit, "chunkId", "chunk_id", "id", "knowledgeId")
    document_id = _first(hit, "documentId", "document_id", "docId")
    source_id = _first(hit, "sourceId", "source_id", "knowledgeId", "id", "documentId", "docId")
    source_type = _first(hit, "sourceType", "source_type", "type") or "KNOWLEDGE"
    section_title = _first(hit, "sectionTitle", "section_title", "section", "heading")
    score = _first(hit, "score", "similarity", "distance")
    content = _first(hit, "content", "chunk", "text", "summary")
    metadata = hit.get("metadata") if isinstance(hit.get("metadata"), dict) else {}
    content_excerpt = str(content)[:500] if content is not None else None
    source = {
        "chunkId": chunk_id,
        "documentId": document_id,
        "title": title,
        "sourceTitle": title,
        "sectionTitle": section_title,
        "sourceType": source_type,
        "sourceId": source_id,
        "score": score,
        "content": content_excerpt,
        "contentExcerpt": content_excerpt,
        "metadata": metadata,
    }
    return _compact(source)


def _business_object_id(item: Dict[str, Any], object_type: str) -> Any:
    normalized_type = _normalize_object_type(object_type)
    if normalized_type == "ASSESSMENT_RESULT":
        return _first(item, "id", "objectId", "sourceId", "source_id")
    return _first(item, "objectId", "object_id", "id", "sourceId", "source_id")


def _business_source_candidate(
    item: Dict[str, Any],
    query_scope: Dict[str, Any],
    result: ToolResult,
    default_object_type: str,
    idx: int,
) -> Dict[str, Any]:
    object_type = _infer_business_object_type(item, default_object_type)
    if not object_type:
        object_type = _normalize_object_type(
            _first(query_scope, "objectType", "object_type", "type", "layerType")
        )
    object_id = _business_object_id(item, object_type)
    if object_id in (None, ""):
        object_id = _first(query_scope, "objectId", "object_id", "id")
    route_code = _business_candidate_value(
        item,
        query_scope,
        "routeCode",
        "route_code",
        "route",
        "routeNo",
        "route_no",
    )
    start_stake = _business_candidate_value(
        item,
        query_scope,
        "startStake",
        "start_stake",
        "stakeStart",
        "stake_start",
        "beginStake",
        "begin_stake",
    )
    end_stake = _business_candidate_value(
        item,
        query_scope,
        "endStake",
        "end_stake",
        "stakeEnd",
        "stake_end",
        "finishStake",
        "finish_stake",
    )
    geometry = _business_candidate_value(item, query_scope, "geometry")
    bbox = _business_candidate_value(item, query_scope, "bbox")
    title = _business_source_title(item, result.toolName, object_type, idx)
    content = _business_source_content(item, result)
    source = {
        "sourceType": "BUSINESS_DATA",
        "title": title,
        "sourceTitle": title,
        "toolName": result.toolName,
        "objectType": object_type,
        "objectId": str(object_id) if object_id not in (None, "") else None,
        "id": str(object_id) if object_id not in (None, "") else None,
        "routeCode": route_code,
        "startStake": start_stake,
        "endStake": end_stake,
        "geometry": geometry,
        "bbox": bbox,
        "sourceId": str(object_id) if object_id not in (None, "") else f"{result.toolName}:{idx}",
        "content": content,
        "contentExcerpt": content,
        "metadata": {
            "toolName": result.toolName,
            "summary": result.summary,
            "count": result.count,
        },
        "raw": item,
    }
    return _compact(source)


def _region_summary_candidate(result: ToolResult) -> Dict[str, Any]:
    data = result.data if isinstance(result.data, dict) else {}
    geometry = data.get("geometry")
    bbox = data.get("bbox")
    has_spatial_scope = geometry not in (None, "", [], {}) or bbox not in (
        None,
        "",
        [],
        {},
    )
    return _compact(
        {
            "sourceType": "BUSINESS_DATA",
            "sourceTitle": "区域统计",
            "toolName": result.toolName,
            "objectType": "MAP_REGION" if has_spatial_scope else None,
            "geometry": geometry,
            "bbox": bbox,
            "sourceId": f"{result.toolName}:summary",
            "content": str(result.summary or "")[:500],
            "contentExcerpt": str(result.summary or "")[:500],
            "metadata": {
                "toolName": result.toolName,
                "summary": result.summary,
                "count": result.count,
            },
        }
    )


def _business_scope_candidate(
    query_scope: Dict[str, Any],
    result: ToolResult,
    default_object_type: str,
) -> Dict[str, Any]:
    scope = dict(query_scope)
    scope.pop("id", None)
    explicit_object_type = _normalize_object_type(
        _first(scope, "objectType", "object_type")
    )
    explicit_object_id = _first(scope, "objectId", "object_id")
    scope_object_type = (
        explicit_object_type
        if explicit_object_type
        else "" if explicit_object_id not in (None, "") else default_object_type
    )
    source = _business_source_candidate(
        scope,
        {},
        result,
        scope_object_type,
        0,
    )
    if not source.get("objectId"):
        source["sourceId"] = f"{result.toolName}:scope"
    source["raw"] = scope
    return source


def _extract_query_scope(data: Any) -> Dict[str, Any]:
    if not isinstance(data, dict):
        return {}
    query_scope = data.get("queryScope") or data.get("query_scope")
    if isinstance(query_scope, dict):
        return query_scope
    nested = data.get("data")
    return _extract_query_scope(nested)


def _business_candidate_value(
    item: Dict[str, Any],
    query_scope: Dict[str, Any],
    *keys: str,
) -> Any:
    value = _first(item, *keys)
    if value not in (None, ""):
        return value
    return _first(query_scope, *keys)


def _business_source_title(item: Dict[str, Any], tool_name: str, object_type: str, idx: int) -> str:
    disease = _first(item, "diseaseName", "disease_name", "diseaseType", "disease_type")
    severity = _first(item, "severity", "severityText", "severity_text")
    metric = _first(item, "mqi", "pqi", "pci", "rqi", "rdi")
    route_code = _first(item, "routeCode", "route_code", "route")
    stake = _format_stake(_first(item, "startStake", "start_stake"), _first(item, "endStake", "end_stake"))
    if object_type == "DISEASE":
        parts = ["病害", disease, severity, route_code, stake]
    elif object_type == "ASSESSMENT_RESULT":
        parts = ["评定结果", route_code, stake, f"MQI {metric}" if metric not in (None, "") else ""]
    elif object_type == "MAP_REGION":
        parts = ["区域统计", route_code]
    else:
        parts = ["业务数据", route_code, stake]
    title = "｜".join([str(part) for part in parts if part not in (None, "")])
    return title or f"{tool_name} 业务记录 {idx + 1}"


def _business_source_content(item: Dict[str, Any], result: ToolResult) -> str:
    values = []
    for key in ("summary", "disease_name", "disease_type", "severity", "mqi", "pqi", "pci", "route_code", "start_stake", "end_stake"):
        value = item.get(key)
        if value not in (None, ""):
            values.append(f"{key}={value}")
    if values:
        return "；".join(values)[:500]
    if result.summary:
        return str(result.summary)[:500]
    return ""


def _format_stake(start: Any, end: Any) -> str:
    if start in (None, ""):
        return ""
    left = str(start)
    right = str(end) if end not in (None, "") else ""
    if not left.upper().startswith("K"):
        left = "K" + left
    if right and not right.upper().startswith("K"):
        right = "K" + right
    return left + ("—" + right if right else "")


def _normalize_object_type(value: Any) -> str:
    raw = str(value or "").strip().upper()
    aliases = {
        "ASSESSMENT": "ASSESSMENT_RESULT",
        "ASSESSMENT_RESULT_RECORD": "ASSESSMENT_RESULT",
        "DISEASE_RECORD": "DISEASE",
        "ROADSECTION": "ROAD_SECTION",
        "ROADROUTE": "ROAD_ROUTE",
    }
    return aliases.get(raw, raw)


def _infer_business_object_type(item: Dict[str, Any], default_object_type: str) -> str:
    default_type = _normalize_object_type(default_object_type)
    if default_type:
        return default_type
    explicit = _normalize_object_type(_first(item, "objectType", "object_type", "type", "layerType"))
    if explicit in {"DISEASE", "ASSESSMENT_RESULT", "ROAD_ROUTE", "ROAD_SECTION", "MAP_REGION"}:
        return explicit
    if any(_first(item, key) not in (None, "") for key in ("diseaseName", "disease_name", "diseaseType", "disease_type", "severity", "quantity")):
        return "DISEASE"
    if any(_first(item, key) not in (None, "") for key in ("mqi", "pqi", "pci", "rqi", "rdi", "sci", "bci", "tci")):
        return "ASSESSMENT_RESULT"
    return explicit


def _compact(data: Dict[str, Any]) -> Dict[str, Any]:
    return {key: value for key, value in data.items() if value not in (None, "", [], {})}


def _dedupe_sources(sources: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    result: List[Dict[str, Any]] = []
    seen = set()
    for source in sources or []:
        key = (
            source.get("sourceType"),
            source.get("toolName"),
            source.get("objectType"),
            source.get("objectId") or source.get("sourceId") or source.get("title"),
            source.get("routeCode"),
            source.get("startStake"),
            source.get("endStake"),
        )
        if key in seen:
            continue
        seen.add(key)
        result.append(source)
    return result


def _first(data: Dict[str, Any], *keys: str) -> Any:
    for key in keys:
        value = data.get(key)
        if value not in (None, ""):
            return value
    return None
