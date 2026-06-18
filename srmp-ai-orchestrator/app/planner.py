from typing import Any, Dict, List, Optional

from .config import settings
from .intent import is_explicit_solution_draft_request, normalize_object_type
from .schemas import MapAiAgentRequest, MapAiContext, ToolCall


def plan_tools(
    request: MapAiAgentRequest,
    intent: str,
    intent_detail: Optional[Dict[str, Any]] = None,
    capability: Optional[Dict[str, Any]] = None,
) -> List[ToolCall]:
    ctx = request.mapContext
    obj = ctx.mapObject if ctx and ctx.mapObject else request.mapObject or {}
    mode = str(ctx.mode or "").upper() if ctx and ctx.mode else ""
    action = str(request.action or (request.options or {}).get("action") or "").upper()
    calls: List[ToolCall] = []

    followup = followup_source(ctx)
    if followup:
        return plan_followup_tools(request, followup)

    if should_use_capability_policy(capability, intent):
        plan_capability_tools(calls, request, intent, capability or {})
        if calls:
            return dedupe_and_limit(calls)

    if intent == "NEARBY_ANALYSIS":
        add(calls, "gis.queryNearbyObjects", context_args(ctx, {"limit": 20}), "查询当前对象周边病害和评定结果")
    if intent == "REGION_ANALYSIS":
        add(calls, "gis.queryRegionSummary", region_args(ctx, {"limit": 50}), "查询框选区域或路线统计摘要")
    if intent == "ROUTE_ANALYSIS":
        add(calls, "gis.queryRegionSummary", region_args(ctx, {"limit": 50}), "查询路线统计摘要")
        add(calls, "gis.queryAssessmentResults", context_args(ctx, {"limit": 50}), "路线评定结果查询")
        add(calls, "gis.queryDiseases", context_args(ctx, {"limit": 50}), "路线病害查询")
    if intent == "SOLUTION_GENERATE" and (action == "GENERATE_REGION_SOLUTION" or mode == "REGION"):
        add(calls, "gis.queryRegionSummary", region_args(ctx, {"limit": 50}), "生成区域方案前查询区域统计摘要")
    if intent == "SOLUTION_GENERATE" and (action == "GENERATE_ROUTE_REPORT" or mode == "ROUTE"):
        add(calls, "gis.queryRegionSummary", region_args(ctx, {"limit": 50}), "生成路线报告前查询路线统计摘要")
        add(calls, "gis.queryAssessmentResults", context_args(ctx, {"limit": 50}), "生成路线报告前查询评定结果")
        add(calls, "gis.queryDiseases", context_args(ctx, {"limit": 50}), "生成路线报告前查询病害")
    if intent == "OBJECT_ANALYSIS" or (intent == "SOLUTION_GENERATE" and action not in {"GENERATE_REGION_SOLUTION", "GENERATE_ROUTE_REPORT"}):
        plan_object_tools(calls, ctx, obj)
    if intent == "TEMPLATE_VERIFY":
        add(calls, "template.match", {"intent": intent, "routeCode": ctx.routeCode if ctx else None, "year": ctx.year if ctx else None}, "检查方案模板匹配情况")
    if intent == "SOLUTION_GENERATE":
        add(calls, "solution.generateDraft", context_args(ctx, {"intent": intent}), "生成方案草稿能力预检查")
    if should_use_knowledge(request.options, intent):
        add(calls, "knowledge.retrieve", knowledge_args(request, intent, capability), "知识库检索处置规则")
    if not calls:
        add(calls, "knowledge.retrieve", knowledge_args(request, intent, capability), "默认知识库问答")
    return dedupe_and_limit(calls)


def followup_source(ctx: Optional[MapAiContext]) -> Dict[str, Any]:
    if not ctx:
        return {}
    direct = getattr(ctx, "followupSource", None)
    if isinstance(direct, dict):
        return direct
    extra = ctx.extra if isinstance(ctx.extra, dict) else {}
    nested = first_value(extra, "followupSource", "followup_source")
    if isinstance(nested, dict):
        return nested
    raw_context = first_dict(extra, "rawContext", "raw_context")
    nested = first_value(raw_context, "followupSource", "followup_source")
    return nested if isinstance(nested, dict) else {}


