#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
阶段二十八：地图对象 AI 回答质量修复

修复点：
1. 统一过滤 <think>...</think>；
2. 对 objectType=DISEASE 的当前地图对象，优先返回单病害对象分析，避免误答成路线级病害统计；
3. 保留最新 mapObjectContextService.resolve(buildContextMap(request)) 体系；
4. 不覆盖整文件，找不到锚点直接失败。
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

def fail(msg):
    print("[FAIL] " + msg)
    sys.exit(1)

def backup(path: Path):
    bak = path.with_suffix(path.suffix + ".phase28.bak")
    if not bak.exists():
        bak.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")

def read(path: Path):
    if not path.exists():
        fail(f"文件不存在：{path}")
    return path.read_text(encoding="utf-8")

def patch_map_object_context():
    path = ROOT / "srmp-agent/src/main/java/com/smartroad/srmp/agent/map/MapObjectContext.java"
    s = read(path)
    backup(path)

    # 最新 raw 上 MapObjectContext 可能只有字段，但 MapObjectContextServiceImpl 使用 empty()/of()。
    # 若本地已有方法则不动；若没有，则替换为包含静态工厂的兼容版本。
    if "static MapObjectContext empty()" in s and "static MapObjectContext of(" in s:
        print(f"[SKIP] {path} 已包含 empty/of")
        return

    replacement = (
        "package com.smartroad.srmp.agent.map;\n\n"
        "import lombok.Data;\n\n"
        "import java.io.Serializable;\n"
        "import java.util.LinkedHashMap;\n"
        "import java.util.Map;\n\n"
        "@Data\n"
        "public class MapObjectContext implements Serializable {\n"
        "    private static final long serialVersionUID = 1L;\n\n"
        "    private boolean present;\n"
        "    private String objectId;\n"
        "    private String objectType;\n"
        "    private String routeCode;\n"
        "    private Integer year;\n"
        "    private Map detail;\n"
        "    private String markdown;\n\n"
        "    public static MapObjectContext empty() {\n"
        "        MapObjectContext ctx = new MapObjectContext();\n"
        "        ctx.setPresent(false);\n"
        "        ctx.setDetail(new LinkedHashMap());\n"
        "        ctx.setMarkdown(null);\n"
        "        return ctx;\n"
        "    }\n\n"
        "    @SuppressWarnings(\"unchecked\")\n"
        "    public static MapObjectContext of(Map mapObject) {\n"
        "        if (mapObject == null || mapObject.isEmpty()) {\n"
        "            return empty();\n"
        "        }\n"
        "        MapObjectContext ctx = new MapObjectContext();\n"
        "        ctx.setPresent(true);\n"
        "        ctx.setDetail(mapObject);\n"
        "        ctx.setObjectType(firstString(mapObject, \"objectType\", \"object_type\", \"type\", \"layerType\"));\n"
        "        ctx.setObjectId(firstString(mapObject, \"objectId\", \"object_id\", \"id\"));\n"
        "        ctx.setRouteCode(firstString(mapObject, \"routeCode\", \"route_code\"));\n"
        "        ctx.setYear(firstInteger(mapObject, \"year\"));\n"
        "        ctx.setMarkdown(buildMarkdown(mapObject, ctx.getObjectType()));\n"
        "        return ctx;\n"
        "    }\n\n"
        "    private static String buildMarkdown(Map mapObject, String objectType) {\n"
        "        StringBuilder sb = new StringBuilder();\n"
        "        sb.append(\"【当前地图选中对象】\\n\");\n"
        "        append(sb, \"对象类型\", objectType);\n"
        "        append(sb, \"对象ID\", firstString(mapObject, \"objectId\", \"object_id\", \"id\"));\n"
        "        append(sb, \"路线\", firstString(mapObject, \"routeCode\", \"route_code\"));\n"
        "        append(sb, \"起点桩号\", firstObject(mapObject, \"startStake\", \"start_stake\"));\n"
        "        append(sb, \"终点桩号\", firstObject(mapObject, \"endStake\", \"end_stake\"));\n"
        "        append(sb, \"病害\", firstString(mapObject, \"diseaseName\", \"disease_name\", \"diseaseType\", \"disease_type\"));\n"
        "        append(sb, \"严重程度\", firstObject(mapObject, \"severity\"));\n"
        "        append(sb, \"数量\", firstObject(mapObject, \"quantity\"));\n"
        "        append(sb, \"单位\", firstObject(mapObject, \"measureUnit\", \"measure_unit\"));\n"
        "        append(sb, \"MQI\", firstObject(mapObject, \"mqi\"));\n"
        "        append(sb, \"PQI\", firstObject(mapObject, \"pqi\"));\n"
        "        append(sb, \"PCI\", firstObject(mapObject, \"pci\"));\n"
        "        append(sb, \"等级\", firstObject(mapObject, \"grade\"));\n"
        "        return sb.toString();\n"
        "    }\n\n"
        "    private static void append(StringBuilder sb, String label, Object value) {\n"
        "        if (value != null && String.valueOf(value).trim().length() > 0) {\n"
        "            sb.append(\"- \").append(label).append(\"：\").append(value).append(\"\\n\");\n"
        "        }\n"
        "    }\n\n"
        "    private static String firstString(Map map, String... keys) {\n"
        "        Object value = firstObject(map, keys);\n"
        "        return value == null ? null : String.valueOf(value);\n"
        "    }\n\n"
        "    private static Integer firstInteger(Map map, String... keys) {\n"
        "        Object value = firstObject(map, keys);\n"
        "        if (value == null) {\n"
        "            return null;\n"
        "        }\n"
        "        try {\n"
        "            return Integer.valueOf(String.valueOf(value));\n"
        "        } catch (Exception e) {\n"
        "            return null;\n"
        "        }\n"
        "    }\n\n"
        "    private static Object firstObject(Map map, String... keys) {\n"
        "        if (map == null) {\n"
        "            return null;\n"
        "        }\n"
        "        for (String key : keys) {\n"
        "            Object value = map.get(key);\n"
        "            if (value != null && String.valueOf(value).trim().length() > 0) {\n"
        "                return value;\n"
        "            }\n"
        "        }\n"
        "        return null;\n"
        "    }\n"
        "}\n"
    )
    path.write_text(replacement, encoding="utf-8")
    print(f"[OK] patched {path}")

