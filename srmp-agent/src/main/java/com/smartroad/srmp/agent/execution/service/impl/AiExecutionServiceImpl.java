package com.smartroad.srmp.agent.execution.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.agent.execution.service.AiExecutionService;
import com.smartroad.srmp.agent.trace.service.AiTraceService;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AiExecutionServiceImpl implements AiExecutionService {
    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Resource
    private AiTraceService aiTraceService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<Map<String, Object>> list(Map<String, Object> query) {
        MapSqlParameterSource p = listParams(query);
        try {
            List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(listSql(), p);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                result.add(toListItem(row, false));
            }
            if (!result.isEmpty() || hasExecutionOnlyFilter(query)) {
                return result;
            }
        } catch (Exception ignored) {
            // New execution tables may not exist before migration. Keep the page usable via legacy trace fallback.
        }
        List<Map<String, Object>> legacy = aiTraceService.list(safe(query.get("status")), safe(query.get("keyword")), intValue(query.get("limit"), 50));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : legacy) {
            Map<String, Object> item = toListItem(row, true);
            result.add(item);
        }
        return result;
    }

    @Override
    public Map<String, Object> detail(String traceId) {
        String tenantId = TenantContextHolder.getTenantId();
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("traceId", safe(traceId));
        try {
            List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                    "select r.*, s.project_id, s.route_code, s.year, s.section_tier, s.context_scope, s.object_type, s.object_id, " +
                            "s.assessment_object_type, s.direction, s.start_stake, s.end_stake, s.bbox, s.geometry_type, s.selected_layers, s.scope_warnings " +
                            "from ai_execution_run r left join ai_execution_scope s on s.tenant_id=r.tenant_id and s.trace_id=r.trace_id and s.deleted=false " +
                            "where r.tenant_id=:tenantId and r.trace_id=:traceId and r.deleted=false order by r.created_at desc limit 1",
                    p);
            if (!rows.isEmpty()) {
                Map<String, Object> detail = toDetail(rows.get(0));
                String executionId = safe(rows.get(0).get("id"));
                List<Map<String, Object>> steps = steps(p);
                List<Map<String, Object>> tools = toolCalls(p, executionId);
                List<Map<String, Object>> sources = evidence(p);
                detail.put("steps", steps);
                detail.put("toolResults", tools);
                detail.put("sources", sources);
                refreshDetailSummaryCounts(detail, tools, sources);
                detail.put("evidence", evidenceSummary(sources, tools));
                detail.put("legacy", false);
                return detail;
            }
        } catch (Exception ignored) {
            // Fall through to legacy trace diagnostics.
        }
        Map<String, Object> legacy = aiTraceService.detail(traceId);
        if (legacy == null) {
            legacy = new LinkedHashMap<>();
        }
        legacy.put("legacy", true);
        legacy.put("diagnosticWarning", "该记录来自旧 trace 表，执行范围、工具查询和证据诊断信息可能不完整。");
        return legacy;
    }

    private String listSql() {
        return "select r.id, r.tenant_id, r.trace_id, r.request_type, r.action, r.intent, r.mode, r.provider, r.model, r.graph_name, " +
                "r.status, r.fallback, r.fallback_reason, r.error_message, r.total_cost_ms, r.started_at, r.finished_at, r.created_at, " +
                "s.project_id, s.route_code, s.year, s.section_tier, s.context_scope, s.object_type, s.object_id, " +
                "(select count(1) from ai_execution_tool_call t where t.tenant_id=r.tenant_id and t.trace_id=r.trace_id and t.deleted=false) as tool_total_count, " +
                "(select count(1) from ai_execution_tool_call t where t.tenant_id=r.tenant_id and t.trace_id=r.trace_id and t.deleted=false and upper(coalesce(t.status,''))='SUCCESS') as tool_success_count, " +
                "(select count(1) from ai_execution_evidence e where e.tenant_id=r.tenant_id and e.trace_id=r.trace_id and e.deleted=false) as source_count " +
                "from ai_execution_run r left join ai_execution_scope s on s.tenant_id=r.tenant_id and s.trace_id=r.trace_id and s.deleted=false " +
                "where r.tenant_id=:tenantId and r.deleted=false " +
                "and (:status='' or upper(r.status)=upper(:status)) " +
                "and (:keyword='' or lower(coalesce(r.trace_id,'')) like :keyword or lower(coalesce(r.action,'')) like :keyword or lower(coalesce(r.intent,'')) like :keyword or lower(cast(r.raw_request as text)) like :keyword) " +
                "and (:projectId='' or s.project_id=:projectId) " +
                "and (:routeCode='' or s.route_code=:routeCode) " +
                "and (:objectType='' or upper(s.object_type)=upper(:objectType)) " +
                "and (:action='' or upper(r.action)=upper(:action)) " +
                "and (:intent='' or upper(r.intent)=upper(:intent)) " +
                "and (:fallbackSet=false or r.fallback=:fallbackBool) " +
                "and (:toolName='' or exists (select 1 from ai_execution_tool_call t where t.tenant_id=r.tenant_id and t.trace_id=r.trace_id and t.deleted=false and t.tool_name=:toolName)) " +
                "and (:hasBusinessEvidence='' or (:hasBusinessEvidence='true' and exists (select 1 from ai_execution_evidence e where e.tenant_id=r.tenant_id and e.trace_id=r.trace_id and e.deleted=false and upper(coalesce(e.source_type,'')) in ('BUSINESS','GIS','TOOL'))) or (:hasBusinessEvidence='false' and not exists (select 1 from ai_execution_evidence e where e.tenant_id=r.tenant_id and e.trace_id=r.trace_id and e.deleted=false and upper(coalesce(e.source_type,'')) in ('BUSINESS','GIS','TOOL')))) " +
                "and (:hasAnswerMeta='' or (:hasAnswerMeta='true' and exists (select 1 from ai_execution_answer a where a.tenant_id=r.tenant_id and a.trace_id=r.trace_id and a.deleted=false and a.answer_meta <> cast('{}' as jsonb))) or (:hasAnswerMeta='false' and not exists (select 1 from ai_execution_answer a where a.tenant_id=r.tenant_id and a.trace_id=r.trace_id and a.deleted=false and a.answer_meta <> cast('{}' as jsonb)))) " +
                "order by r.created_at desc limit :limit";
    }

    private MapSqlParameterSource listParams(Map<String, Object> query) {
        int limit = intValue(query.get("limit"), 50);
        if (limit <= 0) limit = 50;
        if (limit > 200) limit = 200;
        String keyword = safe(query.get("keyword")).toLowerCase(Locale.ROOT);
        return new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("status", safe(query.get("status")))
                .addValue("keyword", keyword.length() == 0 ? "" : "%" + keyword + "%")
                .addValue("projectId", safe(query.get("projectId")))
                .addValue("routeCode", safe(query.get("routeCode")))
                .addValue("objectType", safe(query.get("objectType")))
                .addValue("action", safe(query.get("action")))
                .addValue("intent", safe(query.get("intent")))
                .addValue("fallbackSet", safe(query.get("fallback")).length() > 0)
                .addValue("fallbackBool", booleanValue(query.get("fallback")))
                .addValue("toolName", safe(query.get("toolName")))
                .addValue("hasBusinessEvidence", safe(query.get("hasBusinessEvidence")).toLowerCase(Locale.ROOT))
                .addValue("hasAnswerMeta", safe(query.get("hasAnswerMeta")).toLowerCase(Locale.ROOT))
                .addValue("limit", limit);
    }

    private Map<String, Object> toListItem(Map<String, Object> row, boolean legacy) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.get("id"));
        item.put("tenantId", first(row, "tenant_id", "tenantId"));
        item.put("traceId", first(row, "trace_id", "traceId"));
        item.put("requestType", first(row, "request_type", "requestType"));
        item.put("messagePreview", first(row, "user_message", "userMessage", "messagePreview"));
        item.put("action", row.get("action"));
        item.put("intent", row.get("intent"));
        item.put("mode", row.get("mode"));
        item.put("provider", row.get("provider"));
        item.put("model", row.get("model"));
        item.put("graphName", first(row, "graph_name", "graphName"));
        item.put("status", row.get("status"));
        item.put("fallback", first(row, "fallback", "fallbackLike"));
        item.put("fallbackReason", first(row, "fallback_reason", "fallbackReason"));
        item.put("errorMessage", first(row, "error_message", "errorMessage"));
        item.put("totalCostMs", first(row, "total_cost_ms", "costMs", "totalCostMs"));
        item.put("createdAt", first(row, "created_at", "createdAt"));
        item.put("businessScope", scopeFromRow(row));
        item.put("toolTotalCount", intValue(first(row, "tool_total_count", "toolTotalCount"), 0));
        item.put("toolSuccessCount", intValue(first(row, "tool_success_count", "toolSuccessCount"), 0));
        item.put("toolFailedCount", Math.max(0, intValue(first(row, "tool_total_count", "toolTotalCount"), 0) - intValue(first(row, "tool_success_count", "toolSuccessCount"), 0)));
        item.put("sourceCount", intValue(first(row, "source_count", "sourceCount"), 0));
        item.put("legacy", legacy);
        if (legacy) {
            item.put("diagnosticWarning", "旧 trace 记录，诊断信息有限。");
        }
        return item;
    }

    private Map<String, Object> toDetail(Map<String, Object> row) {
        Map<String, Object> detail = toListItem(row, false);
        detail.put("rawRequest", parseJson(row.get("raw_request")));
        detail.put("rawResponse", parseJson(row.get("raw_response")));
        Map<String, Object> answer = answer(new MapSqlParameterSource()
                .addValue("tenantId", row.get("tenant_id"))
                .addValue("traceId", row.get("trace_id")));
        detail.putAll(answer);
        return detail;
    }

    private Map<String, Object> scopeFromRow(Map<String, Object> row) {
        Map<String, Object> scope = new LinkedHashMap<>();
        put(scope, "projectId", row.get("project_id"));
        put(scope, "routeCode", row.get("route_code"));
        put(scope, "year", row.get("year"));
        put(scope, "sectionTier", row.get("section_tier"));
        put(scope, "contextScope", row.get("context_scope"));
        put(scope, "objectType", row.get("object_type"));
        put(scope, "objectId", row.get("object_id"));
        put(scope, "assessmentObjectType", row.get("assessment_object_type"));
        put(scope, "direction", row.get("direction"));
        put(scope, "startStake", row.get("start_stake"));
        put(scope, "endStake", row.get("end_stake"));
        put(scope, "bbox", parseJson(row.get("bbox")));
        put(scope, "geometryType", row.get("geometry_type"));
        put(scope, "selectedLayers", parseJson(row.get("selected_layers")));
        put(scope, "scopeWarnings", parseJson(row.get("scope_warnings")));
        return scope;
    }

    private List<Map<String, Object>> steps(MapSqlParameterSource p) {
        try {
            List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                    "select step_name, step_label, status, cost_ms, started_at, finished_at, error_message, step_data, created_at " +
                            "from ai_execution_step where tenant_id=:tenantId and trace_id=:traceId and deleted=false order by created_at asc, id asc", p);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", row.get("step_name"));
                item.put("label", row.get("step_label"));
                item.put("status", row.get("status"));
                item.put("costMs", row.get("cost_ms"));
                item.put("startedAt", row.get("started_at"));
                item.put("finishedAt", row.get("finished_at"));
                item.put("errorMessage", row.get("error_message"));
                item.put("data", parseJson(row.get("step_data")));
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> toolCalls(MapSqlParameterSource p, String executionId) {
        try {
            List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                    "select tool_name, status, cost_ms, total_count, returned_count, truncated, query_scope, result_summary, raw_args, raw_result, error_message, created_at " +
                            "from ai_execution_tool_call where tenant_id=:tenantId and trace_id=:traceId and deleted=false order by created_at asc, id asc", p);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("toolName", row.get("tool_name"));
                item.put("name", row.get("tool_name"));
                item.put("success", "SUCCESS".equalsIgnoreCase(safe(row.get("status"))));
                item.put("status", row.get("status"));
                item.put("costMs", row.get("cost_ms"));
                item.put("totalCount", row.get("total_count"));
                item.put("returnedCount", row.get("returned_count"));
                item.put("count", row.get("returned_count"));
                item.put("truncated", row.get("truncated"));
                item.put("queryScope", parseJson(row.get("query_scope")));
                item.put("summary", parseJson(row.get("result_summary")));
                item.put("rawArgs", parseJson(row.get("raw_args")));
                item.put("rawResult", parseJson(row.get("raw_result")));
                item.put("errorMessage", row.get("error_message"));
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> evidence(MapSqlParameterSource p) {
        try {
            List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                    "select source_type, source_name, title, route_code, object_type, object_id, score, payload, created_at " +
                            "from ai_execution_evidence where tenant_id=:tenantId and trace_id=:traceId and deleted=false order by created_at asc, id asc", p);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("sourceType", row.get("source_type"));
                item.put("sourceName", row.get("source_name"));
                item.put("title", row.get("title"));
                item.put("routeCode", row.get("route_code"));
                item.put("objectType", row.get("object_type"));
                item.put("objectId", row.get("object_id"));
                item.put("score", row.get("score"));
                item.put("payload", parseJson(row.get("payload")));
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Map<String, Object> answer(MapSqlParameterSource p) {
        try {
            List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                    "select used_model, answer_text, answer_meta, quality_flags from ai_execution_answer where tenant_id=:tenantId and trace_id=:traceId and deleted=false order by created_at desc limit 1", p);
            if (rows.isEmpty()) return new LinkedHashMap<>();
            Map<String, Object> row = rows.get(0);
            Map<String, Object> answer = new LinkedHashMap<>();
            answer.put("answer", row.get("answer_text"));
            answer.put("answerMeta", parseJson(row.get("answer_meta")));
            answer.put("quality", parseJson(row.get("quality_flags")));
            if (row.get("used_model") != null) {
                answer.put("model", row.get("used_model"));
            }
            return answer;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> evidenceSummary(List<Map<String, Object>> sources, List<Map<String, Object>> tools) {
        int business = 0;
        int knowledge = 0;
        int outline = 0;
        for (Map<String, Object> source : sources == null ? Collections.<Map<String, Object>>emptyList() : sources) {
            String type = safe(source.get("sourceType")).toUpperCase(Locale.ROOT);
            if ("BUSINESS".equals(type) || "GIS".equals(type) || "TOOL".equals(type)) business++;
            if ("KNOWLEDGE".equals(type)) knowledge++;
            if ("OUTLINE".equals(type)) outline++;
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("sourceCount", sources == null ? 0 : sources.size());
        evidence.put("businessHitCount", business);
        evidence.put("knowledgeHitCount", knowledge);
        evidence.put("outlineHitCount", outline);
        evidence.put("toolTotalCount", tools == null ? 0 : tools.size());
        return evidence;
    }

    static void refreshDetailSummaryCounts(Map<String, Object> detail, List<Map<String, Object>> tools, List<Map<String, Object>> sources) {
        int total = tools == null ? 0 : tools.size();
        int success = 0;
        for (Map<String, Object> tool : tools == null ? Collections.<Map<String, Object>>emptyList() : tools) {
            Object explicitSuccess = tool.get("success");
            String status = safeString(tool.get("status"));
            if (Boolean.TRUE.equals(explicitSuccess) || ("".equals(status) && !Boolean.FALSE.equals(explicitSuccess)) || "SUCCESS".equalsIgnoreCase(status)) {
                success++;
            }
        }
        detail.put("toolTotalCount", total);
        detail.put("toolSuccessCount", success);
        detail.put("toolFailedCount", Math.max(0, total - success));
        detail.put("sourceCount", sources == null ? 0 : sources.size());
    }

    private boolean hasExecutionOnlyFilter(Map<String, Object> query) {
        return notBlank(query.get("projectId")) || notBlank(query.get("routeCode")) || notBlank(query.get("objectType")) ||
                notBlank(query.get("action")) || notBlank(query.get("intent")) || notBlank(query.get("toolName")) ||
                notBlank(query.get("fallback")) || notBlank(query.get("hasBusinessEvidence")) || notBlank(query.get("hasAnswerMeta"));
    }

    private Object parseJson(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Map || raw instanceof List || raw instanceof Number || raw instanceof Boolean) return raw;
        String text = String.valueOf(raw).trim();
        if (text.length() == 0) return null;
        if ("null".equalsIgnoreCase(text)) return null;
        try {
            return objectMapper.readValue(text, Object.class);
        } catch (Exception e) {
            return raw;
        }
    }

    private Object first(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null) return value;
        }
        return null;
    }

    private void put(Map<String, Object> data, String key, Object value) {
        Object parsed = value instanceof String && (((String) value).startsWith("{") || ((String) value).startsWith("[")) ? parseJson(value) : value;
        if (parsed == null) return;
        if (parsed instanceof String && ((String) parsed).trim().length() == 0) return;
        data.put(key, parsed);
    }

    private boolean notBlank(Object value) {
        return safe(value).length() > 0;
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String safeString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int intValue(Object value, int fallback) {
        try {
            if (value == null || String.valueOf(value).trim().length() == 0) return fallback;
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private Boolean booleanValue(Object value) {
        String text = safe(value).toLowerCase(Locale.ROOT);
        if (text.length() == 0) return null;
        return "true".equals(text) || "1".equals(text) || "yes".equals(text);
    }
}
