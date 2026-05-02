package com.smartroad.srmp.agent.solution.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.*;

/**
 * Phase38.4：方案兜底模板字段取值修复。
 *
 * 修复问题：
 * 方案任务页面“方案结果”中兜底模板字段为空，但任务本身其实有值。
 *
 * 根因：
 * 兜底模板只从单一 mapObject 中取值，未合并：
 * - ai_solution_task 主表字段 route_code/year/object_type/solution_type
 * - map_object JSON
 * - object_summary JSON
 * - ai_context JSON
 * - ai_context.raw.mapContext
 * - ai_context.raw.mapObject
 * - mapObject.raw
 *
 * 本类提供：
 * 1. mergeContext(...)：从多个来源合并上下文；
 * 2. ensureContentFromTask(...)：基于任务记录生成不丢字段的兜底模板；
 * 3. repairFallbackContentIfNeeded(...)：当历史兜底模板字段为空时，自动重建；
 * 4. normalizeSources(...)：兜底来源去重。
 */
public final class AiSolutionFallbackTemplateSupport {

    private static final String SYSTEM_FALLBACK_SOURCE_TYPE = "SYSTEM_TEMPLATE";
    private static final String SYSTEM_FALLBACK_TITLE = "系统兜底模板";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AiSolutionFallbackTemplateSupport() {}

    public static String ensureContent(String content,
                                       String solutionType,
                                       String routeCode,
                                       Object year,
                                       Map<String, Object> mapObject,
                                       String aiAnswer) {
        Map<String, Object> merged = mergeContext(null, mapObject, null, null, solutionType, routeCode, year, aiAnswer);
        if (!isBlank(content) && !fallbackContentHasEmptyFields(content)) {
            return content;
        }
        if (!isBlank(content) && !isFallbackContent(content)) {
            return content;
        }
        return buildFallbackMarkdown(merged);
    }

    public static String ensureContentFromTask(String content,
                                               Map<String, Object> task,
                                               String aiAnswer) {
        Map<String, Object> merged = mergeContext(
                task,
                objectMap(task == null ? null : firstValue(task, "map_object", "mapObject")),
                objectMap(task == null ? null : firstValue(task, "object_summary", "objectSummary")),
                objectMap(task == null ? null : firstValue(task, "ai_context", "aiContext")),
                task == null ? null : firstString(task, "solution_type", "solutionType"),
                task == null ? null : firstString(task, "route_code", "routeCode"),
                task == null ? null : firstValue(task, "year"),
                aiAnswer
        );

        if (isBlank(content)) {
            return buildFallbackMarkdown(merged);
        }

        if (isFallbackContent(content) && fallbackContentHasEmptyFields(content)) {
            return buildFallbackMarkdown(merged);
        }

        return content;
    }

    public static String repairFallbackContentIfNeeded(String content,
                                                       Map<String, Object> task,
                                                       String aiAnswer) {
        content = AiSolutionFallbackSourceGuard.stripEmbeddedFallbackSourceSection(content);
        if (!isFallbackContent(content)) {
            return content;
        }
        if (!fallbackContentHasEmptyFields(content)) {
            return content;
        }
        return ensureContentFromTask(content, task, aiAnswer);
    }

    public static boolean isFallbackContent(String content) {
        return content != null && content.contains("系统兜底模板") && content.contains("AI 方案草稿");
    }

    public static boolean fallbackContentHasEmptyFields(String content) {
        if (content == null) {
            return true;
        }
        String text = content;
        return text.contains("| 路线编号 | - |")
                || text.contains("| 年度 | - |")
                || text.contains("| 对象类型 | - |")
                || text.contains("| 方案类型 | - |")
                || text.contains("| 桩号范围 | - |")
                || text.contains("| 病害类型 | - |")
                || text.contains("| 严重程度/等级 | - |")
                || text.contains("| 数量 | - |");
    }

    public static List<Map<String, Object>> normalizeSources(List<Map<String, Object>> sources,
                                                             String solutionType,
                                                             boolean ensureSystemFallbackSource) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        if (sources != null) {
            for (Map<String, Object> source : sources) {
                if (source == null || source.isEmpty()) {
                    continue;
                }
                Map<String, Object> normalized = normalizeSource(source);
                String key = sourceKey(normalized);
                if (seen.add(key)) {
                    result.add(normalized);
                }
            }
        }

        if (ensureSystemFallbackSource) {
            Map<String, Object> fallback = systemFallbackTemplateSource(solutionType);
            String key = sourceKey(fallback);
            if (seen.add(key)) {
                result.add(fallback);
            }
        }

