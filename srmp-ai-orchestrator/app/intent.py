from typing import Any, Dict, Optional, Tuple

from .schemas import MapAiAgentRequest


NATIVE_INTENTS = {
    "OBJECT_ANALYSIS",
    "REGION_ANALYSIS",
    "NEARBY_ANALYSIS",
    "KNOWLEDGE_QA",
    "SOLUTION_GENERATE",
    "TEMPLATE_VERIFY",
    "TASK_SAVE",
    "GENERAL_CHAT",
}


def recognize_intent(request: MapAiAgentRequest) -> Tuple[str, Dict[str, Any]]:
    message = (request.message or "").strip()
    ctx = request.mapContext
    obj = ctx.mapObject if ctx and ctx.mapObject else request.mapObject or {}
    mode = str(ctx.mode or "").upper() if ctx and ctx.mode else ""
    object_type = normalize_object_type(first_string(obj, "objectType", "object_type", "type", "layerType"))
    detail = {
        "mode": mode,
        "objectType": object_type,
        "hasMapObject": bool(obj),
        "hasRegionSummary": bool(ctx and ctx.regionSummary),
        "hasGeometry": bool(ctx and ctx.geometry),
        "solutionDraftRequested": is_explicit_solution_draft_request(message),
        "saveRequested": contains_any(message, "保存", "归档", "确认方案"),
    }

    if detail["saveRequested"]:
        return "TASK_SAVE", detail
    if contains_any(message, "模板", "是否生效", "变量"):
        return "TEMPLATE_VERIFY", detail
    if detail["solutionDraftRequested"]:
        return "SOLUTION_GENERATE", detail
    if contains_any(message, "周边", "附近", "相邻", "集中区"):
        return "NEARBY_ANALYSIS", detail
    if contains_any(message, "区域", "框选", "范围") or mode in {"REGION", "BOX", "POLYGON", "SELECTION"} or detail["hasRegionSummary"] or detail["hasGeometry"]:
        return "REGION_ANALYSIS", detail
    if contains_any(message, "规范", "标准", "怎么处理", "工艺", "依据"):
        return "KNOWLEDGE_QA", detail
    if obj or mode == "OBJECT":
        return "OBJECT_ANALYSIS", detail
    return "GENERAL_CHAT", detail


def normalize_object_type(value: Optional[str]) -> str:
    raw = (value or "").strip().upper()
    aliases = {
        "ROUTE": "ROAD_ROUTE",
        "ROADROUTE": "ROAD_ROUTE",
        "SECTION": "ROAD_SECTION",
        "ROAD_SEGMENT": "ROAD_SECTION",
        "SEGMENT": "ROAD_SECTION",
        "ASSESSMENT": "ASSESSMENT_RESULT",
        "ASSESSMENT_RESULT_RECORD": "ASSESSMENT_RESULT",
        "EVALUATION_UNIT": "ASSESSMENT_RESULT",
        "DISEASE_RECORD": "DISEASE",
    }
    return aliases.get(raw, raw)


def is_explicit_solution_draft_request(message: str) -> bool:
    return contains_any(
        message,
        "生成方案",
        "方案草稿",
        "生成草稿",
        "保存方案",
        "保存为方案",
        "保存为方案任务",
        "形成方案",
        "生成任务",
        "生成养护方案",
        "保存任务",
        "创建方案任务",
    )


def contains_any(text: str, *words: str) -> bool:
    value = text or ""
    return any(word and word in value for word in words)


def first_string(data: Dict[str, Any], *keys: str) -> Optional[str]:
    if not isinstance(data, dict):
        return None
    for key in keys:
        value = data.get(key)
        if value not in (None, ""):
            return str(value).strip()
    raw = data.get("raw")
    if isinstance(raw, dict):
        return first_string(raw, *keys)
    return None