def patch_agent_chat_service():
    path = ROOT / "srmp-agent/src/main/java/com/smartroad/srmp/agent/service/impl/AgentChatServiceImpl.java"
    s = read(path)
    backup(path)

    if "mapObjectContextService.resolve(buildContextMap(request))" not in s:
        fail("AgentChatServiceImpl 不是最新 resolve(buildContextMap(request)) 体系，停止修改，避免回退。")

    changed = False

    # 1. 在 answer 取出后立即清洗 think 标签。
    if "answer = sanitizeAssistantAnswer(answer);" not in s:
        anchor = 'String answer = asString(rag.get("answer"));'
        if anchor not in s:
            fail("未找到 answer 读取锚点")
        s = s.replace(anchor, anchor + " answer = sanitizeAssistantAnswer(answer);", 1)
        changed = True

    # 2. direct LLM 返回和 fallback 也清洗。
    if "llm = sanitizeAssistantAnswer(llm);" not in s:
        anchor = 'String llm = llmClient.chat(systemPrompt, "用户问题：" + message + "\\n\\n已有分析：\\n" + analysis.getMarkdown());'
        if anchor in s:
            s = s.replace(anchor, anchor + " llm = sanitizeAssistantAnswer(llm);", 1)
            changed = True

    # 3. 单病害对象优先本地化回答：放在 mode 计算后、response.setAnswer 前。
    if "buildSingleDiseaseObjectAnswer(mapObjCtx)" not in s:
        anchor = 'mode = Boolean.TRUE.equals(llmSuccess) ? "HYBRID_RAG_LLM" : "HYBRID_RAG_FALLBACK";'
        if anchor not in s:
            fail("未找到 mode 计算锚点")
        insert = (
            anchor
            + " if (isDiseaseMapObject(mapObjCtx) && shouldUseSingleObjectAnswer(answer, fallback, llmSuccess)) { "
              "answer = buildSingleDiseaseObjectAnswer(mapObjCtx); "
              "answerSource = \"MAP_OBJECT_LOCAL\"; "
              "fallback = true; "
              "llmSuccess = false; "
              "fallbackReason = fallbackReason == null ? \"当前选择的是单个病害对象，优先返回单对象处置建议\" : fallbackReason; "
              "mode = \"MAP_OBJECT_LOCAL\"; "
              "} "
              "answer = sanitizeAssistantAnswer(answer); "
              "if (mapObjectUsed && answer != null && !answer.startsWith(\"【基于当前地图对象】\")) { "
              "answer = \"【基于当前地图对象】\\n\" + answer; "
              "}"
        )
        s = s.replace(anchor, insert, 1)
        changed = True
    elif "answer = sanitizeAssistantAnswer(answer);" not in s:
        # redundant guard, normally unreachable
        pass

    # 4. labelOfAnswerSource 支持 MAP_OBJECT_LOCAL。
    if '"MAP_OBJECT_LOCAL".equals(answerSource)' not in s:
        anchor = 'if ("BUSINESS_ANALYSIS_FALLBACK".equals(answerSource)) return "业务分析结果降级返回";'
        if anchor not in s:
            fail("未找到 labelOfAnswerSource BUSINESS_ANALYSIS_FALLBACK 锚点")
        s = s.replace(anchor, anchor + ' if ("MAP_OBJECT_LOCAL".equals(answerSource)) return "地图对象本地分析";', 1)
        changed = True

    # 5. noticeOfAnswerSource 支持 MAP_OBJECT_LOCAL，不再显示“未知来源”。
    if '"MAP_OBJECT_LOCAL".equals(answerSource)' in s and "当前 answer 基于地图选中对象生成" not in s:
        anchor = 'if ("LLM".equals(answerSource)) return "本次 answer 由大模型成功生成。";'
        if anchor not in s:
            fail("未找到 noticeOfAnswerSource LLM 锚点")
        s = s.replace(anchor, anchor + ' if ("MAP_OBJECT_LOCAL".equals(answerSource)) return "当前 answer 基于地图选中对象生成，优先使用单对象业务规则。";', 1)
        changed = True

    # 6. helper methods
    helper_anchor = "private int hitCount(Map rag)"
    if "private String sanitizeAssistantAnswer" not in s:
        if helper_anchor not in s:
            fail("未找到 helper 插入锚点 hitCount")
        helpers = (
            "private String sanitizeAssistantAnswer(String answer) { "
            "if (answer == null) return null; "
            "String cleaned = answer.replaceAll(\"(?is)<think>.*?</think>\", \"\"); "
            "cleaned = cleaned.replaceAll(\"(?is)<thinking>.*?</thinking>\", \"\"); "
            "return cleaned.trim(); "
            "} "
            "private boolean isDiseaseMapObject(com.smartroad.srmp.agent.map.MapObjectContext ctx) { "
            "if (ctx == null || !ctx.isPresent()) return false; "
            "String type = ctx.getObjectType(); "
            "if (type == null && ctx.getDetail() != null) { Object t = ctx.getDetail().get(\"object_type\"); if (t == null) t = ctx.getDetail().get(\"objectType\"); type = t == null ? null : String.valueOf(t); } "
            "if (type == null) return false; "
            "String value = type.trim().toUpperCase(); "
            "return \"DISEASE\".equals(value) || \"DISEASE_RECORD\".equals(value); "
            "} "
            "private boolean shouldUseSingleObjectAnswer(String answer, Boolean fallback, Boolean llmSuccess) { "
            "if (!Boolean.TRUE.equals(llmSuccess)) return true; "
            "String value = answer == null ? \"\" : answer; "
            "return value.contains(\"病害总数\") || value.contains(\"热点 TOP\") || value.contains(\"TOP 10\") || value.contains(\"TOP10\") || value.contains(\"<think>\"); "
            "} "
            "private String buildSingleDiseaseObjectAnswer(com.smartroad.srmp.agent.map.MapObjectContext ctx) { "
            "Map d = ctx == null ? null : ctx.getDetail(); "
            "String route = firstNonBlank(d, \"routeCode\", \"route_code\"); "
            "String start = firstNonBlank(d, \"startStake\", \"start_stake\"); "
            "String end = firstNonBlank(d, \"endStake\", \"end_stake\"); "
            "String disease = firstNonBlank(d, \"diseaseName\", \"disease_name\", \"diseaseType\", \"disease_type\"); "
            "String severity = firstNonBlank(d, \"severity\"); "
            "String quantity = firstNonBlank(d, \"quantity\"); "
            "String unit = firstNonBlank(d, \"measureUnit\", \"measure_unit\"); "
            "String stake = start == null ? \"\" : (end == null ? \"K\" + start : \"K\" + start + \"—K\" + end); "
            "StringBuilder sb = new StringBuilder(); "
            "sb.append(\"【当前地图选中对象】\\n\"); "
            "sb.append(\"- 对象类型：DISEASE\\n\"); "
            "appendLine(sb, \"路线\", route); appendLine(sb, \"桩号\", stake); appendLine(sb, \"病害\", disease); appendLine(sb, \"严重程度\", severity); "
            "if (quantity != null || unit != null) sb.append(\"- 数量：\").append(quantity == null ? \"\" : quantity).append(unit == null ? \"\" : unit).append(\"\\n\"); "
            "sb.append(\"\\n## 当前对象判断\\n\"); "
            "sb.append(\"当前选中对象为\").append(route == null ? \"\" : route + \" \").append(stake).append(\" 的\").append(severity == null ? \"\" : severity + \" \").append(disease == null ? \"病害\" : disease).append(\"。该回答仅针对当前地图选中的单个病害对象，不再展开为整条路线病害统计。\\n\"); "
            "sb.append(\"\\n## 主要问题\\n\"); "
            "sb.append(\"该位置存在\").append(disease == null ? \"道路病害\" : disease).append(\"，严重程度为\").append(severity == null ? \"未标明\" : severity).append(\"。若该病害继续发展，可能造成局部路面平整度下降、行车舒适性下降，并增加后续修复成本。\\n\"); "
            "sb.append(\"\\n## 成因判断\\n\"); "
            "sb.append(\"建议现场重点复核既有修补层与原路面的结合情况、基层是否松散或含水、排水是否不畅，以及车辆荷载反复作用导致的二次损坏风险。\\n\"); "
            "sb.append(\"\\n## 养护处置建议\\n\"); "
            "sb.append(\"1. 先进行现场复核，确认损坏边界、深度和基层状态。\\n\"); "
            "sb.append(\"2. 若仅为表层损坏，建议采用局部铣刨、清理、重新摊铺或热补修复。\\n\"); "
            "sb.append(\"3. 若基层松散、脱空或含水，应先处理基层和排水，再恢复面层。\\n\"); "
            "sb.append(\"4. 该对象可列为近期处置对象；若周边存在连续同类病害，可合并为小段集中处置。\\n\"); "
            "return sb.toString(); "
            "} "
            "private String firstNonBlank(Map map, String... keys) { "
            "if (map == null) return null; "
            "for (String key : keys) { Object v = map.get(key); if (v != null && String.valueOf(v).trim().length() > 0) return String.valueOf(v); } "
            "return null; "
            "} "
            "private void appendLine(StringBuilder sb, String label, String value) { if (value != null && value.trim().length() > 0) sb.append(\"- \").append(label).append(\"：\").append(value).append(\"\\n\"); } "
        )
        s = s.replace(helper_anchor, helpers + helper_anchor, 1)
        changed = True

    # 7. looksLikeBusinessQuestion 增加“当前/这个/选中”。
    old = 'return containsAny(message, "G210", "路线", "路况", "病害", "评定", "MQI", "PQI", "PCI", "优良", "次差", "报告", "桩号");'
    new = 'return containsAny(message, "G210", "路线", "路况", "病害", "评定", "MQI", "PQI", "PCI", "优良", "次差", "报告", "桩号", "当前", "这个", "选中");'
    if old in s:
        s = s.replace(old, new, 1)
        changed = True

    if changed:
        path.write_text(s, encoding="utf-8")
        print(f"[OK] patched {path}")
    else:
        print(f"[SKIP] {path} 已包含阶段二十八修复")

patch_map_object_context()
patch_agent_chat_service()
print("[OK] phase28 map object answer quality patch applied")