        return AiSolutionFallbackSourceGuard.dedupeSources(result);
    }

    public static Map<String, Object> systemFallbackTemplateSource(String solutionType) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("sourceType", SYSTEM_FALLBACK_SOURCE_TYPE);
        source.put("sourceId", "SYSTEM_FALLBACK_TEMPLATE:" + safe(solutionType, "GENERAL"));
        source.put("sourceTitle", SYSTEM_FALLBACK_TITLE);
        source.put("title", SYSTEM_FALLBACK_TITLE);
        source.put("sourceUrl", "");
        source.put("score", 1.0d);
        source.put("contentExcerpt", "未匹配到可用模板或模板渲染为空时，系统使用内置兜底模板生成方案草稿。");
        return source;
    }

    public static Map<String, Object> mergeContext(Map<String, Object> task,
                                                   Map<String, Object> mapObject,
                                                   Map<String, Object> objectSummary,
                                                   Map<String, Object> aiContext,
                                                   String solutionType,
                                                   String routeCode,
                                                   Object year,
                                                   String aiAnswer) {
        Map<String, Object> merged = new LinkedHashMap<>();

        mergeInto(merged, task);
        mergeInto(merged, objectSummary);
        mergeInto(merged, mapObject);

        Map<String, Object> rawMapObject = objectMap(firstValue(mapObject, "raw"));
        mergeInto(merged, rawMapObject);

        Map<String, Object> ai = objectMap(aiContext);
        mergeInto(merged, ai);

        Map<String, Object> aiRaw = objectMap(firstValue(ai, "raw"));
        mergeInto(merged, aiRaw);

        Map<String, Object> aiRawMapContext = objectMap(firstValue(aiRaw, "mapContext", "map_context"));
        mergeInto(merged, aiRawMapContext);

        Map<String, Object> aiRawMapObject = objectMap(firstValue(aiRaw, "mapObject", "map_object"));
        mergeInto(merged, aiRawMapObject);

        Map<String, Object> aiRawMapObjectRaw = objectMap(firstValue(aiRawMapObject, "raw"));
        mergeInto(merged, aiRawMapObjectRaw);

        if (!isBlank(solutionType)) {
            putAliases(merged, solutionType, "solutionType", "solution_type");
        }
        if (!isBlank(routeCode)) {
            putAliases(merged, routeCode, "routeCode", "route_code");
        }
        if (year != null && !isBlank(String.valueOf(year))) {
            putAliases(merged, year, "year");
        }
        if (!isBlank(aiAnswer)) {
            merged.put("aiAnswer", aiAnswer);
        }

        // 主表字段兜底覆盖，防止 JSON 内无 routeCode/year。
        String taskRouteCode = firstString(task, "route_code", "routeCode");
        if (!isBlank(taskRouteCode)) {
            putAliases(merged, taskRouteCode, "routeCode", "route_code");
        }
        Object taskYear = firstValue(task, "year");
        if (taskYear != null && !isBlank(String.valueOf(taskYear))) {
            putAliases(merged, taskYear, "year");
        }
        String taskObjectType = firstString(task, "object_type", "objectType");
        if (!isBlank(taskObjectType)) {
            putAliases(merged, taskObjectType, "objectType", "object_type");
        }

        normalizeStakeAliases(merged);
        normalizeDiseaseAliases(merged);
        return merged;
    }

    private static String buildFallbackMarkdown(Map<String, Object> ctx) {
        String solutionType = firstString(ctx, "solutionType", "solution_type");
        String routeCode = firstString(ctx, "routeCode", "route_code");
        Object year = firstValue(ctx, "year");
        String objectType = firstString(ctx, "objectType", "object_type", "type", "layerType");
        String diseaseName = firstString(ctx, "diseaseName", "disease_name", "diseaseType", "disease_type");
        String severity = firstString(ctx, "severity", "grade", "level");
        String startStake = firstString(ctx, "startStake", "start_stake", "startMileage", "start_mileage");
        String endStake = firstString(ctx, "endStake", "end_stake", "endMileage", "end_mileage");
        String quantity = firstString(ctx, "quantity", "area", "length");
        String unit = firstString(ctx, "measureUnit", "measure_unit", "unit");
        String aiAnswer = firstString(ctx, "aiAnswer", "ai_answer");

        StringBuilder md = new StringBuilder();
        md.append("# AI 方案草稿（系统兜底模板）\n\n");
        md.append("> 未匹配到可用方案模板，或模板渲染结果为空；系统已使用内置兜底模板生成草稿，需人工复核后使用。\n\n");

        md.append("## 一、基础信息\n\n");
        md.append("| 字段 | 内容 |\n");
        md.append("|---|---|\n");
        md.append("| 方案类型 | ").append(safe(solutionType, "-")).append(" |\n");
        md.append("| 路线编号 | ").append(safe(routeCode, "-")).append(" |\n");
        md.append("| 年度 | ").append(year == null || isBlank(String.valueOf(year)) ? "-" : String.valueOf(year)).append(" |\n");
        md.append("| 对象类型 | ").append(safe(objectType, "-")).append(" |\n");

        if (!isBlank(startStake) || !isBlank(endStake)) {
            md.append("| 桩号范围 | ")
                    .append(isBlank(startStake) ? "-" : formatStake(startStake))
                    .append(isBlank(endStake) ? "" : "-" + formatStake(endStake))
                    .append(" |\n");
        }

        if (!isBlank(diseaseName)) {
            md.append("| 病害类型 | ").append(diseaseName).append(" |\n");
        }
        if (!isBlank(severity)) {
            md.append("| 严重程度/等级 | ").append(severity).append(" |\n");
        }
        if (!isBlank(quantity)) {
            md.append("| 数量 | ").append(quantity).append(safe(unit, "")).append(" |\n");
        }
        md.append("\n");

        md.append("## 二、AI 分析摘要\n\n");
        if (isBlank(aiAnswer)) {
            md.append("暂无 AI 分析摘要。建议结合当前地图对象、知识库资料和现场复核结果完善方案。\n\n");
        } else {
            md.append(aiAnswer.trim()).append("\n\n");
        }

        md.append("## 三、主要问题\n\n");
        if (!isBlank(diseaseName)) {
            md.append("- 当前对象涉及 ").append(diseaseName).append("，需结合严重程度、影响范围、周边病害和评定结果综合判断。\n");
        } else if (!isBlank(objectType)) {
            md.append("- 当前对象类型为 ").append(objectType).append("，需结合地图上下文、评定指标、病害分布和养护规则综合判断。\n");
        } else {
            md.append("- 当前对象需结合地图上下文、评定指标、病害分布和养护规则综合判断。\n");
        }
        md.append("- 若存在连续病害、低分单元或重度病害，应优先安排现场复核。\n\n");

        md.append("## 四、处置建议\n\n");
        appendTreatmentAdvice(md, diseaseName);
        md.append("\n");

        md.append("## 五、实施与复核要求\n\n");
        md.append("- 复核病害位置、范围、面积/长度、严重程度和发展趋势。\n");
        md.append("- 核查排水、基层、路基、重复修补区域和交通安全风险。\n");
        md.append("- 根据现场复核结果调整工程量、工艺和处置边界。\n");
        md.append("- 本方案为系统兜底模板生成，必须经人工审核确认。\n\n");
        return AiSolutionFallbackSourceGuard.stripEmbeddedFallbackSourceSection(md.toString());
    }

    private static void appendTreatmentAdvice(StringBuilder md, String diseaseName) {
        String name = diseaseName == null ? "" : diseaseName;
        if (name.contains("坑槽")) {
            md.append("- 规则切割坑槽边界，清理松散材料，保持基面干净干燥。\n");
            md.append("- 采用热拌料或冷补料填补，分层摊铺并充分压实。\n");
            md.append("- 若基层破坏或含水，应先处理基层和排水，再恢复面层。\n");
            return;
        }
        if (name.contains("沉陷")) {
            md.append("- 复核沉陷深度、范围、基层稳定性、路基变形和排水条件。\n");
            md.append("- 面层局部沉陷可局部铣刨、找平、重新摊铺并压实。\n");
            md.append("- 涉及基层或路基问题时，应进行结构修复或路基加固。\n");
            return;
        }
        if (name.contains("裂缝")) {
            md.append("- 复核裂缝宽度、密度、长度和是否渗水。\n");
            md.append("- 轻中度裂缝可采用灌缝、开槽灌缝或封缝。\n");
            md.append("- 裂缝密集或表层老化时，可结合封层、薄层罩面或雾封层。\n");
            return;
        }
        if (name.contains("修补")) {
            md.append("- 复核修补边界、基层状态、排水条件和新旧材料结合情况。\n");
            md.append("- 表层损坏可局部铣刨、清理、重新摊铺或热补修复。\n");
            md.append("- 基层松散或含水时，应先处理基层和排水。\n");
            return;
        }
        md.append("- 点状病害可采用局部修补、裂缝处置、坑槽修补等措施。\n");
        md.append("- 连续病害或低分区间可考虑封层、薄层罩面、局部铣刨重铺或中修。\n");
        md.append("- 重度或影响安全的对象应优先纳入近期处置计划。\n");
    }

    private static Map<String, Object> normalizeSource(Map<String, Object> source) {
        Map<String, Object> item = new LinkedHashMap<>(source);
        String title = firstString(item, "sourceTitle", "source_title", "title", "name");
        String excerpt = firstString(item, "contentExcerpt", "content_excerpt", "excerpt", "content");

        if (isSystemFallbackTitle(title)) {
            item.put("sourceType", SYSTEM_FALLBACK_SOURCE_TYPE);
            item.put("sourceId", "SYSTEM_FALLBACK_TEMPLATE:" + safe(firstString(item, "solutionType", "solution_type"), "GENERAL"));
            item.put("sourceTitle", SYSTEM_FALLBACK_TITLE);
            item.put("title", SYSTEM_FALLBACK_TITLE);
            item.put("contentExcerpt", isBlank(excerpt) ? "系统内置兜底模板。" : excerpt);
            return item;
        }

        String sourceType = firstString(item, "sourceType", "source_type", "type");
        String sourceId = firstString(item, "sourceId", "source_id", "id");
        if (!isBlank(sourceType)) item.put("sourceType", sourceType);
        if (!isBlank(sourceId)) item.put("sourceId", sourceId);
        if (!isBlank(title)) {
            item.put("sourceTitle", title);
            item.put("title", title);
        }
        if (!isBlank(excerpt)) item.put("contentExcerpt", excerpt);
        return item;
    }

    private static String sourceKey(Map<String, Object> source) {
        String title = firstString(source, "sourceTitle", "source_title", "title", "name");
        if (isSystemFallbackTitle(title)) {
            return "SYSTEM_TEMPLATE|SYSTEM_FALLBACK_TEMPLATE";
        }
        String type = firstString(source, "sourceType", "source_type", "type");
        String id = firstString(source, "sourceId", "source_id", "id");
        if (!isBlank(type) || !isBlank(id)) {
            return safe(type, "") + "|" + safe(id, "") + "|" + safe(title, "");
        }
        String excerpt = firstString(source, "contentExcerpt", "content_excerpt", "excerpt", "content");
        return "TITLE|" + safe(title, "") + "|" + shortText(excerpt, 80);
    }

    private static boolean isSystemFallbackTitle(String title) {
        if (title == null) return false;
        return title.contains("系统兜底模板") || title.contains("兜底模板") || title.contains("Fallback Template");
    }

    private static void mergeInto(Map<String, Object> target, Map<String, Object> source) {
        if (target == null || source == null) return;
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            Object value = entry.getValue();
            if (value instanceof String && isBlank((String) value)) continue;
            target.put(entry.getKey(), value);
        }
    }

    private static void putAliases(Map<String, Object> map, Object value, String... keys) {
        if (map == null || value == null) return;
        for (String key : keys) {
            if (!isBlank(key)) map.put(key, value);
        }
    }

    private static void normalizeStakeAliases(Map<String, Object> map) {
        Object start = firstValue(map, "startStake", "start_stake", "startMileage", "start_mileage");
        Object end = firstValue(map, "endStake", "end_stake", "endMileage", "end_mileage");
        if (start != null) putAliases(map, start, "startStake", "start_stake", "startMileage", "start_mileage");
        if (end != null) putAliases(map, end, "endStake", "end_stake", "endMileage", "end_mileage");
    }

    private static void normalizeDiseaseAliases(Map<String, Object> map) {
        Object diseaseName = firstValue(map, "diseaseName", "disease_name");
        Object diseaseType = firstValue(map, "diseaseType", "disease_type");
        Object severity = firstValue(map, "severity", "grade", "level");
        Object measureUnit = firstValue(map, "measureUnit", "measure_unit", "unit");
        if (diseaseName != null) putAliases(map, diseaseName, "diseaseName", "disease_name");
        if (diseaseType != null) putAliases(map, diseaseType, "diseaseType", "disease_type");
        if (severity != null) putAliases(map, severity, "severity");
        if (measureUnit != null) putAliases(map, measureUnit, "measureUnit", "measure_unit", "unit");
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> objectMap(Object value) {
        if (value == null) return new LinkedHashMap<>();
        if (value instanceof Map) return new LinkedHashMap<>((Map<String, Object>) value);
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty() || !text.startsWith("{")) return new LinkedHashMap<>();
            try {
                return OBJECT_MAPPER.readValue(text, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {
                return new LinkedHashMap<>();
            }
        }
        return new LinkedHashMap<>();
    }

    private static Object firstValue(Map<String, Object> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !isBlank(String.valueOf(value))) return value;
        }
        Object raw = map.get("raw");
        if (raw instanceof Map || raw instanceof String) {
            Map<String, Object> rawMap = objectMap(raw);
            for (String key : keys) {
                Object value = rawMap.get(key);
                if (value != null && !isBlank(String.valueOf(value))) return value;
            }
        }
        return null;
    }

    private static String firstString(Map<String, Object> map, String... keys) {
        Object value = firstValue(map, keys);
        return value == null ? null : String.valueOf(value).trim();
    }

    private static String formatStake(String value) {
        if (isBlank(value)) return "";
        try {
            return "K" + new BigDecimal(value.trim()).stripTrailingZeros().toPlainString();
        } catch (Exception e) {
            return "K" + value.trim();
        }
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safe(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private static String shortText(String value, int max) {
        if (value == null || value.length() <= max) return value == null ? "" : value;
        return value.substring(0, max);
    }
}
