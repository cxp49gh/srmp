package com.smartroad.srmp.agent.solution.service.impl;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AiSolutionQualityServiceImplTest {

    @Test
    public void enrichQualitySnapshotPassesSectionPlanWithTemplateEvidenceAndMapBinding() {
        AiSolutionQualityServiceImpl service = new AiSolutionQualityServiceImpl();

        Map<String, Object> task = mapOf(
                "solution_type", "SECTION_PLAN",
                "origin_type", "MAP_OBJECT",
                "object_type", "ROAD_SECTION",
                "route_code", "Y016140727",
                "year", 2026,
                "object_id", "section-1",
                "template_meta", mapOf(
                        "matched", true,
                        "fallback", false,
                        "templateCode", "map_object_section_plan_default",
                        "templateName", "路段养护计划默认模板",
                        "missingVariables", Arrays.asList()
                ),
                "map_object", mapOf(
                        "objectType", "ROAD_SECTION",
                        "objectId", "section-1",
                        "routeCode", "Y016140727",
                        "startStake", 10,
                        "endStake", 12.5
                ),
                "object_summary", mapOf(
                        "businessEvidence", mapOf("toolSuccessCount", 3, "businessHitCount", 7)
                ),
                "request_json", mapOf(
                        "sourceSummaries", Arrays.asList(mapOf("sourceType", "BUSINESS_DATA", "sourceTitle", "业务查询证据"))
                ),
                "ai_context", mapOf(
                        "answerMeta", mapOf("llmSuccess", true, "answerSource", "LLM")
                )
        );
        List<Map<String, Object>> sources = Arrays.asList(
                mapOf("source_type", "MAP_OBJECT", "source_title", "当前地图对象"),
                mapOf("source_type", "BUSINESS_DATA", "source_title", "业务查询证据"),
                mapOf("source_type", "TEMPLATE", "source_title", "路段养护计划默认模板")
        );

        Map<String, Object> base = mapOf(
                "passed", true,
                "score", 95,
                "level", "A",
                "items", Arrays.asList()
        );
        Map<String, Object> result = service.enrichQualitySnapshot(task, sources, "## 养护建议\n人工审核", base);

        assertEquals(Boolean.TRUE, result.get("passed"));
        assertTrue(((Number) result.get("score")).intValue() >= 90);
        assertEquals("A", result.get("level"));
        assertDimension(result, "template", "OK");
        assertDimension(result, "businessEvidence", "OK");
        assertDimension(result, "mapBinding", "OK");
        assertDimension(result, "llm", "OK");
        assertDimension(result, "scenario", "OK");
    }

    @Test
    public void enrichQualitySnapshotKeepsEvaluationAdviceSeparateFromLowScoreTreatment() {
        AiSolutionQualityServiceImpl service = new AiSolutionQualityServiceImpl();
        Map<String, Object> task = mapOf(
                "solution_type", "EVALUATION_UNIT_ADVICE",
                "origin_type", "MAP_OBJECT",
                "object_type", "ASSESSMENT_RESULT",
                "object_id", "assessment-1",
                "template_meta", mapOf("matched", true, "fallback", false),
                "map_object", mapOf("objectType", "ASSESSMENT_RESULT", "objectId", "assessment-1", "routeCode", "Y016140727", "startStake", 0, "endStake", 14.072)
        );
        Map<String, Object> result = service.enrichQualitySnapshot(task, Arrays.asList(mapOf("source_type", "MAP_OBJECT")), "评定结果养护建议", mapOf("score", 90, "items", Arrays.asList()));

        assertDimension(result, "scenario", "OK");
        assertFalse(String.valueOf(result.get("summary")).contains("低分"));
    }

    @Test
    public void enrichQualitySnapshotFlagsFallbackTemplateAndMissingBusinessEvidence() {
        AiSolutionQualityServiceImpl service = new AiSolutionQualityServiceImpl();
        Map<String, Object> task = mapOf(
                "solution_type", "SECTION_PLAN",
                "origin_type", "MAP_OBJECT",
                "object_type", "ROAD_SECTION",
                "template_meta", mapOf(
                        "fallback", true,
                        "fallbackReason", "未匹配到模板",
                        "missingVariables", Arrays.asList("businessEvidenceSummary")
                )
        );
        Map<String, Object> result = service.enrichQualitySnapshot(task, Arrays.asList(), "系统兜底模板", mapOf("score", 90, "items", Arrays.asList()));

        assertEquals(Boolean.FALSE, result.get("passed"));
        assertDimension(result, "template", "ERROR");
        assertDimension(result, "businessEvidence", "ERROR");
        assertDimension(result, "mapBinding", "WARN");
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
