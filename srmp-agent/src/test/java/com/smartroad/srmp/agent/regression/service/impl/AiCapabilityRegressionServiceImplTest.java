package com.smartroad.srmp.agent.regression.service.impl;

import com.smartroad.srmp.agent.mapagent.dto.MapAgentActionResult;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunResponse;
import com.smartroad.srmp.agent.mapagent.service.MapAiAgentService;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AiCapabilityRegressionServiceImplTest {

    @Test
    public void defaultCasesCoverMapAnalysisKnowledgeAndSolutionChains() {
        AiCapabilityRegressionServiceImpl service = new AiCapabilityRegressionServiceImpl(successfulAgent());

        List<Map<String, Object>> cases = service.defaultCases();

        assertEquals(12, cases.size());
        Set<String> ids = cases.stream().map(item -> String.valueOf(item.get("caseId"))).collect(Collectors.toSet());
        assertTrue(ids.contains("knowledge.metric_explain"));
        assertTrue(ids.contains("map.route_analysis"));
        assertTrue(ids.contains("map.section_analysis"));
        assertTrue(ids.contains("map.disease_analysis"));
        assertTrue(ids.contains("map.assessment_analysis"));
        assertTrue(ids.contains("map.region_analysis"));
        assertTrue(ids.contains("solution.route_report"));
        assertTrue(ids.contains("solution.section_plan"));
        assertTrue(ids.contains("solution.disease_treatment"));
        assertTrue(ids.contains("solution.disease_review"));
        assertTrue(ids.contains("solution.assessment_advice"));
        assertTrue(ids.contains("solution.region_advice"));
    }

    @Test
    public void runMarksCasePassedWhenCapabilityToolsAndTemplateMatch() {
        AiCapabilityRegressionServiceImpl service = new AiCapabilityRegressionServiceImpl(successfulAgent());

        Map<String, Object> result = service.run(mapOf("caseIds", Collections.singletonList("solution.route_report")));

        assertEquals(1, result.get("total"));
        assertEquals(1, result.get("passed"));
        assertEquals(0, result.get("failed"));
        Map<String, Object> item = resultList(result).get(0);
        assertEquals("PASS", item.get("status"));
        assertCheck(item, "CAPABILITY_MATCH", "PASS");
        assertCheck(item, "REQUIRED_TOOLS", "PASS");
        assertCheck(item, "TEMPLATE_MATCH", "PASS");
    }

    @Test
    public void runRejectsKnowledgeQuestionUsingBusinessTool() {
        AiCapabilityRegressionServiceImpl service = new AiCapabilityRegressionServiceImpl(request -> response(
                "knowledge.metric_explain",
                Arrays.asList("knowledge.retrieve", "gis.queryRegionSummary"),
                null
        ));

        Map<String, Object> result = service.run(mapOf("caseIds", Collections.singletonList("knowledge.metric_explain")));

        assertEquals(1, result.get("total"));
        assertEquals(0, result.get("passed"));
        assertEquals(1, result.get("failed"));
        Map<String, Object> item = resultList(result).get(0);
        assertEquals("ERROR", item.get("status"));
        assertCheck(item, "PROHIBITED_TOOLS", "ERROR");
    }

    @Test
    public void runRejectsMissingTemplateForGeneration() {
        AiCapabilityRegressionServiceImpl service = new AiCapabilityRegressionServiceImpl(request -> response(
                "solution.route_report",
                Arrays.asList("gis.queryRegionSummary", "gis.queryAssessmentResults", "gis.queryDiseases", "knowledge.retrieve", "solution.generateDraft"),
                "legacy_route_report"
        ));

        Map<String, Object> result = service.run(mapOf("caseIds", Collections.singletonList("solution.route_report")));

        assertEquals(1, result.get("total"));
        assertEquals(0, result.get("passed"));
        assertEquals(1, result.get("failed"));
        Map<String, Object> item = resultList(result).get(0);
        assertEquals("ERROR", item.get("status"));
        assertCheck(item, "TEMPLATE_MATCH", "ERROR");
    }

    private static MapAiAgentService successfulAgent() {
        return request -> {
            Map<String, Object> expected = expected(request);
            @SuppressWarnings("unchecked")
            List<String> tools = (List<String>) expected.get("requiredTools");
            return response(String.valueOf(expected.get("capabilityId")), tools, (String) expected.get("templateCode"));
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> expected(MapAgentRunRequest request) {
        Map<String, Object> options = request.getOptions();
        Object caseSpec = options.get("regressionCase");
        if (caseSpec instanceof Map) {
            return (Map<String, Object>) caseSpec;
        }
        throw new AssertionError("missing regression case in request options");
    }

    private static MapAgentRunResponse response(String capabilityId, List<String> tools, String templateCode) {
        MapAgentRunResponse response = new MapAgentRunResponse();
        response.setAnswer("回归测试回答");
        response.setAnswerMeta(mapOf(
                "capabilityId", capabilityId,
                "llmSuccess", true,
                "llmStatus", "SUCCESS",
                "answerSource", "LLM"
        ));
        for (String tool : tools) {
            response.getToolResults().add(mapOf("toolName", tool, "status", "SUCCESS", "data", mapOf("count", 1)));
        }
        if (templateCode != null) {
            MapAgentActionResult actionResult = new MapAgentActionResult();
            actionResult.setTemplateMeta(mapOf("templateCode", templateCode));
            response.setActionResult(actionResult);
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> resultList(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("results");
    }

    @SuppressWarnings("unchecked")
    private static void assertCheck(Map<String, Object> result, String code, String status) {
        List<Map<String, Object>> checks = (List<Map<String, Object>>) result.get("checks");
        for (Map<String, Object> check : checks) {
            if (code.equals(check.get("code"))) {
                assertEquals(status, check.get("status"));
                return;
            }
        }
        throw new AssertionError("missing check: " + code);
    }

    private static Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }
}
