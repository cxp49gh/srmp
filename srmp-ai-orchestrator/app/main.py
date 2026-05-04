import time
from typing import Optional

from fastapi import FastAPI, Header, Query

from .config import settings
from .java_tools import JavaToolGateway
from .llm_client import LlmClient
from .observability import runtime_audit_store
from .schemas import MapAiAgentRequest, ToolCall
from .workflow import LANGGRAPH_AVAILABLE, LangGraphWorkflow

app = FastAPI(title="SRMP LangGraph Orchestrator", version="50.5.0")

gateway = JavaToolGateway()
workflow = LangGraphWorkflow(gateway=gateway, llm_client=LlmClient())


@app.on_event("shutdown")
async def shutdown_event() -> None:
    await gateway.close()


@app.get("/health")
async def health() -> dict:
    return {
        "status": "UP",
        "app": settings.app_name,
        "version": "50.5.0",
        "langgraphAvailable": LANGGRAPH_AVAILABLE,
        "javaBaseUrl": settings.java_base_url,
        "allowWriteTools": settings.allow_write_tools,
        "useLlm": settings.use_llm,
        "observability": runtime_audit_store.summary(),
    }


@app.get("/ready")
async def ready(x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-Id")) -> dict:
    start_payload = {
        "status": "UP",
        "app": settings.app_name,
        "version": "50.5.0",
        "langgraphAvailable": LANGGRAPH_AVAILABLE,
        "javaBaseUrl": settings.java_base_url,
        "toolGateway": {},
        "observability": runtime_audit_store.summary(),
    }
    try:
        tools = await gateway.list_tools(tenant_id=x_tenant_id)
        data = tools.get("data") if isinstance(tools, dict) else tools
        count = len(data) if isinstance(data, list) else None
        start_payload["toolGateway"] = {"ok": True, "toolCount": count, "raw": tools}
    except Exception as exc:  # noqa: BLE001
        start_payload["status"] = "DOWN"
        start_payload["toolGateway"] = {"ok": False, "error": str(exc)}
    return start_payload


@app.get("/api/srmp/langgraph/observability/summary")
async def observability_summary() -> dict:
    return runtime_audit_store.summary()


@app.get("/api/srmp/langgraph/observability/recent")
async def observability_recent(
    limit: int = Query(default=20, ge=1, le=100),
    status: Optional[str] = Query(default=None),
) -> dict:
    return {"records": runtime_audit_store.recent(limit=limit, status=status)}


@app.delete("/api/srmp/langgraph/observability/recent")
async def observability_clear() -> dict:
    return runtime_audit_store.clear()


@app.get("/api/srmp/langgraph/debug/tool-gateway")
async def debug_tool_gateway(
    x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-Id"),
    smoke: bool = Query(default=False),
) -> dict:
    result = {"javaBaseUrl": settings.java_base_url}
    result["tools"] = await gateway.list_tools(tenant_id=x_tenant_id)
    if smoke:
        request = MapAiAgentRequest(message="Phase50.5 Tool Gateway smoke test", options={"topK": 1})
        call = ToolCall(toolName="knowledge.retrieve", args={"query": "道路养护 处置建议"}, reason="smoke test")
        smoke_result = await gateway.execute_tool(call=call, request=request, tenant_id=x_tenant_id or "default", trace_id="phase50-5-smoke")
        result["smoke"] = smoke_result.model_dump(exclude_none=True)
    return result


@app.get("/api/srmp/langgraph/tools")
async def list_tools(x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-Id")) -> dict:
    return await gateway.list_tools(tenant_id=x_tenant_id)


@app.post("/api/srmp/langgraph/map-agent/chat")
async def map_agent_chat(
    request: MapAiAgentRequest,
    x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-Id"),
    x_ai_trace_id: Optional[str] = Header(default=None, alias="X-AI-Trace-Id"),
) -> dict:
    started_at = time.perf_counter()
    try:
        response = await workflow.run(request=request, tenant_id=x_tenant_id, trace_id=x_ai_trace_id)
        cost_ms = int((time.perf_counter() - started_at) * 1000)
        audit_record = runtime_audit_store.record_success(
            request=request,
            response=response,
            tenant_id=x_tenant_id,
            trace_id=x_ai_trace_id,
            cost_ms=cost_ms,
        )
        response.data.setdefault("runtimeAuditId", audit_record.get("id"))
        response.data.setdefault("runtimeCostMs", cost_ms)
        response.trace.setdefault("runtimeAuditId", audit_record.get("id"))
        return response.model_dump(exclude_none=True)
    except Exception as exc:  # noqa: BLE001
        cost_ms = int((time.perf_counter() - started_at) * 1000)
        runtime_audit_store.record_failure(
            request=request,
            tenant_id=x_tenant_id,
            trace_id=x_ai_trace_id,
            cost_ms=cost_ms,
            error=exc,
        )
        raise
