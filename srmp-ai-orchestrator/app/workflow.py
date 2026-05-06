import asyncio
import re
import time
import uuid
from typing import Any, Dict, List, Optional, Tuple, TypedDict

from .config import settings
from .answer_enhancers import enhance_answer
from .intent import recognize_intent
from .java_tools import JavaToolGateway, extract_knowledge_sources
from .llm_client import LlmClient
from .planner import plan_tools
from .prompt import build_answer_prompt, fallback_answer
from .schemas import MapAiAgentRequest, MapAiAgentResponse, MapAiContext, ToolCall, ToolResult

try:
    from langgraph.graph import END, StateGraph

    LANGGRAPH_AVAILABLE = True
except Exception:
    StateGraph = None
    END = "__end__"
    LANGGRAPH_AVAILABLE = False


NODE_FLOW = [
    "request_normalize",
    "context_build",
    "intent_recognize",
    "context_enrich",
    "tool_planning",
    "tool_execute",
    "evidence_fuse",
    "answer_generate",
    "quality_guard",
]

PLAN_NODE_FLOW = [
    "request_normalize",
    "context_build",
    "intent_recognize",
    "context_enrich",
    "tool_planning",
]


class AgentState(TypedDict, total=False):
    request: MapAiAgentRequest
    tenant_id: Optional[str]
    trace_id: str
    started_at: float
    steps: List[Dict[str, Any]]
    context_summary: Dict[str, Any]
    intent: str
    intent_detail: Dict[str, Any]
    tool_plan: List[ToolCall]
    toolPlan: List[ToolCall]
    tool_results: List[ToolResult]
    evidence: Dict[str, Any]
    answer: str
    quality: Dict[str, Any]


def strategy_metadata() -> Dict[str, Any]:
    return {
        "strategyVersion": settings.strategy_version,
        "readOnly": not settings.allow_write_tools,
        "nodeFlow": NODE_FLOW,
        "planNodeFlow": PLAN_NODE_FLOW,
        "parallelToolExecution": settings.parallel_tool_execution,
        "maxParallelTools": settings.max_parallel_tools,
        "maxToolCalls": settings.max_tool_calls,
        "allowedTools": settings.allowed_tools,
        "qualityGuard": settings.enable_quality_guard,
        "contextEnrich": settings.enable_context_enrich,
        "evidenceFusion": settings.enable_evidence_fusion,
        "runtimeReplay": True,
        "diagnosticExport": True,
    }


