import hashlib
import json
import time
from typing import Any, Dict, List, Optional

from fastapi import FastAPI, Header, HTTPException, Query

from .config import settings
from .java_tools import JavaToolGateway
from .live_trace import LiveTraceStore
from .llm_client import LlmClient
from .map_agent_run import MapAgentRunWorkflow
from .observability import runtime_audit_store
from .schemas import MapAgentRunRequest, MapAgentRunResponse, MapAiAgentRequest, MapAiAgentResponse, ToolCall
from .workflow import LANGGRAPH_AVAILABLE, LangGraphWorkflow, strategy_metadata

APP_VERSION = "50.11.0"

app = FastAPI(title="SRMP LangGraph Orchestrator", version=APP_VERSION)

gateway = JavaToolGateway()
live_trace_store = LiveTraceStore(max_records=settings.audit_max_records)
workflow = LangGraphWorkflow(gateway=gateway, llm_client=LlmClient(), live_trace_store=live_trace_store)
map_agent_run_workflow = MapAgentRunWorkflow(base_workflow=workflow, gateway=gateway, live_trace_store=live_trace_store)


def _safe_runtime_config() -> dict:
    return {
        "appName": settings.app_name,
        "strategyVersion": settings.strategy_version,
        "javaBaseUrl": settings.java_base_url,
        "javaToolExecutePath": settings.java_tool_execute_path,
        "javaToolListPath": settings.java_tool_list_path,
        "javaConnectTimeoutSeconds": settings.java_connect_timeout_seconds,
        "javaReadTimeoutSeconds": settings.java_read_timeout_seconds,
        "allowWriteTools": settings.allow_write_tools,
        "useLlm": settings.use_llm,
        "llmBaseUrlConfigured": bool(settings.llm_base_url),
        "llmModel": settings.llm_model,
        "llmApiKeyConfigured": bool(settings.llm_api_key),
        "llmConnectTimeoutSeconds": settings.llm_connect_timeout_seconds,
        "llmReadTimeoutSeconds": settings.llm_read_timeout_seconds,
        "llmMaxTokens": settings.llm_max_tokens,
        "llmCompactRetryEnabled": settings.llm_compact_retry_enabled,
        "llmRawPreviewChars": settings.llm_raw_preview_chars,
        "allowedTools": settings.allowed_tools,
        "maxToolCalls": settings.max_tool_calls,
        "parallelToolExecution": settings.parallel_tool_execution,
        "maxParallelTools": settings.max_parallel_tools,
        "enableContextEnrich": settings.enable_context_enrich,
        "enableEvidenceFusion": settings.enable_evidence_fusion,
        "enableQualityGuard": settings.enable_quality_guard,
        "minAnswerChars": settings.min_answer_chars,
        "requireEvidencePrefix": settings.require_evidence_prefix,
        "auditMaxRecords": settings.audit_max_records,
        "auditPersistEnabled": settings.audit_persist_enabled,
        "auditPersistPath": settings.audit_persist_path,
        "auditLoadOnStart": settings.audit_load_on_start,
        "auditRedactEnabled": settings.audit_redact_enabled,
        "auditMaxPersistBytes": settings.audit_max_persist_bytes,
        "auditPruneDefaultRetainLatest": settings.audit_prune_default_retain_latest,
        "healthIncludeContractDefault": settings.health_include_contract_default,
        "healthIncludeGatewayDefault": settings.health_include_gateway_default,
        "healthPersistenceWarnRatio": settings.health_persistence_warn_ratio,
    }


def _config_fingerprint(config: Dict[str, Any]) -> str:
    raw = json.dumps(config, sort_keys=True, ensure_ascii=False, default=str)
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()[:16]


