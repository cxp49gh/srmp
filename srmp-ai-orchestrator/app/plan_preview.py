from typing import Any, Dict, Iterable, List, Optional

from .schemas import MapAiAgentRequest, ToolCall


TOOL_LABELS = {
    "gis.queryNearbyObjects": "查询周边对象",
    "gis.queryRegionSummary": "查询区域统计",
    "gis.queryAssessmentResults": "查询评定结果",
    "gis.queryDiseases": "查询病害",
    "gis.queryDiseasesByStakeRange": "查询桩号范围病害",
    "knowledge.retrieve": "检索知识库",
    "template.match": "匹配方案模板",
    "solution.generateDraft": "生成方案草稿",
}

WRITE_TOOL_NAMES = {
    "solution.saveDraft",
    "solution.updateDraft",
    "solution.confirmDraft",
    "solution.archiveDraft",
}


def enrich_tool_plan(tool_plan: Iterable[ToolCall]) -> List[Dict[str, Any]]:
    result: List[Dict[str, Any]] = []
    for item in tool_plan or []:
        raw = item.model_dump(exclude_none=True) if hasattr(item, "model_dump") else dict(item)
        name = str(raw.get("toolName") or raw.get("name") or "")
        write_risk = is_write_tool(name)
        raw["label"] = tool_label(name)
        raw["readOnly"] = not write_risk
        raw["writeRisk"] = write_risk
        raw["argsSummary"] = summarize_args(raw.get("args") or {})
        result.append(raw)
    return result


def build_source_hints(request: MapAiAgentRequest, tool_plan: Iterable[Dict[str, Any]]) -> List[Dict[str, str]]:
    hints: List[Dict[str, str]] = []
    ctx = request.mapContext
    if ctx and ctx.mapObject:
        add_hint(hints, "MAP_OBJECT", "地图对象", "当前请求包含选中地图对象。")
    if ctx and (ctx.geometry or getattr(ctx, "regionGeometry", None) or ctx.regionSummary or str(ctx.mode or "").upper() == "REGION"):
        add_hint(hints, "MAP_REGION", "框选区域", "当前请求包含区域范围或区域统计摘要。")
    for item in tool_plan or []:
        name = str(item.get("toolName") or item.get("name") or "")
        if name.startswith("gis."):
            add_hint(hints, "BUSINESS_DATA", "业务数据", "计划会查询 GIS 业务图层、病害或评定结果。")
        if name == "knowledge.retrieve":
            add_hint(hints, "KNOWLEDGE", "知识库", "计划会检索养护规则、处置建议或评定说明。")
        if name in {"template.match", "solution.generateDraft"}:
            add_hint(hints, "TEMPLATE", "方案模板", "计划会匹配或使用方案模板生成结构化内容。")
    return hints


def build_plan_warnings(request: MapAiAgentRequest, tool_plan: Iterable[Dict[str, Any]]) -> List[Dict[str, str]]:
    warnings: List[Dict[str, str]] = []
    ctx = request.mapContext
    action = str(request.action or "").upper()
    mode = str(ctx.mode if ctx else "").upper()
    has_region_geometry = bool(ctx and (ctx.geometry or getattr(ctx, "regionGeometry", None)))
    has_region_summary = bool(ctx and ctx.regionSummary)
    has_map_object = bool(ctx and ctx.mapObject)

    if action in {"ANALYZE_REGION", "GENERATE_REGION_SOLUTION"} or mode == "REGION":
        if not has_region_geometry:
            warnings.append(
                {
                    "level": "WARN",
                    "code": "REGION_GEOMETRY_MISSING",
                    "message": "当前为区域分析，但计划请求未携带 geometry；实际执行可能无法精确查询空间范围。",
                }
            )
        if not has_region_summary:
            warnings.append(
                {
                    "level": "INFO",
                    "code": "REGION_SUMMARY_MISSING",
                    "message": "当前计划未携带区域统计摘要；执行时会依赖工具重新查询统计。",
                }
            )

    if action in {"ANALYZE_OBJECT", "GENERATE_OBJECT_SOLUTION"} and not has_map_object:
        warnings.append(
            {
                "level": "WARN",
                "code": "MAP_OBJECT_MISSING",
                "message": "当前为对象分析，但计划请求未携带 mapObject；请先在地图上选择对象。",
            }
        )

    if any(bool(item.get("writeRisk")) for item in tool_plan or []):
        warnings.append(
            {
                "level": "WARN",
                "code": "WRITE_TOOL_PLANNED",
                "message": "计划中包含写入型工具，执行前需要用户确认。",
            }
        )
    return warnings


def tool_label(name: str) -> str:
    return TOOL_LABELS.get(name, name or "未知工具")


def is_write_tool(name: str) -> bool:
    if name in WRITE_TOOL_NAMES:
        return True
    lowered = name.lower()
    return any(token in lowered for token in [".save", ".update", ".delete", ".confirm", ".archive", ".write"])


def summarize_args(args: Dict[str, Any]) -> Dict[str, Any]:
    keys = [
        "tenantId",
        "routeCode",
        "year",
        "mode",
        "limit",
        "topK",
        "intent",
        "solutionType",
        "stakeStart",
        "stakeEnd",
    ]
    summary = {key: args.get(key) for key in keys if args.get(key) not in (None, "")}
    geometry = args.get("geometry")
    if isinstance(geometry, dict):
        summary["geometryType"] = geometry.get("type") or "Geometry"
    region_summary = args.get("regionSummary")
    if isinstance(region_summary, dict):
        summary["regionSummary"] = summarize_region(region_summary)
    return summary


def summarize_region(region: Dict[str, Any]) -> Dict[str, Any]:
    disease_summary = region.get("diseaseSummary") or {}
    return {
        key: value
        for key, value in {
            "routeCount": first(region, "routeCount", "route_count"),
            "sectionCount": first(region, "sectionCount", "section_count"),
            "diseaseCount": first(region, "diseaseCount", "disease_count") or first(disease_summary, "disease_count", "diseaseCount"),
            "assessmentCount": first(region, "assessmentCount", "assessment_count"),
        }.items()
        if value not in (None, "")
    }


def first(data: Optional[Dict[str, Any]], *keys: str) -> Any:
    if not isinstance(data, dict):
        return None
    for key in keys:
        value = data.get(key)
        if value not in (None, ""):
            return value
    return None


def add_hint(hints: List[Dict[str, str]], source_type: str, label: str, reason: str) -> None:
    if any(item.get("sourceType") == source_type for item in hints):
        return
    hints.append({"sourceType": source_type, "label": label, "reason": reason})