class LangGraphWorkflow:
    def __init__(self, gateway: JavaToolGateway, llm_client: LlmClient):
        self.gateway = gateway
        self.llm_client = llm_client
        self.graph = self._build_graph() if LANGGRAPH_AVAILABLE else None

    async def run(self, request: MapAiAgentRequest, tenant_id: Optional[str], trace_id: Optional[str]) -> MapAiAgentResponse:
        state: AgentState = self._new_state(request=request, tenant_id=tenant_id, trace_id=trace_id)
        if self.graph is not None:
            final_state = await self.graph.ainvoke(state)
        else:
            final_state = await self._run_sequential(state, NODE_FLOW)
        return self._to_response(final_state)

    async def plan(self, request: MapAiAgentRequest, tenant_id: Optional[str], trace_id: Optional[str]) -> Dict[str, Any]:
        """只跑归一化、意图识别、工具规划；用于前端/联调解释为什么会调这些工具。"""
        state: AgentState = self._new_state(request=request, tenant_id=tenant_id, trace_id=trace_id)
        final_state = await self._run_sequential(state, PLAN_NODE_FLOW)
        return {
            "traceId": final_state.get("trace_id"),
            "strategy": strategy_metadata(),
            "intent": final_state.get("intent"),
            "contextSummary": final_state.get("context_summary", {}),
            "normalizedRequest": final_state["request"].model_dump(exclude_none=True),
            "toolPlan": [item.model_dump(exclude_none=True) for item in final_state.get("tool_plan", [])],
            "steps": final_state.get("steps", []),
        }

    def _new_state(self, request: MapAiAgentRequest, tenant_id: Optional[str], trace_id: Optional[str]) -> AgentState:
        safe_request = request or MapAiAgentRequest()
        resolved_trace_id = trace_id or _option_str(safe_request, "traceId") or "lg-" + uuid.uuid4().hex
        return {
            "request": safe_request,
            "tenant_id": tenant_id,
            "trace_id": resolved_trace_id,
            "started_at": time.perf_counter(),
            "steps": [],
        }

    def _build_graph(self):
        builder = StateGraph(AgentState)
        builder.add_node("request_normalize", self._request_normalize)
        builder.add_node("context_build", self._context_build)
        builder.add_node("intent_recognize", self._intent_recognize)
        builder.add_node("context_enrich", self._context_enrich)
        builder.add_node("tool_planning", self._tool_plan)
        builder.add_node("tool_execute", self._tool_execute)
        builder.add_node("evidence_fuse", self._evidence_fuse)
        builder.add_node("answer_generate", self._answer_generate)
        builder.add_node("quality_guard", self._quality_guard)
        builder.set_entry_point("request_normalize")
        for left, right in zip(NODE_FLOW, NODE_FLOW[1:]):
            builder.add_edge(left, right)
        builder.add_edge("quality_guard", END)
        return builder.compile()

    async def _run_sequential(self, state: AgentState, nodes: List[str]) -> AgentState:
        for node in nodes:
            updates = await self._node_handler(node)(state)
            state.update(updates or {})
        return state

    def _node_handler(self, node: str):
        if node == "tool_planning":
            return self._tool_plan
        handler = getattr(self, "_" + node, None)
        if handler is None:
            raise AttributeError("LangGraphWorkflow has no handler for node: " + str(node))
        return handler

    def _step(
        self,
        state: AgentState,
        node: str,
        message: str,
        data: Optional[Dict[str, Any]] = None,
        status: str = "SUCCESS",
        count: Optional[int] = None,
        error: Optional[str] = None,
    ) -> None:
        steps = state.setdefault("steps", [])
        item = {
            "node": node,
            "name": node,
            "message": message,
            "status": status,
            "data": data or {},
            "elapsedMs": int((time.perf_counter() - state.get("started_at", time.perf_counter())) * 1000),
        }
        if count is not None:
            item["count"] = count
        if error:
            item["error"] = error
            item["errorMessage"] = error
        steps.append(item)

    async def _request_normalize(self, state: AgentState) -> Dict[str, Any]:
        request = state["request"]
        normalized, changed = normalize_request(request, tenant_id=state.get("tenant_id"), trace_id=state.get("trace_id"))
        self._step(state, "request_normalize", "兼容旧字段并归一化地图上下文", {"changed": changed})
        return {"request": normalized}

    async def _context_build(self, state: AgentState) -> Dict[str, Any]:
        request = state["request"]
        summary = _summarize_context(request)
        self._step(state, "context_build", "构建地图上下文摘要", summary)
        return {"context_summary": summary}

    async def _intent_recognize(self, state: AgentState) -> Dict[str, Any]:
        intent, detail = recognize_intent(state["request"])
        self._step(state, "intent_recognize", "识别用户意图", {"intent": intent, "intentDetail": detail})
        return {"intent": intent, "intent_detail": detail}

    async def _context_enrich(self, state: AgentState) -> Dict[str, Any]:
        if not settings.enable_context_enrich:
            self._step(state, "context_enrich", "上下文补全已关闭")
            return {}

        request = state["request"]
        context = request.mapContext
        obj = context.mapObject if context and context.mapObject else {}
        changed: Dict[str, Any] = {}

        if context:
            if not context.routeCode:
                route_code = _pick(obj, "routeCode", "route_code", "route", "routeNo", "route_no", "routeName")
                if route_code:
                    context.routeCode = str(route_code)
                    changed["routeCode"] = route_code
            if not context.year:
                year = _int_or_none(_pick(obj, "year", "detectYear", "evalYear", "assessmentYear")) or _int_or_none((request.options or {}).get("year"))
                if year:
                    context.year = year
                    changed["year"] = year
            if not context.geometry:
                geometry = _pick(obj, "geometry", "geojson", "geom") or _pick(request.context or {}, "geometry", "geojson", "geom")
                if isinstance(geometry, dict):
                    context.geometry = geometry
                    changed["geometry"] = True
            context.extra = context.extra or {}
            context.extra["orchestratorStrategy"] = settings.strategy_version
            context.extra["readOnly"] = not settings.allow_write_tools
            context.extra["intent"] = state.get("intent")
            context.extra["traceId"] = state.get("trace_id")
            if obj:
                context.extra["mapObjectKeys"] = sorted(list(obj.keys()))[:30]

        summary = _summarize_context(request)
        self._step(state, "context_enrich", "补全路线/年度/对象摘要", {"changed": changed, "summary": summary})
        return {"request": request, "context_summary": summary}

    async def _tool_plan(self, state: AgentState) -> Dict[str, Any]:
        calls = plan_tools(state["request"], state["intent"], state.get("intent_detail", {}))
        self._step(state, "tool_plan", "规划只读工具", {"strategy": settings.strategy_version, "tools": [item.model_dump() for item in calls]})
        return {"tool_plan": calls}

    async def _tool_execute(self, state: AgentState) -> Dict[str, Any]:
        calls = state.get("tool_plan", [])
        if not calls:
            self._step(state, "tool_execute", "无可执行工具")
            return {"tool_results": []}

        if settings.parallel_tool_execution:
            semaphore = asyncio.Semaphore(max(1, settings.max_parallel_tools))

            async def run_call(call: ToolCall) -> ToolResult:
                async with semaphore:
                    return await self._execute_single_tool(state, call)

            results = list(await asyncio.gather(*(run_call(call) for call in calls)))
        else:
            results = []
            for call in calls:
                results.append(await self._execute_single_tool(state, call))

        self._step(
            state,
            "tool_execute",
            "执行只读工具",
            {
                "success": sum(1 for item in results if item.success),
                "failed": sum(1 for item in results if not item.success),
                "tools": [item.toolName for item in results],
            },
            status="SUCCESS" if any(item.success for item in results) else "FAILED",
            count=len(results),
        )
        return {"tool_results": results}

    async def _execute_single_tool(self, state: AgentState, call: ToolCall) -> ToolResult:
        if call.toolName not in settings.allowed_tools:
            return ToolResult(toolName=call.toolName, success=False, reason="tool not allowed in readonly strategy", errorMessage="TOOL_NOT_ALLOWED")
        return await self.gateway.execute_tool(
            call=call,
            request=state["request"],
            tenant_id=state.get("tenant_id") or "default",
            trace_id=state.get("trace_id"),
        )

    async def _evidence_fuse(self, state: AgentState) -> Dict[str, Any]:
        results = state.get("tool_results", [])
        success = [item for item in results if item.success]
        failed = [item for item in results if not item.success]
        business_hit_count = 0
        knowledge_hit_count = 0

        for item in success:
            count = item.count if isinstance(item.count, int) else _estimate_hit_count(item.data)
            if item.toolName == "knowledge.retrieve":
                knowledge_hit_count += count
            else:
                business_hit_count += count

        evidence = {
            "strategyVersion": settings.strategy_version,
            "toolSuccessCount": len(success),
            "toolFailedCount": len(failed),
            "businessHitCount": business_hit_count,
            "knowledgeHitCount": knowledge_hit_count,
            "sufficient": bool(success) and (business_hit_count > 0 or knowledge_hit_count > 0),
            "failedTools": [{"toolName": item.toolName, "error": item.errorMessage or item.error} for item in failed],
            "toolSummary": [
                {
                    "toolName": item.toolName,
                    "success": item.success,
                    "hitCount": item.count if isinstance(item.count, int) else _estimate_hit_count(item.data),
                    "reason": item.summary or item.reason,
                    "costMs": item.costMs,
                }
                for item in results
            ],
        }
        self._step(state, "evidence_fuse", "融合 GIS 与知识库证据", evidence)
        return {"evidence": evidence}

    async def _answer_generate(self, state: AgentState) -> Dict[str, Any]:
        request = state["request"]
        tool_results = state.get("tool_results", [])
        evidence = state.get("evidence", {})
        context_summary = state.get("context_summary", {})
        intent = state.get("intent", "KNOWLEDGE_QA")

        answer = ""
        if settings.use_llm:
            try:
                prompt = build_answer_prompt(request, intent, context_summary, tool_results, evidence)
                answer = await self.llm_client.chat(prompt) or ""
            except Exception as exc:  # noqa: BLE001
                answer = ""
                self._step(state, "answer_generate", "LLM 生成失败，使用 fallback", {"error": str(exc)})

        if not answer:
            answer = fallback_answer(request, intent, context_summary, evidence, tool_results)

        sources = extract_knowledge_sources(tool_results)
        answer = enhance_answer(answer, request, intent, state.get("intent_detail", {}), tool_results, sources)

        self._step(state, "answer_generate", "生成回答", {"useLlm": settings.use_llm, "answerLength": len(answer)})
        return {"answer": answer}

    async def _quality_guard(self, state: AgentState) -> Dict[str, Any]:
        answer = state.get("answer") or ""
        before = answer
        answer = _strip_thinking(answer)

        context_summary = state.get("context_summary", {})
        prefix = "【基于当前地图对象】"
        mode = context_summary.get("mode")
        if mode and str(mode).upper() in {"BOX", "POLYGON", "REGION", "SELECTION"}:
            prefix = "【基于当前框选区域】"

        changed = []
        if settings.require_evidence_prefix and not answer.startswith("【基于"):
            answer = prefix + answer
            changed.append("prefix_added")

        if len(answer.strip()) < settings.min_answer_chars:
            answer = fallback_answer(
                state["request"],
                state.get("intent", "KNOWLEDGE_QA"),
                context_summary,
                state.get("evidence", {}),
                state.get("tool_results", []),
            )
            changed.append("short_answer_fallback")

        if not settings.allow_write_tools and re.search(r"(已保存|已派单|已转工单|已更新|已删除|已经写入)", answer):
            answer += "\n\n注意：当前 LangGraph 编排处于只读模式，未执行保存、派单或数据库更新。"
            changed.append("readonly_notice_added")

        quality = {
            "strategyVersion": settings.strategy_version,
            "changed": changed,
            "beforeLength": len(before),
            "afterLength": len(answer),
        }
        self._step(state, "quality_guard", "回答质量保护", quality)
        return {"answer": answer, "quality": quality}

    def _to_response(self, state: AgentState) -> MapAiAgentResponse:
        tool_results = state.get("tool_results", [])
        request = state["request"]
        sources = extract_knowledge_sources(tool_results)
        context_dump = request.mapContext.model_dump(exclude_none=True) if request.mapContext else None
        has_map_object = bool(request.mapContext and request.mapContext.mapObject)
        has_region = bool(request.mapContext and (request.mapContext.regionSummary or request.mapContext.geometry or str(request.mapContext.mode or "").upper() in {"BOX", "POLYGON", "REGION", "SELECTION"}))
        return MapAiAgentResponse(
            answer=state.get("answer", ""),
            mode="LANGGRAPH_AGENT",
            intent=state.get("intent"),
            mapContext=context_dump,
            toolResults=[item.model_dump(exclude_none=True) for item in tool_results],
            knowledgeSources=sources,
            sources=sources,
            trace={
                "traceId": state.get("trace_id"),
                "orchestratorProvider": "langgraph",
                "strategyVersion": settings.strategy_version,
                "nodeFlow": NODE_FLOW,
                "steps": state.get("steps", []),
                "costMs": int((time.perf_counter() - state.get("started_at", time.perf_counter())) * 1000),
            },
            data={
                "orchestratorProvider": "langgraph",
                "orchestratorFallback": False,
                "strategyVersion": settings.strategy_version,
                "intent": state.get("intent"),
                "intentDetail": state.get("intent_detail", {}),
                "nodeFlow": NODE_FLOW,
                "toolPlan": [item.model_dump(exclude_none=True) for item in state.get("tool_plan", [])],
                "toolResults": [item.model_dump(exclude_none=True) for item in tool_results],
                "toolTotalCount": len(tool_results),
                "toolSuccessCount": sum(1 for item in tool_results if item.success),
                "toolFailedCount": sum(1 for item in tool_results if not item.success),
                "evidence": state.get("evidence", {}),
                "quality": state.get("quality", {}),
                "mapObjectUsed": has_map_object,
                "mapContextUsed": bool(request.mapContext),
                "regionUsed": has_region,
                "sourceCount": len(sources),
                "readOnly": not settings.allow_write_tools,
                "langgraphAvailable": LANGGRAPH_AVAILABLE,
                "parityVersion": "phase50.15-native-parity",
            },
        )


