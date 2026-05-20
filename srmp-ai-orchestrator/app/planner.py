from typing import Any, Dict, List, Optional

from .config import settings
from .intent import is_explicit_solution_draft_request, normalize_object_type
from .schemas import MapAiAgentRequest, MapAiContext, ToolCall


def plan_tools(request: MapAiAgentRequest, intent: str, intent_detail: Optional[Dict[str, Any]] = None) -> List[ToolCall]:
    ctx = request.mapContext
    obj = ctx.mapObject if ctx and ctx.mapObject else request.mapObject or {}
    calls: List[ToolCall] = []

    if intent == "NEARBY_ANALYSIS":
        add(calls, "gis.queryNearbyObjects", context_args(ctx, {"limit": 20}), "查询当前对象周边病害和评定结果")
    if intent == "REGION_ANALYSIS":
        add(calls, "gis.queryRegionSummary", region_args(ctx, {"limit": 50}), "查询框选区域或路线统计摘要")
    if intent in {"OBJECT_ANALYSIS", "SOLUTION_GENERATE"}:
        plan_object_tools(calls, ctx, obj)
    if intent == "TEMPLATE_VERIFY":
        add(calls, "template.match", {"intent": intent, "routeCode": ctx.routeCode if ctx else None, "year": ctx.year if ctx else None}, "检查方案模板匹配情况")
    if intent == "SOLUTION_GENERATE" and is_explicit_solution_draft_request(request.message or ""):
        add(calls, "solution.generateDraft", {"intent": intent, "routeCode": ctx.routeCode if ctx else None, "year": ctx.year if ctx else None}, "生成方案草稿能力预检查")
    if should_use_knowledge(request.options, intent):
        add(calls, "knowledge.retrieve", {"query": build_query(request, intent), "topK": option_int(request, "topK", 5)}, "知识库检索处置规则")
    if not calls:
        add(calls, "knowledge.retrieve", {"query": build_query(request, intent), "topK": option_int(request, "topK", 5)}, "默认知识库问答")
    return dedupe_and_limit(calls)


def plan_object_tools(calls: List[ToolCall], ctx: Optional[MapAiContext], obj: Dict[str, Any]) -> None:
    object_type = normalize_object_type(first(obj, "objectType", "object_type", "type", "layerType"))
    if object_type == "ROAD_ROUTE":
        add(calls, "gis.queryAssessmentResults", context_args(ctx, {"limit": 20}), "路线评定结果查询")
        add(calls, "gis.queryDiseases", context_args(ctx, {"limit": 50}), "路线病害查询")
        return
    if object_type == "ROAD_SECTION":
        add(calls, "gis.queryAssessmentResults", context_args(ctx, {"limit": 20}), "路段评定结果查询")
        add(calls, "gis.queryDiseases", context_args(ctx, {"limit": 50}), "路段病害查询")
        add(calls, "gis.queryDiseasesByStakeRange", stake_args(ctx, obj, {"limit": 50}), "路段桩号范围内病害查询")
        return
    if object_type == "DISEASE":
        add(calls, "gis.queryNearbyObjects", context_args(ctx, {"limit": 20}), "当前病害周边对象查询")
        return
    if object_type == "ASSESSMENT_RESULT":
        add(calls, "gis.queryAssessmentResults", context_args(ctx, {"limit": 20}), "当前评定结果查询")
        add(calls, "gis.queryDiseasesByStakeRange", stake_args(ctx, obj, {"limit": 50}), "评定单元内病害查询")


def should_use_knowledge(options: Dict[str, Any], intent: str) -> bool:
    if options and "useKnowledge" in options:
        return str(options.get("useKnowledge")).lower() not in {"false", "0", "no", "off"}
    return intent in {"KNOWLEDGE_QA", "SOLUTION_GENERATE", "OBJECT_ANALYSIS", "REGION_ANALYSIS", "TEMPLATE_VERIFY", "GENERAL_CHAT", "NEARBY_ANALYSIS"}


def add(calls: List[ToolCall], tool_name: str, args: Dict[str, Any], reason: str) -> None:
    if settings.allowed_tools and tool_name not in settings.allowed_tools:
        return
    calls.append(ToolCall(toolName=tool_name, args=compact(args), reason=reason))


def dedupe_and_limit(calls: List[ToolCall]) -> List[ToolCall]:
    result: List[ToolCall] = []
    seen = set()
    for call in calls:
        key = (call.toolName, str(sorted((call.args or {}).items())))
        if key in seen:
            continue
        seen.add(key)
        result.append(call)
        if len(result) >= settings.max_tool_calls:
            break
    return result


