package com.smartroad.srmp.agent.solution.service.impl;

import com.smartroad.srmp.agent.solution.service.AiSolutionEvalService;
import com.smartroad.srmp.agent.solution.service.AiSolutionQualityService;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

@Service
public class AiSolutionEvalServiceImpl implements AiSolutionEvalService {

    @Resource
    private AiSolutionQualityService aiSolutionQualityService;

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public AiSolutionEvalServiceImpl() {
    }

    AiSolutionEvalServiceImpl(AiSolutionQualityService aiSolutionQualityService) {
        this.aiSolutionQualityService = aiSolutionQualityService;
    }

    @Override
    public List<Map<String, Object>> defaultCases() {
        List<Map<String, Object>> cases = new ArrayList<>();
        cases.add(defaultCase("route-report", "路线技术状况报告", "ROUTE_REPORT", "MAP_OBJECT", "ROAD_ROUTE", "MAP_OBJECT"));
        cases.add(defaultCase("section-plan", "路段养护计划", "SECTION_PLAN", "MAP_OBJECT", "ROAD_SECTION", "MAP_OBJECT"));
        cases.add(defaultCase("evaluation-advice", "评定结果养护建议", "EVALUATION_UNIT_ADVICE", "MAP_OBJECT", "ASSESSMENT_RESULT", "MAP_OBJECT"));
        cases.add(defaultCase("low-score-treatment", "低分评定处置建议", "LOW_SCORE_TREATMENT", "MAP_OBJECT", "ASSESSMENT_RESULT", "MAP_OBJECT"));
        cases.add(defaultCase("disease-review", "病害复核意见", "DISEASE_REVIEW", "MAP_OBJECT", "DISEASE", "MAP_OBJECT"));
        cases.add(defaultCase("disease-treatment", "病害处置建议", "DISEASE_TREATMENT", "MAP_OBJECT", "DISEASE", "MAP_OBJECT"));
        cases.add(defaultCase("region-maintenance", "区域养护建议", "REGION_MAINTENANCE_SUGGESTION", "MAP_REGION", "MAP_REGION", "MAP_REGION"));
        return cases;
    }

    @Override
    public Map<String, Object> run(Map<String, Object> request) {
        Map<String, Object> req = request == null ? new LinkedHashMap<>() : request;
        List<Map<String, Object>> cases = readCaseList(req.get("cases"));
        if (cases.isEmpty()) {
            cases = defaultCases();
        }
        boolean useLatestTask = readBoolean(req.get("useLatestTask"), true);

        List<Map<String, Object>> results = new ArrayList<>();
        int passed = 0;
        int fixtureCount = 0;
        int savedTaskCount = 0;
        for (Map<String, Object> evalCase : cases) {
            Map<String, Object> result = evaluateCase(evalCase, useLatestTask);
            results.add(result);
            if (Boolean.TRUE.equals(result.get("passed"))) {
                passed++;
            }
            if ("SAVED_TASK".equals(result.get("sourceMode"))) {
                savedTaskCount++;
            } else {
                fixtureCount++;
            }
        }

        int total = results.size();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("passed", passed);
        summary.put("failed", total - passed);
        summary.put("passRate", total == 0 ? 0 : passed * 1.0 / total);
        summary.put("sourceModeSummary", mapOf("savedTask", savedTaskCount, "fixture", fixtureCount));
        summary.put("results", results);
        summary.put("failedCaseIds", failedCaseIds(results));
        summary.put("qualityVersion", "solution-quality-closure-v1");
        summary.put("generatedAt", new Date());
        return summary;
    }