def normalize_request(request: MapAiAgentRequest, tenant_id: Optional[str], trace_id: Optional[str]) -> Tuple[MapAiAgentRequest, Dict[str, Any]]:
    changed: Dict[str, Any] = {}
    if request.mapContext is None:
        map_ctx_data = None
        if isinstance(request.context, dict):
            nested = request.context.get("mapContext")
            if isinstance(nested, dict):
                map_ctx_data = dict(nested)
            else:
                known_keys = {"tenantId", "mode", "routeCode", "year", "mapObject", "regionSummary", "viewport", "geometry", "selectedLayers", "nearbyObjects", "userQuestion", "extra"}
                if any(key in request.context for key in known_keys):
                    map_ctx_data = {key: request.context.get(key) for key in known_keys if key in request.context}
        request.mapContext = MapAiContext(**(map_ctx_data or {}))
        changed["mapContextCreated"] = True

    ctx = request.mapContext
    if tenant_id and not ctx.tenantId:
        ctx.tenantId = tenant_id
        changed["tenantIdFromHeader"] = tenant_id
    if request.message and not ctx.userQuestion:
        ctx.userQuestion = request.message
        changed["userQuestionSynced"] = True
    if request.mapObject and not ctx.mapObject:
        ctx.mapObject = request.mapObject
        changed["topLevelMapObjectMerged"] = True
    if isinstance(request.context, dict):
        if not ctx.mapObject and isinstance(request.context.get("mapObject"), dict):
            ctx.mapObject = request.context.get("mapObject")
            changed["contextMapObjectMerged"] = True
        if not ctx.regionSummary and isinstance(request.context.get("regionSummary"), dict):
            ctx.regionSummary = request.context.get("regionSummary")
            changed["contextRegionSummaryMerged"] = True
        if not ctx.viewport and isinstance(request.context.get("viewport"), dict):
            ctx.viewport = request.context.get("viewport")
            changed["contextViewportMerged"] = True
        if not ctx.geometry and isinstance(request.context.get("geometry"), dict):
            ctx.geometry = request.context.get("geometry")
            changed["contextGeometryMerged"] = True
    ctx.extra = ctx.extra or {}
    if trace_id and not ctx.extra.get("traceId"):
        ctx.extra["traceId"] = trace_id
    return request, changed


