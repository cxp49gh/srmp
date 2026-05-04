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
