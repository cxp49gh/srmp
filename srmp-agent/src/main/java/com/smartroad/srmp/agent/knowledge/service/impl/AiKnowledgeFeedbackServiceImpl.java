package com.smartroad.srmp.agent.knowledge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeFeedbackCreateRequest;
import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeFeedbackQuery;
import com.smartroad.srmp.agent.knowledge.service.AiKnowledgeFeedbackService;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AiKnowledgeFeedbackServiceImpl implements AiKnowledgeFeedbackService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public Map<String, Object> create(AiKnowledgeFeedbackCreateRequest request) {
        AiKnowledgeFeedbackCreateRequest req = request == null ? new AiKnowledgeFeedbackCreateRequest() : request;
        String feedbackType = normalizeType(req.getFeedbackType());
        if (feedbackType == null) {
            throw new IllegalArgumentException("feedbackType 必须为 MISSING_KNOWLEDGE 或 SOURCE_INACCURATE");
        }
        String tenantId = TenantContextHolder.getTenantId();
        String id = uuid();
        String userId = safe(req.getUserId());
        String question = safe(req.getQuestion());
        String remark = safe(req.getRemark());
        String businessJson = toJson(req.getBusinessContext());
        String sourcesJson = toJson(req.getCitedSources() == null ? Collections.emptyList() : req.getCitedSources());

        namedParameterJdbcTemplate.update(
                "insert into ai_knowledge_feedback(id, tenant_id, user_id, feedback_type, question, remark, business_context, cited_sources, created_at) " +
                        "values(:id, :tenantId, :userId, :feedbackType, :question, :remark, cast(:businessContext as jsonb), cast(:citedSources as jsonb), now())",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("tenantId", tenantId)
                        .addValue("userId", userId)
                        .addValue("feedbackType", feedbackType)
                        .addValue("question", question)
                        .addValue("remark", remark)
                        .addValue("businessContext", businessJson)
                        .addValue("citedSources", sourcesJson)
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("tenantId", tenantId);
        result.put("feedbackType", feedbackType);
        return result;
    }

    @Override
    public List<Map<String, Object>> list(AiKnowledgeFeedbackQuery query) {
        AiKnowledgeFeedbackQuery q = query == null ? new AiKnowledgeFeedbackQuery() : query;
        int limit = q.getLimit() == null || q.getLimit() <= 0 ? 50 : Math.min(q.getLimit(), 200);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("limit", limit);
        String sql = "select id, tenant_id, user_id, feedback_type, question, remark, business_context, cited_sources, created_at " +
                "from ai_knowledge_feedback where tenant_id=:tenantId ";
        if (notBlank(q.getFeedbackType())) {
            sql += "and feedback_type=:feedbackType ";
            params.addValue("feedbackType", normalizeType(q.getFeedbackType()));
        }
        sql += "order by created_at desc limit :limit";
        return namedParameterJdbcTemplate.queryForList(sql, params);
    }

    private String normalizeType(String type) {
        if (!notBlank(type)) {
            return null;
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if ("MISSING_KNOWLEDGE".equals(normalized) || "SOURCE_INACCURATE".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value == null ? Collections.emptyMap() : value);
        } catch (Exception e) {
            return value instanceof List ? "[]" : "{}";
        }
    }

    private boolean notBlank(String value) {
        return value != null && value.trim().length() > 0;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
