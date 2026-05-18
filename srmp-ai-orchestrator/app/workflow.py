import asyncio
import re
import time
import uuid
from typing import Any, Dict, List, Optional, Tuple, TypedDict

from .adaptive_planner import plan_adaptive_tools, summarize_evidence
from .answer_enhancers import enhance_answer
from .config import settings
from .intent import recognize_intent
from .java_tools import JavaToolGateway, extract_knowledge_sources
from .live_trace import LiveTraceStore
from .llm_client import LlmClient, LlmResult
from .plan_execution import build_plan_execution, normalize_plan_preview
from .plan_preview import build_plan_warnings, build_source_hints, enrich_tool_plan
from .planner import plan_tools
from .prompt import build_answer_prompt, build_compact_answer_prompt, fallback_answer
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
    "adaptive_tool_planning",
    "adaptive_tool_execute",
    "adaptive_evidence_fuse",
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

NODE_LABELS = {
    "request_normalize": "归一化请求",
    "context_build": "构建地图上下文",
    "intent_recognize": "识别用户意图",
    "context_enrich": "补全上下文",
    "tool_planning": "规划工具",
    "tool_plan": "规划工具",
    "tool_execute": "执行只读工具",
    "evidence_fuse": "融合证据",
    "adaptive_tool_planning": "自适应补充规划",
    "adaptive_tool_execute": "执行补充工具",
    "adaptive_evidence_fuse": "融合补充证据",
    "answer_generate": "生成回答",
    "quality_guard": "质量保护",
    "llm_answer": "大模型回答",
}


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
    adaptive_tool_plan: List[ToolCall]
    adaptive_tool_results: List[ToolResult]
    adaptive_planning: Dict[str, Any]
    evidence: Dict[str, Any]
    answer: str
    answer_meta: Dict[str, Any]
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
        "adaptivePlanning": settings.adaptive_planning_enabled,
        "maxAdaptiveIterations": settings.max_adaptive_iterations,
        "maxAdaptiveAddedTools": settings.max_adaptive_added_tools,
        "runtimeReplay": True,
        "diagnosticExport": True,
    }


