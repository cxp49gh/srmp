package com.smartroad.srmp.agent.trace.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class AiTraceDiagnostics {
    private AiTraceDiagnostics() {
    }

    static Map<String, Object> enrich(Map<String, Object> detail, List<Map<String, Object>> steps) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (detail != null) {
            result.putAll(detail);
        }
        List<Map<String, Object>> safeSteps = steps == null ? new ArrayList<Map<String, Object>>() : steps;
        Map<String, Object> answerMeta = extractAnswerMeta(result, safeSteps);
        List<Map<String, Object>> toolResults = extractToolResults(safeSteps);
        Map<String, Object> evidence = extractEvidence(safeSteps);
        String failureReason = firstString(
                result.get("error_message"),
                result.get("errorMessage"),
                answerMeta.get("fallbackReason"),
                answerMeta.get("fallback_reason"),
                answerMeta.get("errorMessage"),
                answerMeta.get("error_message"),
                firstFailedStepError(safeSteps)
        );

        result.put("steps", safeSteps);
        result.put("answerMeta", answerMeta);
        result.put("toolResults", toolResults);
        result.put("evidence", evidence);
        result.put("toolTotalCount", toolResults.size());
        result.put("toolSuccessCount", countTools(toolResults, true));
        result.put("toolFailedCount", countTools(toolResults, false));
        result.put("sourceCount", intValue(evidence.get("businessHitCount")) + intValue(evidence.get("knowledgeHitCount")) + intValue(evidence.get("outlineHitCount")));
        if (!isBlank(failureReason)) {
            result.put("failureReason", failureReason);
        }
        result.put("trace", buildTrace(result, safeSteps));
        return result;
    }

    private static Map<String, Object> extractAnswerMeta(Map<String, Object> detail, List<Map<String, Object>> steps) {
        Map<String, Object> direct = asMap(firstValue(detail.get("answerMeta"), detail.get("answer_meta")));
        if (!direct.isEmpty()) {
            return direct;
        }

        for (Map<String, Object> step : steps) {
            Map<String, Object> data = stepData(step);
            Map<String, Object> meta = asMap(firstValue(data.get("answerMeta"), data.get("answer_meta")));
            if (!meta.isEmpty()) {
                return meta;
            }
        }

        for (Map<String, Object> step : steps) {
            Map<String, Object> data = stepData(step);
            String name = lower(firstString(step.get("step_name"), step.get("name"), step.get("node")));
            boolean looksLikeLlm = name.contains("llm") || data.containsKey("llmStatus") || data.containsKey("llm_status") || data.containsKey("llmSuccess") || data.containsKey("llm_success");
            if (!looksLikeLlm) {
                continue;
            }
            String llmStatus = firstString(data.get("llmStatus"), data.get("llm_status"), step.get("status"));
            boolean llmSuccess = boolValue(firstValue(data.get("llmSuccess"), data.get("llm_success")), "SUCCESS".equalsIgnoreCase(llmStatus));
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("answerSource", firstString(data.get("answerSource"), data.get("answer_source"), llmSuccess ? "LLM" : ""));
            meta.put("answerSourceLabel", llmSuccess ? "大模型返回" : "回答来源待确认");
            meta.put("llmSuccess", llmSuccess);
            meta.put("llmStatus", llmStatus);
            copyIfPresent(meta, "llmModel", firstValue(data.get("llmModel"), data.get("llm_model"), data.get("model")));
            copyIfPresent(meta, "fallbackReason", firstValue(data.get("fallbackReason"), data.get("fallback_reason"), data.get("reason"), data.get("errorMessage"), data.get("error_message"), step.get("error_message"), step.get("error")));
            copyIfPresent(meta, "errorType", firstValue(data.get("errorType"), data.get("error_type")));
            copyIfPresent(meta, "errorMessage", firstValue(data.get("errorMessage"), data.get("error_message"), step.get("error_message"), step.get("error")));
            meta.put("fallback", !llmSuccess);
            return meta;
        }

        return new LinkedHashMap<>();
    }

    private static List<Map<String, Object>> extractToolResults(List<Map<String, Object>> steps) {
        for (Map<String, Object> step : steps) {
            List<Map<String, Object>> toolSummary = mapList(firstValue(stepData(step).get("toolSummary"), stepData(step).get("tool_summary")));
            if (!toolSummary.isEmpty()) {
                return normalizeToolRows(toolSummary);
            }
        }

        for (Map<String, Object> step : steps) {
            Map<String, Object> data = stepData(step);
            List<Map<String, Object>> toolResults = mapList(firstValue(data.get("toolResults"), data.get("tool_results")));
            if (!toolResults.isEmpty()) {
                return normalizeToolRows(toolResults);
            }
            Object tools = data.get("tools");
            if (tools instanceof List) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Object tool : (List<?>) tools) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("toolName", String.valueOf(tool));
                    row.put("success", true);
                    rows.add(row);
                }
                return rows;
            }
        }
        return new ArrayList<>();
    }

    private static Map<String, Object> extractEvidence(List<Map<String, Object>> steps) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        int business = 0;
        int knowledge = 0;
        int outline = 0;
        int success = 0;
        int failed = 0;

        for (Map<String, Object> step : steps) {
            Map<String, Object> data = stepData(step);
            Map<String, Object> nested = asMap(data.get("evidence"));
            Map<String, Object> source = nested.isEmpty() ? data : nested;
            business = Math.max(business, intValue(firstValue(source.get("businessHitCount"), source.get("business_hit_count"))));
            knowledge = Math.max(knowledge, intValue(firstValue(source.get("knowledgeHitCount"), source.get("knowledge_hit_count"))));
            outline = Math.max(outline, intValue(firstValue(source.get("outlineHitCount"), source.get("outline_hit_count"))));
            success = Math.max(success, intValue(firstValue(source.get("toolSuccessCount"), source.get("tool_success_count"))));
            failed = Math.max(failed, intValue(firstValue(source.get("toolFailedCount"), source.get("tool_failed_count"))));

            String name = lower(firstString(step.get("step_name"), step.get("name")));
            if (business == 0 && name.contains("business")) {
                business = intValue(firstValue(step.get("hit_count"), step.get("count")));
            }
            if (knowledge == 0 && (name.contains("rag") || name.contains("knowledge"))) {
                knowledge = intValue(firstValue(step.get("hit_count"), step.get("count")));
            }
        }

        evidence.put("businessHitCount", business);
        evidence.put("knowledgeHitCount", knowledge);
        evidence.put("outlineHitCount", outline);
        evidence.put("toolSuccessCount", success);
        evidence.put("toolFailedCount", failed);
        evidence.put("sufficient", business > 0 || knowledge > 0 || outline > 0);
        return evidence;
    }

    private static Map<String, Object> buildTrace(Map<String, Object> detail, List<Map<String, Object>> steps) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("traceId", firstString(detail.get("trace_id"), detail.get("traceId")));
        trace.put("requestType", firstString(detail.get("request_type"), detail.get("requestType")));
        trace.put("mode", detail.get("mode"));
        trace.put("status", detail.get("status"));
        trace.put("fallback", firstValue(detail.get("fallback"), detail.get("fallbackLike"), detail.get("fallback_like")));
        trace.put("error", firstString(detail.get("failureReason"), detail.get("error_message"), detail.get("errorMessage")));
        trace.put("totalCostMs", firstValue(detail.get("total_cost_ms"), detail.get("totalCostMs")));
        trace.put("answerMeta", detail.get("answerMeta"));
        trace.put("steps", steps);
        return trace;
    }

    private static List<Map<String, Object>> normalizeToolRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> next = new LinkedHashMap<>();
            next.put("toolName", firstString(row.get("toolName"), row.get("tool_name"), row.get("name"), row.get("tool")));
            next.put("success", boolValue(row.get("success"), !"FAILED".equalsIgnoreCase(firstString(row.get("status")))));
            copyIfPresent(next, "count", firstValue(row.get("count"), row.get("hitCount"), row.get("hit_count"), row.get("resultCount")));
            copyIfPresent(next, "costMs", firstValue(row.get("costMs"), row.get("cost_ms"), row.get("elapsedMs")));
            copyIfPresent(next, "errorMessage", firstValue(row.get("errorMessage"), row.get("error_message"), row.get("error"), row.get("reason")));
            result.add(next);
        }
        return result;
    }

    private static int countTools(List<Map<String, Object>> tools, boolean expectedSuccess) {
        int count = 0;
        for (Map<String, Object> tool : tools) {
            boolean success = boolValue(tool.get("success"), true);
            if (success == expectedSuccess) {
                count++;
            }
        }
        return count;
    }

    private static String firstFailedStepError(List<Map<String, Object>> steps) {
        for (Map<String, Object> step : steps) {
            String status = firstString(step.get("status"));
            if ("FAILED".equalsIgnoreCase(status) || "TIMEOUT".equalsIgnoreCase(status)) {
                return firstString(step.get("error_message"), step.get("error"), stepData(step).get("errorMessage"), stepData(step).get("error_message"));
            }
        }
        return "";
    }

    private static Map<String, Object> stepData(Map<String, Object> step) {
        return asMap(firstValue(step.get("data"), step.get("step_data"), step.get("detail"), step.get("details")));
    }

    private static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static List<Map<String, Object>> mapList(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(value instanceof List)) {
            return result;
        }
        for (Object item : (List<?>) value) {
            Map<String, Object> row = asMap(item);
            if (!row.isEmpty()) {
                result.add(row);
            }
        }
        return result;
    }

    private static Object firstValue(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof String && ((String) value).trim().isEmpty()) {
                continue;
            }
            return value;
        }
        return null;
    }

    private static String firstString(Object... values) {
        Object value = firstValue(values);
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean boolValue(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(text);
    }

    private static int intValue(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static void copyIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null && (!(value instanceof String) || !((String) value).trim().isEmpty())) {
            map.put(key, value);
        }
    }
}
