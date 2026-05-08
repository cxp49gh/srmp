from typing import Any, Dict, List

from .evidence import first, tool_summary
from .intent import normalize_object_type
from .schemas import MapAiAgentRequest, ToolResult


def enhance_answer(
    answer: str,
    request: MapAiAgentRequest,
    intent: str,
    intent_detail: Dict[str, Any],
    tool_results: List[ToolResult],
    sources: List[Dict[str, Any]],
) -> str:
    value = (answer or "").strip()
    ctx = request.mapContext
    obj = ctx.mapObject if ctx and ctx.mapObject else request.mapObject or {}
    object_type = normalize_object_type(first(obj, "objectType", "object_type", "type", "layerType"))
    if intent == "REGION_ANALYSIS":
        value = append_once(value, "当前框选区域专项分析", region_advice(ctx.regionSummary if ctx else {}, tool_results))
    if object_type == "DISEASE" or first(obj, "diseaseName", "disease_name", "diseaseType", "disease_type"):
        value = append_once(value, "当前对象专项处置建议", disease_advice(obj, tool_results, sources))
    if object_type == "ASSESSMENT_RESULT":
        value = append_once(value, "当前评定单元专项分析", assessment_advice(obj, tool_results))
    if object_type == "ROAD_ROUTE":
        value = append_once(value, "当前路线专项分析", route_advice(obj, tool_results))
    if object_type == "ROAD_SECTION":
        value = append_once(value, "当前路段专项分析", section_advice(obj, tool_results))
    value = enrich_crack_terms(value, request.message or "", obj, sources)
    return polish(value)


def disease_advice(obj: Dict[str, Any], tool_results: List[ToolResult], sources: List[Dict[str, Any]]) -> str:
    disease_name = first(obj, "diseaseName", "disease_name", "diseaseType", "disease_type") or "道路病害"
    severity = first(obj, "severity", "grade", "level")
    route = first(obj, "routeCode", "route_code") or "-"
    stake = stake_range(obj)
    priority = priority_by_severity(severity)
    lines = [
        "### 当前对象专项处置建议",
        f"- 当前对象：{route}{(' ' + stake) if stake else ''}，病害为{disease_name}{('，严重程度为 ' + severity) if severity else ''}。",
        f"- 建议优先级：{priority}。",
        "",
    ]
    if contains_any(disease_name, "坑槽", "POTHOLE"):
        lines.extend(
            [
                "一、主要问题",
                "坑槽影响行车安全和舒适性，通常与水损害、松散、裂缝发展、基层破坏或局部材料脱落有关。",
                "",
                "二、现场复核重点",
                "- 复核坑槽深度、边界松散范围、基层是否破坏和是否存在积水；",
                "- 检查周边是否有裂缝、松散、沉陷或重复修补痕迹。",
                "",
                "三、养护处置建议",
                "- 规则切割病害边界，清理松散材料，保持基面干净干燥；",
                "- 采用热拌料或冷补料填补，并分层摊铺、充分压实；",
                "- 若基层已破坏，应先处理基层和排水，再恢复面层。",
            ]
        )
    elif contains_any(disease_name, "沉陷", "SUBSIDENCE"):
        lines.extend(
            [
                "一、主要问题",
                "沉陷可能涉及基层松散、路基不均匀变形、含水软化或排水不良。",
                "",
                "二、现场复核重点",
                "- 复核沉陷深度、范围、发展趋势、周边裂缝和积水痕迹；",
                "- 检查基层稳定性、路基局部变形、含水情况和排水条件。",
                "",
                "三、养护处置建议",
                "- 若仅为面层局部沉陷，可局部铣刨、找平、重新摊铺并压实；",
                "- 若基层松散或含水，应先处理基层和排水；",
                "- 若涉及路基不均匀沉降，应进行局部结构修复或路基加固。",
            ]
        )
    elif contains_any(disease_name, "裂缝", "开裂", "CRACK"):
        lines.extend(
            [
                "一、主要问题",
                "裂缝类病害需重点防止雨水渗水和下渗诱发基层水损害。",
                "",
                "二、现场复核重点",
                "- 复核裂缝宽度、长度、密度、发展方向和是否渗水；",
                "- 判断是否为横向裂缝、纵向裂缝、网裂或块裂。",
                "",
                "三、养护处置建议",
                "- 轻中度裂缝可采用灌缝、开槽灌缝或封缝；",
                "- 裂缝较密集或表层老化时，可结合封层、薄层罩面或雾封层；",
                "- 重度网裂或块裂应进一步判断结构层承载能力。",
            ]
        )
    else:
        lines.extend(
            [
                "一、主要问题",
                "该对象属于道路病害，应结合严重程度、范围、周边病害和评定指标综合判断。",
                "",
                "二、现场复核重点",
                "- 复核病害范围、深度、面积、发展趋势和排水条件；",
                "- 检查是否与低分单元、同类病害聚集区或结构性问题相关。",
                "",
                "三、养护处置建议",
                "- 轻度病害可纳入日常养护；",
                "- 中度病害建议近期处置；",
                "- 重度病害建议优先复核并制定专项处置方案。",
            ]
        )
    if sources:
        lines.extend(["", "四、参考依据", "- " + "；".join([str(item.get("title") or "知识片段") for item in sources[:3]])])
    return "\n".join(lines)