    private Map<String, Object> evaluateCase(Map<String, Object> evalCase, boolean useLatestTask) {
        Map<String, Object> task = null;
        List<Map<String, Object>> sources = new ArrayList<>();
        String sourceMode = "FIXTURE";
        String fallbackReason = "";

        if (useLatestTask) {
            try {
                task = loadLatestTask(evalCase);
                if (task != null && !task.isEmpty()) {
                    sourceMode = "SAVED_TASK";
                    sources = loadSources(asString(task.get("id")));
                }
            } catch (Exception e) {
                fallbackReason = "读取最近任务失败：" + e.getMessage();
            }
        }

        if (task == null || task.isEmpty()) {
            task = buildFixtureTask(evalCase);
            sources = buildFixtureSources(evalCase);
            if (fallbackReason.isEmpty()) {
                fallbackReason = "未找到同场景最近任务，使用内置样例。";
            }
        }

        Map<String, Object> base = mapOf(
                "passed", true,
                "score", 100,
                "level", "A",
                "items", new ArrayList<Map<String, Object>>()
        );
        Map<String, Object> quality = aiSolutionQualityService.evaluateSnapshot(
                task,
                sources,
                asString(task.get("result_content")),
                base
        );

        List<Map<String, Object>> dimensions = mapList(quality.get("dimensions"));
        List<String> errors = collectErrors(evalCase, quality, dimensions, sources);
        boolean passed = errors.isEmpty() && readBoolean(quality.get("passed"), false);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", asString(firstPresent(evalCase.get("id"), evalCase.get("caseId"))));
        result.put("name", asString(firstPresent(evalCase.get("name"), evalCase.get("label"))));
        result.put("solutionType", asString(firstPresent(evalCase.get("solutionType"), evalCase.get("solution_type"))));
        result.put("originType", asString(firstPresent(evalCase.get("originType"), evalCase.get("origin_type"))));
        result.put("objectType", asString(firstPresent(evalCase.get("objectType"), evalCase.get("object_type"))));
        result.put("sourceMode", sourceMode);
        result.put("fallbackReason", fallbackReason);
        result.put("taskId", asString(task.get("id")));
        result.put("taskTitle", asString(task.get("title")));
        result.put("routeCode", asString(task.get("route_code")));
        result.put("year", task.get("year"));
        result.put("passed", passed);
        result.put("score", quality.get("score"));
        result.put("level", quality.get("level"));
        result.put("qualityPassed", quality.get("passed"));
        result.put("qualitySummary", quality.get("summary"));
        result.put("dimensions", dimensions);
        result.put("checks", quality.get("checks"));
        result.put("errors", errors);
        result.put("sourceCount", sources.size());
        result.put("sourceTypes", sourceTypes(sources));
        result.put("requiredDimensions", requiredDimensions(evalCase));
        return result;
    }

    private List<String> collectErrors(Map<String, Object> evalCase,
                                       Map<String, Object> quality,
                                       List<Map<String, Object>> dimensions,
                                       List<Map<String, Object>> sources) {
        List<String> errors = new ArrayList<>();
        int minScore = intValue(evalCase.get("minScore"), 80);
        int score = intValue(quality.get("score"), 0);
        if (score < minScore) {
            errors.add("质量评分低于阈值：" + score + " < " + minScore);
        }

        for (String code : requiredDimensions(evalCase)) {
            Map<String, Object> dimension = findDimension(dimensions, code);
            if (dimension.isEmpty()) {
                errors.add("缺少质量维度：" + code);
            } else if (!"OK".equals(asString(dimension.get("level")))) {
                errors.add(asString(dimension.get("label")) + "未通过：" + asString(dimension.get("summary")));
            }
        }

        Set<String> actualSourceTypes = sourceTypes(sources);
        for (String expected : expectedSourceTypes(evalCase)) {
            if (!actualSourceTypes.contains(expected)) {
                errors.add("缺少来源类型：" + expected);
            }
        }
        return errors;
    }

    private Map<String, Object> defaultCase(String id,
                                            String name,
                                            String solutionType,
                                            String originType,
                                            String objectType,
                                            String mapSourceType) {
        return mapOf(
                "id", id,
                "name", name,
                "solutionType", solutionType,
                "originType", originType,
                "objectType", objectType,
                "minScore", 80,
                "requiredDimensions", Arrays.asList("template", "businessEvidence", "mapBinding", "scenario"),
                "expectedSourceTypes", Arrays.asList(mapSourceType, "BUSINESS_DATA", "TEMPLATE")
        );
    }

