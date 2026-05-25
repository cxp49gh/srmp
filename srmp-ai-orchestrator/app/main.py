import hashlib
import json
import time
from typing import Any, Dict, List, Optional

from fastapi import FastAPI, Header, HTTPException, Query

from .config import settings
from .governance import governance_capability_detail, governance_config_bundle, governance_config_draft_validate, governance_config_publish_requests, governance_config_publish_rollback, governance_config_publish_submit, governance_policy_examples, governance_readiness, governance_summary, governance_tool_detail, governance_tool_impact, resolve_capability, validate_governance
from .intent import recognize_intent
from .java_tools import JavaToolGateway
from .live_trace import LiveTraceStore
from .llm_client import LlmClient
from .map_agent_run import MapAgentRunWorkflow
from .observability import runtime_audit_store
from .plan_preview import enrich_tool_plan
from .planner import plan_tools
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
        "adaptivePlanningEnabled": settings.adaptive_planning_enabled,
        "maxAdaptiveIterations": settings.max_adaptive_iterations,
        "maxAdaptiveAddedTools": settings.max_adaptive_added_tools,
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
    if bool(config.get("adaptivePlanningEnabled")) and int(config.get("maxToolCalls") or 0) < 2:
        warnings.append({"level": "WARN", "code": "ADAPTIVE_WITH_LOW_TOOL_LIMIT", "message": "自适应规划已开启，但 maxToolCalls 小于 2，几乎没有追加工具空间。"})
    if bool(config.get("adaptivePlanningEnabled")) and int(config.get("maxAdaptiveIterations") or 0) <= 0:
        warnings.append({"level": "WARN", "code": "ADAPTIVE_ITERATIONS_DISABLED", "message": "自适应规划已开启，但 maxAdaptiveIterations 为 0。"})
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


def _run_governance_policy_case(example: Dict[str, Any]) -> Dict[str, Any]:
    request_payload = example.get("request") if isinstance(example.get("request"), dict) else {}
    expect = example.get("expect") if isinstance(example.get("expect"), dict) else {}
    request = MapAiAgentRequest(**request_payload)
    fallback_intent, detail = recognize_intent(request)
    capability = resolve_capability(request, fallback_intent=fallback_intent, intent_detail=detail)
    intent = capability.get("intent") or fallback_intent
    tool_plan = enrich_tool_plan(plan_tools(request, intent, detail, capability=capability))
    actual_tool_names = [str(item.get("toolName") or "") for item in tool_plan if item.get("toolName")]
    expected_capability = str(expect.get("capabilityId") or example.get("capabilityId") or "")
    required_tools = _string_list(expect.get("requiredTools"))
    prohibited_tools = _string_list(expect.get("prohibitedTools"))
    exact_tools = _string_list(expect.get("exactToolNames"))
    missing_tools = [name for name in required_tools if name not in actual_tool_names]
    prohibited_hits = [name for name in prohibited_tools if name in actual_tool_names]
    capability_mismatch = bool(expected_capability and expected_capability != capability.get("capabilityId"))
    exact_mismatch = bool(exact_tools and exact_tools != actual_tool_names)
    warnings: List[Dict[str, str]] = []
    if capability_mismatch:
        warnings.append({
            "code": "CAPABILITY_MISMATCH",
            "message": "期望能力 " + expected_capability + "，实际命中 " + str(capability.get("capabilityId") or ""),
        })
    if missing_tools:
        warnings.append({"code": "REQUIRED_TOOL_MISSING", "message": "缺少必选工具：" + ", ".join(missing_tools)})
    if prohibited_hits:
        warnings.append({"code": "PROHIBITED_TOOL_PLANNED", "message": "计划误用了禁用工具：" + ", ".join(prohibited_hits)})
    if exact_mismatch:
        warnings.append({"code": "EXACT_TOOLS_MISMATCH", "message": "计划工具与精确期望不一致。"})
    status = "PASS" if not warnings else "FAIL"
    return {
        "id": str(example.get("id") or ""),
        "name": str(example.get("name") or example.get("id") or ""),
        "status": status,
        "expectedCapabilityId": expected_capability,
        "actualCapabilityId": capability.get("capabilityId"),
        "actualCapabilityName": capability.get("name"),
        "actualIntent": intent,
        "fallbackIntent": fallback_intent,
        "expectedRequiredTools": required_tools,
        "expectedProhibitedTools": prohibited_tools,
        "expectedExactToolNames": exact_tools,
        "actualToolNames": actual_tool_names,
        "missingRequiredTools": missing_tools,
        "prohibitedToolNames": prohibited_hits,
        "warnings": warnings,
        "request": request.model_dump(exclude_none=True),
        "capability": capability,
        "toolPlan": tool_plan,
    }