def _config_warnings(config: Dict[str, Any]) -> List[Dict[str, str]]:
    warnings: List[Dict[str, str]] = []
    if not config.get("allowedTools"):
        warnings.append({"level": "WARN", "code": "NO_ALLOWED_TOOLS", "message": "SRMP_LANGGRAPH_ALLOWED_TOOLS 为空时 Runtime 不做工具白名单限制，请确认生产环境是否符合预期。"})
    if config.get("useLlm") and not config.get("llmBaseUrlConfigured"):
        warnings.append({"level": "ERROR", "code": "LLM_BASE_URL_MISSING", "message": "已开启 Runtime LLM，但未配置 SRMP_LLM_BASE_URL。"})
    if config.get("useLlm") and not config.get("llmModel"):
        warnings.append({"level": "ERROR", "code": "LLM_MODEL_MISSING", "message": "已开启 Runtime LLM，但未配置 SRMP_LLM_MODEL。"})
    if config.get("useLlm") and not config.get("llmApiKeyConfigured"):
        warnings.append({"level": "WARN", "code": "LLM_API_KEY_MISSING", "message": "已开启 Runtime LLM，但未配置 SRMP_LLM_API_KEY；如经 Java Gateway 统一调用 LLM 可忽略。"})
    if int(config.get("maxToolCalls") or 0) <= 0:
        warnings.append({"level": "ERROR", "code": "MAX_TOOL_CALLS_INVALID", "message": "SRMP_LANGGRAPH_MAX_TOOL_CALLS 必须大于 0。"})
    if bool(config.get("parallelToolExecution")) and int(config.get("maxParallelTools") or 0) <= 0:
        warnings.append({"level": "ERROR", "code": "MAX_PARALLEL_TOOLS_INVALID", "message": "开启并行工具执行时 SRMP_LANGGRAPH_MAX_PARALLEL_TOOLS 必须大于 0。"})
    if bool(config.get("auditPersistEnabled")) and not config.get("auditPersistPath"):
        warnings.append({"level": "ERROR", "code": "AUDIT_PATH_MISSING", "message": "已开启审计持久化，但未配置 SRMP_LANGGRAPH_AUDIT_PERSIST_PATH。"})
    if bool(config.get("allowWriteTools")):
        warnings.append({"level": "WARN", "code": "WRITE_TOOLS_ENABLED", "message": "Runtime 已允许写工具，请确认前端/业务流程已有人工确认和权限校验。"})
    return warnings


def _runtime_config_payload() -> Dict[str, Any]:
    config = _safe_runtime_config()
    warnings = _config_warnings(config)
    return {
        "app": settings.app_name,
        "version": APP_VERSION,
        "generatedAtMs": int(time.time() * 1000),
        "reloadMode": "restart-required",
        "note": "当前 Runtime 配置来自进程启动时环境变量；修改环境变量后需要重启 srmp-ai-orchestrator 生效。",
        "fingerprint": _config_fingerprint(config),
        "safeConfig": config,
        "warnings": warnings,
        "warningCount": len(warnings),
        "redaction": {"enabled": settings.audit_redact_enabled, "keys": list(settings.audit_redact_keys or [])},
    }


def _check_item(name: str, ok: bool, detail: Any = None, level: str = "CRITICAL") -> Dict[str, Any]:
    return {"name": name, "ok": bool(ok), "level": level, "detail": detail}


def _overall_status(checks: List[Dict[str, Any]]) -> str:
    for item in checks:
        if not item.get("ok") and item.get("level") == "CRITICAL":
            return "DOWN"
    for item in checks:
        if not item.get("ok"):
            return "DEGRADED"
    return "UP"


def _persistence_health() -> Dict[str, Any]:
    status = runtime_audit_store.persistence_status()
    ok = not status.get("lastError")
    warn = False
    try:
        size = float(status.get("sizeBytes") or 0)
        max_bytes = float(status.get("maxBytes") or 0)
        warn = bool(max_bytes and size / max_bytes >= float(settings.health_persistence_warn_ratio or 0.9))
    except Exception:  # noqa: BLE001
        warn = False
    return {"ok": ok and not warn, "status": status, "nearLimit": warn}


@app.on_event("shutdown")
async def shutdown_event() -> None:
    await gateway.close()


