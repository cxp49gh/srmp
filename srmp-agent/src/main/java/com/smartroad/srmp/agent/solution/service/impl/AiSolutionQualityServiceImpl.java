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
            return (Map<String, Object>) value;
        }
        try {
            return objectMapper.readValue(String.valueOf(value), Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
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
        md.append("| 年度 | ").append(asString(task.get("year"))).append(" |\n");
        md.append("| 模板版本 | ").append(asString(task.get("template_version"))).append(" |\n");
        if ("MAP_OBJECT".equals(asString(task.get("origin_type")))) {
            md.append("| 来源类型 | 地图对象 |\n");
            md.append("| 对象类型 | ").append(asString(task.get("object_type"))).append(" |\n");
            md.append("| 对象ID | ").append(asString(task.get("object_id"))).append(" |\n");
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

    private Map<String, Object> loadTask(String taskId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("id", taskId);
        List<Map<String, Object>> list = namedParameterJdbcTemplate.queryForList(
                "select id, tenant_id, solution_type, title, route_code, year, template_id, template_version, status, request_json, result_content, quality_result, created_at, updated_at, " +
                        "origin_type, object_type, object_id, object_summary, draft_status " +
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
}