def _string_list(value: Any) -> List[str]:
    if not isinstance(value, list):
        return []
    return [str(item).strip() for item in value if str(item or "").strip()]


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


@app.get("/api/srmp/langgraph/governance/capabilities")
async def governance_capabilities() -> dict:
    summary = governance_summary()
    return {
        "version": summary.get("version"),
        "configValid": summary.get("configValid"),
        "validation": summary.get("validation"),
        "capabilityCount": summary.get("capabilityCount"),
        "enabledCapabilityCount": summary.get("enabledCapabilityCount"),
        "capabilities": summary.get("capabilities") or [],
    }


@app.get("/api/srmp/langgraph/governance/config")
async def governance_config() -> dict:
    return governance_config_bundle()


@app.post("/api/srmp/langgraph/governance/config/draft/validate")
async def governance_config_draft_validate_endpoint(body: Dict[str, Any]) -> dict:
    return governance_config_draft_validate(body or {})


@app.get("/api/srmp/langgraph/governance/config/publish/requests")
async def governance_config_publish_requests_endpoint(
    limit: int = Query(default=20),
) -> dict:
    return governance_config_publish_requests(limit=limit)


@app.post("/api/srmp/langgraph/governance/config/publish/request")
async def governance_config_publish_request_endpoint(
    body: Optional[Dict[str, Any]] = None,
    x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-Id"),
    x_operator_id: Optional[str] = Header(default=None, alias="X-Operator-Id"),
    x_user_id: Optional[str] = Header(default=None, alias="X-User-Id"),
) -> dict:
    return governance_config_publish_submit(body or {}, actor=x_operator_id or x_user_id or "", tenant_id=x_tenant_id or "")


@app.post("/api/srmp/langgraph/governance/config/publish/requests/{request_id}/rollback")
async def governance_config_publish_rollback_endpoint(
    request_id: str,
    body: Optional[Dict[str, Any]] = None,
    x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-Id"),
    x_operator_id: Optional[str] = Header(default=None, alias="X-Operator-Id"),
    x_user_id: Optional[str] = Header(default=None, alias="X-User-Id"),
) -> dict:
    return governance_config_publish_rollback(request_id, body or {}, actor=x_operator_id or x_user_id or "", tenant_id=x_tenant_id or "")


@app.get("/api/srmp/langgraph/governance/capabilities/{capability_id}")
async def governance_capability(capability_id: str) -> dict:
    detail = governance_capability_detail(capability_id)
    if not detail:
        raise HTTPException(status_code=404, detail="Capability not found")
    return detail


@app.get("/api/srmp/langgraph/governance/tools")
async def governance_tools() -> dict:
    summary = governance_summary()
    return {
        "version": summary.get("toolVersion"),
        "configValid": summary.get("configValid"),
        "validation": summary.get("validation"),
        "toolCount": summary.get("toolCount"),
        "tools": summary.get("tools") or [],
    }


@app.get("/api/srmp/langgraph/governance/tools/impact")
async def governance_tools_impact() -> dict:
    return governance_tool_impact()


@app.get("/api/srmp/langgraph/governance/tools/{tool_name}")
async def governance_tool(
    tool_name: str,
    include_contract: bool = Query(default=False, alias="includeContract"),
    x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-Id"),
) -> dict:
    contract = await gateway.inspect_contract(tenant_id=x_tenant_id) if include_contract else None
    detail = governance_tool_detail(tool_name, contract=contract)
    if not detail:
        raise HTTPException(status_code=404, detail="Tool not found")
    return detail


@app.get("/api/srmp/langgraph/governance/readiness")
async def governance_readiness_endpoint(
    include_contract: bool = Query(default=True, alias="includeContract"),
    run_coverage: bool = Query(default=True, alias="runCoverage"),
    x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-Id"),
) -> dict:
    contract = await gateway.inspect_contract(tenant_id=x_tenant_id) if include_contract else None
    policy_coverage = _policy_coverage_payload() if run_coverage else None
    return governance_readiness(contract=contract, policy_coverage=policy_coverage)