def plan_followup_tools(
    request: MapAiAgentRequest,
    source: Dict[str, Any],
) -> List[ToolCall]:
    calls: List[ToolCall] = []
    binding_type = str(source.get("bindingType") or source.get("binding_type") or "NONE").strip().upper()
    binding_status = str(source.get("bindingStatus") or source.get("binding_status") or "UNVERIFIED").strip().upper()
    target = source.get("mapTarget") or source.get("map_target")
    target = target if isinstance(target, dict) else {}

    if binding_status not in {"VALID", "UNVERIFIED"} or binding_type not in {"OBJECT", "RANGE"}:
        _add_followup_knowledge(calls, request)
        return dedupe_and_limit(calls)

    if binding_type == "OBJECT":
        object_type = normalize_object_type(
            str(first_value(target, "objectType", "object_type") or "")
        )
        object_id = first_value(target, "objectId", "object_id")
        if not object_type or object_id in (None, ""):
            _add_followup_knowledge(calls, request)
            return dedupe_and_limit(calls)
        args = followup_target_args(request, target, {"limit": 20})
        if object_type == "DISEASE":
            add(calls, "gis.queryNearbyObjects", args, "查询来源病害周边对象")
        elif object_type in {"ASSESSMENT_RESULT", "EVALUATION_UNIT"}:
            add(calls, "gis.queryAssessmentResults", args, "查询来源评定结果")
            if _has_complete_stake_range(target):
                add(
                    calls,
                    "gis.queryDiseasesByStakeRange",
                    {**args, "limit": 50},
                    "查询来源评定范围内病害",
                )
        elif object_type == "ROAD_SECTION":
            add(calls, "gis.queryAssessmentResults", args, "查询来源路段评定结果")
            add(calls, "gis.queryDiseases", {**args, "limit": 50}, "查询来源路段病害")
            if _has_complete_stake_range(target):
                add(
                    calls,
                    "gis.queryDiseasesByStakeRange",
                    {**args, "limit": 50},
                    "查询来源路段桩号范围内病害",
                )
        elif object_type == "ROAD_ROUTE":
            add(calls, "gis.queryRegionSummary", {**args, "limit": 50}, "查询来源路线统计")
            add(calls, "gis.queryAssessmentResults", {**args, "limit": 50}, "查询来源路线评定结果")
            add(calls, "gis.queryDiseases", {**args, "limit": 50}, "查询来源路线病害")
        elif object_type == "MAP_REGION":
            add(calls, "gis.queryRegionSummary", {**args, "limit": 50}, "查询来源区域统计")
    else:
        args = followup_target_args(request, target, {"limit": 50})
        geometry = first_value(target, "geometry")
        bbox = first_value(target, "bbox")
        route_code = first_value(target, "routeCode", "route_code")
        start_stake = first_value(target, "startStake", "start_stake")
        end_stake = first_value(target, "endStake", "end_stake")
        if geometry not in (None, "", [], {}) or bbox not in (None, "", [], {}):
            add(calls, "gis.queryRegionSummary", args, "查询来源空间范围统计")
        elif route_code not in (None, "") and start_stake is not None and end_stake is not None:
            add(calls, "gis.queryAssessmentResults", args, "查询来源桩号范围评定结果")
            add(calls, "gis.queryDiseasesByStakeRange", args, "查询来源桩号范围病害")

    _add_followup_knowledge(calls, request)
    return dedupe_and_limit(calls)