def assessment_advice(obj: Dict[str, Any], tool_results: List[ToolResult]) -> str:
    summary = tool_summary(tool_results)
    route = first(obj, "routeCode", "route_code") or "-"
    return "\n".join(
        [
            "### 当前评定单元专项分析",
            f"- 当前对象：{route} {stake_range(obj)}，年度：{first(obj, 'year') or '-'}。",
            f"- 评定等级：{first(obj, 'grade') or '-'}。",
            f"- 指标值：MQI={first(obj, 'mqi') or '-'}，PQI={first(obj, 'pqi') or '-'}，PCI={first(obj, 'pci') or '-'}，RQI={first(obj, 'rqi') or '-'}，RDI={first(obj, 'rdi') or '-'}。",
            f"- 单元内病害：已查询 {summary.get('diseaseCount', 0)} 条，重点关注 PCI/PQI 偏低与病害集中分布的关联。",
            "",
            "一、主要问题",
            "该对象为道路技术状况评定结果单元，应结合 MQI/PQI/PCI 判断综合技术状况、路面使用性能和路面损坏状况。",
            "",
            "二、现场复核重点",
            "- 复核单元范围内裂缝、坑槽、沉陷、松散和修补损坏的数量、范围和严重程度；",
            "- 对比相邻评定单元，判断低分是否为局部异常还是连续区间问题。",
            "",
            "三、养护处置建议",
            "- PCI 偏低时优先处置路面破损类病害；",
            "- PQI 偏低且病害连续时，可考虑封层、薄层罩面、局部铣刨重铺或中修方案；",
            "- 若存在基层松散、含水软化或排水不良，应先处理基层和排水，再恢复面层。",
        ]
    )


def route_advice(obj: Dict[str, Any], tool_results: List[ToolResult]) -> str:
    summary = tool_summary(tool_results)
    return "\n".join(
        [
            "### 当前路线专项分析",
            f"- 当前路线：{first(obj, 'routeCode', 'route_code') or '-'}。",
            f"- 系统已查询评定结果 {summary.get('assessmentCount', 0)} 条、病害 {summary.get('diseaseCount', 0)} 条。",
            "一、主要问题",
            "路线级分析应重点识别低分单元、病害热点、连续破损区间和重复修补区间。",
            "",
            "二、养护处置建议",
            "- 先按 MQI/PQI/PCI 对路线分段排序，识别优先处置区间；",
            "- 对点状病害采用局部修补、裂缝灌缝、坑槽修补；",
            "- 对连续低分或病害聚集区间采用封层、薄层罩面、局部铣刨重铺或中修。",
        ]
    )