@app.get("/api/srmp/langgraph/governance/policies/validate")
async def governance_policy_validate() -> dict:
    return validate_governance()


@app.post("/api/srmp/langgraph/governance/plan-simulate")
async def governance_plan_simulate(
    request: MapAiAgentRequest,
    x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-Id"),
    x_ai_trace_id: Optional[str] = Header(default=None, alias="X-AI-Trace-Id"),
) -> dict:
    plan = await workflow.plan(request=request, tenant_id=x_tenant_id, trace_id=x_ai_trace_id)
    return {
        "traceId": plan.get("traceId"),
        "strategy": plan.get("strategy"),
        "action": plan.get("action"),
        "intent": plan.get("intent"),
        "capabilityId": plan.get("capabilityId"),
        "capability": plan.get("capability") or {},
        "contextSummary": plan.get("contextSummary") or {},
        "toolPlan": plan.get("toolPlan") or [],
        "sourceHints": plan.get("sourceHints") or [],
        "warnings": plan.get("warnings") or [],
        "steps": plan.get("steps") or [],
    }


@app.get("/api/srmp/langgraph/governance/policies/coverage")
async def governance_policy_coverage() -> dict:
    return _policy_coverage_payload()


def _policy_coverage_payload() -> Dict[str, Any]:
    cases = [_run_governance_policy_case(example) for example in governance_policy_examples()]
    failed = [item for item in cases if item.get("status") != "PASS"]
    return {
        "version": governance_summary().get("version"),
        "caseCount": len(cases),
        "passedCount": len(cases) - len(failed),
        "failedCount": len(failed),
        "cases": cases,
    }


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
    adaptive_mode: str = Query(default="default", alias="adaptiveMode"),
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

    mode = (adaptive_mode or "default").strip().lower()
    if mode not in {"default", "off", "compare"}:
        raise HTTPException(status_code=400, detail="adaptiveMode must be default, off, or compare")
    if mode == "compare" and not execute:
        raise HTTPException(status_code=400, detail="adaptiveMode=compare requires execute=true replay")

    request = MapAiAgentRequest(**payload)
    replay_trace_id = x_ai_trace_id or "replay-" + str(record.get("traceId") or record.get("id") or record_id)
    tenant_id = x_tenant_id or record.get("tenantId")
    if execute:
        if mode == "compare":
            baseline = await _run_replay_once(
                request=_request_with_adaptive_off(request),
                tenant_id=tenant_id,
                trace_id=replay_trace_id + "-baseline",
            )
            adaptive = await _run_replay_once(
                request=_request_without_adaptive_override(request),
                tenant_id=tenant_id,
                trace_id=replay_trace_id + "-adaptive",
            )
            return {
                "execute": True,
                "adaptiveMode": "compare",
                "sourceRecordId": record.get("id"),
                "sourceTraceId": record.get("traceId"),
                "baseline": baseline,
                "adaptive": adaptive,
                "response": adaptive.get("response"),
                "compare": _build_adaptive_compare(baseline, adaptive),
            }

        replay_request = _request_with_adaptive_off(request) if mode == "off" else request
        started_at = time.perf_counter()
        response = await workflow.run(request=replay_request, tenant_id=tenant_id, trace_id=replay_trace_id)
        return {
            "execute": True,
            "adaptiveMode": mode,
            "costMs": int((time.perf_counter() - started_at) * 1000),
            "sourceRecordId": record.get("id"),
            "sourceTraceId": record.get("traceId"),
            "response": response.model_dump(exclude_none=True),
        }

    plan = await workflow.plan(request=request, tenant_id=tenant_id, trace_id=replay_trace_id)
    plan["execute"] = False
    plan["adaptiveMode"] = mode
    plan["sourceRecordId"] = record.get("id")
    plan["sourceTraceId"] = record.get("traceId")
    return plan


async def _run_replay_once(request: MapAiAgentRequest, tenant_id: Optional[str], trace_id: str) -> Dict[str, Any]:
    started_at = time.perf_counter()
    response = await workflow.run(request=request, tenant_id=tenant_id, trace_id=trace_id)
    return {
        "costMs": int((time.perf_counter() - started_at) * 1000),
        "traceId": trace_id,
        "response": response.model_dump(exclude_none=True),
    }


