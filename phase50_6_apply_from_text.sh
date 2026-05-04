#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-$(pwd)}"

if [ ! -d "$ROOT/srmp-ai-orchestrator" ]; then
  echo "ERROR: 请在 SRMP 根目录执行，或传入 SRMP 根目录路径" >&2
  echo "用法: bash phase50_6_apply_from_text.sh /path/to/srmp" >&2
  exit 1
fi

mkdir -p "$ROOT/srmp-ai-orchestrator/app" "$ROOT/scripts" "$ROOT/docs"

echo "[Phase50.6] 写入 config.py..."

cat > "$ROOT/srmp-ai-orchestrator/app/config.py" <<'PY'
import os
from dataclasses import dataclass
from typing import List


def _int_env(name: str, default: int) -> int:
    value = os.getenv(name)
    if value is None or value == "":
        return default
    try:
        return int(value)
    except ValueError:
        return default


def _float_env(name: str, default: float) -> float:
    value = os.getenv(name)
    if value is None or value == "":
        return default
    try:
        return float(value)
    except ValueError:
        return default


def _bool_env(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None or value == "":
        return default
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


def _list_env(name: str, default: str) -> List[str]:
    value = os.getenv(name, default)
    return [item.strip() for item in value.split(",") if item.strip()]


@dataclass(frozen=True)
class Settings:
    app_name: str = os.getenv("SRMP_ORCHESTRATOR_APP_NAME", "srmp-langgraph-orchestrator")
    java_base_url: str = os.getenv("SRMP_JAVA_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
    java_tool_execute_path: str = os.getenv("SRMP_JAVA_TOOL_EXECUTE_PATH", "/api/agent/tools/execute")
    java_tool_list_path: str = os.getenv("SRMP_JAVA_TOOL_LIST_PATH", "/api/agent/tools")
    java_connect_timeout_seconds: int = _int_env("SRMP_JAVA_CONNECT_TIMEOUT_SECONDS", 10)
    java_read_timeout_seconds: int = _int_env("SRMP_JAVA_READ_TIMEOUT_SECONDS", 120)
    allow_write_tools: bool = _bool_env("SRMP_LANGGRAPH_ALLOW_WRITE_TOOLS", False)
    use_llm: bool = _bool_env("SRMP_LANGGRAPH_USE_LLM", False)
    llm_base_url: str = os.getenv("SRMP_LLM_BASE_URL", "").rstrip("/")
    llm_api_key: str = os.getenv("SRMP_LLM_API_KEY", "")
    llm_model: str = os.getenv("SRMP_LLM_MODEL", "")
    llm_temperature: float = _float_env("SRMP_LLM_TEMPERATURE", 0.2)
    max_tool_items_in_prompt: int = _int_env("SRMP_LANGGRAPH_MAX_TOOL_ITEMS_IN_PROMPT", 8)

    strategy_version: str = os.getenv("SRMP_LANGGRAPH_STRATEGY_VERSION", "phase50.6-readonly-v1")
    enable_context_enrich: bool = _bool_env("SRMP_LANGGRAPH_ENABLE_CONTEXT_ENRICH", True)
    enable_evidence_fusion: bool = _bool_env("SRMP_LANGGRAPH_ENABLE_EVIDENCE_FUSION", True)
    enable_quality_guard: bool = _bool_env("SRMP_LANGGRAPH_ENABLE_QUALITY_GUARD", True)
    parallel_tool_execution: bool = _bool_env("SRMP_LANGGRAPH_PARALLEL_TOOLS", True)
    max_parallel_tools: int = _int_env("SRMP_LANGGRAPH_MAX_PARALLEL_TOOLS", 4)
    max_tool_calls: int = _int_env("SRMP_LANGGRAPH_MAX_TOOL_CALLS", 6)
    min_answer_chars: int = _int_env("SRMP_LANGGRAPH_MIN_ANSWER_CHARS", 80)
    require_evidence_prefix: bool = _bool_env("SRMP_LANGGRAPH_REQUIRE_EVIDENCE_PREFIX", True)
    allowed_tools: List[str] = None  # type: ignore

    def __post_init__(self):
        object.__setattr__(
            self,
            "allowed_tools",
            _list_env(
                "SRMP_LANGGRAPH_ALLOWED_TOOLS",
                "knowledge.retrieve,gis.queryDiseases,gis.queryAssessmentResults,gis.queryDiseasesByStakeRange,gis.queryRegionSummary,gis.queryNearbyObjects,template.match",
            ),
        )


settings = Settings()
PY

echo "[Phase50.6] 写入 prompt.py..."

cat > "$ROOT/srmp-ai-orchestrator/app/prompt.py" <<'PY'
from typing import Any, Dict, List

from .config import settings
from .schemas import MapAiAgentRequest, ToolResult


def build_answer_prompt(
    request: MapAiAgentRequest,
    intent: str,
    context_summary: Dict[str, Any],
    tool_results: List[ToolResult],
    evidence: Dict[str, Any],
) -> str:
    lines = [
        "你是道路养护一张图 AI 助手。",
        "必须基于地图上下文、GIS 查询结果和知识库证据回答。",
        "当前处于只读模式：不能声称已保存、已派单、已更新数据库。",
        "",
        f"意图：{intent}",
        f"地图上下文摘要：{context_summary}",
        f"证据摘要：{evidence}",
        "",
        "工具结果：",
    ]
    for item in tool_results[: settings.max_tool_items_in_prompt]:
        lines.append(f"- tool={item.toolName}, success={item.success}, reason={item.reason}, data={item.data}")
    lines.extend(
        [
            "",
            f"用户问题：{request.message}",
            "",
            "请输出：",
            "1. 先说明依据的当前对象或框选区域；",
            "2. 给出主要发现；",
            "3. 给出处置建议或下一步核查建议；",
            "4. 如果证据不足，明确说明缺少哪些数据。",
        ]
    )
    return "\n".join(lines)


def fallback_answer(
    request: MapAiAgentRequest,
    intent: str,
    context_summary: Dict[str, Any],
    evidence: Dict[str, Any],
    tool_results: List[ToolResult],
) -> str:
    prefix = "【基于当前地图对象】"
    mode = context_summary.get("mode")
    if mode and str(mode).upper() in {"BOX", "POLYGON", "REGION", "SELECTION"}:
        prefix = "【基于当前框选区域】"

    success_tools = [item.toolName for item in tool_results if item.success]
    failed_tools = [item.toolName for item in tool_results if not item.success]
    route = context_summary.get("routeCode") or "当前路线"
    year = context_summary.get("year") or "当前年度"

    parts = [
        f"{prefix}已按 {settings.strategy_version} 只读编排完成分析。",
        f"分析范围：{route}，{year}。",
        f"识别意图：{intent}。",
    ]
    if success_tools:
        parts.append("已调用工具：" + "、".join(success_tools) + "。")
    if evidence.get("knowledgeHitCount"):
        parts.append(f"知识库命中 {evidence.get('knowledgeHitCount')} 条，可作为处置建议依据。")
    if evidence.get("businessHitCount"):
        parts.append(f"业务数据命中 {evidence.get('businessHitCount')} 条，建议结合病害等级、桩号范围和评定结果排序处理。")
    if failed_tools:
        parts.append("部分工具未成功：" + "、".join(failed_tools) + "，建议检查 Tool Gateway 或数据条件。")

    parts.append("建议优先核查重度病害、低分评定单元、连续桩号范围内的集中病害，并结合养护规范生成后续方案草稿。")
    parts.append("当前为只读分析，未执行保存、派单或数据库更新。")
    return "\n".join(parts)
PY

echo "[Phase50.6] 写入 workflow.py..."

cat > "$ROOT/srmp-ai-orchestrator/app/workflow.py" <<'PY'
import asyncio
import re
import time
from typing import Any, Dict, List, Optional, TypedDict

from .config import settings
from .java_tools import JavaToolGateway
from .llm_client import LlmClient
from .prompt import build_answer_prompt, fallback_answer
from .schemas import MapAiAgentRequest, ToolCall, ToolResult

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

    async def run(self, request: MapAiAgentRequest, tenant_id: str, trace_id: str) -> Dict[str, Any]:
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
            "failedTools": [{"toolName": item.toolName, "error": item.error} for item in failed],
            "toolSummary": [
                {
                    "toolName": item.toolName,
                    "success": item.success,
                    "hitCount": _estimate_hit_count(item.data),
                    "reason": item.reason,
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

    def _to_response(self, state: AgentState) -> Dict[str, Any]:
        tool_results = state.get("tool_results", [])
        return {
            "answer": state.get("answer", ""),
            "data": {
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
            "trace": {
                "orchestratorProvider": "langgraph",
                "strategyVersion": settings.strategy_version,
                "nodeFlow": NODE_FLOW,
                "steps": state.get("steps", []),
                "costMs": int((time.perf_counter() - state.get("started_at", time.perf_counter())) * 1000),
            },
            "toolResults": [item.model_dump(exclude_none=True) for item in tool_results],
        }


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
PY

echo "[Phase50.6] 修补 main.py：新增 /strategy 并使用增强 workflow..."

python3 - "$ROOT/srmp-ai-orchestrator/app/main.py" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

text = text.replace(
    "from .workflow import LANGGRAPH_AVAILABLE, LangGraphWorkflow",
    "from .workflow import LANGGRAPH_AVAILABLE, LangGraphWorkflow, strategy_metadata",
)

if "async def strategy()" not in text:
    marker = '@app.get("/api/srmp/langgraph/observability/summary")'
    insert = '''@app.get("/api/srmp/langgraph/strategy")
async def strategy() -> dict:
    return strategy_metadata()


'''
    text = text.replace(marker, insert + marker)

text = text.replace(
    '"version": "50.5.0"',
    '"version": "50.6.0"',
)

text = text.replace(
    'version="50.5.0"',
    'version="50.6.0"',
)

if '"strategy": strategy_metadata(),' not in text:
    text = text.replace(
        '"observability": runtime_audit_store.summary(),',
        '"observability": runtime_audit_store.summary(),\n        "strategy": strategy_metadata(),',
    )

path.write_text(text, encoding="utf-8")
PY

echo "[Phase50.6] 写入验证脚本..."

cat > "$ROOT/scripts/check-phase50-6-langgraph-strategy.sh" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

JAVA_BASE_URL="${JAVA_BASE_URL:-http://127.0.0.1:8080}"
LANGGRAPH_URL="${LANGGRAPH_URL:-http://127.0.0.1:18080}"
TENANT_ID="${TENANT_ID:-default}"
REQUIRE_LANGGRAPH="${REQUIRE_LANGGRAPH:-false}"

curl_json() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  if [ -n "$body" ]; then
    curl -sS -X "$method" "$url" \
      -H 'Content-Type: application/json' \
      -H "X-Tenant-Id: ${TENANT_ID}" \
      -H 'X-AI-Trace-Id: phase50-6-check' \
      -d "$body"
  else
    curl -sS -X "$method" "$url" \
      -H 'Content-Type: application/json' \
      -H "X-Tenant-Id: ${TENANT_ID}"
  fi
}

assert_contains() {
  local text="$1"
  local pattern="$2"
  local message="$3"
  if ! echo "$text" | grep -q "$pattern"; then
    echo "FAIL: $message" >&2
    echo "$text" >&2
    exit 1
  fi
}

printf '[1/5] LangGraph strategy metadata...\n'
STRATEGY="$(curl_json GET "${LANGGRAPH_URL}/api/srmp/langgraph/strategy")"
assert_contains "$STRATEGY" 'phase50.6-readonly-v1' 'strategy should include phase50.6-readonly-v1'
assert_contains "$STRATEGY" 'nodeFlow' 'strategy should include nodeFlow'

printf '[2/5] LangGraph ready...\n'
READY="$(curl_json GET "${LANGGRAPH_URL}/ready")"
assert_contains "$READY" 'toolGateway' 'ready should include toolGateway status'

printf '[3/5] LangGraph direct chat strategy smoke...\n'
BODY='{"message":"Phase50.6 smoke：请分析 G210 当前对象养护建议","mapContext":{"tenantId":"'"${TENANT_ID}"'","mode":"ROUTE","routeCode":"G210","year":2026,"mapObject":{"objectType":"disease","diseaseName":"裂缝","severity":"中度","routeCode":"G210"}},"options":{"topK":3}}'
LG_CHAT="$(curl_json POST "${LANGGRAPH_URL}/api/srmp/langgraph/map-agent/chat" "$BODY")"
assert_contains "$LG_CHAT" 'strategyVersion' 'LangGraph response should include strategyVersion'
assert_contains "$LG_CHAT" 'toolPlan' 'LangGraph response should include toolPlan'
assert_contains "$LG_CHAT" 'evidence' 'LangGraph response should include evidence'
assert_contains "$LG_CHAT" 'quality_guard' 'LangGraph trace should include quality_guard'

printf '[4/5] Java orchestrator path smoke...\n'
JAVA_CHAT="$(curl_json POST "${JAVA_BASE_URL}/api/agent/map-agent/chat" "$BODY")"
assert_contains "$JAVA_CHAT" 'code' 'Java chat should be wrapped by ApiResult/R'
if [ "$REQUIRE_LANGGRAPH" = "true" ]; then
  assert_contains "$JAVA_CHAT" 'langgraph' 'Java chat should route to langgraph when REQUIRE_LANGGRAPH=true'
fi

printf '[5/5] Runtime observability recent...\n'
RECENT="$(curl_json GET "${LANGGRAPH_URL}/api/srmp/langgraph/observability/recent?limit=5")"
assert_contains "$RECENT" 'records' 'recent response should be available'

printf '\nPASS: Phase50.6 LangGraph read-only strategy check completed.\n'
SH

chmod +x "$ROOT/scripts/check-phase50-6-langgraph-strategy.sh"

echo "[Phase50.6] 写入文档..."

cat > "$ROOT/docs/phase50_6_langgraph_readonly_strategy.md" <<'MD'
# Phase50.6 LangGraph 只读编排策略增强

## 目标

把 LangGraph Runtime 从“能调用工具”升级为“有明确只读分析策略”。

节点流：

```text
context_build
  -> intent_recognize
  -> context_enrich
  -> tool_plan
  -> tool_execute
  -> evidence_fuse
  -> answer_generate
  -> quality_guard