package com.smartroad.srmp.agent.regression.service.impl;

import com.smartroad.srmp.agent.mapagent.dto.MapAgentActionResult;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunResponse;
import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.mapagent.service.MapAiAgentService;
import com.smartroad.srmp.agent.regression.service.AiCapabilityRegressionService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AiCapabilityRegressionServiceImpl implements AiCapabilityRegressionService {

    private static final String ROUTE_CODE = "Y016140727";
    private static final Integer YEAR = 2026;
    private static final String PROJECT_ID = "d20a49cdee904299a4967e196f676c9e";

    private final MapAiAgentService mapAiAgentService;

    public AiCapabilityRegressionServiceImpl(MapAiAgentService mapAiAgentService) {
        this.mapAiAgentService = mapAiAgentService;
    }

    @Override
    public List<Map<String, Object>> defaultCases() {
        List<Map<String, Object>> cases = new ArrayList<>();
        cases.add(caseDef(
                "knowledge.metric_explain",
                "知识问答：解释 PCI 指标",
                "KNOWLEDGE",
                "CHAT",
                "解释 PCI 指标",
                context("ROUTE", routeObject(), null),
                new LinkedHashMap<String, Object>(),
                "knowledge.metric_explain",
                list("knowledge.retrieve"),
                list("gis.queryRegionSummary", "gis.queryAssessmentResults", "gis.queryDiseases", "gis.queryDiseasesByStakeRange", "gis.queryNearbyObjects", "solution.generateDraft"),
                null
        ));
        cases.add(caseDef(
                "map.route_analysis",
                "一张图：分析路线",
                "ANALYSIS",
                "ANALYZE_ROUTE",
                "分析当前路线",
                context("ROUTE", routeObject(), null),
                actionInput(routeObject(), null),
                "map.route_analysis",
                list("gis.queryRegionSummary", "gis.queryAssessmentResults", "gis.queryDiseases", "knowledge.retrieve"),
                Collections.<String>emptyList(),
                null
        ));
        cases.add(caseDef(
                "map.section_analysis",
                "一张图：分析路段",
                "ANALYSIS",
                "ANALYZE_OBJECT",
                "分析当前路段",
                context("OBJECT", sectionObject(), null),
                actionInput(sectionObject(), null),
                "map.section_analysis",
                list("gis.queryAssessmentResults", "gis.queryDiseases", "gis.queryDiseasesByStakeRange", "knowledge.retrieve"),
                list("gis.queryRegionSummary"),
                null
        ));
        cases.add(caseDef(
                "map.disease_analysis",
                "一张图：分析病害",
                "ANALYSIS",
                "ANALYZE_OBJECT",
                "分析当前病害",
                context("OBJECT", diseaseObject(), null),
                actionInput(diseaseObject(), null),
                "map.disease_analysis",
                list("gis.queryNearbyObjects", "knowledge.retrieve"),
                list("gis.queryRegionSummary"),
                null
        ));
        cases.add(caseDef(
                "map.assessment_analysis",
                "一张图：分析评定结果",
                "ANALYSIS",
                "ANALYZE_OBJECT",
                "分析当前评定结果",
                context("OBJECT", assessmentObject(), null),
                actionInput(assessmentObject(), null),
                "map.assessment_analysis",
                list("gis.queryAssessmentResults", "gis.queryDiseasesByStakeRange", "knowledge.retrieve"),
                list("gis.queryRegionSummary"),
                null
        ));
        cases.add(caseDef(
                "map.region_analysis",
                "一张图：分析框选区域",
                "ANALYSIS",
                "ANALYZE_REGION",
                "分析当前框选区域",
                context("REGION", null, regionGeometry()),
                mapOf("geometry", regionGeometry()),
                "map.region_analysis",
                list("gis.queryRegionSummary", "knowledge.retrieve"),
                Collections.<String>emptyList(),
                null
        ));
        cases.add(caseDef(
                "solution.route_report",
                "生成：路线养护报告",
                "SOLUTION",
                "GENERATE_ROUTE_REPORT",
                "生成当前路线养护报告",
                context("ROUTE", routeObject(), null),
                actionInput(routeObject(), "ROUTE_REPORT"),
                "solution.route_report",
                list("gis.queryRegionSummary", "gis.queryAssessmentResults", "gis.queryDiseases", "knowledge.retrieve", "solution.generateDraft"),
                Collections.<String>emptyList(),
                "route_report_default"
        ));
        cases.add(caseDef(
                "solution.section_plan",
                "生成：路段养护计划",
                "SOLUTION",
                "GENERATE_OBJECT_SOLUTION",
                "生成当前路段养护计划",
                context("OBJECT", sectionObject(), null),
                actionInput(sectionObject(), "SECTION_PLAN"),
                "solution.section_plan",
                list("gis.queryAssessmentResults", "gis.queryDiseases", "gis.queryDiseasesByStakeRange", "knowledge.retrieve", "solution.generateDraft"),
                Collections.<String>emptyList(),
                "map_object_section_plan_default"
        ));
        cases.add(caseDef(
                "solution.disease_treatment",
                "生成：病害处置建议",
                "SOLUTION",
                "GENERATE_OBJECT_SOLUTION",
                "生成当前病害处置建议",
                context("OBJECT", diseaseObject(), null),
                actionInput(diseaseObject(), "DISEASE_TREATMENT"),
                "solution.disease_treatment",
                list("gis.queryNearbyObjects", "knowledge.retrieve", "solution.generateDraft"),
                Collections.<String>emptyList(),
                "map_object_disease_treatment_default"
        ));
        cases.add(caseDef(
                "solution.disease_review",
                "生成：病害复核意见",
                "SOLUTION",
                "GENERATE_OBJECT_SOLUTION",
                "生成当前病害复核意见",
                context("OBJECT", diseaseObject(), null),
                actionInput(diseaseObject(), "DISEASE_REVIEW"),
                "solution.disease_review",
                list("gis.queryNearbyObjects", "knowledge.retrieve", "solution.generateDraft"),
                Collections.<String>emptyList(),
                "map_object_disease_review_default"
        ));
        cases.add(caseDef(
                "solution.assessment_advice",
                "生成：评定养护建议",
                "SOLUTION",
                "GENERATE_OBJECT_SOLUTION",
                "生成当前评定结果养护建议",
                context("OBJECT", assessmentObject(), null),
                actionInput(assessmentObject(), "EVALUATION_UNIT_ADVICE"),
                "solution.assessment_advice",
                list("gis.queryAssessmentResults", "gis.queryDiseasesByStakeRange", "knowledge.retrieve", "solution.generateDraft"),
                Collections.<String>emptyList(),
                "map_object_evaluation_unit_advice_default"
        ));
        cases.add(caseDef(
                "solution.region_advice",
                "生成：区域养护建议",
                "SOLUTION",
                "GENERATE_REGION_SOLUTION",
                "生成当前框选区域养护建议",
                context("REGION", null, regionGeometry()),
                mapOf("solutionType", "REGION_MAINTENANCE_SUGGESTION", "geometry", regionGeometry()),
                "solution.region_advice",
                list("gis.queryRegionSummary", "knowledge.retrieve", "solution.generateDraft"),
                Collections.<String>emptyList(),
                "map_region_maintenance_advice_default"
        ));
        return cases;
    }

    @Override
    public Map<String, Object> run(Map<String, Object> request) {
        Map<String, Object> safeRequest = request == null ? new LinkedHashMap<String, Object>() : request;
        Set<String> caseIds = stringSet(safeRequest.get("caseIds"));
        boolean strictAi = readBoolean(safeRequest.get("strictAi"), false);
        boolean requireAi = readBoolean(safeRequest.get("requireAi"), true);

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> item : defaultCases()) {
            String caseId = string(item.get("caseId"));
            if (!caseIds.isEmpty() && !caseIds.contains(caseId)) {
                continue;
            }
            results.add(runCase(item, strictAi, requireAi, safeRequest));
        }

        int passed = 0;
        int warned = 0;
        int failed = 0;
        List<String> failedCaseIds = new ArrayList<>();
        for (Map<String, Object> result : results) {
            String status = string(result.get("status"));
            if ("PASS".equals(status)) {
                passed++;
            } else if ("WARN".equals(status)) {
                warned++;
            } else {
                failed++;
                failedCaseIds.add(string(result.get("caseId")));
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", results.size());
        summary.put("passed", passed);
        summary.put("warned", warned);
        summary.put("failed", failed);
        summary.put("passRate", passRate(passed, results.size()));
        summary.put("strictAi", strictAi);
        summary.put("generatedAt", new Date());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total", results.size());
        response.put("passed", passed);
        response.put("warned", warned);
        response.put("failed", failed);
        response.put("passRate", summary.get("passRate"));
        response.put("strictAi", strictAi);
        response.put("failedCaseIds", failedCaseIds);
        response.put("summary", summary);
        response.put("results", results);
        return response;
    }

    private Map<String, Object> runCase(Map<String, Object> item, boolean strictAi, boolean requireAi, Map<String, Object> request) {
        MapAgentRunResponse response = null;
        List<Map<String, Object>> checks = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        try {
            response = mapAiAgentService.run(buildRunRequest(item, requireAi, request));
        } catch (Exception e) {
            addCheck(checks, "RUNTIME_EXCEPTION", "执行异常", "ERROR", e.getMessage());
            errors.add(e.getMessage());
        }

        if (response != null) {
            evaluateResponse(item, response, strictAi, checks, errors, warnings);
        }

        String status = finalStatus(checks);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("caseId", item.get("caseId"));
        result.put("name", item.get("name"));
        result.put("category", item.get("category"));
        result.put("action", item.get("action"));
        result.put("status", status);
        result.put("expectedCapabilityId", item.get("capabilityId"));
        result.put("actualCapabilityId", response == null ? "" : actualCapabilityId(response));
        result.put("expectedTools", item.get("requiredTools"));
        result.put("actualTools", response == null ? Collections.emptyList() : new ArrayList<String>(actualToolNames(response)));
        result.put("prohibitedTools", item.get("prohibitedTools"));
        result.put("expectedTemplateCode", item.get("templateCode"));
        result.put("actualTemplateCode", response == null ? "" : actualTemplateCode(response));
        result.put("checks", checks);
        result.put("errors", errors);
        result.put("warnings", warnings);
        result.put("answerPreview", response == null ? "" : preview(response.getAnswer(), 160));
        result.put("answerMeta", response == null ? Collections.emptyMap() : response.getAnswerMeta());
        result.put("traceId", response == null ? "" : traceId(response));
        return result;
    }

    @SuppressWarnings("unchecked")
    private MapAgentRunRequest buildRunRequest(Map<String, Object> item, boolean requireAi, Map<String, Object> request) {
        MapAgentRunRequest runRequest = new MapAgentRunRequest();
        runRequest.setAction(string(item.get("action")));
        runRequest.setMessage(string(item.get("message")));
        runRequest.setMapContext(toMapAiContext(objectMap(item.get("mapContext"))));
        runRequest.setActionInput(cloneMap(objectMap(item.get("actionInput"))));

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("useBusinessData", true);
        options.put("useKnowledge", true);
        options.put("useOutline", readBoolean(request.get("useOutline"), false));
        options.put("topK", intValue(request.get("topK"), 5));
        options.put("requireAi", requireAi);
        options.put("traceId", "cap-reg-" + item.get("caseId") + "-" + System.currentTimeMillis());
        options.put("regressionCase", expectedSpec(item));
        Object overrideOptions = request.get("options");
        if (overrideOptions instanceof Map) {
            options.putAll((Map<String, Object>) overrideOptions);
            options.put("regressionCase", expectedSpec(item));
        }
        runRequest.setOptions(options);
        return runRequest;
    }

    private void evaluateResponse(Map<String, Object> item,
                                  MapAgentRunResponse response,
                                  boolean strictAi,
                                  List<Map<String, Object>> checks,
                                  List<String> errors,
                                  List<String> warnings) {
        String answer = response.getAnswer();
        if (answer == null || answer.trim().isEmpty()) {
            addCheck(checks, "ANSWER_PRESENT", "回答内容", "ERROR", "未返回回答内容。");
            errors.add("未返回回答内容");
        } else {
            addCheck(checks, "ANSWER_PRESENT", "回答内容", "PASS", "已返回回答内容。");
        }

        Map<String, Object> answerMeta = response.getAnswerMeta() == null ? Collections.<String, Object>emptyMap() : response.getAnswerMeta();
        if (answerMeta.isEmpty()) {
            addCheck(checks, "ANSWER_META_PRESENT", "answerMeta", "ERROR", "未返回 answerMeta，无法判断能力、模型和降级状态。");
            errors.add("未返回 answerMeta");
        } else {
            addCheck(checks, "ANSWER_META_PRESENT", "answerMeta", "PASS", "已返回 answerMeta。");
        }

        String expectedCapability = string(item.get("capabilityId"));
        String actualCapability = actualCapabilityId(response);
        if (!expectedCapability.isEmpty() && !expectedCapability.equals(actualCapability)) {
            String message = "期望能力 " + expectedCapability + "，实际 " + emptyToDash(actualCapability) + "。";
            addCheck(checks, "CAPABILITY_MATCH", "能力识别", "ERROR", message);
            errors.add(message);
        } else {
            addCheck(checks, "CAPABILITY_MATCH", "能力识别", "PASS", "能力识别符合预期。");
        }

        Set<String> actualTools = actualToolNames(response);
        List<String> requiredTools = stringList(item.get("requiredTools"));
        List<String> missing = missing(requiredTools, actualTools);
        if (missing.isEmpty()) {
            addCheck(checks, "REQUIRED_TOOLS", "必需工具", "PASS", "必需工具均已调用。");
        } else {
            String message = "缺少必需工具：" + String.join("、", missing);
            addCheck(checks, "REQUIRED_TOOLS", "必需工具", "ERROR", message);
            errors.add(message);
        }

        List<String> prohibitedTools = stringList(item.get("prohibitedTools"));
        List<String> forbidden = present(prohibitedTools, actualTools);
        if (forbidden.isEmpty()) {
            addCheck(checks, "PROHIBITED_TOOLS", "禁止工具", "PASS", "未调用禁止工具。");
        } else {
            String message = "不应调用工具：" + String.join("、", forbidden);
            addCheck(checks, "PROHIBITED_TOOLS", "禁止工具", "ERROR", message);
            errors.add(message);
        }

        String expectedTemplate = string(item.get("templateCode"));
        if (!expectedTemplate.isEmpty()) {
            String actualTemplate = actualTemplateCode(response);
            if (!expectedTemplate.equals(actualTemplate)) {
                String message = "期望模板 " + expectedTemplate + "，实际 " + emptyToDash(actualTemplate) + "。";
                addCheck(checks, "TEMPLATE_MATCH", "方案模板", "ERROR", message);
                errors.add(message);
            } else {
                addCheck(checks, "TEMPLATE_MATCH", "方案模板", "PASS", "方案模板符合预期。");
            }
        }

        MapAgentActionResult actionResult = response.getActionResult();
        String actionStatus = actionResult == null ? "" : string(actionResult.getStatus());
        if (!actionStatus.isEmpty() && !"SUCCESS".equalsIgnoreCase(actionStatus)) {
            String message = "actionResult 状态为 " + actionStatus + "。";
            addCheck(checks, "ACTION_STATUS", "动作状态", "ERROR", message);
            errors.add(message);
        } else {
            addCheck(checks, "ACTION_STATUS", "动作状态", "PASS", "动作状态正常。");
        }

        evaluateLlmStatus(answerMeta, strictAi, checks, errors, warnings);
    }

    private void evaluateLlmStatus(Map<String, Object> answerMeta,
                                   boolean strictAi,
                                   List<Map<String, Object>> checks,
                                   List<String> errors,
                                   List<String> warnings) {
        if (answerMeta.isEmpty()) {
            return;
        }
        boolean llmSuccess = readBoolean(firstValue(answerMeta.get("llmSuccess"), answerMeta.get("llm_success")), false);
        String llmStatus = string(firstValue(answerMeta.get("llmStatus"), answerMeta.get("llm_status")));
        String source = string(firstValue(answerMeta.get("answerSource"), answerMeta.get("answer_source")));
        String fallbackReason = string(firstValue(answerMeta.get("fallbackReason"), answerMeta.get("fallback_reason")));
        boolean fallback = "FALLBACK".equalsIgnoreCase(source) || !fallbackReason.isEmpty() || "FAILED".equalsIgnoreCase(llmStatus);
        if (llmSuccess && !fallback) {
            addCheck(checks, "LLM_STATUS", "大模型状态", "PASS", "大模型生成成功。");
            return;
        }
        String message = fallbackReason.isEmpty() ? "大模型未成功生成或发生降级。" : "大模型降级：" + fallbackReason;
        if (strictAi) {
            addCheck(checks, "LLM_STATUS", "大模型状态", "ERROR", message);
            errors.add(message);
        } else {
            addCheck(checks, "LLM_STATUS", "大模型状态", "WARN", message);
            warnings.add(message);
        }
    }

    private MapAiContext toMapAiContext(Map<String, Object> map) {
        MapAiContext context = new MapAiContext();
        context.setMode(string(map.get("mode")));
        context.setRouteCode(emptyToNull(string(map.get("routeCode"))));
        Object year = map.get("year");
        if (year instanceof Number) {
            context.setYear(((Number) year).intValue());
        }
        context.setMapObject(cloneMap(objectMap(map.get("mapObject"))));
        context.setRegionSummary(cloneMap(objectMap(map.get("regionSummary"))));
        context.setViewport(cloneMap(objectMap(map.get("viewport"))));
        context.setGeometry(cloneMap(objectMap(map.get("geometry"))));
        context.setSelectedLayers(stringList(map.get("selectedLayers")));
        context.setNearbyObjects(mapList(map.get("nearbyObjects")));
        context.setUserQuestion(string(map.get("userQuestion")));
        context.setExtra(cloneMap(objectMap(map.get("extra"))));
        return context;
    }

    private Map<String, Object> caseDef(String caseId,
                                        String name,
                                        String category,
                                        String action,
                                        String message,
                                        Map<String, Object> mapContext,
                                        Map<String, Object> actionInput,
                                        String capabilityId,
                                        List<String> requiredTools,
                                        List<String> prohibitedTools,
                                        String templateCode) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("caseId", caseId);
        item.put("name", name);
        item.put("category", category);
        item.put("action", action);
        item.put("message", message);
        item.put("mapContext", mapContext);
        item.put("actionInput", actionInput);
        item.put("capabilityId", capabilityId);
        item.put("requiredTools", requiredTools);
        item.put("prohibitedTools", prohibitedTools);
        item.put("templateCode", templateCode);
        return item;
    }

    private Map<String, Object> context(String mode, Map<String, Object> mapObject, Map<String, Object> geometry) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("mode", mode);
        if (!"REGION".equals(mode)) {
            context.put("routeCode", ROUTE_CODE);
        }
        context.put("year", YEAR);
        if (mapObject != null) {
            context.put("mapObject", mapObject);
        }
        if (geometry != null) {
            context.put("geometry", geometry);
        }
        context.put("selectedLayers", list("ROAD_ROUTE", "ROAD_SECTION", "DISEASE", "ASSESSMENT_RESULT"));
        context.put("viewport", mapOf(
                "zoom", 11,
                "center", mapOf("lat", 37.282718914591484, "lng", 112.45886354785156),
                "bbox", list(111.82090759277345, 36.99652046106025, 113.09669494628908, 37.567984011320256),
                "crs", "EPSG:4326"
        ));
        Map<String, Object> rawContext = mapOf(
                "query", mapOf("projectId", PROJECT_ID, "indexCode", "MQI", "grade", "", "sectionTier", "LINE"),
                "regionGeometry", geometry
        );
        context.put("extra", mapOf(
                "rawContext", rawContext,
                "contextScope", mode,
                "selectedLayers", list("ROAD_ROUTE", "ROAD_SECTION", "DISEASE", "ASSESSMENT_RESULT")
        ));
        return context;
    }

    private static Map<String, Object> routeObject() {
        return mapOf(
                "objectType", "ROAD_ROUTE",
                "objectId", ROUTE_CODE,
                "id", ROUTE_CODE,
                "routeCode", ROUTE_CODE,
                "routeName", "Y016140727",
                "year", YEAR,
                "startStake", 0,
                "endStake", 14.072
        );
    }

    private static Map<String, Object> sectionObject() {
        return mapOf(
                "objectType", "ROAD_SECTION",
                "objectId", "section-" + ROUTE_CODE + "-0-1",
                "id", "section-" + ROUTE_CODE + "-0-1",
                "routeCode", ROUTE_CODE,
                "sectionCode", ROUTE_CODE + "-K0-K1.2",
                "year", YEAR,
                "startStake", 0,
                "endStake", 1.2,
                "sectionName", "Y016140727 K0-K1.2"
        );
    }

    private static Map<String, Object> diseaseObject() {
        return mapOf(
                "objectType", "DISEASE",
                "objectId", "disease-" + ROUTE_CODE + "-0.1",
                "id", "disease-" + ROUTE_CODE + "-0.1",
                "routeCode", ROUTE_CODE,
                "year", YEAR,
                "startStake", 0.1,
                "endStake", 0.18,
                "diseaseName", "裂缝",
                "severity", "MEDIUM",
                "quantity", 12.5,
                "measureUnit", "m"
        );
    }

    private static Map<String, Object> assessmentObject() {
        return mapOf(
                "objectType", "ASSESSMENT_RESULT",
                "objectId", "f62c40555bb34b9aa418663e7bb06e64",
                "id", "f62c40555bb34b9aa418663e7bb06e64",
                "routeCode", ROUTE_CODE,
                "year", YEAR,
                "startStake", 0,
                "endStake", 14.072,
                "mqi", 85.067,
                "pqi", 75.111,
                "pci", 77.614,
                "activeIndexCode", "MQI",
                "activeMetricValue", 85.067,
                "activeMetricGrade", "GOOD",
                "metrics", mapOf("MQI", 85.067, "PQI", 75.111, "PCI", 77.614, "RQI", 60.926, "RDI", 0, "SCI", 100, "BCI", 100, "TCI", 100)
        );
    }

    private static Map<String, Object> regionGeometry() {
        return mapOf(
                "type", "Polygon",
                "coordinates", Collections.singletonList(Arrays.asList(
                        list(112.3, 37.2),
                        list(112.6, 37.2),
                        list(112.6, 37.4),
                        list(112.3, 37.4),
                        list(112.3, 37.2)
                ))
        );
    }

    private static Map<String, Object> actionInput(Map<String, Object> object, String solutionType) {
        Map<String, Object> input = cloneMap(object);
        input.put("mapObject", object);
        if (solutionType != null) {
            input.put("solutionType", solutionType);
        }
        return input;
    }

    private Map<String, Object> expectedSpec(Map<String, Object> item) {
        return mapOf(
                "caseId", item.get("caseId"),
                "capabilityId", item.get("capabilityId"),
                "requiredTools", item.get("requiredTools"),
                "prohibitedTools", item.get("prohibitedTools"),
                "templateCode", item.get("templateCode")
        );
    }

    private String actualCapabilityId(MapAgentRunResponse response) {
        Map<String, Object> meta = response.getAnswerMeta() == null ? Collections.<String, Object>emptyMap() : response.getAnswerMeta();
        Map<String, Object> data = response.getData() == null ? Collections.<String, Object>emptyMap() : response.getData();
        Map<String, Object> trace = response.getTrace() == null ? Collections.<String, Object>emptyMap() : response.getTrace();
        Map<String, Object> capability = objectMap(firstValue(trace.get("capability"), data.get("capability")));
        return firstString(
                meta.get("capabilityId"),
                meta.get("capability_id"),
                data.get("capabilityId"),
                data.get("capability_id"),
                capability.get("capabilityId"),
                capability.get("capability_id"),
                capability.get("id")
        );
    }

    private String actualTemplateCode(MapAgentRunResponse response) {
        MapAgentActionResult actionResult = response.getActionResult();
        Map<String, Object> templateMeta = actionResult == null || actionResult.getTemplateMeta() == null
                ? Collections.<String, Object>emptyMap()
                : actionResult.getTemplateMeta();
        Map<String, Object> data = response.getData() == null ? Collections.<String, Object>emptyMap() : response.getData();
        return firstString(
                templateMeta.get("templateCode"),
                templateMeta.get("template_code"),
                data.get("templateCode"),
                data.get("template_code")
        );
    }

    private Set<String> actualToolNames(MapAgentRunResponse response) {
        Set<String> names = new LinkedHashSet<>();
        List<Map<String, Object>> tools = response.getToolResults() == null ? Collections.<Map<String, Object>>emptyList() : response.getToolResults();
        for (Map<String, Object> tool : tools) {
            Map<String, Object> data = objectMap(tool.get("data"));
            String name = firstString(tool.get("toolName"), tool.get("tool_name"), tool.get("tool"), tool.get("name"), tool.get("id"), data.get("toolName"), data.get("tool"));
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    private String traceId(MapAgentRunResponse response) {
        return firstString(
                response.getAnswerMeta() == null ? null : response.getAnswerMeta().get("traceId"),
                response.getAnswerMeta() == null ? null : response.getAnswerMeta().get("trace_id"),
                response.getTrace() == null ? null : response.getTrace().get("traceId"),
                response.getTrace() == null ? null : response.getTrace().get("trace_id")
        );
    }

    private void addCheck(List<Map<String, Object>> checks, String code, String name, String status, String message) {
        checks.add(mapOf("code", code, "name", name, "status", status, "message", message));
    }

    private String finalStatus(List<Map<String, Object>> checks) {
        boolean warn = false;
        for (Map<String, Object> check : checks) {
            String status = string(check.get("status"));
            if ("ERROR".equals(status)) {
                return "ERROR";
            }
            if ("WARN".equals(status)) {
                warn = true;
            }
        }
        return warn ? "WARN" : "PASS";
    }

    private static List<String> missing(List<String> expected, Set<String> actual) {
        List<String> result = new ArrayList<>();
        for (String item : expected) {
            if (!actual.contains(item)) {
                result.add(item);
            }
        }
        return result;
    }

    private static List<String> present(List<String> expected, Set<String> actual) {
        List<String> result = new ArrayList<>();
        for (String item : expected) {
            if (actual.contains(item)) {
                result.add(item);
            }
        }
        return result;
    }

    private static BigDecimal passRate(int passed, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(passed * 100.0 / total).setScale(2, RoundingMode.HALF_UP);
    }

    private static Object firstValue(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstString(Object... values) {
        for (Object value : values) {
            String text = string(value);
            if (!text.isEmpty()) {
                return text;
            }
        }
        return "";
    }

    private static String emptyToDash(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static String preview(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String compact = value.replace("\r", " ").replace("\n", " ").trim();
        if (compact.length() <= maxLength) {
            return compact;
        }
        return compact.substring(0, maxLength) + "...";
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(string(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean readBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = string(value);
        if (text.isEmpty()) {
            return fallback;
        }
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Set<String> stringSet(Object value) {
        return new LinkedHashSet<>(stringList(value));
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<Object>) value) {
                String text = string(item);
                if (!text.isEmpty()) {
                    result.add(text);
                }
            }
            return result;
        }
        String text = string(value);
        if (text.isEmpty()) {
            return new ArrayList<>();
        }
        return Collections.singletonList(text);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value) {
        if (value instanceof List) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : (List<Object>) value) {
                if (item instanceof Map) {
                    result.add(cloneMap((Map<String, Object>) item));
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> cloneMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source != null) {
            result.putAll(source);
        }
        return result;
    }

    private static List<String> list(String... values) {
        return Arrays.asList(values);
    }

    private static List<Object> list(Object... values) {
        return Arrays.asList(values);
    }

    private static Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }
}