def followup_target_args(
    request: MapAiAgentRequest,
    target: Dict[str, Any],
    extra: Dict[str, Any],
) -> Dict[str, Any]:
    ctx = request.mapContext
    extra_context = ctx.extra if ctx and isinstance(ctx.extra, dict) else {}
    raw_context = first_dict(extra_context, "rawContext", "raw_context")
    query = first_dict(raw_context, "query")
    start_stake = first_value(target, "startStake", "start_stake", "stakeStart", "stake_start")
    end_stake = first_value(target, "endStake", "end_stake", "stakeEnd", "stake_end")
    args = {
        "tenantId": ctx.tenantId if ctx else None,
        "projectId": first_value(extra_context, "projectId", "project_id")
        or first_value(query, "projectId", "project_id")
        or first_value(raw_context, "projectId", "project_id"),
        "year": ctx.year if ctx else None,
        "sectionTier": first_value(query, "sectionTier", "section_tier")
        or first_value(extra_context, "sectionTier", "section_tier"),
        "contextScope": "SOURCE_BINDING",
        "objectType": first_value(target, "objectType", "object_type"),
        "objectId": first_value(target, "objectId", "object_id"),
        "routeCode": first_value(target, "routeCode", "route_code"),
        "stakeStart": start_stake,
        "stakeEnd": end_stake,
        "startStake": start_stake,
        "endStake": end_stake,
        "geometry": first_value(target, "geometry"),
        "bbox": first_value(target, "bbox"),
    }
    args.update(extra)
    return compact(args)


def _has_complete_stake_range(target: Dict[str, Any]) -> bool:
    route_code = first_value(target, "routeCode", "route_code")
    start_stake = first_value(target, "startStake", "start_stake", "stakeStart", "stake_start")
    end_stake = first_value(target, "endStake", "end_stake", "stakeEnd", "stake_end")
    return route_code not in (None, "") and start_stake is not None and end_stake is not None


def _add_followup_knowledge(
    calls: List[ToolCall],
    request: MapAiAgentRequest,
) -> None:
    add(
        calls,
        "knowledge.retrieve",
        knowledge_args(request, "KNOWLEDGE_QA"),
        "围绕来源内容继续解释",
    )


def should_use_capability_policy(capability: Optional[Dict[str, Any]], intent: str) -> bool:
    if not isinstance(capability, dict):
        return False
    capability_id = str(capability.get("capabilityId") or "")
    category = str(capability.get("category") or "").upper()
    if not capability_id or capability_id.startswith("legacy."):
        return False
    return category in {"KNOWLEDGE", "MAP_ANALYSIS", "SOLUTION"}


def plan_capability_tools(calls: List[ToolCall], request: MapAiAgentRequest, intent: str, capability: Dict[str, Any]) -> None:
    policy = capability.get("toolPolicy") if isinstance(capability.get("toolPolicy"), dict) else {}
    prohibited = set(_policy_list(policy, "prohibited"))
    required = _policy_list(policy, "required")
    optional = _policy_list(policy, "optional")
    for tool_name in required:
        if tool_name in prohibited:
            continue
        add_capability_tool(calls, request, intent, tool_name, "required", capability)
    for tool_name in optional:
        if tool_name in prohibited:
            continue
        if tool_name == "knowledge.retrieve" and not should_use_knowledge(request.options, intent):
            continue
        add_capability_tool(calls, request, intent, tool_name, "optional", capability)


def add_capability_tool(calls: List[ToolCall], request: MapAiAgentRequest, intent: str, tool_name: str, relation: str, capability: Dict[str, Any]) -> None:
    ctx = request.mapContext
    obj = ctx.mapObject if ctx and ctx.mapObject else request.mapObject or {}
    capability_name = capability.get("name") or capability.get("capabilityId") or intent
    reason = f"{capability_name} {relation} 工具"
    if tool_name == "gis.queryNearbyObjects":
        add(calls, tool_name, context_args(ctx, {"limit": 20}), reason)
        return
    if tool_name == "gis.queryRegionSummary":
        add(calls, tool_name, region_args(ctx, {"limit": 50}), reason)
        return
    if tool_name == "gis.queryAssessmentResults":
        limit = 50 if intent in {"ROUTE_ANALYSIS", "REGION_ANALYSIS"} else 20
        add(calls, tool_name, context_args(ctx, {"limit": limit}), reason)
        return
    if tool_name == "gis.queryDiseases":
        add(calls, tool_name, context_args(ctx, {"limit": 50}), reason)
        return
    if tool_name == "gis.queryDiseasesByStakeRange":
        add(calls, tool_name, stake_args(ctx, obj, {"limit": 50}), reason)
        return
    if tool_name == "knowledge.retrieve":
        add(calls, tool_name, knowledge_args(request, intent, capability), reason)
        return
    if tool_name == "template.match":
        add(calls, tool_name, {"intent": intent, "routeCode": ctx.routeCode if ctx else None, "year": ctx.year if ctx else None}, reason)
        return
    if tool_name == "solution.generateDraft":
        add(calls, tool_name, context_args(ctx, {"intent": intent}), reason)


