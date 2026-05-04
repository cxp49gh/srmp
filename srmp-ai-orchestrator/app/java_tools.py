import time
from typing import Any, Dict, List, Optional

import httpx

from .config import settings
from .schemas import MapAiAgentRequest, ToolCall, ToolResult

WRITE_HINTS = ("save", "delete", "update", "create", "archive")


class JavaToolGateway:
    def __init__(self) -> None:
        timeout = httpx.Timeout(
            connect=settings.java_connect_timeout_seconds,
            read=settings.java_read_timeout_seconds,
            write=30.0,
            pool=30.0,
        )
        self.client = httpx.AsyncClient(timeout=timeout)

    async def close(self) -> None:
        await self.client.aclose()

    async def list_tools(self, tenant_id: Optional[str] = None) -> Dict[str, Any]:
        headers = self._headers(tenant_id=tenant_id, trace_id=None)
        url = settings.java_base_url + settings.java_tool_list_path
        response = await self.client.get(url, headers=headers)
        response.raise_for_status()
        return response.json()

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
            data = _unwrap_r(body)
            if isinstance(data, dict):
                data.setdefault("toolName", call.toolName)
                data.setdefault("costMs", int((time.perf_counter() - start) * 1000))
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
                errorMessage=str(exc),
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


def _unwrap_r(body: Any) -> Any:
    if isinstance(body, dict) and "data" in body and ("code" in body or "message" in body):
        return body.get("data")
    return body


def extract_knowledge_sources(tool_results: List[ToolResult]) -> List[Dict[str, Any]]:
    for result in tool_results:
        if result.toolName != "knowledge.retrieve" or not result.success:
            continue
        data = result.data
        if isinstance(data, dict):
            hits = data.get("hits") or data.get("list") or data.get("records")
            if isinstance(hits, list):
                return [hit for hit in hits if isinstance(hit, dict)]
        if hasattr(data, "hits") and isinstance(data.hits, list):
            return data.hits
    return []