    private Map<String, Object> buildFixtureTask(Map<String, Object> evalCase) {
        String solutionType = asString(firstPresent(evalCase.get("solutionType"), evalCase.get("solution_type")));
        String originType = asString(firstPresent(evalCase.get("originType"), evalCase.get("origin_type")));
        String objectType = asString(firstPresent(evalCase.get("objectType"), evalCase.get("object_type")));
        String caseId = asString(firstPresent(evalCase.get("id"), evalCase.get("caseId"), solutionType));
        String routeCode = "Y016140727";
        Map<String, Object> mapObject = mapOf(
                "objectType", objectType,
                "objectId", "fixture-" + caseId,
                "id", "fixture-" + caseId,
                "routeCode", routeCode,
                "startStake", 0,
                "endStake", 1.2
        );
        if ("MAP_REGION".equals(originType)) {
            mapObject.put("geometry", mapOf("type", "Polygon", "coordinates", Arrays.asList()));
        }
        return mapOf(
                "id", "fixture-" + caseId,
                "solution_type", solutionType,
                "title", asString(firstPresent(evalCase.get("name"), evalCase.get("label"), solutionType)),
                "route_code", routeCode,
                "year", 2026,
                "origin_type", originType,
                "object_type", objectType,
                "object_id", "fixture-" + caseId,
                "map_object", mapObject,
                "object_summary", mapOf(
                        "businessEvidence", mapOf(
                                "toolSuccessCount", 3,
                                "businessHitCount", 8,
                                "toolSummary", Arrays.asList(
                                        mapOf("tool", "gis.queryAssessmentResults", "hitCount", 3),
                                        mapOf("tool", "gis.queryDiseases", "hitCount", 5)
                                )
                        ),
                        "geometry", "MAP_REGION".equals(originType) ? mapOf("type", "Polygon", "coordinates", Arrays.asList()) : new LinkedHashMap<String, Object>()
                ),
                "request_json", mapOf(
                        "sourceSummaries", Arrays.asList(mapOf("sourceType", "BUSINESS_DATA", "sourceTitle", "业务查询证据"))
                ),
                "template_meta", mapOf(
                        "matched", true,
                        "fallback", false,
                        "templateCode", "fixture_" + solutionType.toLowerCase(Locale.ROOT),
                        "templateName", asString(firstPresent(evalCase.get("name"), evalCase.get("label"), solutionType)) + "模板",
                        "missingVariables", Arrays.asList()
                ),
                "ai_context", mapOf(
                        "answerMeta", mapOf("llmSuccess", true, "llmStatus", "SUCCESS", "answerSource", "LLM")
                ),
                "result_content", fixtureContent(evalCase)
        );
    }

    private List<Map<String, Object>> buildFixtureSources(Map<String, Object> evalCase) {
        String originType = asString(firstPresent(evalCase.get("originType"), evalCase.get("origin_type")));
        String mapSourceType = "MAP_REGION".equals(originType) ? "MAP_REGION" : "MAP_OBJECT";
        return Arrays.asList(
                mapOf("source_type", mapSourceType, "source_title", "地图对象或区域"),
                mapOf("source_type", "BUSINESS_DATA", "source_title", "评定、病害与统计查询结果"),
                mapOf("source_type", "TEMPLATE", "source_title", "业务方案模板"),
                mapOf("source_type", "KNOWLEDGE", "source_title", "养护标准知识")
        );
    }

    private String fixtureContent(Map<String, Object> evalCase) {
        String name = asString(firstPresent(evalCase.get("name"), evalCase.get("label"), evalCase.get("solutionType")));
        return "## " + name + "\n"
                + "路线概况、评定结果、主要病害已基于业务查询整理。\n"
                + "问题分析包含成因判断，养护建议包含处置策略和优先级。\n"
                + "风险提示：本内容为 AI 方案草稿，需人工审核确认。";
    }