def context_args(ctx: Optional[MapAiContext], extra: Dict[str, Any]) -> Dict[str, Any]:
    obj = ctx.mapObject if ctx and ctx.mapObject else {}
    raw = obj.get("raw") if isinstance(obj.get("raw"), dict) else {}
    extra_context = ctx.extra if ctx and isinstance(ctx.extra, dict) else {}
    raw_context = first_dict(extra_context, "rawContext", "raw_context")
    query = first_dict(raw_context, "query")
    base = {
        "tenantId": ctx.tenantId if ctx else None,
        "projectId": first_value(extra_context, "projectId", "project_id") or first_value(query, "projectId", "project_id") or first_value(raw_context, "projectId", "project_id"),
        "routeCode": ctx.routeCode if ctx else None,
        "year": ctx.year if ctx else None,
        "sectionTier": first_value(query, "sectionTier", "section_tier") or first_value(extra_context, "sectionTier", "section_tier"),
        "contextScope": ctx.mode if ctx else None,
        "objectType": first_value(obj, "objectType", "object_type", "type", "layerType"),
        "objectId": first_value(obj, "objectId", "object_id", "id"),
        "assessmentObjectType": first_value(raw, "objectType", "object_type"),
        "direction": first_value(obj, "direction") or first_value(raw, "direction"),
        "stakeStart": first_present(first_value(obj, "stakeStart", "startStake", "start_stake"), first_value(raw, "stakeStart", "startStake", "start_stake")),
        "stakeEnd": first_present(first_value(obj, "stakeEnd", "endStake", "end_stake"), first_value(raw, "stakeEnd", "endStake", "end_stake")),
        "selectedLayers": ctx.selectedLayers if ctx else None,
    }
    base.update(extra)
    return base


def region_args(ctx: Optional[MapAiContext], extra: Dict[str, Any]) -> Dict[str, Any]:
    base = context_args(ctx, extra)
    if ctx:
        base.update({"mode": ctx.mode, "geometry": ctx.geometry, "viewport": ctx.viewport, "regionSummary": ctx.regionSummary})
    return base


def stake_args(ctx: Optional[MapAiContext], obj: Dict[str, Any], extra: Dict[str, Any]) -> Dict[str, Any]:
    base = context_args(ctx, {})
    base.update({
        "routeCode": first(obj, "routeCode", "route_code", "route", "routeNo", "route_no") or base.get("routeCode"),
        "stakeStart": first_present(first_value(obj, "stakeStart", "startStake", "startStakeNo", "start_stake", "startMileage"), base.get("stakeStart")),
        "stakeEnd": first_present(first_value(obj, "stakeEnd", "endStake", "endStakeNo", "end_stake", "endMileage"), base.get("stakeEnd")),
    })
    base.update(extra)
    return base


def build_query(request: MapAiAgentRequest, intent: str) -> str:
    ctx = request.mapContext
    parts = [request.message or "", intent]
    if ctx:
        parts.extend([str(ctx.routeCode or ""), str(ctx.year or ""), str(ctx.mode or "")])
        obj = ctx.mapObject or {}
        parts.extend([str(first(obj, "diseaseName", "disease_name", "diseaseType", "disease_type", "name") or ""), str(first(obj, "severity", "grade") or "")])
    parts.extend(["道路养护", "处置建议", "评定规则"])
    return " ".join([part for part in parts if part])


def option_int(request: MapAiAgentRequest, key: str, default: int) -> int:
    try:
        return int((request.options or {}).get(key) or default)
    except Exception:
        return default


def compact(data: Dict[str, Any]) -> Dict[str, Any]:
    return {key: value for key, value in data.items() if value not in (None, "")}


def first(data: Dict[str, Any], *keys: str) -> Optional[str]:
    if not isinstance(data, dict):
        return None
    for key in keys:
        value = data.get(key)
        if value not in (None, ""):
            return str(value).strip()
    raw = data.get("raw")
    if isinstance(raw, dict):
        return first(raw, *keys)
    return None


def first_value(data: Dict[str, Any], *keys: str) -> Optional[Any]:
    if not isinstance(data, dict):
        return None
    for key in keys:
        value = data.get(key)
        if value not in (None, ""):
            return value
    raw = data.get("raw")
    if isinstance(raw, dict):
        return first_value(raw, *keys)
    return None


def first_dict(data: Dict[str, Any], *keys: str) -> Dict[str, Any]:
    value = first_value(data, *keys)
    return value if isinstance(value, dict) else {}


def first_present(*values: Any) -> Optional[Any]:
    for value in values:
        if value not in (None, ""):
            return value
    return None
