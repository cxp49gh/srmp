from dataclasses import dataclass, field
from typing import Any, Dict, Iterable, List, Optional, Set, Tuple

from .config import settings
from .planner import build_query, context_args, region_args
from .schemas import MapAiAgentRequest, ToolCall, ToolResult


ADAPTIVE_READONLY_TOOLS = {
    "knowledge.retrieve",
    "gis.queryDiseases",
    "gis.queryAssessmentResults",
    "gis.queryDiseasesByStakeRange",
    "gis.queryRegionSummary",
    "gis.queryNearbyObjects",
    "template.match",
}


@dataclass
class AdaptivePlanningDecision:
    enabled: bool
    should_replan: bool
    status: str
    reason: str
    added_calls: List[ToolCall] = field(default_factory=list)
    skipped_tool_names: List[str] = field(default_factory=list)
    iteration: int = 1
    max_iterations: int = 1
    max_added_tools: int = 1
    evidence_sufficient_before: bool = False
    evidence_before: Dict[str, Any] = field(default_factory=dict)

    def to_summary(
        self,
        executed_results: Optional[Iterable[ToolResult]] = None,
        evidence_after: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        status = self.status
        evidence_sufficient_after = bool((evidence_after or {}).get("sufficient")) if evidence_after is not None else None
        if self.should_replan and executed_results is not None:
            results = list(executed_results or [])
            status = "EXECUTED" if any(item.success for item in results) else "FAILED"
        evidence_before_summary = summarize_evidence(self.evidence_before, self.evidence_sufficient_before)
        evidence_after_summary = summarize_evidence(evidence_after, evidence_sufficient_after) if evidence_after is not None else None
        return {
            "enabled": self.enabled,
            "status": status,
            "iterations": 1 if self.should_replan else 0,
            "maxIterations": self.max_iterations,
            "maxAddedTools": self.max_added_tools,
            "reason": self.reason,
            "addedToolNames": [item.toolName for item in self.added_calls],
            "skippedToolNames": self.skipped_tool_names,
            "evidenceSufficientBefore": self.evidence_sufficient_before,
            "evidenceSufficientAfter": evidence_sufficient_after,
            "evidenceBefore": evidence_before_summary,
            "evidenceAfter": evidence_after_summary,
        }


def plan_adaptive_tools(
    request: MapAiAgentRequest,
    intent: str,
    intent_detail: Optional[Dict[str, Any]],
    evidence: Dict[str, Any],
    tool_results: List[ToolResult],
    existing_plan: List[ToolCall],
    iteration: int = 1,
) -> AdaptivePlanningDecision:
    options = request.options or {}
    max_iterations = _effective_max_iterations(options)
    max_added_tools = _effective_max_added_tools(options)
    evidence_sufficient = bool((evidence or {}).get("sufficient"))
    if not getattr(settings, "adaptive_planning_enabled", True) or _bool_option(options, "disableAdaptivePlanning", False):
        return _decision(False, False, "DISABLED", "自适应工具规划已关闭。", max_iterations, max_added_tools, evidence or {}, evidence_sufficient)
    if max_iterations <= 0:
        return _decision(True, False, "DISABLED", "本次请求不允许自适应追加工具。", max_iterations, max_added_tools, evidence or {}, evidence_sufficient)
    if iteration > max_iterations:
        return _decision(True, False, "SKIPPED_LIMIT", "已达到自适应规划轮次上限。", max_iterations, max_added_tools, evidence or {}, evidence_sufficient)
    if evidence_sufficient:
        return _decision(True, False, "SKIPPED_SUFFICIENT", "第一轮证据已足够。", max_iterations, max_added_tools, evidence or {}, evidence_sufficient)
    if len(tool_results or []) >= settings.max_tool_calls:
        return _decision(True, False, "SKIPPED_LIMIT", "已达到工具调用数量上限。", max_iterations, max_added_tools, evidence or {}, evidence_sufficient)

    executed_tool_names = {item.toolName for item in tool_results or [] if item.toolName}
    candidates = build_adaptive_tool_calls(request, intent, intent_detail or {}, evidence or {}, executed_tool_names)
    remaining_slots = settings.max_tool_calls - len(tool_results or [])
    added_calls, skipped = _filter_candidates(candidates, executed_tool_names, existing_plan or [], remaining_slots, max_added_tools)
    if not added_calls:
        status = "SKIPPED_LIMIT" if candidates and (remaining_slots <= 0 or max_added_tools <= 0) else "SKIPPED_NO_CANDIDATE"
        reason = "已达到自适应追加工具预算上限。" if status == "SKIPPED_LIMIT" else "证据不足，但没有未执行过的安全只读候选工具。"
        return AdaptivePlanningDecision(
            enabled=True,
            should_replan=False,
            status=status,
            reason=reason,
            skipped_tool_names=skipped,
            max_iterations=max_iterations,
            max_added_tools=max_added_tools,
            evidence_sufficient_before=evidence_sufficient,
            evidence_before=evidence or {},
        )
    return AdaptivePlanningDecision(
        enabled=True,
        should_replan=True,
        status="PLANNED",
        reason=_reason_for_calls(added_calls, evidence or {}, max_added_tools, skipped),
        added_calls=added_calls,
        skipped_tool_names=skipped,
        iteration=iteration,
        max_iterations=max_iterations,
        max_added_tools=max_added_tools,
        evidence_sufficient_before=evidence_sufficient,
        evidence_before=evidence or {},
    )


def build_adaptive_tool_calls(
    request: MapAiAgentRequest,
    intent: str,
    intent_detail: Optional[Dict[str, Any]],
    evidence: Dict[str, Any],
    executed_tool_names: Set[str],
) -> List[ToolCall]:
    ctx = request.mapContext
    candidates: List[ToolCall] = []
    business_hits = _number(evidence.get("businessHitCount"))
    knowledge_hits = _number(evidence.get("knowledgeHitCount"))
    mode = str(ctx.mode if ctx else "").upper()

    if _is_region_request(intent, mode):
        candidates.append(ToolCall(toolName="gis.queryRegionSummary", args=region_args(ctx, {"limit": 50}), reason="第一轮证据不足，补充区域统计摘要"))

    if business_hits <= 0 or knowledge_hits <= 0:
        candidates.append(
            ToolCall(
                toolName="knowledge.retrieve",
                args={"query": build_query(request, intent), "topK": _option_int(request, "topK", 5)},
                reason="第一轮证据不足，补充知识库规则和处置依据",
            )
        )

    if intent in {"OBJECT_ANALYSIS", "SOLUTION_GENERATE"}:
        candidates.append(ToolCall(toolName="gis.queryAssessmentResults", args=context_args(ctx, {"limit": 20}), reason="第一轮证据不足，补充评定结果"))

    return candidates


def _filter_candidates(
    candidates: Iterable[ToolCall],
    executed_tool_names: Set[str],
    existing_plan: List[ToolCall],
    remaining_slots: int,
    max_added_tools: int,
) -> Tuple[List[ToolCall], List[str]]:
    added: List[ToolCall] = []
    skipped: List[str] = []
    existing_tool_names = {item.toolName for item in existing_plan or [] if item.toolName}
    for call in candidates:
        if call.toolName in executed_tool_names:
            skipped.append(call.toolName)
            continue
        if call.toolName not in settings.allowed_tools or call.toolName not in ADAPTIVE_READONLY_TOOLS:
            skipped.append(call.toolName)
            continue
        if call.toolName in existing_tool_names:
            skipped.append(call.toolName)
            continue
        if remaining_slots <= 0 or len(added) >= max_added_tools:
            skipped.append(call.toolName)
            continue
        added.append(call)
        existing_tool_names.add(call.toolName)
        remaining_slots -= 1
    return added, _unique(skipped)


def _decision(
    enabled: bool,
    should_replan: bool,
    status: str,
    reason: str,
    max_iterations: int,
    max_added_tools: int,
    evidence: Dict[str, Any],
    evidence_sufficient: bool,
) -> AdaptivePlanningDecision:
    return AdaptivePlanningDecision(
        enabled=enabled,
        should_replan=should_replan,
        status=status,
        reason=reason,
        max_iterations=max_iterations,
        max_added_tools=max_added_tools,
        evidence_sufficient_before=evidence_sufficient,
        evidence_before=evidence,
    )


def _effective_max_iterations(options: Dict[str, Any]) -> int:
    setting_limit = getattr(settings, "max_adaptive_iterations", 1)
    requested = _option_int_from_dict(options, "maxAdaptiveIterations", setting_limit)
    return max(0, min(requested, setting_limit, 1))


def _effective_max_added_tools(options: Dict[str, Any]) -> int:
    requested = _option_int_from_dict(options, "maxAdaptiveAddedTools", getattr(settings, "max_adaptive_added_tools", 1))
    return max(0, min(requested, int(settings.max_tool_calls or 0)))


def summarize_evidence(evidence: Optional[Dict[str, Any]], fallback_sufficient: Optional[bool] = None) -> Dict[str, Any]:
    data = evidence or {}
    return {
        "sufficient": bool(data.get("sufficient")) if data.get("sufficient") is not None else bool(fallback_sufficient),
        "businessHitCount": int(_number(data.get("businessHitCount"))),
        "knowledgeHitCount": int(_number(data.get("knowledgeHitCount"))),
        "toolSuccessCount": int(_number(data.get("toolSuccessCount"))),
        "toolFailedCount": int(_number(data.get("toolFailedCount"))),
    }


def _option_int(request: MapAiAgentRequest, key: str, default: int) -> int:
    return _option_int_from_dict(request.options or {}, key, default)


def _option_int_from_dict(options: Dict[str, Any], key: str, default: int) -> int:
    try:
        return int(options.get(key, default))
    except (TypeError, ValueError):
        return default


def _bool_option(options: Dict[str, Any], key: str, default: bool) -> bool:
    value = options.get(key)
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    return str(value).strip().lower() in {"1", "true", "yes", "y", "on"}


def _is_region_request(intent: str, mode: str) -> bool:
    return intent in {"REGION_ANALYSIS", "ROUTE_ANALYSIS"} or mode in {"REGION", "BOX", "POLYGON", "SELECTION", "ROUTE"}


def _reason_for_calls(calls: List[ToolCall], evidence: Dict[str, Any], max_added_tools: int, skipped: List[str]) -> str:
    names = [item.toolName for item in calls]
    budget_suffix = " 本轮预算最多追加 " + str(max_added_tools) + " 个工具。" if skipped else ""
    if names == ["knowledge.retrieve"]:
        return "业务工具未命中，追加知识检索补充解释依据。" + budget_suffix
    if "gis.queryRegionSummary" in names:
        return "第一轮证据不足，追加区域统计摘要补充业务依据。" + budget_suffix
    return "第一轮证据不足，追加安全只读工具补充依据。" + budget_suffix


def _number(value: Any) -> float:
    try:
        return float(value or 0)
    except (TypeError, ValueError):
        return 0


def _unique(values: Iterable[str]) -> List[str]:
    result: List[str] = []
    for value in values:
        if value and value not in result:
            result.append(value)
    return result