def _contains_any(text: str, words: List[str]) -> bool:
    return any(word in text for word in words)


def _pick(data: Dict[str, Any], *keys: str) -> Any:
    for key in keys:
        if isinstance(data, dict) and data.get(key) not in (None, ""):
            return data.get(key)
    return None


def _int_or_none(value: Any) -> Optional[int]:
    try:
        if value is None or value == "":
            return None
        return int(value)
    except Exception:
        return None


def _option_int(request: MapAiAgentRequest, key: str, default: int) -> int:
    return _int_or_none((request.options or {}).get(key)) or default


def _option_str(request: MapAiAgentRequest, key: str) -> Optional[str]:
    value = (request.options or {}).get(key)
    if value is None or str(value).strip() == "":
        return None
    return str(value).strip()


def _merge_args(left: Dict[str, Any], right: Dict[str, Any]) -> Dict[str, Any]:
    merged = dict(left)
    merged.update({k: v for k, v in right.items() if v not in (None, "")})
    return merged


def _summarize_context(request: MapAiAgentRequest) -> Dict[str, Any]:
    ctx = request.mapContext
    if not ctx:
        return {"message": request.message}
    obj = ctx.mapObject or {}
    return {
        "tenantId": ctx.tenantId,
        "mode": ctx.mode,
        "routeCode": ctx.routeCode,
        "year": ctx.year,
        "selectedLayers": ctx.selectedLayers,
        "viewport": ctx.viewport,
        "geometryType": (ctx.geometry or {}).get("type") if isinstance(ctx.geometry, dict) else None,
        "hasRegionSummary": bool(ctx.regionSummary),
        "mapObjectType": _pick(obj, "objectType", "type", "bizType"),
        "diseaseName": _pick(obj, "diseaseName", "diseaseType", "name"),
        "severity": _pick(obj, "severity", "level", "grade"),
        "stakeStart": _pick(obj, "stakeStart", "startStake", "startStakeNo", "start_stake", "startMileage"),
        "stakeEnd": _pick(obj, "stakeEnd", "endStake", "endStakeNo", "end_stake", "endMileage"),
        "message": request.message,
    }


