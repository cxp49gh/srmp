package com.smartroad.srmp.agent.knowledge.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.agent.embedding.EmbeddingClient;
import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeSearchRequest;
import com.smartroad.srmp.agent.knowledge.service.AiKnowledgeRetrieverService;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeSearchHit;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeSearchResponse;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Slf4j
@Service
public class AiKnowledgeRetrieverServiceImpl implements AiKnowledgeRetrieverService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Resource
    private EmbeddingClient embeddingClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AiKnowledgeSearchResponse search(AiKnowledgeSearchRequest request) {
        AiKnowledgeSearchResponse response = new AiKnowledgeSearchResponse();
        String query = request == null ? "" : safe(request.getQuery());
        response.setQuery(query);
        if (query.length() == 0) {
            return response;
        }
        int topK = request.getTopK() == null ? 5 : request.getTopK();
        if (topK <= 0) {
            topK = 5;
        }
        if (topK > 20) {
            topK = 20;
        }
        try {
            response.setHits(vectorSearch(request, query, topK));
            return response;
        } catch (Exception e) {
            log.warn("[AI-KNOWLEDGE] vector search fallback to keyword, error={}", e.getMessage());
            response.setHits(keywordSearch(request, query, topK));
            return response;
        }
    }

    private List<AiKnowledgeSearchHit> vectorSearch(AiKnowledgeSearchRequest request, String query, int topK) {
        List<Float> embedding = embeddingClient.embed(query);
        MapSqlParameterSource p = baseParams(request, topK);
        p.addValue("embedding", vectorLiteral(embedding));
        String sql = baseSelect() +
                " where c.tenant_id=:tenantId and d.status='ACTIVE' " + sourceTypeFilter(request) +
                " order by c.embedding <=> cast(:embedding as vector) limit :limit";
        return namedParameterJdbcTemplate.query(sql, p, (rs, rowNum) -> mapHit(rs, true));
    }

    private List<AiKnowledgeSearchHit> keywordSearch(AiKnowledgeSearchRequest request, String query, int topK) {
        MapSqlParameterSource p = baseParams(request, topK);
        p.addValue("kw", "%" + query.toLowerCase() + "%");
        String sql = baseSelect() +
                " where c.tenant_id=:tenantId and d.status='ACTIVE' " + sourceTypeFilter(request) +
                " and (lower(c.content) like :kw or lower(coalesce(c.title,'')) like :kw or lower(coalesce(c.section_title,'')) like :kw) " +
                " order by c.updated_at desc limit :limit";
        return namedParameterJdbcTemplate.query(sql, p, (rs, rowNum) -> mapHit(rs, false));
    }

    private String baseSelect() {
        return "select c.id chunk_id, c.document_id, c.title, c.section_title, c.source_type, c.source_id, c.content, c.metadata, " +
                "case when c.embedding is null then null else 1.0 end as vector_score " +
                "from ai_knowledge_chunk c join ai_knowledge_document d on d.id=c.document_id and d.tenant_id=c.tenant_id ";
    }

    private String sourceTypeFilter(AiKnowledgeSearchRequest request) {
        List<String> sourceTypes = sourceTypes(request);
        return sourceTypes.isEmpty() ? "" : " and c.source_type in (:sourceTypes) ";
    }

    private MapSqlParameterSource baseParams(AiKnowledgeSearchRequest request, int topK) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("tenantId", tenantId(request));
        p.addValue("limit", topK);
        List<String> sourceTypes = sourceTypes(request);
        if (!sourceTypes.isEmpty()) {
            p.addValue("sourceTypes", sourceTypes);
        }
        return p;
    }

    private String tenantId(AiKnowledgeSearchRequest request) {
        String tenantId = request == null ? null : request.getTenantId();
        return safe(tenantId).length() > 0 ? tenantId.trim() : TenantContextHolder.getTenantId();
    }

    private List<String> sourceTypes(AiKnowledgeSearchRequest request) {
        if (request == null) {
            return Collections.emptyList();
        }
        if (request.getSourceTypes() != null && !request.getSourceTypes().isEmpty()) {
            return request.getSourceTypes();
        }
        Object value = request.getFilters() == null ? null : request.getFilters().get("sourceType");
        if (value instanceof Collection) {
            List<String> result = new ArrayList<>();
            for (Object item : (Collection<?>) value) {
                if (item != null && safe(String.valueOf(item)).length() > 0) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        if (value != null && safe(String.valueOf(value)).length() > 0) {
            return Collections.singletonList(String.valueOf(value));
        }
        return Collections.emptyList();
    }

    private AiKnowledgeSearchHit mapHit(ResultSet rs, boolean vector) throws SQLException {
        AiKnowledgeSearchHit hit = new AiKnowledgeSearchHit();
        hit.setChunkId(rs.getString("chunk_id"));
        hit.setDocumentId(rs.getString("document_id"));
        hit.setTitle(rs.getString("title"));
        hit.setSectionTitle(rs.getString("section_title"));
        hit.setSourceType(rs.getString("source_type"));
        hit.setSourceId(rs.getString("source_id"));
        hit.setContent(rs.getString("content"));
        hit.setScore(vector ? 1.0d : 0.5d);
        hit.setMetadata(parseJson(rs.getString("metadata")));
        return hit;
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String vectorLiteral(List<Float> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(values.get(i));
        }
        sb.append(']');
        return sb.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
