import json
from typing import Any, Dict, List

from .config import settings
from .schemas import MapAiAgentRequest, ToolResult


def build_prompt(request: MapAiAgentRequest, intent: str, tool_results: List[ToolResult]) -> str:
    context = request.mapContext.model_dump(exclude_none=True) if request.mapContext else {}
    compact_results = [_compact_tool_result(item) for item in tool_results]
    return f"""
你是 SRMP 智路养护平台的一张图 AI 养护助手。
请只基于当前地图上下文、工具结果和知识库资料回答，不要编造不存在的道路、桩号、病害或规范来源。

【用户问题】
{request.message or ''}

【识别意图】
{intent}

【地图上下文】
{json.dumps(context, ensure_ascii=False, default=str)}

【工具结果】
{json.dumps(compact_results, ensure_ascii=False, default=str)}

请输出中文答案，结构建议：
1. 结论
2. 当前对象/区域依据
3. 处置或复核建议
4. 风险与后续动作
""".strip()


def build_fallback_answer(request: MapAiAgentRequest, intent: str, tool_results: List[ToolResult]) -> str:
    context = request.mapContext.model_dump(exclude_none=True) if request.mapContext else {}
    route_code = context.get("routeCode") or _pick(context.get("mapObject"), "routeCode", "route_code") or "当前路线"
    obj_name = _pick(context.get("mapObject"), "diseaseName", "disease_name", "objectName", "name", "objectType", "object_type")
    success_results = [item for item in tool_results if item.success]
    failed_results = [item for item in tool_results if not item.success]

    lines = ["【基于当前地图对象】"]
    if obj_name:
        lines.append(f"- 当前对象：{obj_name}，路线：{route_code}。")
    else:
        lines.append(f"- 当前范围：{route_code}。")

    if success_results:
        lines.append("- 已完成工具分析：" + "；".join(_safe_summary(item) for item in success_results[:4]) + "。")
    if failed_results:
        lines.append("- 部分工具暂未返回有效结果：" + "；".join(_safe_summary(item) for item in failed_results[:3]) + "。")

    lines.append("\n建议：")
    if intent == "OBJECT_ANALYSIS":
        lines.append("1. 先结合病害类型、严重程度、位置桩号和近邻评定结果确认是否需要复核。")
        lines.append("2. 若为裂缝、坑槽、龟裂等典型病害，优先匹配知识库处置规则，再生成处置建议或方案草稿。")
    elif intent == "REGION_ANALYSIS":
        lines.append("1. 先按路线/框选区域统计病害数量、严重程度和评定等级分布。")
        lines.append("2. 优先处置低分单元、重度病害集中区和存在连续劣化风险的路段。")
    else:
        lines.append("1. 优先依据知识库命中资料回答；资料不足时，应提示补充检测、评定或现场复核数据。")

    lines.append("3. 涉及保存方案、转工单或更新业务数据时，应走前端人工确认流程。")
    return "\n".join(lines)


def _compact_tool_result(result: ToolResult) -> Dict[str, Any]:
    data = result.data
    if isinstance(data, dict):
        data = _trim_dict(data)
    return {
        "toolName": result.toolName,
        "success": result.success,
        "summary": result.summary,
        "count": result.count,
        "data": data,
        "errorMessage": result.errorMessage,
    }


def _trim_dict(data: Dict[str, Any]) -> Dict[str, Any]:
    copied = dict(data)
    for key in ("items", "hits", "records", "list"):
        if isinstance(copied.get(key), list):
            copied[key] = copied[key][: settings.max_tool_items_in_prompt]
    return copied


def _pick(data: Any, *keys: str) -> str:
    if not isinstance(data, dict):
        return ""
    for key in keys:
        value = data.get(key)
        if value is not None and str(value).strip():
            return str(value).strip()
    return ""


def _safe_summary(result: ToolResult) -> str:
    return f"{result.toolName}: {result.summary or result.errorMessage or '无摘要'}"
