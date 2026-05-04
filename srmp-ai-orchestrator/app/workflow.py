import time
import uuid
from typing import Any, Dict, List, Optional, TypedDict

try:
    from langgraph.graph import END, StateGraph

    LANGGRAPH_AVAILABLE = True
except Exception:  # noqa: BLE001
    END = "__end__"  # type: ignore
    StateGraph = None  # type: ignore
    LANGGRAPH_AVAILABLE = False

from .java_tools import JavaToolGateway, extract_knowledge_sources
from .llm_client import LlmClient
from .prompt import build_fallback_answer, build_prompt
from .schemas import MapAiAgentRequest, MapAiAgentResponse, MapAiContext, ToolCall, ToolResult


class AgentState(TypedDict, total=False):
    request: MapAiAgentRequest
    tenant_id: str
    trace_id: str
    intent: str
    tool_plan: List[ToolCall]
    tool_results: List[ToolResult]
    answer: str
    trace_steps: List[Dict[str, Any]]
    started_at: float


class LangGraphWorkflow:
    def __init__(self, gateway: JavaToolGateway, llm_client: LlmClient) -> None:
        self.gateway = gateway
        self.llm_client = llm_client
        self.graph = self._build_graph() if LANGGRAPH_AVAILABLE else None

    async def run(self, request: MapAiAgentRequest, tenant_id: Optional[str], trace_id: Optional[str]) -> MapAiAgentResponse:
        state: AgentState = {
            "request": request,
            "tenant_id": tenant_id or _tenant_from_request(request) or "default",
            "trace_id": trace_id or str(uuid.uuid4()),
            "tool_results": [],
            "trace_steps": [],
            "started_at": time.perf_counter(),
        }
        if self.graph is not None:
            final_state = await self.graph.ainvoke(state)  # type: ignore[attr-defined]
        else:
            final_state = await self._run_sequential(state)
        return self._to_response(final_state)

    def _build_graph(self):
        workflow = StateGraph(AgentState)
        workflow.add_node("context_build", self._context_build)
        workflow.add_node("intent_recognize", self._intent_recognize)
        workflow.add_node("tool_plan", self._tool_plan)
        workflow.add_node("tool_execute", self._tool_execute)
        workflow.add_node("answer_generate", self._answer_generate)
        workflow.add_node("quality_guard", self._quality_guard)
        workflow.add_edge("context_build", "intent_recognize")
        workflow.add_edge("intent_recognize", "tool_plan")
        workflow.add_edge("tool_plan", "tool_execute")
        workflow.add_edge("tool_execute", "answer_generate")
        workflow.add_edge("answer_generate", "quality_guard")
        workflow.add_edge("quality_guard", END)
        workflow.set_entry_point("context_build")
        return workflow.compile()

    async def _run_sequential(self, state: AgentState) -> AgentState:
        for node in (
            self._context_build,
            self._intent_recognize,
            self._tool_plan,
            self._tool_execute,
            self._answer_generate,
            self._quality_guard,
        ):
            update = await node(state)
            state.update(update)
        return state

    async def _context_build(self, state: AgentState) -> Dict[str, Any]:
        request = state["request"]
        if request.mapContext is None:
            request.mapContext = MapAiContext()
        if request.mapContext:
            if not request.mapContext.tenantId:
                request.mapContext.tenantId = state["tenant_id"]
            if not request.mapContext.userQuestion:
                request.mapContext.userQuestion = request.message
            if not request.mapContext.mapObject and request.mapObject:
                request.mapContext.mapObject = request.mapObject
        self._step(state, "context_build", "标准化地图上下文")
        return {"request": request}

    async def _intent_recognize(self, state: AgentState) -> Dict[str, Any]:
        request = state["request"]
        text = (request.message or "").lower()
        context = request.mapContext
        obj = context.mapObject if context and context.mapObject else {}
        object_type = str(obj.get("objectType") or obj.get("object_type") or "").lower()
        if any(word in text for word in ("区域", "框选", "多边形", "范围", "统计", "汇总")) or (context and context.mode and context.mode.upper() in {"REGION", "BOX", "POLYGON"}):
            intent = "REGION_ANALYSIS"
        elif "assessment" in object_type or "评定" in text or any(word in text for word in ("mqi", "pqi", "pci", "rqi", "rdi")):
            intent = "ASSESSMENT_ANALYSIS"
        elif obj or "disease" in object_type or any(word in text for word in ("病害", "裂缝", "坑槽", "龟裂", "沉陷", "车辙", "处置")):
            intent = "OBJECT_ANALYSIS"
        else:
            intent = "KNOWLEDGE_QA"
        self._step(state, "intent_recognize", "识别意图", {"intent": intent})
        return {"intent": intent}

    async def _tool_plan(self, state: AgentState) -> Dict[str, Any]:
        request = state["request"]
        intent = state["intent"]
        calls: List[ToolCall] = []
        map_context = request.mapContext
        obj = map_context.mapObject if map_context and map_context.mapObject else {}
        query = _build_query(request)

        if intent == "REGION_ANALYSIS":
            calls.append(ToolCall(toolName="gis.queryRegionSummary", args={"limit": 50}, reason="区域/框选范围统计"))
            calls.append(ToolCall(toolName="gis.queryDiseases", args={"limit": 50}, reason="区域病害明细"))
        elif intent == "ASSESSMENT_ANALYSIS":
            calls.append(ToolCall(toolName="gis.queryAssessmentResults", args={"limit": 20}, reason="评定结果查询"))
            if _has_stake_range(obj):
                calls.append(ToolCall(toolName="gis.queryDiseasesByStakeRange", args={"limit": 50}, reason="评定单元内病害查询"))
        elif intent == "OBJECT_ANALYSIS":
            calls.append(ToolCall(toolName="gis.queryDiseases", args={"limit": 30, "severity": _pick(obj, "severity")}, reason="当前对象病害查询"))
            calls.append(ToolCall(toolName="gis.queryNearbyObjects", args={"limit": 20}, reason="周边对象辅助判断"))

        calls.append(ToolCall(toolName="knowledge.retrieve", args={"query": query}, reason="知识库检索处置规则"))
        self._step(state, "tool_plan", "规划只读工具", {"tools": [item.model_dump() for item in calls]})
        return {"tool_plan": calls}

    async def _tool_execute(self, state: AgentState) -> Dict[str, Any]:
        results: List[ToolResult] = []
        for call in state.get("tool_plan", []):
            result = await self.gateway.execute_tool(
                call=call,
                request=state["request"],
                tenant_id=state["tenant_id"],
                trace_id=state["trace_id"],
            )
            results.append(result)
        self._step(
            state,
            "tool_execute",
            "执行 Java Tool Gateway",
            {"success": sum(1 for item in results if item.success), "total": len(results)},
        )
        return {"tool_results": results}

    async def _answer_generate(self, state: AgentState) -> Dict[str, Any]:
        request = state["request"]
        intent = state["intent"]
        tool_results = state.get("tool_results", [])
        prompt = build_prompt(request, intent, tool_results)
        answer = await self.llm_client.chat(prompt)
        if not answer:
            answer = build_fallback_answer(request, intent, tool_results)
        self._step(state, "answer_generate", "生成回答", {"llmUsed": bool(answer)})
        return {"answer": answer}

    async def _quality_guard(self, state: AgentState) -> Dict[str, Any]:
        answer = (state.get("answer") or "").strip()
        if not answer:
            answer = "当前 AI 编排服务未生成有效答案，请检查工具网关、知识库和模型配置。"
        for start, end in (("<think>", "</think>"), ("<thinking>", "</thinking>")):
            while start in answer and end in answer:
                prefix, rest = answer.split(start, 1)
                _, suffix = rest.split(end, 1)
                answer = (prefix + suffix).strip()
        self._step(state, "quality_guard", "回答质量保护")
        return {"answer": answer}

    def _to_response(self, state: AgentState) -> MapAiAgentResponse:
        request = state["request"]
        tool_results = state.get("tool_results", [])
        sources = extract_knowledge_sources(tool_results)
        trace = {
            "traceId": state.get("trace_id"),
            "engine": "langgraph" if LANGGRAPH_AVAILABLE else "sequential-fallback",
            "mode": "LANGGRAPH_AGENT",
            "status": "SUCCESS",
            "steps": state.get("trace_steps", []),
            "costMs": int((time.perf_counter() - state.get("started_at", time.perf_counter())) * 1000),
        }
        map_context = request.mapContext.model_dump(exclude_none=True) if request.mapContext else None
        return MapAiAgentResponse(
            answer=state.get("answer") or "",
            mode="LANGGRAPH_AGENT",
            intent=state.get("intent"),
            mapContext=map_context,
            toolResults=[item.model_dump(exclude_none=True) for item in tool_results],
            knowledgeSources=sources,
            sources=sources,
            trace=trace,
            data={
                "orchestrator": "langgraph",
                "orchestratorProvider": "langgraph",
                "orchestratorFallback": False,
                "langgraphAvailable": LANGGRAPH_AVAILABLE,
                "mapObjectUsed": bool(map_context and map_context.get("mapObject")),
                "readOnly": True,
                "toolSuccessCount": sum(1 for item in tool_results if item.success),
                "toolTotalCount": len(tool_results),
            },
        )

    def _step(self, state: AgentState, name: str, summary: str, data: Optional[Dict[str, Any]] = None) -> None:
        steps = state.setdefault("trace_steps", [])
        steps.append({"name": name, "summary": summary, "data": data or {}, "ts": int(time.time() * 1000)})


def _tenant_from_request(request: MapAiAgentRequest) -> Optional[str]:
    if request.mapContext and request.mapContext.tenantId:
        return request.mapContext.tenantId
    return None


def _build_query(request: MapAiAgentRequest) -> str:
    parts: List[str] = []
    if request.message:
        parts.append(request.message)
    if request.mapContext:
        if request.mapContext.routeCode:
            parts.append(request.mapContext.routeCode)
        if request.mapContext.year:
            parts.append(str(request.mapContext.year))
        obj = request.mapContext.mapObject or {}
        for key in ("objectType", "object_type", "diseaseName", "disease_name", "diseaseType", "disease_type", "severity", "grade"):
            value = obj.get(key)
            if value:
                parts.append(str(value))
    return " ".join(parts).strip()


def _pick(data: Dict[str, Any], key: str) -> str:
    value = data.get(key) if isinstance(data, dict) else None
    return "" if value is None else str(value)


def _has_stake_range(data: Dict[str, Any]) -> bool:
    if not isinstance(data, dict):
        return False
    return any(data.get(key) is not None for key in ("startStake", "start_stake", "endStake", "end_stake"))