def _request_with_adaptive_off(request: MapAiAgentRequest) -> MapAiAgentRequest:
    cloned = request.model_copy(deep=True)
    options = dict(cloned.options or {})
    options["disableAdaptivePlanning"] = True
    cloned.options = options
    return cloned


def _request_without_adaptive_override(request: MapAiAgentRequest) -> MapAiAgentRequest:
    cloned = request.model_copy(deep=True)
    options = dict(cloned.options or {})
    options.pop("disableAdaptivePlanning", None)
    cloned.options = options
    return cloned


def _build_adaptive_compare(baseline: Dict[str, Any], adaptive: Dict[str, Any]) -> Dict[str, Any]:
    baseline_response = baseline.get("response") if isinstance(baseline, dict) else {}
    adaptive_response = adaptive.get("response") if isinstance(adaptive, dict) else {}
    baseline_data = baseline_response.get("data") if isinstance(baseline_response, dict) else {}
    adaptive_data = adaptive_response.get("data") if isinstance(adaptive_response, dict) else {}
    baseline_planning = _adaptive_planning_from_response_dict(baseline_response)
    adaptive_planning = _adaptive_planning_from_response_dict(adaptive_response)
    baseline_evidence = _evidence_from_response_dict(baseline_response)
    adaptive_evidence = _evidence_from_response_dict(adaptive_response)
    baseline_tool_total = int((baseline_data or {}).get("toolTotalCount") or len((baseline_response or {}).get("toolResults") or []))
    adaptive_tool_total = int((adaptive_data or {}).get("toolTotalCount") or len((adaptive_response or {}).get("toolResults") or []))
    baseline_business = int(baseline_evidence.get("businessHitCount") or 0)
    adaptive_business = int(adaptive_evidence.get("businessHitCount") or 0)
    baseline_knowledge = int(baseline_evidence.get("knowledgeHitCount") or 0)
    adaptive_knowledge = int(adaptive_evidence.get("knowledgeHitCount") or 0)
    return {
        "toolDelta": adaptive_tool_total - baseline_tool_total,
        "costDeltaMs": int((adaptive or {}).get("costMs") or 0) - int((baseline or {}).get("costMs") or 0),
        "baselineAdaptiveStatus": baseline_planning.get("status"),
        "adaptiveStatus": adaptive_planning.get("status"),
        "evidenceImproved": _evidence_improved(baseline_evidence, adaptive_evidence),
        "baselineEvidenceSufficient": bool(baseline_evidence.get("sufficient")),
        "adaptiveEvidenceSufficient": bool(adaptive_evidence.get("sufficient")),
        "baselineBusinessHitCount": baseline_business,
        "adaptiveBusinessHitCount": adaptive_business,
        "baselineKnowledgeHitCount": baseline_knowledge,
        "adaptiveKnowledgeHitCount": adaptive_knowledge,
    }


def _adaptive_planning_from_response_dict(response: Dict[str, Any]) -> Dict[str, Any]:
    data = response.get("data") if isinstance(response, dict) else {}
    trace = response.get("trace") if isinstance(response, dict) else {}
    planning = data.get("adaptivePlanning") if isinstance(data, dict) else None
    if not isinstance(planning, dict) and isinstance(trace, dict):
        planning = trace.get("adaptivePlanning")
    return planning if isinstance(planning, dict) else {}


def _evidence_from_response_dict(response: Dict[str, Any]) -> Dict[str, Any]:
    data = response.get("data") if isinstance(response, dict) else {}
    evidence = data.get("evidence") if isinstance(data, dict) else None
    if isinstance(evidence, dict):
        return evidence
    planning = _adaptive_planning_from_response_dict(response)
    evidence = planning.get("evidenceAfter") or planning.get("evidenceBefore")
    return evidence if isinstance(evidence, dict) else {}


def _evidence_improved(baseline: Dict[str, Any], adaptive: Dict[str, Any]) -> bool:
    if not bool(baseline.get("sufficient")) and bool(adaptive.get("sufficient")):
        return True
    baseline_hits = int(baseline.get("businessHitCount") or 0) + int(baseline.get("knowledgeHitCount") or 0)
    adaptive_hits = int(adaptive.get("businessHitCount") or 0) + int(adaptive.get("knowledgeHitCount") or 0)
    return adaptive_hits > baseline_hits


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
