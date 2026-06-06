package com.smartroad.srmp.agent.solution.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.agent.solution.service.AiSolutionQualityService;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

@Service
public class AiSolutionQualityServiceImpl implements AiSolutionQualityService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String[] REQUIRED_SECTIONS = new String[]{
            "路线概况", "评定结果", "主要病害", "问题分析", "养护建议", "风险提示"
    };

    private static final String[] EMPTY_TOKENS = new String[]{
            "null", "undefined", "NaN", "平均 MQI null", "平均 PCI null", "平均 PQI null", "总数 0"
    };

    @Override
    public Map<String, Object> check(String taskId) {
        Map<String, Object> task = loadTask(taskId);
        if (task.isEmpty()) {
            throw new IllegalArgumentException("方案任务不存在：" + taskId);
        }
        List<Map<String, Object>> sources = loadSources(taskId);
        String content = asString(task.get("result_content"));

        if ("MAP_OBJECT".equals(asString(task.get("origin_type")))) {
            Map<String, Object> result = checkMapObjectTask(task, sources, content);
            result = enrichQualitySnapshot(task, sources, content, result);
            saveQualityResult(taskId, result);
            return result;
        }
        if ("MAP_REGION".equals(asString(task.get("origin_type")))) {
            Map<String, Object> result = checkMapRegionTask(task, sources, content);
            result = enrichQualitySnapshot(task, sources, content, result);
            saveQualityResult(taskId, result);
            return result;
        }

        List<Map<String, Object>> items = new ArrayList<>();
        int score = 100;

        score -= checkRequiredSections(content, items);
        score -= checkEmptyTokens(content, items);
        score -= checkSources(sources, items);
        score -= checkDraftNotice(content, items);

        if (score < 0) {
            score = 0;
        }

        boolean passed = score >= 80 && items.stream().noneMatch(it -> "ERROR".equals(it.get("level")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", passed);
        result.put("score", score);
        result.put("level", score >= 90 ? "A" : score >= 80 ? "B" : score >= 60 ? "C" : "D");
        result.put("items", items);
        result.put("summary", buildSummary(passed, score, items));
        result.put("checkedAt", new Date());
        result = enrichQualitySnapshot(task, sources, content, result);

        saveQualityResult(taskId, result);
        return result;
    }

    @Override
    public Map<String, Object> qualityResult(String taskId) {
        Map<String, Object> task = loadTask(taskId);
        if (task.isEmpty()) {
            throw new IllegalArgumentException("方案任务不存在：" + taskId);
        }
        Object value = task.get("quality_result");
        if (value == null) {
            return new LinkedHashMap<>();
        }
        if (value instanceof Map) {
            Map<String, Object> result = (Map<String, Object>) value;
            if (!result.containsKey("dimensions")) {
                result = enrichQualitySnapshot(task, loadSources(taskId), asString(task.get("result_content")), result);
                saveQualityResult(taskId, result);
            }
            return result;
        }
        try {
            Map<String, Object> result = objectMapper.readValue(String.valueOf(value), Map.class);
            if (!result.containsKey("dimensions")) {
                result = enrichQualitySnapshot(task, loadSources(taskId), asString(task.get("result_content")), result);
                saveQualityResult(taskId, result);
            }
            return result;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    @Override
    public Map<String, Object> evaluateSnapshot(Map<String, Object> task,
                                                List<Map<String, Object>> sources,
                                                String content,
                                                Map<String, Object> baseResult) {
        return enrichQualitySnapshot(task, sources, content, baseResult);
    }

    @Override
    public String exportMarkdown(String taskId) {
        Map<String, Object> task = loadTask(taskId);
        if (task.isEmpty()) {
            throw new IllegalArgumentException("方案任务不存在：" + taskId);
        }
        List<Map<String, Object>> sources = loadSources(taskId);
        Map<String, Object> quality = qualityResult(taskId);
        if (quality.isEmpty()) {
            quality = check(taskId);
        }

        StringBuilder md = new StringBuilder();
        md.append("# ").append(asString(task.get("title"))).append("\n\n");
        md.append("> 本文档由智路养护平台 AI 生成，作为方案草稿，需人工审核后使用。\n\n");
        md.append("## 一、任务信息\n\n");
        md.append("| 字段 | 内容 |\n");
        md.append("|---|---|\n");
        md.append("| 任务ID | ").append(asString(task.get("id"))).append(" |\n");
        md.append("| 方案类型 | ").append(asString(task.get("solution_type"))).append(" |\n");
        md.append("| 路线编号 | ").append(asString(task.get("route_code"))).append(" |\n");
        md.append("| 模板版本 | ").append(asString(task.get("template_version"))).append(" |\n");
        if ("MAP_OBJECT".equals(asString(task.get("origin_type")))) {
            md.append("| 来源类型 | 地图对象 |\n");
            md.append("| 对象类型 | ").append(asString(task.get("object_type"))).append(" |\n");
            md.append("| 对象ID | ").append(asString(task.get("object_id"))).append(" |\n");
            md.append("| 草稿状态 | ").append(asString(task.get("draft_status"))).append(" |\n");
        } else if ("MAP_REGION".equals(asString(task.get("origin_type")))) {
            md.append("| 来源类型 | 地图区域 |\n");
            md.append("| 对象类型 | ").append(asString(task.get("object_type"))).append(" |\n");
            md.append("| Trace | ").append(asString(task.get("object_id"))).append(" |\n");
            md.append("| 草稿状态 | ").append(asString(task.get("draft_status"))).append(" |\n");
        }
        md.append("| 生成时间 | ").append(asString(task.get("created_at"))).append(" |\n\n");

        md.append("## 二、质量校验\n\n");
        md.append("| 指标 | 值 |\n");
        md.append("|---|---|\n");
        md.append("| 是否通过 | ").append(Boolean.TRUE.equals(quality.get("passed")) ? "通过" : "未通过").append(" |\n");
        md.append("| 评分 | ").append(asString(quality.get("score"))).append(" |\n");
        md.append("| 等级 | ").append(asString(quality.get("level"))).append(" |\n\n");
        md.append(asString(quality.get("summary"))).append("\n\n");

        md.append("## 三、方案正文\n\n");
        md.append(asString(task.get("result_content"))).append("\n\n");

        md.append("## 四、引用来源\n\n");
        if (sources.isEmpty()) {
            md.append("暂无引用来源。\n");
        } else {
            int i = 1;
            for (Map<String, Object> source : sources) {
                md.append(i++).append(". **").append(asString(source.get("source_title"))).append("**");
                md.append("（").append(asString(source.get("source_type"))).append("）\n\n");
                String url = asString(source.get("source_url"));
                if (!url.isEmpty()) {
                    md.append("   - 链接：").append(url).append("\n");
                }
                md.append("   - 摘要：").append(asString(source.get("content_excerpt"))).append("\n\n");
            }
        }

        return md.toString();
    }

    private int checkRequiredSections(String content, List<Map<String, Object>> items) {
        int penalty = 0;
        for (String section : REQUIRED_SECTIONS) {
            boolean hit = content != null && content.contains(section);
            if (!hit) {
                items.add(item("ERROR", "REQUIRED_SECTION", "缺少必填章节：" + section, 15));
                penalty += 15;
            } else {
                items.add(item("OK", "REQUIRED_SECTION", "已包含章节：" + section, 0));
            }
        }
        return penalty;
    }

    private int checkEmptyTokens(String content, List<Map<String, Object>> items) {
        int penalty = 0;
        String text = content == null ? "" : content;
        for (String token : EMPTY_TOKENS) {
            if (text.contains(token)) {
                items.add(item("WARN", "EMPTY_VALUE", "疑似存在空值或无数据表达：" + token, 8));
                penalty += 8;
            }
        }
        if (penalty == 0) {
            items.add(item("OK", "EMPTY_VALUE", "未发现明显 null / undefined / NaN 空值", 0));
        }
        return penalty;
    }

    private int checkSources(List<Map<String, Object>> sources, List<Map<String, Object>> items) {
        boolean hasBusiness = sources.stream().anyMatch(s -> "BUSINESS_DATA".equals(asString(s.get("source_type"))));
        boolean hasKnowledge = sources.stream().anyMatch(s -> "KNOWLEDGE".equals(asString(s.get("source_type"))) || "OUTLINE".equals(asString(s.get("source_type"))));
        boolean hasTemplate = sources.stream().anyMatch(s -> "TEMPLATE".equals(asString(s.get("source_type"))));

        int penalty = 0;
        if (!hasBusiness) {
            items.add(item("ERROR", "SOURCE", "缺少业务数据来源", 15));
            penalty += 15;
        } else {
            items.add(item("OK", "SOURCE", "已包含业务数据来源", 0));
        }
        if (!hasKnowledge) {
            items.add(item("WARN", "SOURCE", "缺少知识库或 Outline 来源", 10));
            penalty += 10;
        } else {
            items.add(item("OK", "SOURCE", "已包含知识库或 Outline 来源", 0));
        }
        if (!hasTemplate) {
            items.add(item("WARN", "SOURCE", "缺少模板来源", 5));
            penalty += 5;
        } else {
            items.add(item("OK", "SOURCE", "已包含模板来源", 0));
        }
        return penalty;
    }

    private int checkDraftNotice(String content, List<Map<String, Object>> items) {
        String text = content == null ? "" : content;
        if (text.contains("草稿") || text.contains("人工审核") || text.contains("审核确认")) {
            items.add(item("OK", "RISK_NOTICE", "已包含 AI 草稿或人工审核提示", 0));
            return 0;
        }
        items.add(item("WARN", "RISK_NOTICE", "建议补充 AI 生成草稿和人工审核提示", 8));
        return 8;
    }

    private Map<String, Object> checkMapObjectTask(Map<String, Object> task, List<Map<String, Object>> sources, String content) {
        List<Map<String, Object>> items = new ArrayList<>();
        int score = 100;

        score -= addQualityItem(items, hasAny(task, "route_code", "object_id") || containsAny(content, "路线", "桩号"), "MAP_OBJECT_POSITION", "地图对象位置", 20);
        score -= addQualityItem(items, containsAny(content, "成因", "原因"), "MAP_OBJECT_CAUSE", "成因判断", 15);
        score -= addQualityItem(items, containsAny(content, "处置", "养护建议", "推荐"), "MAP_OBJECT_TREATMENT", "处置建议", 20);
        score -= addQualityItem(items, containsAny(content, "优先级", "P1", "P2", "P3"), "MAP_OBJECT_PRIORITY", "优先级", 15);
        score -= addQualityItem(items, sources.stream().anyMatch(s -> "MAP_OBJECT".equals(asString(s.get("source_type")))), "MAP_OBJECT_SOURCE", "地图对象来源", 10);
        score -= addQualityItem(items, containsAny(content, "草稿", "人工审核", "审核确认"), "MAP_OBJECT_REVIEW_NOTICE", "人工审核提示", 8);

        if (score < 0) {
            score = 0;
        }
        boolean passed = score >= 80 && items.stream().noneMatch(i -> "ERROR".equals(i.get("level")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", passed);
        result.put("score", score);
        result.put("level", score >= 90 ? "A" : score >= 80 ? "B" : score >= 60 ? "C" : "D");
        result.put("items", items);
        result.put("summary", "地图对象方案质量校验" + (passed ? "通过" : "未通过") + "，评分 " + score + "。");
        result.put("originType", "MAP_OBJECT");
        result.put("checkedAt", new Date());
        return result;
    }

    private Map<String, Object> checkMapRegionTask(Map<String, Object> task, List<Map<String, Object>> sources, String content) {
        List<Map<String, Object>> items = new ArrayList<>();
        int score = 100;

        score -= addQualityItem(items, !asString(task.get("object_summary")).isEmpty(), "REGION_STATISTICS", "区域统计", 20);
        score -= addQualityItem(items, containsAny(content, "热点", "重点"), "REGION_HOTSPOT", "热点分析", 15);
        score -= addQualityItem(items, containsAny(content, "养护", "处置", "策略"), "REGION_STRATEGY", "养护策略", 20);
        score -= addQualityItem(items, containsAny(content, "优先", "P1", "P2", "近期"), "REGION_PRIORITY", "优先级", 15);
        score -= addQualityItem(items, sources.stream().anyMatch(s -> "MAP_REGION".equals(asString(s.get("source_type")))), "REGION_SOURCE", "区域来源", 10);
        score -= addQualityItem(items, containsAny(content, "人工审核", "复核", "草稿"), "REGION_REVIEW_NOTICE", "人工审核提示", 10);

        if (score < 0) {
            score = 0;
        }
        boolean passed = score >= 80 && items.stream().noneMatch(i -> "ERROR".equals(i.get("level")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", passed);
        result.put("score", score);
        result.put("level", score >= 90 ? "A" : score >= 80 ? "B" : score >= 60 ? "C" : "D");
        result.put("items", items);
        result.put("summary", "区域方案质量校验" + (passed ? "通过" : "未通过") + "，评分 " + score + "。");
        result.put("originType", "MAP_REGION");
        result.put("checkedAt", new Date());
        return result;
    }

    private int addQualityItem(List<Map<String, Object>> items, boolean passed, String code, String name, int penalty) {
        if (passed) {
            items.add(item("OK", code, "已包含：" + name, 0));
            return 0;
        }
        items.add(item(penalty >= 20 ? "ERROR" : "WARN", code, "缺少或需补充：" + name, penalty));
        return penalty;
    }

    private boolean hasAny(Map<String, Object> map, String... keys) {
        if (map == null) {
            return false;
        }
        for (String key : keys) {
            if (!asString(map.get(key)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String text, String... words) {
        String value = text == null ? "" : text;
        for (String word : words) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> item(String level, String code, String message, int penalty) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("level", level);
        item.put("code", code);
        item.put("message", message);
        item.put("penalty", penalty);
        return item;
    }

    private String buildSummary(boolean passed, int score, List<Map<String, Object>> items) {
        long errors = items.stream().filter(i -> "ERROR".equals(i.get("level"))).count();
        long warnings = items.stream().filter(i -> "WARN".equals(i.get("level"))).count();
        return "质量校验" + (passed ? "通过" : "未通过") + "，评分 " + score + "，错误 " + errors + " 项，警告 " + warnings + " 项。";
    }

    Map<String, Object> enrichQualitySnapshot(Map<String, Object> task,
                                               List<Map<String, Object>> sources,
                                               String content,
                                               Map<String, Object> baseResult) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (baseResult != null) {
            result.putAll(baseResult);
        }
        List<Map<String, Object>> items = copyItemList(result.get("items"));
        List<Map<String, Object>> dimensions = new ArrayList<>();
        List<Map<String, Object>> checks = new ArrayList<>();

        addDimension(dimensions, checks, templateDimension(task, sources, content));
        addDimension(dimensions, checks, businessEvidenceDimension(task, sources));
        addDimension(dimensions, checks, mapBindingDimension(task, sources));
        addDimension(dimensions, checks, llmDimension(task));
        addDimension(dimensions, checks, scenarioDimension(task));

        int score = intValue(result.get("score"), 100);
        for (Map<String, Object> check : checks) {
            int penalty = intValue(check.get("penalty"), 0);
            if (penalty > 0) {
                score -= penalty;
            }
            items.add(check);
        }
        if (score < 0) {
            score = 0;
        }
        boolean basePassed = result.containsKey("passed") ? readBoolean(result.get("passed"), true) : true;
        boolean hasError = items.stream().anyMatch(item -> "ERROR".equals(asString(item.get("level"))));
        boolean passed = basePassed && score >= 80 && !hasError;

        result.put("passed", passed);
        result.put("score", score);
        result.put("level", score >= 90 ? "A" : score >= 80 ? "B" : score >= 60 ? "C" : "D");
        result.put("items", items);
        result.put("checks", checks);
        result.put("dimensions", dimensions);
        result.put("summary", buildQualityClosureSummary(passed, score, dimensions));
        result.put("qualityVersion", "solution-quality-closure-v1");
        result.put("checkedAt", new Date());
        return result;
    }

    private Map<String, Object> templateDimension(Map<String, Object> task, List<Map<String, Object>> sources, String content) {
        Map<String, Object> meta = objectMap(firstPresent(task.get("template_meta"), task.get("templateMeta")));
        boolean hasTemplateSource = hasSourceType(sources, "TEMPLATE") || hasSourceType(sources, "TEMPLATE_META");
        boolean fallback = readBoolean(firstPresent(meta.get("fallback"), meta.get("isFallback")), false)
                || asString(content).contains("系统兜底模板");
        boolean matched = readBoolean(firstPresent(meta.get("matched"), meta.get("templateMatched")), false)
                || !asString(task.get("template_id")).isEmpty()
                || !asString(firstPresent(meta.get("templateId"), meta.get("template_id"))).isEmpty()
                || hasTemplateSource;
        int missingCount = objectList(firstPresent(meta.get("missingVariables"), meta.get("missing_variables"))).size();
        if (fallback) {
            return dimension("template", "模板命中", "ERROR", "使用了系统兜底模板，需补齐可用业务模板。", 20, mapOf(
                    "templateCode", firstPresent(meta.get("templateCode"), meta.get("template_code")),
                    "fallbackReason", firstPresent(meta.get("fallbackReason"), meta.get("fallback_reason")),
                    "missingVariables", missingCount
            ));
        }
        if (!matched) {
            return dimension("template", "模板命中", "WARN", "未记录模板命中元信息，建议重新生成或检查模板管线。", 10, mapOf(
                    "templateCode", "",
                    "missingVariables", missingCount
            ));
        }
        if (missingCount > 0) {
            return dimension("template", "模板命中", "WARN", "已命中模板，但仍存在缺失变量 " + missingCount + " 个。", 8, mapOf(
                    "templateCode", firstPresent(meta.get("templateCode"), meta.get("template_code")),
                    "missingVariables", missingCount
            ));
        }
        return dimension("template", "模板命中", "OK", "已命中业务模板。", 0, mapOf(
                "templateCode", firstPresent(meta.get("templateCode"), meta.get("template_code"), task.get("template_id")),
                "templateName", firstPresent(meta.get("templateName"), meta.get("template_name"))
        ));
    }

    private Map<String, Object> businessEvidenceDimension(Map<String, Object> task, List<Map<String, Object>> sources) {
        Map<String, Object> request = objectMap(task.get("request_json"));
        Map<String, Object> objectSummary = objectMap(task.get("object_summary"));
        Map<String, Object> evidence = firstNonEmptyMap(
                objectMap(objectSummary.get("businessEvidence")),
                objectMap(request.get("businessEvidence")),
                objectMap(objectMap(request.get("requestContext")).get("businessEvidence")),
                objectMap(objectMap(request.get("requestContext")).get("businessEvidenceSummary"))
        );
        List<?> toolSummary = objectList(evidence.get("toolSummary"));
        int toolSuccessCount = intValue(evidence.get("toolSuccessCount"), -1);
        int businessHitCount = intValue(evidence.get("businessHitCount"), -1);
        boolean hasBusinessSource = hasSourceType(sources, "BUSINESS_DATA")
                || objectList(request.get("sourceSummaries")).stream().anyMatch(item -> "BUSINESS_DATA".equals(sourceType(objectMap(item))));
        boolean hasEvidence = hasBusinessSource || toolSuccessCount > 0 || businessHitCount > 0 || !toolSummary.isEmpty();
        if (!hasEvidence) {
            return dimension("businessEvidence", "业务证据", "ERROR", "未记录业务查询证据，无法确认方案是否基于评定、病害或统计数据生成。", 20, mapOf(
                    "toolSuccessCount", 0,
                    "businessHitCount", 0
            ));
        }
        if (businessHitCount == 0) {
            return dimension("businessEvidence", "业务证据", "WARN", "已记录业务查询过程，但业务命中数为 0，建议核对路线、桩号或区域范围。", 10, mapOf(
                    "toolSuccessCount", Math.max(toolSuccessCount, 0),
                    "businessHitCount", businessHitCount
            ));
        }
        return dimension("businessEvidence", "业务证据", "OK", "已记录业务查询证据。", 0, mapOf(
                "toolSuccessCount", toolSuccessCount < 0 ? "" : toolSuccessCount,
                "businessHitCount", businessHitCount < 0 ? "" : businessHitCount,
                "sourceCount", sources == null ? 0 : sources.size()
        ));
    }

    private Map<String, Object> mapBindingDimension(Map<String, Object> task, List<Map<String, Object>> sources) {
        String originType = asString(task.get("origin_type"));
        Map<String, Object> mapObject = objectMap(task.get("map_object"));
        Map<String, Object> objectSummary = objectMap(task.get("object_summary"));
        boolean hasMapSource = hasSourceType(sources, "MAP_OBJECT") || hasSourceType(sources, "MAP_REGION");
        boolean hasRegionGeometry = "MAP_REGION".equals(originType)
                && (!objectMap(mapObject.get("geometry")).isEmpty() || !objectMap(objectSummary.get("geometry")).isEmpty() || hasMapSource);
        boolean hasObjectId = !asString(firstPresent(task.get("object_id"), mapObject.get("objectId"), mapObject.get("object_id"), mapObject.get("id"), objectSummary.get("objectId"), objectSummary.get("object_id"))).isEmpty();
        boolean hasRoute = !asString(firstPresent(task.get("route_code"), mapObject.get("routeCode"), mapObject.get("route_code"), objectSummary.get("routeCode"), objectSummary.get("route_code"))).isEmpty();
        boolean hasStake = !asString(firstPresent(mapObject.get("startStake"), mapObject.get("start_stake"), objectSummary.get("startStake"), objectSummary.get("start_stake"))).isEmpty()
                || !asString(firstPresent(mapObject.get("stakeRange"), objectSummary.get("stakeRange"))).isEmpty();
        if (hasRegionGeometry || hasMapSource || (hasObjectId && (hasRoute || hasStake))) {
            return dimension("mapBinding", "地图关联", "OK", "已具备地图定位或对象关联信息。", 0, mapOf(
                    "objectId", firstPresent(task.get("object_id"), mapObject.get("objectId"), mapObject.get("object_id"), mapObject.get("id")),
                    "routeCode", firstPresent(task.get("route_code"), mapObject.get("routeCode"), mapObject.get("route_code"))
            ));
        }
        if (hasObjectId || hasRoute || hasStake) {
            return dimension("mapBinding", "地图关联", "WARN", "地图关联信息不完整，追问或定位可能受限。", 8, mapOf(
                    "objectId", firstPresent(task.get("object_id"), mapObject.get("objectId"), mapObject.get("object_id"), mapObject.get("id")),
                    "routeCode", firstPresent(task.get("route_code"), mapObject.get("routeCode"), mapObject.get("route_code"))
            ));
        }
        return dimension("mapBinding", "地图关联", "WARN", "未记录可定位的地图对象或区域信息。", 10, new LinkedHashMap<>());
    }

    private Map<String, Object> llmDimension(Map<String, Object> task) {
        Map<String, Object> answerMeta = findAnswerMeta(task);
        if (answerMeta.isEmpty()) {
            return dimension("llm", "大模型", "WARN", "未记录大模型元信息；可能是旧任务或保存草稿时未同步 answerMeta。", 5, new LinkedHashMap<>());
        }
        boolean success = readBoolean(firstPresent(answerMeta.get("llmSuccess"), answerMeta.get("llm_success")), false);
        String status = asString(firstPresent(answerMeta.get("llmStatus"), answerMeta.get("llm_status")));
        boolean fallback = readBoolean(firstPresent(answerMeta.get("fallback"), answerMeta.get("qualityFallback")), false)
                || !asString(firstPresent(answerMeta.get("fallbackReason"), answerMeta.get("fallback_reason"))).isEmpty()
                || "FALLBACK".equalsIgnoreCase(asString(firstPresent(answerMeta.get("answerSource"), answerMeta.get("answer_source"))));
        if (success && !fallback) {
            return dimension("llm", "大模型", "OK", "已记录大模型成功生成元信息。", 0, mapOf("llmStatus", status, "answerSource", firstPresent(answerMeta.get("answerSource"), answerMeta.get("answer_source"))));
        }
        return dimension("llm", "大模型", "WARN", "大模型未成功或发生降级，需查看执行过程。", 10, mapOf(
                "llmStatus", status,
                "fallbackReason", firstPresent(answerMeta.get("fallbackReason"), answerMeta.get("fallback_reason"))
        ));
    }

    private Map<String, Object> scenarioDimension(Map<String, Object> task) {
        String solutionType = asString(task.get("solution_type"));
        String originType = asString(task.get("origin_type"));
        String objectType = asString(task.get("object_type"));
        if (solutionType.isEmpty()) {
            return dimension("scenario", "场景匹配", "ERROR", "缺少方案类型，无法判断质量规则。", 20, new LinkedHashMap<>());
        }
        if (matchesScenario(solutionType, originType, objectType)) {
            return dimension("scenario", "场景匹配", "OK", scenarioLabel(solutionType) + "与当前对象类型匹配。", 0, mapOf(
                    "solutionType", solutionType,
                    "originType", originType,
                    "objectType", objectType
            ));
        }
        if ("".equals(originType) || "".equals(objectType)) {
            return dimension("scenario", "场景匹配", "WARN", "缺少来源类型或对象类型，建议补齐任务上下文。", 8, mapOf(
                    "solutionType", solutionType,
                    "originType", originType,
                    "objectType", objectType
            ));
        }
        return dimension("scenario", "场景匹配", "ERROR", "方案类型与对象类型不匹配，可能存在上下文串线。", 20, mapOf(
                "solutionType", solutionType,
                "originType", originType,
                "objectType", objectType
        ));
    }

    private boolean matchesScenario(String solutionType, String originType, String objectType) {
        String solution = asString(solutionType);
        String origin = asString(originType);
        String object = asString(objectType);
        if ("ROUTE_REPORT".equals(solution) || "ROAD_ASSESSMENT_REPORT".equals(solution)) {
            return "ROAD_ROUTE".equals(object) && ("ROUTE_REPORT".equals(origin) || "MAP_OBJECT".equals(origin) || origin.isEmpty());
        }
        if ("SECTION_PLAN".equals(solution) || "MAINTENANCE_SUGGESTION".equals(solution)) {
            return "MAP_OBJECT".equals(origin) && "ROAD_SECTION".equals(object);
        }
        if ("EVALUATION_UNIT_ADVICE".equals(solution)) {
            return "MAP_OBJECT".equals(origin) && "ASSESSMENT_RESULT".equals(object);
        }
        if ("LOW_SCORE_TREATMENT".equals(solution) || "LOW_SCORE_SECTION_ANALYSIS".equals(solution)) {
            return "MAP_OBJECT".equals(origin) && "ASSESSMENT_RESULT".equals(object);
        }
        if ("DISEASE_REVIEW".equals(solution) || "DISEASE_TREATMENT".equals(solution) || "DISEASE_TREATMENT_PLAN".equals(solution)) {
            return "MAP_OBJECT".equals(origin) && "DISEASE".equals(object);
        }
        if ("REGION_MAINTENANCE_SUGGESTION".equals(solution)) {
            return "MAP_REGION".equals(origin) && "MAP_REGION".equals(object);
        }
        return true;
    }

    private String scenarioLabel(String solutionType) {
        if ("SECTION_PLAN".equals(solutionType)) return "路段养护计划";
        if ("EVALUATION_UNIT_ADVICE".equals(solutionType)) return "评定结果养护建议";
        if ("LOW_SCORE_TREATMENT".equals(solutionType)) return "低分评定处置建议";
        if ("DISEASE_REVIEW".equals(solutionType)) return "病害复核意见";
        if ("DISEASE_TREATMENT".equals(solutionType)) return "病害处置建议";
        if ("REGION_MAINTENANCE_SUGGESTION".equals(solutionType)) return "区域养护建议";
        if ("ROUTE_REPORT".equals(solutionType)) return "路线技术状况报告";
        return solutionType;
    }

    private Map<String, Object> findAnswerMeta(Map<String, Object> task) {
        Map<String, Object> request = objectMap(task.get("request_json"));
        Map<String, Object> aiContext = objectMap(task.get("ai_context"));
        Map<String, Object> raw = objectMap(aiContext.get("raw"));
        Map<String, Object> objectSummary = objectMap(task.get("object_summary"));
        Map<String, Object> answerMeta = firstNonEmptyMap(
                objectMap(task.get("answer_meta")),
                objectMap(aiContext.get("answerMeta")),
                objectMap(aiContext.get("answer_meta")),
                objectMap(raw.get("answerMeta")),
                objectMap(raw.get("answer_meta")),
                objectMap(objectSummary.get("answerMeta")),
                objectMap(objectSummary.get("answer_meta")),
                objectMap(request.get("answerMeta")),
                objectMap(request.get("answer_meta"))
        );
        if (!answerMeta.isEmpty()) {
            return answerMeta;
        }
        for (Object item : objectList(firstPresent(task.get("ai_tool_results"), aiContext.get("aiToolResults"), aiContext.get("ai_tool_results")))) {
            Map<String, Object> tool = objectMap(item);
            Map<String, Object> data = objectMap(tool.get("data"));
            answerMeta = firstNonEmptyMap(objectMap(tool.get("answerMeta")), objectMap(tool.get("answer_meta")), objectMap(data.get("answerMeta")), objectMap(data.get("answer_meta")));
            if (!answerMeta.isEmpty()) {
                return answerMeta;
            }
        }
        return new LinkedHashMap<>();
    }

    private String buildQualityClosureSummary(boolean passed, int score, List<Map<String, Object>> dimensions) {
        long errors = dimensions.stream().filter(i -> "ERROR".equals(i.get("level"))).count();
        long warnings = dimensions.stream().filter(i -> "WARN".equals(i.get("level"))).count();
        return "方案质量闭环" + (passed ? "通过" : "需复核") + "，评分 " + score + "，关键问题 " + errors + " 项，提示 " + warnings + " 项。";
    }

    private void addDimension(List<Map<String, Object>> dimensions, List<Map<String, Object>> checks, Map<String, Object> dimension) {
        dimensions.add(dimension);
        checks.add(item(asString(dimension.get("level")), "QUALITY_" + asString(dimension.get("code")).toUpperCase(Locale.ROOT), asString(dimension.get("summary")), intValue(dimension.get("penalty"), 0)));
    }

    private Map<String, Object> dimension(String code, String label, String level, String summary, int penalty, Map<String, Object> details) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("label", label);
        item.put("level", level);
        item.put("summary", summary);
        item.put("penalty", penalty);
        item.put("details", details == null ? new LinkedHashMap<>() : details);
        return item;
    }

    private Map<String, Object> loadTask(String taskId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("id", taskId);
        List<Map<String, Object>> list = namedParameterJdbcTemplate.queryForList(
                "select id, tenant_id, solution_type, title, route_code, year, template_id, template_version, status, request_json, result_content, quality_result, created_at, updated_at, " +
                        "origin_type, object_type, object_id, map_object, object_summary, template_meta, draft_status, ai_trace_id, ai_answer, ai_sources, ai_tool_results, ai_evidence, ai_context, generation_mode " +
                        "from ai_solution_task where tenant_id=:tenantId and id=:id",
                params
        );
        return list.isEmpty() ? new LinkedHashMap<>() : list.get(0);
    }

    private List<Map<String, Object>> loadSources(String taskId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("taskId", taskId);
        return namedParameterJdbcTemplate.queryForList(
                "select id, tenant_id, task_id, source_type, source_title, source_id, source_url, content_excerpt, created_at " +
                        "from ai_solution_source where tenant_id=:tenantId and task_id=:taskId order by created_at asc",
                params
        );
    }

    private void saveQualityResult(String taskId, Map<String, Object> result) {
        try {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("tenantId", TenantContextHolder.getTenantId())
                    .addValue("id", taskId)
                    .addValue("qualityResult", objectMapper.writeValueAsString(result));
            namedParameterJdbcTemplate.update(
                    "update ai_solution_task set quality_result=cast(:qualityResult as jsonb), updated_at=now() where tenant_id=:tenantId and id=:id",
                    params
            );
        } catch (Exception e) {
            throw new RuntimeException("保存质量校验结果失败：" + e.getMessage(), e);
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private List<Map<String, Object>> copyItemList(Object raw) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                Map<String, Object> map = objectMap(item);
                if (!map.isEmpty()) {
                    result.add(new LinkedHashMap<>(map));
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        if (value == null) {
            return new LinkedHashMap<>();
        }
        String text = String.valueOf(value);
        if (text.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(text, Map.class);
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private List<?> objectList(Object value) {
        if (value instanceof List) {
            return (List<?>) value;
        }
        if (value == null || asString(value).isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(String.valueOf(value), List.class);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private Map<String, Object> firstNonEmptyMap(Map<String, Object>... maps) {
        for (Map<String, Object> map : maps) {
            if (map != null && !map.isEmpty()) {
                return map;
            }
        }
        return new LinkedHashMap<>();
    }

    private boolean hasSourceType(List<Map<String, Object>> sources, String type) {
        if (sources == null) {
            return false;
        }
        for (Map<String, Object> source : sources) {
            if (type.equals(sourceType(source))) {
                return true;
            }
        }
        return false;
    }

    private String sourceType(Map<String, Object> source) {
        return asString(firstPresent(source.get("source_type"), source.get("sourceType"), source.get("type"))).toUpperCase(Locale.ROOT);
    }

    private Object firstPresent(Object... values) {
        for (Object value : values) {
            if (value != null && !asString(value).trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private boolean readBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value == null) {
            return fallback;
        }
        String text = asString(value).toLowerCase(Locale.ROOT);
        if ("true".equals(text) || "1".equals(text) || "yes".equals(text)) {
            return true;
        }
        if ("false".equals(text) || "0".equals(text) || "no".equals(text)) {
            return false;
        }
        return fallback;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(asString(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return result;
    }
}