def _context_filter_args(ctx: Optional[MapAiContext]) -> Dict[str, Any]:
    if not ctx:
        return {}
    return {
        "tenantId": ctx.tenantId,
        "routeCode": ctx.routeCode,
        "year": ctx.year,
    }


def _object_filter_args(obj: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "id": _pick(obj, "id", "objectId", "bizId"),
        "routeCode": _pick(obj, "routeCode", "route_code", "route", "routeNo", "route_no"),
        "diseaseName": _pick(obj, "diseaseName", "diseaseType", "name"),
        "severity": _pick(obj, "severity", "level", "grade"),
        "stakeStart": _pick(obj, "stakeStart", "startStake", "startStakeNo", "start_stake", "startMileage"),
        "stakeEnd": _pick(obj, "stakeEnd", "endStake", "endStakeNo", "end_stake", "endMileage"),
    }


def _region_args(map_context: Optional[MapAiContext]) -> Dict[str, Any]:
    if not map_context:
        return {}
    return {
        "mode": map_context.mode,
        "routeCode": map_context.routeCode,
        "year": map_context.year,
        "geometry": map_context.geometry,
        "viewport": map_context.viewport,
    }


def _has_stake_range(obj: Dict[str, Any]) -> bool:
    return bool(_pick(obj, "stakeStart", "startStake", "startStakeNo", "start_stake", "startMileage") and _pick(obj, "stakeEnd", "endStake", "endStakeNo", "end_stake", "endMileage"))


