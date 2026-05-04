import asyncio
import re
import time
from typing import Any, Dict, List, Optional, TypedDict

from .config import settings
from .java_tools import JavaToolGateway
from .llm_client import LlmClient
from .prompt import build_answer_prompt, fallback_answer
from .schemas import MapAiAgentRequest, MapAiAgentResponse, ToolCall, ToolResult

try:
    from langgraph.graph import END, StateGraph

    LANGGRAPH_AVAILABLE = True
except Exception:
    StateGraph = None
    END = "__end__"
    LANGGRAPH_AVAILABLE = False


NODE_FLOW = [
    "context_build",
    "intent_recognize",
    "context_enrich",
    "tool_plan",
    "tool_execute",
    "evidence_fuse",
    "answer_generate",
    "quality_guard",
]


class AgentState(TypedDict, total=False):
    request: MapAiAgentRequest
    tenant_id: str
    trace_id: str
    started_at: float
    steps: List[Dict[str, Any]]
    context_summary: Dict[str, Any]
    intent: str
    tool_plan: List[ToolCall]
    tool_results: List[ToolResult]
    evidence: Dict[str, Any]
    answer: str
    quality: Dict[str, Any]


def strategy_metadata() -> Dict[str, Any]:
    return {
        "strategyVersion": settings.strategy_version,
        "readOnly": not settings.allow_write_tools,
        "nodeFlow": NODE_FLOW,
        "parallelToolExecution": settings.parallel_tool_execution,
        "maxParallelTools": settings.max_parallel_tools,
        "maxToolCalls": settings.max_tool_calls,
        "allowedTools": settings.allowed_tools,
        "qualityGuard": settings.enable_quality_guard,
        "contextEnrich": settings.enable_context_enrich,
        "evidenceFusion": settings.enable_evidence_fusion,
    }


