package com.smartroad.srmp.agent.execution.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.agent.execution.service.AiExecutionRecorder;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentActionResult;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunResponse;
import com.smartroad.srmp.agent.tool.AiToolContext;
import com.smartroad.srmp.agent.tool.support.AiBusinessScope;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiExecutionRecorderImpl implements AiExecutionRecorder {
    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    @Override
    public void record(MapAgentRunRequest request, MapAgentRunResponse response, String tenantId, String traceId, long costMs) {
        if (response == null) return;
        String safeTenantId = blank(tenantId) ? TenantContextHolder.getTenantId() : tenantId;
        if (blank(safeTenantId)) safeTenantId = "default";
        String safeTraceId = firstNonBlank(traceId, stringValue(first(response.getTrace(), "traceId", "trace_id")), stringValue(first(response.getData(), "traceId", "trace_id")));
        if (blank(safeTraceId)) return;

        MapSqlParameterSource key = new MapSqlParameterSource()
                .addValue("tenantId", safeTenantId)
                .addValue("traceId", safeTraceId);
        cleanupExisting(key);

        String executionId = uuid();
        MapAgentActionResult actionResult = response.getActionResult();
        String actionStatus = actionResult == null ? "SUCCESS" : firstNonBlank(actionResult.getStatus(), "SUCCESS");
        boolean fallback = Boolean.TRUE.equals(first(response.getAnswerMeta(), "fallback")) || !"SUCCESS".equalsIgnoreCase(actionStatus);

        namedParameterJdbcTemplate.update(
                "insert into ai_execution_run(id,tenant_id,trace_id,request_type,action,intent,mode,provider,model,graph_name,status,fallback,fallback_reason,error_message,total_cost_ms,started_at,finished_at,raw_request,raw_response,created_at,updated_at) " +
                        "values(:id,:tenantId,:traceId,:requestType,:action,:intent,:mode,:provider,:model,:graphName,:status,:fallback,:fallbackReason,:errorMessage,:totalCostMs,now() - cast((:totalCostMs || ' milliseconds') as interval),now(),cast(:rawRequest as jsonb),cast(:rawResponse as jsonb),now(),now())",
                new MapSqlParameterSource()
                        .addValue("id", executionId)
                        .addValue("tenantId", safeTenantId)
                        .addValue("traceId", safeTraceId)
                        .addValue("requestType", "MAP_AGENT_RUN")
                        .addValue("action", firstNonBlank(response.getAction(), request == null ? null : request.getAction()))
                        .addValue("intent", response.getIntent())
                        .addValue("mode", response.getMode())
                        .addValue("provider", "langgraph")
                        .addValue("model", firstNonBlank(stringValue(first(response.getAnswerMeta(), "llmModel", "llm_model")), stringValue(first(response.getData(), "llmModel", "llm_model"))))
                        .addValue("graphName", stringValue(first(response.getTrace(), "graphName", "graph_name")))
                        .addValue("status", actionStatus)
                        .addValue("fallback", fallback)
                        .addValue("fallbackReason", stringValue(first(response.getAnswerMeta(), "fallbackReason", "fallback_reason")))
                        .addValue("errorMessage", actionResult == null ? null : actionResult.getErrorMessage())
                        .addValue("totalCostMs", (int) Math.max(0, costMs))
                        .addValue("rawRequest", toJson(request))
                        .addValue("rawResponse", toJson(response))
        );
        insertScope(executionId, safeTenantId, safeTraceId, request);
        insertSteps(executionId, safeTenantId, safeTraceId, response);
        insertToolCalls(executionId, safeTenantId, safeTraceId, response);
        insertEvidence(executionId, safeTenantId, safeTraceId, response);
        insertAnswer(executionId, safeTenantId, safeTraceId, response);
    }

    private void cleanupExisting(MapSqlParameterSource key) {
        namedParameterJdbcTemplate.update("delete from ai_execution_answer where tenant_id=:tenantId and trace_id=:traceId", key);
        namedParameterJdbcTemplate.update("delete from ai_execution_evidence where tenant_id=:tenantId and trace_id=:traceId", key);
        namedParameterJdbcTemplate.update("delete from ai_execution_tool_call where tenant_id=:tenantId and trace_id=:traceId", key);
        namedParameterJdbcTemplate.update("delete from ai_execution_step where tenant_id=:tenantId and trace_id=:traceId", key);
        namedParameterJdbcTemplate.update("delete from ai_execution_scope where tenant_id=:tenantId and trace_id=:traceId", key);
        namedParameterJdbcTemplate.update("delete from ai_execution_run where tenant_id=:tenantId and trace_id=:traceId", key);
    }

    private void insertScope(String executionId, String tenantId, String traceId, MapAgentRunRequest request) {
        AiToolContext toolContext = new AiToolContext();
        toolContext.setTenantId(tenantId);
        toolContext.setTraceId(traceId);
        toolContext.setMapContext(request == null ? null : request.getMapContext());
        AiBusinessScope scope = AiBusinessScope.from(toolContext, request == null ? null : request.getActionInput());
        namedParameterJdbcTemplate.update(
                "insert into ai_execution_scope(id,execution_id,tenant_id,trace_id,project_id,route_code,year,section_tier,context_scope,object_type,object_id,assessment_object_type,direction,start_stake,end_stake,bbox,geometry_type,selected_layers,scope_warnings,created_at,updated_at) " +
                        "values(:id,:executionId,:tenantId,:traceId,:projectId,:routeCode,:year,:sectionTier,:contextScope,:objectType,:objectId,:assessmentObjectType,:direction,:startStake,:endStake,cast(:bbox as jsonb),:geometryType,cast(:selectedLayers as jsonb),cast(:scopeWarnings as jsonb),now(),now())",
                new MapSqlParameterSource()
                        .addValue("id", uuid())
                        .addValue("executionId", executionId)
                        .addValue("tenantId", tenantId)
                        .addValue("traceId", traceId)
                        .addValue("projectId", emptyToNull(scope.getProjectId()))
                        .addValue("routeCode", emptyToNull(scope.getRouteCode()))
                        .addValue("year", scope.getYear())
                        .addValue("sectionTier", emptyToNull(scope.getSectionTier()))
                        .addValue("contextScope", emptyToNull(scope.getContextScope()))
                        .addValue("objectType", emptyToNull(scope.getObjectType()))
                        .addValue("objectId", emptyToNull(scope.getObjectId()))
                        .addValue("assessmentObjectType", emptyToNull(scope.getAssessmentObjectType()))
                        .addValue("direction", emptyToNull(scope.getDirection()))
                        .addValue("startStake", scope.getStartStake())
                        .addValue("endStake", scope.getEndStake())
                        .addValue("bbox", toJson(scope.getBbox()))
                        .addValue("geometryType", emptyToNull(scope.getGeometryType()))
                        .addValue("selectedLayers", toJson(scope.getSelectedLayers()))
                        .addValue("scopeWarnings", toJson(scope.getScopeWarnings()))
        );
    }

    @SuppressWarnings("unchecked")
    private void insertSteps(String executionId, String tenantId, String traceId, MapAgentRunResponse response) {
        Object stepsRaw = first(response.getTrace(), "steps");
        List<Object> steps = stepsRaw instanceof List ? (List<Object>) stepsRaw : Collections.emptyList();
        int index = 0;
        for (Object raw : steps) {
            Map<String, Object> step = raw instanceof Map ? (Map<String, Object>) raw : new LinkedHashMap<String, Object>();
            namedParameterJdbcTemplate.update(
                    "insert into ai_execution_step(id,execution_id,tenant_id,trace_id,step_name,step_label,status,cost_ms,error_message,step_data,created_at) " +
                            "values(:id,:executionId,:tenantId,:traceId,:stepName,:stepLabel,:status,:costMs,:errorMessage,cast(:stepData as jsonb),now())",
                    new MapSqlParameterSource()
                            .addValue("id", String.format("%08d-%s", index++, uuid()))
                            .addValue("executionId", executionId)
                            .addValue("tenantId", tenantId)
                            .addValue("traceId", traceId)
                            .addValue("stepName", first(step, "name", "step_name", "node"))
                            .addValue("stepLabel", first(step, "label", "step_label", "message"))
                            .addValue("status", first(step, "status"))
                            .addValue("costMs", intOrNull(first(step, "costMs", "cost_ms", "elapsedMs")))
                            .addValue("errorMessage", first(step, "errorMessage", "error_message", "error"))
                            .addValue("stepData", toJson(first(step, "data", "step_data", "detail", "details")))
            );
        }
    }

    @SuppressWarnings("unchecked")
    private void insertToolCalls(String executionId, String tenantId, String traceId, MapAgentRunResponse response) {
        List<Map<String, Object>> tools = response.getToolResults() == null ? Collections.<Map<String, Object>>emptyList() : response.getToolResults();
        for (Map<String, Object> tool : tools) {
            Map<String, Object> data = first(tool, "data") instanceof Map ? (Map<String, Object>) first(tool, "data") : new LinkedHashMap<String, Object>();
            namedParameterJdbcTemplate.update(
                    "insert into ai_execution_tool_call(id,execution_id,tenant_id,trace_id,tool_name,status,cost_ms,total_count,returned_count,truncated,query_scope,result_summary,raw_args,raw_result,error_message,created_at) " +
                            "values(:id,:executionId,:tenantId,:traceId,:toolName,:status,:costMs,:totalCount,:returnedCount,:truncated,cast(:queryScope as jsonb),cast(:resultSummary as jsonb),cast(:rawArgs as jsonb),cast(:rawResult as jsonb),:errorMessage,now())",
                    new MapSqlParameterSource()
                            .addValue("id", uuid())
                            .addValue("executionId", executionId)
                            .addValue("tenantId", tenantId)
                            .addValue("traceId", traceId)
                            .addValue("toolName", firstNonBlank(stringValue(first(tool, "toolName", "name", "tool")), "UNKNOWN"))
                            .addValue("status", isFalse(first(tool, "success")) ? "FAILED" : "SUCCESS")
                            .addValue("costMs", intOrNull(first(tool, "costMs", "cost_ms")))
                            .addValue("totalCount", intOrNull(first(data, "totalCount", "total_count", "count")))
                            .addValue("returnedCount", intOrNull(first(data, "returnedCount", "returned_count", "count")))
                            .addValue("truncated", isTrue(first(data, "truncated")))
                            .addValue("queryScope", toJson(first(data, "queryScope", "query_scope")))
                            .addValue("resultSummary", toJson(first(data, "summary")))
                            .addValue("rawArgs", toJson(first(tool, "args", "rawArgs", "raw_args")))
                            .addValue("rawResult", toJson(tool))
                            .addValue("errorMessage", first(tool, "errorMessage", "error_message", "error"))
            );
        }
    }

    private void insertEvidence(String executionId, String tenantId, String traceId, MapAgentRunResponse response) {
        List<Map<String, Object>> sources = response.getSources() == null || response.getSources().isEmpty() ? response.getKnowledgeSources() : response.getSources();
        if (sources == null) return;
        for (Map<String, Object> source : sources) {
            namedParameterJdbcTemplate.update(
                    "insert into ai_execution_evidence(id,execution_id,tenant_id,trace_id,source_type,source_name,title,route_code,object_type,object_id,score,payload,created_at) " +
                            "values(:id,:executionId,:tenantId,:traceId,:sourceType,:sourceName,:title,:routeCode,:objectType,:objectId,:score,cast(:payload as jsonb),now())",
                    new MapSqlParameterSource()
                            .addValue("id", uuid())
                            .addValue("executionId", executionId)
                            .addValue("tenantId", tenantId)
                            .addValue("traceId", traceId)
                            .addValue("sourceType", first(source, "sourceType", "type", "source"))
                            .addValue("sourceName", first(source, "sourceName", "name", "tableName"))
                            .addValue("title", first(source, "title", "name", "summary"))
                            .addValue("routeCode", first(source, "routeCode", "route_code"))
                            .addValue("objectType", first(source, "objectType", "object_type"))
                            .addValue("objectId", first(source, "objectId", "object_id", "id"))
                            .addValue("score", first(source, "score"))
                            .addValue("payload", toJson(source))
            );
        }
    }

    private void insertAnswer(String executionId, String tenantId, String traceId, MapAgentRunResponse response) {
        namedParameterJdbcTemplate.update(
                "insert into ai_execution_answer(id,execution_id,tenant_id,trace_id,used_model,answer_text,answer_meta,quality_flags,created_at,updated_at) " +
                        "values(:id,:executionId,:tenantId,:traceId,:usedModel,:answerText,cast(:answerMeta as jsonb),cast(:qualityFlags as jsonb),now(),now())",
                new MapSqlParameterSource()
                        .addValue("id", uuid())
                        .addValue("executionId", executionId)
                        .addValue("tenantId", tenantId)
                        .addValue("traceId", traceId)
                        .addValue("usedModel", first(response.getAnswerMeta(), "llmModel", "llm_model"))
                        .addValue("answerText", response.getAnswer())
                        .addValue("answerMeta", toJson(response.getAnswerMeta()))
                        .addValue("qualityFlags", toJson(first(response.getData(), "quality")))
        );
    }

    private Object first(Map<String, Object> data, String... keys) {
        if (data == null) return null;
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null) return value;
        }
        return null;
    }

    private Integer intOrNull(Object value) {
        if (value == null || String.valueOf(value).trim().length() == 0) return null;
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? null : value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (!blank(value)) return value.trim();
        }
        return null;
    }

    private boolean blank(String value) {
        return value == null || value.trim().length() == 0;
    }

    private boolean isFalse(Object value) {
        if (value instanceof Boolean) return !((Boolean) value);
        return "false".equalsIgnoreCase(String.valueOf(value)) || "0".equals(String.valueOf(value));
    }

    private boolean isTrue(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private Object emptyToNull(String value) {
        return blank(value) ? null : value;
    }
}
