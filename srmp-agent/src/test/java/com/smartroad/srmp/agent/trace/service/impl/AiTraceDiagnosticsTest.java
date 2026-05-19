package com.smartroad.srmp.agent.trace.service.impl;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AiTraceDiagnosticsTest {

    @Test
    public void enrichAggregatesAnswerMetaToolsEvidenceAndFailureReason() {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("trace_id", "ai-trace-1");
        detail.put("request_type", "MAP_AGENT_RUN");
        detail.put("status", "FAILED");
        detail.put("total_cost_ms", 1200);
        detail.put("fallback", true);

        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(step("evidence_fuse", "融合证据", "SUCCESS", 20, null, mapOf(
                "businessHitCount", 8,
                "knowledgeHitCount", 2,
                "toolSuccessCount", 1,
                "toolFailedCount", 1,
                "toolSummary", listOf(
                        mapOf("toolName", "gis.queryDiseases", "success", true, "hitCount", 8, "costMs", 10),
                        mapOf("toolName", "knowledge.retrieve", "success", false, "error", "timeout")
                )
        )));
        steps.add(step("llm_answer", "大模型回答", "FAILED", 30, "LLM 返回为空", mapOf(
                "llmStatus", "FAILED",
                "errorType", "EMPTY_RESPONSE",
                "errorMessage", "LLM 返回为空"
        )));

        Map<String, Object> result = AiTraceDiagnostics.enrich(detail, steps);

        assertEquals("ai-trace-1", ((Map) result.get("trace")).get("traceId"));
        assertEquals(2, result.get("toolTotalCount"));
        assertEquals(1, result.get("toolSuccessCount"));
        assertEquals(1, result.get("toolFailedCount"));
        assertEquals(10, result.get("sourceCount"));
        assertEquals("LLM 返回为空", result.get("failureReason"));

        Map answerMeta = (Map) result.get("answerMeta");
        assertEquals("FAILED", answerMeta.get("llmStatus"));
        assertEquals("EMPTY_RESPONSE", answerMeta.get("errorType"));
        assertFalse((Boolean) answerMeta.get("llmSuccess"));

        Map evidence = (Map) result.get("evidence");
        assertEquals(8, evidence.get("businessHitCount"));
        assertEquals(2, evidence.get("knowledgeHitCount"));
        assertTrue((Boolean) evidence.get("sufficient"));

        List toolResults = (List) result.get("toolResults");
        assertEquals("knowledge.retrieve", ((Map) toolResults.get(1)).get("toolName"));
        assertFalse((Boolean) ((Map) toolResults.get(1)).get("success"));
    }

    @Test
    public void enrichInfersBusinessAndKnowledgeHitsFromLegacySteps() {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("trace_id", "legacy-trace");
        detail.put("status", "SUCCESS");

        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(step("business_analysis", "业务数据分析", "SUCCESS", 8, null, null));
        steps.add(step("rag_answer", "RAG 问答生成", "SUCCESS", 3, null, null));

        Map<String, Object> result = AiTraceDiagnostics.enrich(detail, steps);
        Map evidence = (Map) result.get("evidence");

        assertEquals(8, evidence.get("businessHitCount"));
        assertEquals(3, evidence.get("knowledgeHitCount"));
        assertEquals(11, result.get("sourceCount"));
        assertTrue(((Map) result.get("answerMeta")).isEmpty());
    }

    private Map<String, Object> step(String name, String label, String status, Integer count, String error, Map<String, Object> data) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("step_name", name);
        step.put("step_label", label);
        step.put("status", status);
        step.put("hit_count", count);
        step.put("error_message", error);
        if (data != null) {
            step.put("data", data);
        }
        return step;
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    private List<Map<String, Object>> listOf(Map<String, Object>... values) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> value : values) {
            list.add(value);
        }
        return list;
    }
}