@app.get("/health")
async def health() -> dict:
    return {
        "status": "UP",
        "app": settings.app_name,
        "version": APP_VERSION,
        "langgraphAvailable": LANGGRAPH_AVAILABLE,
        "javaBaseUrl": settings.java_base_url,
        "allowWriteTools": settings.allow_write_tools,
        "useLlm": settings.use_llm,
        "observability": runtime_audit_store.summary(),
        "strategy": strategy_metadata(),
    }


@app.get("/ready")
async def ready(x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-Id")) -> dict:
    start_payload = {
        "status": "UP",
        "app": settings.app_name,
        "version": APP_VERSION,
        "langgraphAvailable": LANGGRAPH_AVAILABLE,
        "javaBaseUrl": settings.java_base_url,
        "toolGateway": {},
        "observability": runtime_audit_store.summary(),
        "strategy": strategy_metadata(),
    }
    try:
        tools = await gateway.list_tools(tenant_id=x_tenant_id)
        data = tools.get("data") if isinstance(tools, dict) else tools
        count = len(data) if isinstance(data, list) else None
        ok = bool(tools.get("ok", True)) if isinstance(tools, dict) else True
        if not ok:
            start_payload["status"] = "DOWN"
        start_payload["toolGateway"] = {"ok": ok, "toolCount": count, "raw": tools}
    except Exception as exc:  # noqa: BLE001
        start_payload["status"] = "DOWN"
        start_payload["toolGateway"] = {"ok": False, "error": str(exc)}
    return start_payload


@app.get("/api/srmp/langgraph/strategy")
async def strategy() -> dict:
    return strategy_metadata()


@app.get("/api/srmp/langgraph/runtime/config")
async def runtime_config() -> dict:
    return _runtime_config_payload()


@app.get("/api/srmp/langgraph/diagnostics/health")
async def diagnostics_health(
    include_gateway: bool = Query(default=settings.health_include_gateway_default),
    include_contract: bool = Query(default=settings.health_include_contract_default),
    x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-Id"),
) -> dict:
    checks: List[Dict[str, Any]] = []
    config_payload = _runtime_config_payload()
    config_errors = [item for item in config_payload.get("warnings") or [] if item.get("level") == "ERROR"]
    checks.append(_check_item("runtime.langgraphAvailable", bool(LANGGRAPH_AVAILABLE), {"langgraphAvailable": LANGGRAPH_AVAILABLE}, level="WARN"))
    checks.append(_check_item("runtime.config", not config_errors, {"fingerprint": config_payload.get("fingerprint"), "warnings": config_payload.get("warnings")}, level="CRITICAL"))
    persistence = _persistence_health()
    checks.append(_check_item("runtime.persistence", bool(persistence.get("ok")), persistence, level="WARN"))
    summary = runtime_audit_store.summary()
    checks.append(_check_item("runtime.auditCapacity", int(summary.get("recordCount") or 0) <= int(summary.get("maxRecords") or 0), summary, level="WARN"))

    gateway_result: Dict[str, Any] = {}
    if include_gateway:
        gateway_result = await gateway.list_tools(tenant_id=x_tenant_id)
        checks.append(_check_item("java.toolGateway.list", bool(gateway_result.get("ok")), gateway_result, level="CRITICAL"))

    contract_result: Dict[str, Any] = {}
    if include_contract:
        contract_result = await gateway.inspect_contract(tenant_id=x_tenant_id)
        checks.append(_check_item("java.toolGateway.contract", bool(contract_result.get("ok")), contract_result, level="CRITICAL"))

    return {
        "status": _overall_status(checks),
        "app": settings.app_name,
        "version": APP_VERSION,
        "generatedAtMs": int(time.time() * 1000),
        "javaBaseUrl": settings.java_base_url,
        "strategy": strategy_metadata(),
        "config": config_payload,
        "checks": checks,
        "toolGateway": gateway_result,
        "contract": contract_result,
    }


@app.get("/api/srmp/langgraph/observability/summary")
async def observability_summary() -> dict:
    return runtime_audit_store.summary()


@app.get("/api/srmp/langgraph/observability/recent")
async def observability_recent(
    limit: int = Query(default=20, ge=1, le=100),
    status: Optional[str] = Query(default=None),
) -> dict:
    return {"records": runtime_audit_store.recent(limit=limit, status=status)}


@app.get("/api/srmp/langgraph/observability/record/{record_id}")
async def observability_record(record_id: str) -> dict:
    record = runtime_audit_store.get(record_id)
    if not record:
        raise HTTPException(status_code=404, detail="Runtime audit record not found")
    return record


@app.get("/api/srmp/langgraph/observability/export")
async def observability_export(
    limit: int = Query(default=30, ge=1, le=100),
    status: Optional[str] = Query(default=None),
) -> dict:
    return {
        "app": settings.app_name,
        "version": APP_VERSION,
        "javaBaseUrl": settings.java_base_url,
        "strategy": strategy_metadata(),
        "observability": runtime_audit_store.export_bundle(limit=limit, status=status),
    }


@app.get("/api/srmp/langgraph/observability/persistence")
async def observability_persistence() -> dict:
    return runtime_audit_store.persistence_status()


@app.get("/api/srmp/langgraph/observability/snapshot")
async def observability_snapshot(
    limit: int = Query(default=30, ge=1, le=100),
    status: Optional[str] = Query(default=None),
) -> dict:
    return {
        "app": settings.app_name,
        "version": APP_VERSION,
        "generatedAtMs": int(time.time() * 1000),
        "javaBaseUrl": settings.java_base_url,
        "runtime": {
            "langgraphAvailable": LANGGRAPH_AVAILABLE,
            "allowWriteTools": settings.allow_write_tools,
            "useLlm": settings.use_llm,
        },
        "strategy": strategy_metadata(),
        "safeConfig": _safe_runtime_config(),
        "observability": runtime_audit_store.export_bundle(limit=limit, status=status),
    }


@app.delete("/api/srmp/langgraph/observability/recent")
async def observability_clear() -> dict:
    return runtime_audit_store.clear()


@app.delete("/api/srmp/langgraph/observability/prune")
async def observability_prune(
    status: Optional[str] = Query(default=None),
    before_ms: Optional[int] = Query(default=None, alias="beforeMs"),
    retain_latest: int = Query(default=settings.audit_prune_default_retain_latest, ge=0, le=1000, alias="retainLatest"),
    include_persist: bool = Query(default=True, alias="includePersist"),
) -> dict:
    return runtime_audit_store.prune(
        status=status,
        before_ms=before_ms,
        retain_latest=retain_latest,
        include_persist=include_persist,
    )


@app.get("/api/srmp/langgraph/trace/live/{trace_id}")
async def live_trace(trace_id: str) -> dict:
    snapshot = live_trace_store.get(trace_id)
    if snapshot:
        return snapshot
    return live_trace_store.not_found(trace_id)


@app.get("/api/srmp/langgraph/debug/tool-gateway")
async def debug_tool_gateway(
    x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-Id"),
    smoke: bool = Query(default=False),
) -> dict:
    result = {"javaBaseUrl": settings.java_base_url}
    result["tools"] = await gateway.list_tools(tenant_id=x_tenant_id)
    if smoke:
        request = MapAiAgentRequest(message="Phase50.11 Tool Gateway smoke test", options={"topK": 1})
        call = ToolCall(toolName="knowledge.retrieve", args={"query": "道路养护 处置建议"}, reason="smoke test")
        smoke_result = await gateway.execute_tool(call=call, request=request, tenant_id=x_tenant_id or "default", trace_id="phase50-10-smoke")
        result["smoke"] = smoke_result.model_dump(exclude_none=True)
    return result


@app.get("/api/srmp/langgraph/debug/contract")
async def debug_contract(x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-Id")) -> dict:
    return await gateway.inspect_contract(tenant_id=x_tenant_id)


@app.get("/api/srmp/langgraph/debug/llm-probe")
async def debug_llm_probe(probe: bool = Query(default=False)) -> dict:
    client = workflow.llm_client
    diagnostics = client.diagnostics() if hasattr(client, "diagnostics") else {}
    data = {
        "enabled": settings.use_llm,
        "status": diagnostics.get("status") or ("READY" if settings.use_llm else "SKIPPED"),
        "available": False,
        "model": settings.llm_model,
        "baseUrlConfigured": bool(settings.llm_base_url),
        "apiKeyConfigured": bool(settings.llm_api_key),
        "connectTimeoutSeconds": settings.llm_connect_timeout_seconds,
        "readTimeoutSeconds": settings.llm_read_timeout_seconds,
        "maxTokens": settings.llm_max_tokens,
        "temperature": settings.llm_temperature,
        "compactRetryEnabled": settings.llm_compact_retry_enabled,
        "diagnostics": diagnostics,
    }
    if probe:
        if hasattr(client, "probe"):
            probe_data = await client.probe()
        else:
            answer = await client.chat("请只返回 OK") if hasattr(client, "chat") else ""
            probe_data = {"status": "SUCCESS" if answer else "FAILED", "available": bool(answer), "probeAnswerPreview": answer or ""}
        data.update(probe_data or {})
        data["available"] = data.get("status") == "SUCCESS"
    return data


@app.post("/api/srmp/langgraph/debug/plan")
async def debug_plan(
    request: MapAiAgentRequest,
    x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-Id"),
    x_ai_trace_id: Optional[str] = Header(default=None, alias="X-AI-Trace-Id"),
) -> dict:
    return await workflow.plan(request=request, tenant_id=x_tenant_id, trace_id=x_ai_trace_id)


@app.post("/api/srmp/langgraph/debug/replay/{record_id}")
async def debug_replay(
    record_id: str,
    execute: bool = Query(default=False),
    x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-Id"),
    x_ai_trace_id: Optional[str] = Header(default=None, alias="X-AI-Trace-Id"),
) -> dict:
    """基于内存审计记录回放。

    默认 execute=false，只回放 request_normalize -> tool_plan，不调用 Java 工具；
    execute=true 时会重新跑完整只读链路，适合修复 Tool Gateway/契约后验证同一请求。
    """
    record = runtime_audit_store.get(record_id)
    if not record:
        raise HTTPException(status_code=404, detail="Runtime audit record not found")
    payload = record.get("requestPayload")
    if not isinstance(payload, dict):
        raise HTTPException(status_code=400, detail="Runtime audit record has no replayable requestPayload")

    request = MapAiAgentRequest(**payload)
    replay_trace_id = x_ai_trace_id or "replay-" + str(record.get("traceId") or record.get("id") or record_id)
    tenant_id = x_tenant_id or record.get("tenantId")
    if execute:
        started_at = time.perf_counter()
        response = await workflow.run(request=request, tenant_id=tenant_id, trace_id=replay_trace_id)
        return {
            "execute": True,
            "costMs": int((time.perf_counter() - started_at) * 1000),
            "sourceRecordId": record.get("id"),
            "sourceTraceId": record.get("traceId"),
            "response": response.model_dump(exclude_none=True),
        }

    plan = await workflow.plan(request=request, tenant_id=tenant_id, trace_id=replay_trace_id)
    plan["execute"] = False
    plan["sourceRecordId"] = record.get("id")
    plan["sourceTraceId"] = record.get("traceId")
    return plan


@app.get("/api/srmp/langgraph/tools")
async def list_tools(x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-Id")) -> dict:
    return await gateway.list_tools(tenant_id=x_tenant_id)


@app.post("/api/srmp/langgraph/map-agent/run")
async def map_agent_run(
    request: MapAgentRunRequest,
    x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-Id"),
    x_ai_trace_id: Optional[str] = Header(default=None, alias="X-AI-Trace-Id"),
) -> MapAgentRunResponse:
    started_at = time.perf_counter()
    try:
        response = await map_agent_run_workflow.run(request=request, tenant_id=x_tenant_id, trace_id=x_ai_trace_id)
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
        return response
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
