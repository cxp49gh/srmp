package com.smartroad.srmp.agent.solution.service.impl;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AiSolutionEvalServiceImplTest {

    @Test
    public void defaultCasesCoverOneMapSolutionScenarios() {
        AiSolutionEvalServiceImpl service = new AiSolutionEvalServiceImpl(new AiSolutionQualityServiceImpl());

        List<Map<String, Object>> cases = service.defaultCases();

        assertEquals(6, cases.size());
        Set<String> solutionTypes = cases.stream()
                .map(item -> String.valueOf(item.get("solutionType")))
                .collect(Collectors.toSet());
        assertTrue(solutionTypes.contains("ROUTE_REPORT"));
        assertTrue(solutionTypes.contains("SECTION_PLAN"));
        assertTrue(solutionTypes.contains("EVALUATION_UNIT_ADVICE"));
        assertTrue(solutionTypes.contains("LOW_SCORE_TREATMENT"));
        assertTrue(solutionTypes.contains("DISEASE_TREATMENT"));
        assertTrue(solutionTypes.contains("REGION_MAINTENANCE_SUGGESTION"));
    }

    @Test
    public void runUsesFixtureQualitySnapshotWhenNoSavedTaskIsAvailable() {
        AiSolutionEvalServiceImpl service = new AiSolutionEvalServiceImpl(new AiSolutionQualityServiceImpl());

        Map<String, Object> result = service.run(new LinkedHashMap<>());

        assertEquals(6, result.get("total"));
        assertEquals(6, result.get("passed"));
        assertEquals(0, result.get("failed"));
        List<Map<String, Object>> items = resultList(result);
        assertEquals(6, items.size());
        for (Map<String, Object> item : items) {
            assertEquals(Boolean.TRUE, item.get("passed"));
            assertEquals("FIXTURE", item.get("sourceMode"));
            assertDimension(item, "template", "OK");
            assertDimension(item, "businessEvidence", "OK");
            assertDimension(item, "mapBinding", "OK");
            assertDimension(item, "scenario", "OK");
        }
    }

    @Test
    public void runFlagsScenarioMismatchAsFailedRegressionCase() {
        AiSolutionEvalServiceImpl service = new AiSolutionEvalServiceImpl(new AiSolutionQualityServiceImpl());
        Map<String, Object> request = mapOf(
                "useLatestTask", false,
                "cases", Arrays.asList(mapOf(
                        "id", "mismatch-section-assessment",
                        "name", "路段计划误挂评定结果",
                        "solutionType", "SECTION_PLAN",
                        "originType", "MAP_OBJECT",
                        "objectType", "ASSESSMENT_RESULT",
                        "requiredDimensions", Arrays.asList("scenario")
                ))
        );

        Map<String, Object> result = service.run(request);

        assertEquals(1, result.get("total"));
        assertEquals(0, result.get("passed"));
        assertEquals(1, result.get("failed"));
        Map<String, Object> item = resultList(result).get(0);
        assertEquals(Boolean.FALSE, item.get("passed"));
        assertDimension(item, "scenario", "ERROR");
        assertTrue(String.valueOf(item.get("errors")).contains("场景匹配"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> resultList(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("results");
    }

    @SuppressWarnings("unchecked")
    private static void assertDimension(Map<String, Object> result, String code, String level) {
        List<Map<String, Object>> dimensions = (List<Map<String, Object>>) result.get("dimensions");
        for (Map<String, Object> item : dimensions) {
            if (code.equals(item.get("code"))) {
                assertEquals(level, item.get("level"));
                return;
            }
        }
        throw new AssertionError("Missing dimension: " + code);
    }

    private static Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }
}