class LangGraphWorkflow:
    def __init__(self, gateway: JavaToolGateway, llm_client: LlmClient):
        self.gateway = gateway
        self.llm_client = llm_client
        self.graph = self._build_graph() if LANGGRAPH_AVAILABLE else None

    async def run(self, request: MapAiAgentRequest, tenant_id: str, trace_id: str) -> MapAiAgentResponse:
        state: AgentState = {
            "request": request,
            "tenant_id": tenant_id,
            "trace_id": trace_id,
            "started_at": time.perf_counter(),
            "steps": [],
        }
        if self.graph is not None:
            final_state = await self.graph.ainvoke(state)
        else:
            final_state = await self._run_sequential(state)
        return self._to_response(final_state)

    def _build_graph(self):
        builder = StateGraph(AgentState)
        builder.add_node("context_build", self._context_build)
        builder.add_node("intent_recognize", self._intent_recognize)
        builder.add_node("context_enrich", self._context_enrich)
        builder.add_node("tool_plan", self._tool_plan)
        builder.add_node("tool_execute", self._tool_execute)
        builder.add_node("evidence_fuse", self._evidence_fuse)
        builder.add_node("answer_generate", self._answer_generate)
        builder.add_node("quality_guard", self._quality_guard)
        builder.set_entry_point("context_build")
        for left, right in zip(NODE_FLOW, NODE_FLOW[1:]):
            builder.add_edge(left, right)
        builder.add_edge("quality_guard", END)
        return builder.compile()

    async def _run_sequential(self, state: AgentState) -> AgentState:
        for node in NODE_FLOW:
            updates = await getattr(self, "_" + node)(state)
            state.update(updates or {})
        return state

    def _step(self, state: AgentState, node: str, message: str, data: Optional[Dict[str, Any]] = None) -> None:
        steps = state.setdefault("steps", [])
        steps.append(
            {
                "node": node,
                "message": message,
                "data": data or {},
                "elapsedMs": int((time.perf_counter() - state.get("started_at", time.perf_counter())) * 1000),
            }
        )

    async def _context_build(self, state: AgentState) -> Dict[str, Any]:
        request = state["request"]
        summary = _summarize_context(request)
        self._step(state, "context_build", "构建地图上下文摘要", summary)
        return {"context_summary": summary}

    async def _intent_recognize(self, state: AgentState) -> Dict[str, Any]:
        request = state["request"]
        text = (request.message or "").lower()
        ctx = request.mapContext
        obj = ctx.mapObject if ctx and ctx.mapObject else {}
        joined = " ".join([text, str(obj).lower(), str(ctx.mode if ctx else "").lower()])

        if _contains_any(joined, ["框选", "多边形", "区域", "范围", "统计", "汇总", "polygon", "box", "region"]):
            intent = "REGION_ANALYSIS"
        elif _contains_any(joined, ["评定", "评定单元", "mqi", "pqi", "pci", "rqi", "rdi", "低分"]):
            intent = "ASSESSMENT_ANALYSIS"
        elif _contains_any(joined, ["养护建议", "处置建议", "方案", "修复", "维修", "预防性养护"]):
            intent = "MAINTENANCE_ADVICE"
        elif _contains_any(joined, ["病害", "裂缝", "坑槽", "龟裂", "沉陷", "车辙", "object", "disease"]):
            intent = "OBJECT_ANALYSIS"
        else:
            intent = "KNOWLEDGE_QA"

        self._step(state, "intent_recognize", "识别用户意图", {"intent": intent})
        return {"intent": intent}

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
                route_code = _pick(obj, "routeCode", "route_code", "route", "routeNo", "route_no")
                if route_code:
                    context.routeCode = str(route_code)
                    changed["routeCode"] = route_code
            if not context.year:
                year = _int_or_none(_pick(obj, "year", "detectYear", "evalYear")) or _int_or_none((request.options or {}).get("year"))
                if year:
                    context.year = year
                    changed["year"] = year
            context.extra = context.extra or {}
            context.extra["orchestratorStrategy"] = settings.strategy_version
            context.extra["readOnly"] = True
            context.extra["intent"] = state.get("intent")
            if obj:
                context.extra["mapObjectKeys"] = sorted(list(obj.keys()))[:30]

        summary = _summarize_context(request)
        self._step(state, "context_enrich", "补全路线/年度/对象摘要", {"changed": changed, "summary": summary})
        return {"request": request, "context_summary": summary}

    async def _tool_plan(self, state: AgentState) -> Dict[str, Any]:
        request = state["request"]
        intent = state["intent"]
        calls: List[ToolCall] = []
        map_context = request.mapContext
        obj = map_context.mapObject if map_context and map_context.mapObject else {}
        query = _build_query(request, intent)

        if intent == "REGION_ANALYSIS":
            calls.append(ToolCall(toolName="gis.queryRegionSummary", args={"limit": 50}, reason="区域/框选范围统计"))
            calls.append(ToolCall(toolName="gis.queryDiseases", args=_merge_args({"limit": 50}, _region_args(map_context)), reason="区域病害明细"))
        elif intent == "ASSESSMENT_ANALYSIS":
            calls.append(ToolCall(toolName="gis.queryAssessmentResults", args=_merge_args({"limit": 20}, _object_filter_args(obj)), reason="评定结果查询"))
            if _has_stake_range(obj):
                calls.append(ToolCall(toolName="gis.queryDiseasesByStakeRange", args=_stake_args(obj, {"limit": 50}), reason="评定单元内病害查询"))
            else:
                calls.append(ToolCall(toolName="gis.queryDiseases", args={"limit": 30}, reason="评定上下文缺少桩号时查询相关病害"))
        elif intent in {"OBJECT_ANALYSIS", "MAINTENANCE_ADVICE"}:
            calls.append(ToolCall(toolName="gis.queryDiseases", args=_merge_args({"limit": 30}, _object_filter_args(obj)), reason="当前对象病害查询"))
            calls.append(ToolCall(toolName="gis.queryNearbyObjects", args={"limit": 20}, reason="周边对象辅助判断"))
            if _has_stake_range(obj):
                calls.append(ToolCall(toolName="gis.queryAssessmentResults", args=_stake_args(obj, {"limit": 10}), reason="对象所在单元评定结果"))

        calls.append(ToolCall(toolName="knowledge.retrieve", args={"query": query, "topK": _option_int(request, "topK", 5)}, reason="知识库检索处置规则"))
        calls = _dedupe_and_limit_calls(calls)
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
        )
        return {"tool_results": results}

    async def _execute_single_tool(self, state: AgentState, call: ToolCall) -> ToolResult:
        if call.toolName not in settings.allowed_tools:
            return ToolResult(toolName=call.toolName, success=False, reason="tool not allowed in readonly strategy", error="TOOL_NOT_ALLOWED")
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
            count = _estimate_hit_count(item.data)
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
            "failedTools": [{"toolName": item.toolName, "error": item.errorMessage} for item in failed],
            "toolSummary": [
                {
                    "toolName": item.toolName,
                    "success": item.success,
                    "hitCount": _estimate_hit_count(item.data),
                    "reason": item.summary,
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
            prompt = build_answer_prompt(request, intent, context_summary, tool_results, evidence)
            try:
                answer = await self.llm_client.chat(prompt)
            except Exception as exc:
                answer = ""
                self._step(state, "answer_generate", "LLM 生成失败，使用 fallback", {"error": str(exc)})

        if not answer:
            answer = fallback_answer(request, intent, context_summary, evidence, tool_results)

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

        if re.search(r"(已保存|已派单|已转工单|已更新|已删除|已经写入)", answer):
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
        return MapAiAgentResponse(
            answer=state.get("answer", ""),
            mode="LANGGRAPH_AGENT",
            intent=state.get("intent"),
            toolResults=[item.model_dump(exclude_none=True) for item in tool_results],
            trace={
                "orchestratorProvider": "langgraph",
                "strategyVersion": settings.strategy_version,
                "nodeFlow": NODE_FLOW,
                "steps": state.get("steps", []),
                "costMs": int((time.perf_counter() - state.get("started_at", time.perf_counter())) * 1000),
            },
            data={
                "orchestratorProvider": "langgraph",
                "strategyVersion": settings.strategy_version,
                "intent": state.get("intent"),
                "nodeFlow": NODE_FLOW,
                "toolPlan": [item.model_dump(exclude_none=True) for item in state.get("tool_plan", [])],
                "toolResults": [item.model_dump(exclude_none=True) for item in tool_results],
                "evidence": state.get("evidence", {}),
                "quality": state.get("quality", {}),
                "mapObjectUsed": True,
                "readOnly": True,
            },
        )


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
        "mapObjectType": _pick(obj, "objectType", "type", "bizType"),
        "diseaseName": _pick(obj, "diseaseName", "diseaseType", "name"),
        "severity": _pick(obj, "severity", "level", "grade"),
        "stakeStart": _pick(obj, "stakeStart", "startStake", "startStakeNo"),
        "stakeEnd": _pick(obj, "stakeEnd", "endStake", "endStakeNo"),
        "message": request.message,
    }


def _object_filter_args(obj: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "id": _pick(obj, "id", "objectId", "bizId"),
        "routeCode": _pick(obj, "routeCode", "route_code", "route"),
        "diseaseName": _pick(obj, "diseaseName", "diseaseType", "name"),
        "severity": _pick(obj, "severity", "level", "grade"),
        "stakeStart": _pick(obj, "stakeStart", "startStake", "startStakeNo"),
        "stakeEnd": _pick(obj, "stakeEnd", "endStake", "endStakeNo"),
    }


def _region_args(map_context: Any) -> Dict[str, Any]:
    if not map_context:
        return {}
    return {
        "mode": map_context.mode,
        "routeCode": map_context.routeCode,
        "year": map_context.year,
        "geometry": getattr(map_context, "geometry", None),
        "viewport": map_context.viewport,
    }


def _has_stake_range(obj: Dict[str, Any]) -> bool:
    return bool(_pick(obj, "stakeStart", "startStake", "startStakeNo") and _pick(obj, "stakeEnd", "endStake", "endStakeNo"))


def _stake_args(obj: Dict[str, Any], base: Dict[str, Any]) -> Dict[str, Any]:
    return _merge_args(
        base,
        {
            "routeCode": _pick(obj, "routeCode", "route_code", "route"),
            "stakeStart": _pick(obj, "stakeStart", "startStake", "startStakeNo"),
            "stakeEnd": _pick(obj, "stakeEnd", "endStake", "endStakeNo"),
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
        for key in ["list", "items", "records", "sources", "data"]:
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
