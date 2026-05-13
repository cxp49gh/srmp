from typing import Any, Dict, Iterable, List, Optional


SOURCE_ORDER = ["BUSINESS_DATA", "KNOWLEDGE", "MAP_OBJECT", "MAP_REGION", "TEMPLATE"]


def normalize_plan_preview(value: Any) -> Dict[str, Any]:
    if not isinstance(value, dict) or not value:
        return {
            "available": False,
            "planTraceId": None,
            "plannedAction": None,
            "plannedIntent": None,
            "plannedToolNames": [],
            "plannedSourceTypes": [],
            "contextChips": [],
            "warningCodes": [],
        }

    return {
        "available": True,
        "planTraceId": _string_or_none(value.get("planTraceId") or value.get("traceId") or value.get("trace_id")),
        "plannedAction": _upper_or_none(value.get("action") or value.get("plannedAction")),
        "plannedIntent": _upper_or_none(value.get("intent") or value.get("plannedIntent")),
        "plannedToolNames": _unique_strings(value.get("toolNames") or value.get("plannedToolNames") or []),
        "plannedSourceTypes": _unique_upper_strings(value.get("sourceTypes") or value.get("plannedSourceTypes") or []),
        "contextChips": _unique_strings(value.get("contextChips") or []),
        "warningCodes": _unique_upper_strings(value.get("warningCodes") or []),
    }


def build_plan_execution(plan: Optional[Dict[str, Any]], response_state: Dict[str, Any]) -> Dict[str, Any]:
    normalized_plan = plan if isinstance(plan, dict) else normalize_plan_preview(None)
    run_trace_id = _string_or_none(response_state.get("runTraceId") or response_state.get("traceId") or response_state.get("trace_id"))
    if not normalized_plan.get("available"):
        return {
            "available": False,
            "status": "NO_PLAN",
            "planTraceId": None,
            "runTraceId": run_trace_id,
            "plannedAction": None,
            "actualAction": _upper_or_none(response_state.get("actualAction")),
            "plannedIntent": None,
            "actualIntent": _upper_or_none(response_state.get("actualIntent")),
            "plannedToolNames": [],
            "actualToolNames": _actual_tool_names(response_state.get("toolResults") or []),
            "missingToolNames": [],
            "extraToolNames": [],
            "plannedSourceTypes": [],
            "actualSourceTypes": derive_actual_source_types(
                response_state.get("toolResults") or [],
                response_state.get("sources") or [],
                response_state.get("evidence") or {},
            ),
            "missingSourceTypes": [],
            "warnings": [],
        }

    planned_action = _upper_or_none(normalized_plan.get("plannedAction"))
    actual_action = _upper_or_none(response_state.get("actualAction"))
    planned_intent = _upper_or_none(normalized_plan.get("plannedIntent"))
    actual_intent = _upper_or_none(response_state.get("actualIntent"))
    planned_tools = _unique_strings(normalized_plan.get("plannedToolNames") or [])
    actual_tools = _actual_tool_names(response_state.get("toolResults") or [])
    planned_sources = _unique_upper_strings(normalized_plan.get("plannedSourceTypes") or [])
    actual_sources = derive_actual_source_types(
        response_state.get("toolResults") or [],
        response_state.get("sources") or [],
        response_state.get("evidence") or {},
    )
    missing_tools = [item for item in planned_tools if item not in actual_tools]
    extra_tools = [item for item in actual_tools if item not in planned_tools]
    missing_sources = [item for item in planned_sources if item not in actual_sources]
    warnings: List[Dict[str, str]] = []

    action_diverged = bool(planned_action and actual_action and planned_action != actual_action)
    intent_diverged = bool(planned_intent and actual_intent and planned_intent != actual_intent)
    if action_diverged:
        warnings.append({"level": "WARN", "code": "PLAN_ACTION_DIVERGED", "message": "实际执行 action 与计划不一致。"})
    if intent_diverged:
        warnings.append({"level": "WARN", "code": "PLAN_INTENT_DIVERGED", "message": "实际执行 intent 与计划不一致。"})
    if missing_tools:
        warnings.append({"level": "WARN", "code": "PLANNED_TOOL_MISSING", "message": "实际执行缺少计划中的工具。"})
    if extra_tools:
        warnings.append({"level": "INFO", "code": "EXTRA_TOOL_EXECUTED", "message": "实际执行调用了计划外工具。"})
    if missing_sources:
        warnings.append({"level": "INFO", "code": "PLANNED_SOURCE_MISSING", "message": "实际来源未命中部分计划来源。"})

    if action_diverged or intent_diverged or missing_tools:
        status = "DIVERGED"
    elif extra_tools or missing_sources:
        status = "PARTIAL"
    else:
        status = "MATCHED"

    return {
        "available": True,
        "status": status,
        "planTraceId": normalized_plan.get("planTraceId"),
        "runTraceId": run_trace_id,
        "plannedAction": planned_action,
        "actualAction": actual_action,
        "plannedIntent": planned_intent,
        "actualIntent": actual_intent,
        "plannedToolNames": planned_tools,
        "actualToolNames": actual_tools,
        "missingToolNames": missing_tools,
        "extraToolNames": extra_tools,
        "plannedSourceTypes": planned_sources,
        "actualSourceTypes": actual_sources,
        "missingSourceTypes": missing_sources,
        "warnings": warnings,
    }