def _stake_args(obj: Dict[str, Any], base: Dict[str, Any]) -> Dict[str, Any]:
    return _merge_args(
        base,
        {
            "routeCode": _pick(obj, "routeCode", "route_code", "route", "routeNo", "route_no"),
            "stakeStart": _pick(obj, "stakeStart", "startStake", "startStakeNo", "start_stake", "startMileage"),
            "stakeEnd": _pick(obj, "stakeEnd", "endStake", "endStakeNo", "end_stake", "endMileage"),
        },
    )


def _build_query(request: MapAiAgentRequest, intent: str) -> str:
    ctx = request.mapContext
    parts = [request.message or "", intent]
    if ctx:
        parts.extend([str(ctx.routeCode or ""), str(ctx.year or ""), str(ctx.mode or "")])
        obj = ctx.mapObject or {}
        parts.extend([str(_pick(obj, "diseaseName", "diseaseType", "name") or ""), str(_pick(obj, "severity", "level", "grade") or "")])
    parts.extend(["道路养护", "处置建议", "评定规则"])
    return " ".join([p for p in parts if p])


def _dedupe_and_limit_calls(calls: List[ToolCall]) -> List[ToolCall]:
    result = []
    seen = set()
    for call in calls:
        if call.toolName not in settings.allowed_tools:
            continue
        key = (call.toolName, str(sorted((call.args or {}).items())))
        if key in seen:
            continue
        seen.add(key)
        result.append(call)
        if len(result) >= settings.max_tool_calls:
            break
    return result


def _estimate_hit_count(data: Any) -> int:
    if data is None:
        return 0
    if isinstance(data, list):
        return len(data)
    if isinstance(data, dict):
        for key in ["total", "count", "totalElements", "hitCount"]:
            value = data.get(key)
            if isinstance(value, int):
                return value
        for key in ["list", "items", "records", "sources", "data", "hits"]:
            value = data.get(key)
            if isinstance(value, list):
                return len(value)
            if isinstance(value, dict):
                nested = _estimate_hit_count(value)
                if nested:
                    return nested
    return 1


def _strip_thinking(answer: str) -> str:
    answer = re.sub(r"<think>.*?</think>", "", answer, flags=re.S | re.I)
    answer = re.sub(r"<thinking>.*?</thinking>", "", answer, flags=re.S | re.I)
    return answer.strip()