def _policy_list(policy: Dict[str, Any], key: str) -> List[str]:
    value = policy.get(key)
    if not isinstance(value, list):
        return []
    return [str(item).strip() for item in value if str(item or "").strip()]


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
    return intent in {"KNOWLEDGE_QA", "SOLUTION_GENERATE", "OBJECT_ANALYSIS", "REGION_ANALYSIS", "ROUTE_ANALYSIS", "TEMPLATE_VERIFY", "GENERAL_CHAT", "NEARBY_ANALYSIS"}


def knowledge_args(request: MapAiAgentRequest, intent: str, capability: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    args: Dict[str, Any] = {"query": build_query(request, intent), "topK": option_int(request, "topK", 5)}
    filters = knowledge_filters(request, capability)
    if filters:
        args["filters"] = filters
    return args


def knowledge_filters(request: MapAiAgentRequest, capability: Optional[Dict[str, Any]]) -> Dict[str, Any]:
    if not isinstance(capability, dict):
        return {}
    capability_id = str(capability.get("capabilityId") or "").strip()
    if not capability_id or capability_id.startswith("legacy."):
        return {}

    filters: Dict[str, Any] = {"capabilityIds": [capability_id]}
    category = str(capability.get("category") or "").upper()
    if category == "SOLUTION":
        solution_types = _unique_strings([
            capability.get("solutionType"),
            (request.actionInput or {}).get("solutionType"),
            (request.options or {}).get("solutionType"),
        ])
        if solution_types:
            filters["solutionTypes"] = solution_types
        object_type = _current_object_type(request)
        if object_type:
            filters["objectTypes"] = [object_type]
    elif category == "MAP_ANALYSIS":
        object_type = _current_object_type(request)
        if object_type:
            filters["objectTypes"] = [object_type]
    return filters


def _current_object_type(request: MapAiAgentRequest) -> Optional[str]:
    ctx = request.mapContext
    obj = ctx.mapObject if ctx and ctx.mapObject else request.mapObject or {}
    object_type = normalize_object_type(first(obj, "objectType", "object_type", "type", "layerType"))
    if object_type:
        return object_type
    mode = str(ctx.mode or "").upper() if ctx and ctx.mode else ""
    if mode == "ROUTE":
        return "ROAD_ROUTE"
    return None


def _unique_strings(values: List[Any]) -> List[str]:
    result: List[str] = []
    for value in values:
        if isinstance(value, list):
            for item in _unique_strings(value):
                if item not in result:
                    result.append(item)
            continue
        text = str(value or "").strip()
        if text and text not in result:
            result.append(text)
    return result


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
    mode = str(ctx.mode if ctx else "").upper()
    object_route_code = first_value(obj, "routeCode", "route_code", "route", "routeNo", "route_no")
    context_route_code = ctx.routeCode if ctx else None
    base = {
        "tenantId": ctx.tenantId if ctx else None,
        "projectId": first_value(extra_context, "projectId", "project_id") or first_value(query, "projectId", "project_id") or first_value(raw_context, "projectId", "project_id"),
        "routeCode": object_route_code if mode == "OBJECT" and object_route_code else context_route_code,
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
    source = followup_source(ctx)
    if source:
        parts = [
            request.message or "",
            intent,
            str(source.get("sourceTitle") or source.get("source_title") or ""),
            str(source.get("contentExcerpt") or source.get("content_excerpt") or ""),
            "道路养护",
            "来源解释",
        ]
        return " ".join([part for part in parts if part])
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