def derive_actual_source_types(tool_results: Iterable[Any], sources: Iterable[Any], evidence: Dict[str, Any]) -> List[str]:
    result: List[str] = []
    for item in tool_results or []:
        raw = _dict_value(item)
        name = str(raw.get("toolName") or raw.get("name") or "")
        if name.startswith("gis."):
            _add_source(result, "BUSINESS_DATA")
        if name == "knowledge.retrieve":
            _add_source(result, "KNOWLEDGE")
        if name in {"template.match", "solution.generateDraft"}:
            _add_source(result, "TEMPLATE")

    for item in sources or []:
        raw = _dict_value(item)
        source_type = _upper_or_none(raw.get("sourceType") or raw.get("source_type") or raw.get("type"))
        if source_type:
            _add_source(result, source_type)

    if _number_value((evidence or {}).get("businessHitCount")) > 0:
        _add_source(result, "BUSINESS_DATA")
    if _number_value((evidence or {}).get("knowledgeHitCount")) > 0:
        _add_source(result, "KNOWLEDGE")

    return sorted(result, key=lambda item: SOURCE_ORDER.index(item) if item in SOURCE_ORDER else len(SOURCE_ORDER))


def _actual_tool_names(tool_results: Iterable[Any]) -> List[str]:
    names: List[str] = []
    for item in tool_results or []:
        raw = _dict_value(item)
        name = _string_or_none(raw.get("toolName") or raw.get("name"))
        if name and name not in names:
            names.append(name)
    return names


def _unique_strings(values: Iterable[Any]) -> List[str]:
    result: List[str] = []
    if not isinstance(values, list):
        return result
    for value in values:
        item = _string_or_none(value)
        if item and item not in result:
            result.append(item)
    return result


def _unique_upper_strings(values: Iterable[Any]) -> List[str]:
    result: List[str] = []
    if not isinstance(values, list):
        return result
    for value in values:
        item = _upper_or_none(value)
        if item and item not in result:
            result.append(item)
    return result


def _add_source(result: List[str], source_type: str) -> None:
    normalized = _upper_or_none(source_type)
    if normalized and normalized not in result:
        result.append(normalized)


def _dict_value(value: Any) -> Dict[str, Any]:
    if hasattr(value, "model_dump"):
        return value.model_dump(exclude_none=True)
    return value if isinstance(value, dict) else {}


def _string_or_none(value: Any) -> Optional[str]:
    if value is None or value == "":
        return None
    return str(value)


def _upper_or_none(value: Any) -> Optional[str]:
    text = _string_or_none(value)
    return text.upper() if text else None


def _number_value(value: Any) -> float:
    try:
        return float(value or 0)
    except (TypeError, ValueError):
        return 0