def section_advice(obj: Dict[str, Any], tool_results: List[ToolResult]) -> str:
    summary = tool_summary(tool_results)
    return "\n".join(
        [
            "### 当前路段专项分析",
            f"- 当前路段：{first(obj, 'routeCode', 'route_code') or '-'} {stake_range(obj)}。",
            f"- 系统已查询评定结果 {summary.get('assessmentCount', 0)} 条、病害 {summary.get('diseaseCount', 0)} 条。",
            "一、主要问题",
            "路段级分析应关注病害是否沿桩号连续分布、是否与排水、基层或重复修补有关。",
            "",
            "二、养护处置建议",
            "- 病害点状分布时采用局部修补、坑槽修补、裂缝灌缝；",
            "- 病害连续分布或评定指标偏低时采用封层、薄层罩面、局部铣刨重铺或中修；",
            "- 若存在沉陷、基层松散或排水问题，应先处理基层和排水。",
        ]
    )


def region_advice(region_summary: Dict[str, Any], tool_results: List[ToolResult]) -> str:
    summary = region_summary if isinstance(region_summary, dict) else {}
    disease = summary.get("diseaseSummary") if isinstance(summary.get("diseaseSummary"), dict) else {}
    assessment = summary.get("assessmentSummary") if isinstance(summary.get("assessmentSummary"), dict) else {}
    return "\n".join(
        [
            "### 当前框选区域专项分析",
            f"- 区域路段数：{summary.get('sectionCount', '-')}，评定单元数：{summary.get('unitCount', '-')}。",
            f"- 病害数量：{disease.get('disease_count', disease.get('diseaseCount', '-'))}，重度病害：{disease.get('heavy_count', disease.get('heavyCount', '-'))}。",
            f"- 平均 MQI：{assessment.get('avg_mqi', assessment.get('avgMqi', '-'))}。",
            "",
            "一、区域风险判断",
            "优先关注重度病害集中区、低分评定单元和病害热点重叠范围。",
            "",
            "二、区域养护建议",
            "- 对热点区间安排现场复核，确认病害边界和工程量；",
            "- 对连续低分或病害聚集区间形成区段化养护建议；",
            "- 对零散轻中度病害纳入日常养护和预防性养护计划。",
        ]
    )


def enrich_crack_terms(answer: str, message: str, obj: Dict[str, Any], sources: List[Dict[str, Any]]) -> str:
    disease_name = first(obj, "diseaseName", "disease_name", "diseaseType", "disease_type")
    if not contains_any(message + disease_name, "裂缝", "开裂", "灌缝", "封缝"):
        return answer
    compact = answer.replace(" ", "")
    if contains_any(compact, "灌缝", "封缝") and contains_any(compact, "封层", "雾封层", "薄层罩面") and contains_any(compact, "渗水", "下渗", "水损害"):
        return answer
    return answer + "\n\n### 知识库处置要点补充\n结合裂缝类病害处置资料，应重点关注雨水渗水和下渗风险，防止基层水损害扩大；处置上可采用开槽灌缝或封缝，裂缝较密集时结合封层、薄层罩面或雾封层。"


def append_once(answer: str, marker: str, addition: str) -> str:
    if marker in answer:
        return answer
    return (answer.rstrip() + "\n\n" + addition).strip() if answer else addition.strip()


def stake_range(obj: Dict[str, Any]) -> str:
    start = first(obj, "stakeStart", "startStake", "start_stake", "startMileage")
    end = first(obj, "stakeEnd", "endStake", "end_stake", "endMileage")
    if start and end:
        return f"K{start}-K{end}"
    if start:
        return f"K{start}"
    return ""


def priority_by_severity(severity: str) -> str:
    if contains_any(severity, "HEAVY", "重", "严重"):
        return "P1（优先复核和近期处置）"
    if contains_any(severity, "MEDIUM", "中"):
        return "P2（近期计划处置）"
    if contains_any(severity, "LIGHT", "轻"):
        return "P3（日常养护跟踪）"
    return "结合现场复核结果确定"


def contains_any(text: str, *words: str) -> bool:
    value = text or ""
    return any(word and word in value for word in words)


def polish(answer: str) -> str:
    return "\n".join([line.rstrip() for line in (answer or "").splitlines()]).strip()