class LangGraphWorkflow:
    def __init__(self, gateway: JavaToolGateway, llm_client: LlmClient, live_trace_store: Optional[LiveTraceStore] = None):
        self.gateway = gateway
        self.llm_client = llm_client
        self.live_trace_store = live_trace_store
        self.graph = self._build_graph() if LANGGRAPH_AVAILABLE else None

    async def run(self, request: MapAiAgentRequest, tenant_id: Optional[str], trace_id: Optional[str]) -> MapAiAgentResponse:
        state: AgentState = self._new_state(request=request, tenant_id=tenant_id, trace_id=trace_id)
        self._live_start_trace(state)
        if self.graph is not None:
            final_state = await self.graph.ainvoke(state)
        else:
            final_state = await self._run_sequential(state, NODE_FLOW)
        return self._to_response(final_state)

    async def plan(self, request: MapAiAgentRequest, tenant_id: Optional[str], trace_id: Optional[str]) -> Dict[str, Any]:
        """只跑归一化、意图识别、工具规划；用于前端/联调解释为什么会调这些工具。"""
        state: AgentState = self._new_state(request=request, tenant_id=tenant_id, trace_id=trace_id)
        self._live_start_trace(state)
        final_state = await self._run_sequential(state, PLAN_NODE_FLOW)
        raw_tool_plan = final_state.get("tool_plan", [])
        enriched_tool_plan = enrich_tool_plan(raw_tool_plan)
        return {
            "traceId": final_state.get("trace_id"),
            "strategy": strategy_metadata(),
            "action": final_state["request"].action,
            "intent": final_state.get("intent"),
            "contextSummary": final_state.get("context_summary", {}),
            "normalizedRequest": final_state["request"].model_dump(exclude_none=True),
            "toolPlan": enriched_tool_plan,
            "sourceHints": build_source_hints(final_state["request"], enriched_tool_plan),
            "warnings": build_plan_warnings(final_state["request"], enriched_tool_plan),
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
        builder.add_node("request_normalize", self._wrap_node("request_normalize", self._request_normalize))
        builder.add_node("context_build", self._wrap_node("context_build", self._context_build))
        builder.add_node("intent_recognize", self._wrap_node("intent_recognize", self._intent_recognize))
        builder.add_node("context_enrich", self._wrap_node("context_enrich", self._context_enrich))
        builder.add_node("tool_planning", self._wrap_node("tool_planning", self._tool_plan))
        builder.add_node("tool_execute", self._wrap_node("tool_execute", self._tool_execute))
        builder.add_node("evidence_fuse", self._wrap_node("evidence_fuse", self._evidence_fuse))
        builder.add_node("adaptive_tool_planning", self._wrap_node("adaptive_tool_planning", self._adaptive_tool_planning))
        builder.add_node("adaptive_tool_execute", self._wrap_node("adaptive_tool_execute", self._adaptive_tool_execute))
        builder.add_node("adaptive_evidence_fuse", self._wrap_node("adaptive_evidence_fuse", self._adaptive_evidence_fuse))
        builder.add_node("answer_generate", self._wrap_node("answer_generate", self._answer_generate))
        builder.add_node("quality_guard", self._wrap_node("quality_guard", self._quality_guard))
        builder.set_entry_point("request_normalize")
        for left, right in zip(NODE_FLOW, NODE_FLOW[1:]):
            builder.add_edge(left, right)
        builder.add_edge("quality_guard", END)
        return builder.compile()

    async def _run_sequential(self, state: AgentState, nodes: List[str]) -> AgentState:
        for node in nodes:
            updates = await self._wrap_node(node, self._node_handler(node))(state)
            state.update(updates or {})
        return state

    def _node_handler(self, node: str):
        if node == "tool_planning":
            return self._tool_plan
        handler = getattr(self, "_" + node, None)
        if handler is None:
            raise AttributeError("LangGraphWorkflow has no handler for node: " + str(node))
        return handler

    def _action_from_state(self, state: AgentState) -> str:
        request = state.get("request")
        options = request.options if request and request.options else {}
        return str(options.get("action") or "CHAT").upper()

    def _graph_name_from_action(self, action: str) -> str:
        if action == "ANALYZE_OBJECT":
            return "object_analysis_graph"
        if action == "ANALYZE_REGION":
            return "region_analysis_graph"
        if action == "ANALYZE_ROUTE":
            return "route_report_graph"
        if action == "PLAN_ONLY":
            return "plan_graph"
        return "chat_graph"

    def _live_start_trace(self, state: AgentState) -> None:
        if not self.live_trace_store:
            return
        action = self._action_from_state(state)
        self.live_trace_store.start_trace(
            state.get("trace_id") or "",
            action=action,
            graph_name=self._graph_name_from_action(action),
        )

    def _wrap_node(self, node: str, handler):
        async def wrapped(state: AgentState) -> Dict[str, Any]:
            if self.live_trace_store:
                self.live_trace_store.start_step(
                    state.get("trace_id") or "",
                    node,
                    NODE_LABELS.get(node, node),
                )
            try:
                return await handler(state)
            except Exception as exc:
                if self.live_trace_store:
                    self.live_trace_store.fail_step(state.get("trace_id") or "", node, str(exc))
                raise
        return wrapped

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
        if self.live_trace_store:
            self.live_trace_store.complete_step(state.get("trace_id") or "", item)

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
        self._step(state, "tool_plan", "规划只读工具", {"readOnly": not settings.allow_write_tools, "tools": [item.model_dump() for item in calls]})
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
        evidence = self._build_evidence(state.get("tool_results", []))
        self._step(state, "evidence_fuse", "融合 GIS 与知识库证据", evidence)
        return {"evidence": evidence}

    def _build_evidence(self, results: List[ToolResult]) -> Dict[str, Any]:
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

        return {
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

    async def _adaptive_tool_planning(self, state: AgentState) -> Dict[str, Any]:
        decision = plan_adaptive_tools(
            request=state["request"],
            intent=state.get("intent", "KNOWLEDGE_QA"),
            intent_detail=state.get("intent_detail", {}),
            evidence=state.get("evidence", {}),
            tool_results=state.get("tool_results", []),
            existing_plan=state.get("tool_plan", []),
        )
        summary = decision.to_summary()
        self._step(
            state,
            "adaptive_tool_planning",
            decision.reason,
            summary,
            status="SUCCESS" if decision.should_replan else "SKIPPED",
            count=len(decision.added_calls),
        )
        return {"adaptive_tool_plan": decision.added_calls, "adaptive_planning": summary}

    async def _adaptive_tool_execute(self, state: AgentState) -> Dict[str, Any]:
        calls = state.get("adaptive_tool_plan", [])
        if not calls:
            self._step(state, "adaptive_tool_execute", "无补充工具需要执行", state.get("adaptive_planning", {}), status="SKIPPED")
            return {"adaptive_tool_results": []}

        results: List[ToolResult] = []
        for call in calls:
            results.append(await self._execute_single_tool(state, call))
        combined_results = list(state.get("tool_results", [])) + results
        planning = dict(state.get("adaptive_planning") or {})
        planning.update(
            {
                "status": "EXECUTED" if any(item.success for item in results) else "FAILED",
                "addedToolNames": [item.toolName for item in calls],
            }
        )
        self._step(
            state,
            "adaptive_tool_execute",
            "执行自适应补充工具",
            {
                "success": sum(1 for item in results if item.success),
                "failed": sum(1 for item in results if not item.success),
                "tools": [item.toolName for item in results],
            },
            status="SUCCESS" if any(item.success for item in results) else "FAILED",
            count=len(results),
        )
        return {"tool_results": combined_results, "adaptive_tool_results": results, "adaptive_planning": planning}

    async def _adaptive_evidence_fuse(self, state: AgentState) -> Dict[str, Any]:
        results = state.get("adaptive_tool_results", [])
        if not results:
            evidence = state.get("evidence", {})
            planning = dict(state.get("adaptive_planning") or {})
            planning["evidenceSufficientAfter"] = bool((evidence or {}).get("sufficient"))
            planning["evidenceAfter"] = summarize_evidence(evidence)
            self._step(state, "adaptive_evidence_fuse", "无补充证据需要融合", planning, status="SKIPPED")
            return {"adaptive_planning": planning}
        evidence = self._build_evidence(state.get("tool_results", []))
        planning = dict(state.get("adaptive_planning") or {})
        planning["evidenceSufficientAfter"] = bool(evidence.get("sufficient"))
        planning["evidenceAfter"] = summarize_evidence(evidence)
        self._step(state, "adaptive_evidence_fuse", "融合自适应补充证据", {"adaptivePlanning": planning, "evidence": evidence})
        return {"evidence": evidence, "adaptive_planning": planning}

    async def _call_llm(self, prompt: str) -> LlmResult:
        if not settings.use_llm:
            return LlmResult.skipped("SRMP_LANGGRAPH_USE_LLM=false", "DISABLED")
        if hasattr(self.llm_client, "generate"):
            result = await self.llm_client.generate(prompt)
            if isinstance(result, LlmResult):
                return result
            if isinstance(result, dict):
                return LlmResult(**result)
        if hasattr(self.llm_client, "chat"):
            try:
                answer = await self.llm_client.chat(prompt)
            except Exception as exc:  # noqa: BLE001
                return LlmResult.failed("UNKNOWN", str(exc), model=settings.llm_model)
            if answer:
                return LlmResult.success(answer=str(answer), model=settings.llm_model)
        return LlmResult.failed("EMPTY_RESPONSE", "LLM 返回为空", model=settings.llm_model)

    def _record_llm_step(
        self,
        state: AgentState,
        result: LlmResult,
        retried: bool,
        first_attempt: LlmResult,
    ) -> None:
        data = result.to_dict()
        data["retriedWithCompactPrompt"] = retried
        data["firstAttempt"] = first_attempt.to_dict()
        self._step(
            state,
            "llm_answer",
            "大模型回答",
            data,
            status=result.status,
            count=1 if result.status == "SUCCESS" else 0,
            error=result.errorMessage if result.status == "FAILED" else None,
        )

    def _answer_meta(self, result: LlmResult, source: str, retried: bool, fallback_reason: str = "") -> Dict[str, Any]:
        meta = {
            "answerSource": source,
            "llmSuccess": result.status == "SUCCESS" and source == "LLM",
            "llmStatus": result.status,
            "retriedWithCompactPrompt": retried,
            "llmModel": result.model or settings.llm_model,
        }
        if fallback_reason:
            meta["fallbackReason"] = fallback_reason
        if result.errorType:
            meta["errorType"] = result.errorType
        if result.errorMessage:
            meta["errorMessage"] = result.errorMessage
        return meta

    async def _answer_generate(self, state: AgentState) -> Dict[str, Any]:
        request = state["request"]
        tool_results = state.get("tool_results", [])
        evidence = state.get("evidence", {})
        context_summary = state.get("context_summary", {})
        intent = state.get("intent", "KNOWLEDGE_QA")

        prompt = build_answer_prompt(request, intent, context_summary, tool_results, evidence)
        first_result = await self._call_llm(prompt)
        result = first_result
        retried = False

        if (
            settings.use_llm
            and settings.llm_compact_retry_enabled
            and result.status != "SUCCESS"
            and result.errorType in {"EMPTY_RESPONSE", ""}
        ):
            compact_prompt = build_compact_answer_prompt(request, intent, context_summary, tool_results, evidence)
            result = await self._call_llm(compact_prompt)
            retried = True

        self._record_llm_step(state, result, retried, first_result)

        answer = result.answer if result.status == "SUCCESS" and result.answer else ""
        answer_source = "LLM" if answer else "FALLBACK"
        fallback_reason = ""
        if not answer:
            fallback_reason = result.errorType or ("DISABLED" if not settings.use_llm else "EMPTY_RESPONSE")
            answer = fallback_answer(request, intent, context_summary, evidence, tool_results)

        sources = extract_knowledge_sources(tool_results)
        answer = enhance_answer(answer, request, intent, state.get("intent_detail", {}), tool_results, sources)
        answer_meta = self._answer_meta(result, answer_source, retried, fallback_reason)

        self._step(
            state,
            "answer_generate",
            "生成回答",
            {
                "useLlm": settings.use_llm,
                "answerLength": len(answer),
                "answerSource": answer_meta.get("answerSource"),
                "llmSuccess": answer_meta.get("llmSuccess"),
                "fallbackReason": answer_meta.get("fallbackReason"),
            },
        )
        return {"answer": answer, "answer_meta": answer_meta}

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
            meta = dict(state.get("answer_meta") or {})
            meta["answerSource"] = "FALLBACK"
            meta["llmSuccess"] = False
            meta["qualityFallback"] = True
            meta["fallbackReason"] = "QUALITY_GUARD_SHORT_ANSWER"
            state["answer_meta"] = meta

        if not settings.allow_write_tools and re.search(r"(已保存|已派单|已转工单|已更新|已删除|已经写入)", answer):
            answer += "\n\n注意：当前为只读分析，未执行保存、派单或数据库更新。"
            changed.append("readonly_notice_added")

        quality = {
            "changed": changed,
            "beforeLength": len(before),
            "afterLength": len(answer),
        }
        self._step(state, "quality_guard", "回答质量保护", quality)
        return {"answer": answer, "quality": quality, "answer_meta": state.get("answer_meta", {})}

    def _to_response(self, state: AgentState) -> MapAiAgentResponse:
        tool_results = state.get("tool_results", [])
        request = state["request"]
        sources = extract_knowledge_sources(tool_results)
        context_dump = request.mapContext.model_dump(exclude_none=True) if request.mapContext else None
        has_map_object = bool(request.mapContext and request.mapContext.mapObject)
        has_region = bool(request.mapContext and (request.mapContext.regionSummary or request.mapContext.geometry or str(request.mapContext.mode or "").upper() in {"BOX", "POLYGON", "REGION", "SELECTION"}))
        answer_meta = state.get("answer_meta", {})
        actual_action = str(request.action or (request.options or {}).get("action") or "CHAT").upper()
        adaptive_planning = state.get("adaptive_planning") or {
            "enabled": settings.adaptive_planning_enabled,
            "status": "SKIPPED_NO_CANDIDATE",
            "iterations": 0,
            "maxIterations": settings.max_adaptive_iterations,
            "maxAddedTools": settings.max_adaptive_added_tools,
            "reason": "自适应规划未产生补充工具。",
            "addedToolNames": [],
            "skippedToolNames": [],
            "evidenceSufficientBefore": bool((state.get("evidence") or {}).get("sufficient")),
            "evidenceSufficientAfter": bool((state.get("evidence") or {}).get("sufficient")),
            "evidenceBefore": summarize_evidence(state.get("evidence", {})),
            "evidenceAfter": summarize_evidence(state.get("evidence", {})),
        }
        if not adaptive_planning.get("evidenceBefore"):
            adaptive_planning["evidenceBefore"] = summarize_evidence(state.get("evidence", {}))
        if not adaptive_planning.get("evidenceAfter"):
            adaptive_planning["evidenceAfter"] = summarize_evidence(state.get("evidence", {}))
        plan_execution = build_plan_execution(
            normalize_plan_preview((request.options or {}).get("planPreview")),
            {
                "runTraceId": state.get("trace_id"),
                "actualAction": actual_action,
                "actualIntent": state.get("intent"),
                "toolResults": [item.model_dump(exclude_none=True) for item in tool_results],
                "sources": sources,
                "evidence": state.get("evidence", {}),
                "adaptivePlanning": adaptive_planning,
            },
        )
        answer_meta = dict(answer_meta)
        answer_meta["planExecutionStatus"] = plan_execution.get("status")
        answer_meta["adaptivePlanningStatus"] = adaptive_planning.get("status")
        trace_payload = {
            "traceId": state.get("trace_id"),
            "orchestratorProvider": "langgraph",
            "nodeFlow": NODE_FLOW,
            "steps": state.get("steps", []),
            "planExecution": plan_execution,
            "adaptivePlanning": adaptive_planning,
            "costMs": int((time.perf_counter() - state.get("started_at", time.perf_counter())) * 1000),
        }
        if self.live_trace_store:
            self.live_trace_store.complete_trace(
                state.get("trace_id") or "",
                status="SUCCESS",
                answer_meta=answer_meta,
                trace=trace_payload,
            )
        return MapAiAgentResponse(
            answer=state.get("answer", ""),
            mode="LANGGRAPH_AGENT",
            intent=state.get("intent"),
            mapContext=context_dump,
            toolResults=[item.model_dump(exclude_none=True) for item in tool_results],
            knowledgeSources=sources,
            sources=sources,
            answerMeta=answer_meta,
            planExecution=plan_execution,
            trace=trace_payload,
            data={
                "orchestratorProvider": "langgraph",
                "orchestratorFallback": False,
                "intent": state.get("intent"),
                "intentDetail": state.get("intent_detail", {}),
                "nodeFlow": NODE_FLOW,
                "toolPlan": [item.model_dump(exclude_none=True) for item in state.get("tool_plan", [])],
                "toolResults": [item.model_dump(exclude_none=True) for item in tool_results],
                "toolTotalCount": len(tool_results),
                "toolSuccessCount": sum(1 for item in tool_results if item.success),
                "toolFailedCount": sum(1 for item in tool_results if not item.success),
                "evidence": state.get("evidence", {}),
                "adaptivePlanning": adaptive_planning,
                "answerMeta": answer_meta,
                "planExecution": plan_execution,
                "answerSource": answer_meta.get("answerSource"),
                "llmSuccess": answer_meta.get("llmSuccess"),
                "quality": state.get("quality", {}),
                "mapObjectUsed": has_map_object,
                "mapContextUsed": bool(request.mapContext),
                "regionUsed": has_region,
                "sourceCount": len(sources),
                "readOnly": not settings.allow_write_tools,
                "langgraphAvailable": LANGGRAPH_AVAILABLE,
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
