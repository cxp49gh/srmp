import time
from typing import Any, Dict, List, Optional, Tuple

import httpx

from .config import settings
from .schemas import MapAiAgentRequest, ToolCall, ToolResult

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
        if tenant_id:
            headers["X-Tenant-Id"] = tenant_id
        if trace_id:
            headers["X-AI-Trace-Id"] = trace_id
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


def extract_knowledge_sources(tool_results: List[ToolResult]) -> List[Dict[str, Any]]:
    for result in tool_results:
        if result.toolName != "knowledge.retrieve" or not result.success:
            continue
        data = result.data
        candidates = _extract_source_candidates(data)
        if candidates:
            return [_normalize_source(hit, idx) for idx, hit in enumerate(candidates) if isinstance(hit, dict)]
    return []


def _extract_source_candidates(data: Any) -> List[Dict[str, Any]]:
    if isinstance(data, list):
        return [item for item in data if isinstance(item, dict)]
    if not isinstance(data, dict):
        return []
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


def _normalize_source(hit: Dict[str, Any], idx: int) -> Dict[str, Any]:
    title = _first(hit, "title", "documentTitle", "docTitle", "name", "sourceName") or f"知识片段 {idx + 1}"
    chunk_id = _first(hit, "chunkId", "chunk_id", "id", "knowledgeId")
    document_id = _first(hit, "documentId", "document_id", "docId")
    source_id = _first(hit, "sourceId", "source_id", "knowledgeId", "id", "documentId", "docId")
    source_type = _first(hit, "sourceType", "source_type", "type")
    section_title = _first(hit, "sectionTitle", "section_title", "section", "heading")
    score = _first(hit, "score", "similarity", "distance")
    content = _first(hit, "content", "chunk", "text", "summary")
    metadata = hit.get("metadata") if isinstance(hit.get("metadata"), dict) else {}
    return {
        "chunkId": chunk_id,
        "documentId": document_id,
        "title": title,
        "sectionTitle": section_title,
        "sourceType": source_type,
        "sourceId": source_id,
        "score": score,
        "content": str(content)[:500] if content is not None else None,
        "metadata": metadata,
    }


def _first(data: Dict[str, Any], *keys: str) -> Any:
    for key in keys:
        value = data.get(key)
        if value not in (None, ""):
            return value
    return None