    private Map<String, Object> loadLatestTask(Map<String, Object> evalCase) {
        if (namedParameterJdbcTemplate == null) {
            return new LinkedHashMap<>();
        }
        String solutionType = asString(firstPresent(evalCase.get("solutionType"), evalCase.get("solution_type")));
        String originType = asString(firstPresent(evalCase.get("originType"), evalCase.get("origin_type")));
        String objectType = asString(firstPresent(evalCase.get("objectType"), evalCase.get("object_type")));
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("solutionType", solutionType)
                .addValue("originType", originType)
                .addValue("objectType", objectType);
        List<Map<String, Object>> list = namedParameterJdbcTemplate.queryForList(
                "select id, tenant_id, solution_type, title, route_code, year, template_id, template_version, status, request_json, result_content, quality_result, created_at, updated_at, " +
                        "origin_type, object_type, object_id, map_object, object_summary, template_meta, draft_status, ai_trace_id, ai_answer, ai_sources, ai_tool_results, ai_evidence, ai_context, generation_mode " +
                        "from ai_solution_task " +
                        "where tenant_id=:tenantId and solution_type=:solutionType and origin_type=:originType and object_type=:objectType " +
                        "order by created_at desc limit 1",
                params
        );
        return list.isEmpty() ? new LinkedHashMap<>() : list.get(0);
    }

    private List<Map<String, Object>> loadSources(String taskId) {
        if (namedParameterJdbcTemplate == null || taskId.isEmpty()) {
            return new ArrayList<>();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("taskId", taskId);
        return namedParameterJdbcTemplate.queryForList(
                "select id, tenant_id, task_id, source_type, source_title, source_id, source_url, content_excerpt, created_at " +
                        "from ai_solution_source where tenant_id=:tenantId and task_id=:taskId order by created_at asc",
                params
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readCaseList(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item instanceof Map) {
                    result.add(new LinkedHashMap<>((Map<String, Object>) item));
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item instanceof Map) {
                    result.add((Map<String, Object>) item);
                }
            }
        }
        return result;
    }

    private List<String> requiredDimensions(Map<String, Object> evalCase) {
        List<String> value = stringList(evalCase.get("requiredDimensions"));
        if (value.isEmpty()) {
            return Arrays.asList("template", "businessEvidence", "mapBinding", "scenario");
        }
        return value;
    }

    private List<String> expectedSourceTypes(Map<String, Object> evalCase) {
        return stringList(evalCase.get("expectedSourceTypes"));
    }

    private List<String> stringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                String text = asString(item);
                if (!text.isEmpty()) {
                    result.add(text);
                }
            }
        }
        return result;
    }

    private List<String> failedCaseIds(List<Map<String, Object>> results) {
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> result : results) {
            if (!Boolean.TRUE.equals(result.get("passed"))) {
                ids.add(asString(result.get("id")));
            }
        }
        return ids;
    }

    private Set<String> sourceTypes(List<Map<String, Object>> sources) {
        Set<String> types = new LinkedHashSet<>();
        for (Map<String, Object> source : sources) {
            String type = asString(firstPresent(source.get("source_type"), source.get("sourceType"), source.get("type"))).toUpperCase(Locale.ROOT);
            if (!type.isEmpty()) {
                types.add(type);
            }
        }
        return types;
    }

    private Map<String, Object> findDimension(List<Map<String, Object>> dimensions, String code) {
        for (Map<String, Object> dimension : dimensions) {
            if (code.equals(asString(dimension.get("code")))) {
                return dimension;
            }
        }
        return new LinkedHashMap<>();
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

    private Object firstPresent(Object... values) {
        for (Object value : values) {
            if (value != null && !asString(value).trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return result;
    }
}
