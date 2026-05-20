package com.smartroad.srmp.agent.knowledge.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.agent.embedding.EmbeddingClient;
import com.smartroad.srmp.agent.embedding.EmbeddingProperties;
import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeSearchRequest;
import com.smartroad.srmp.agent.knowledge.service.AiKnowledgeRetrieverService;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeSearchHit;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeSearchResponse;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeStatsResponse;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
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

    @Resource
    private EmbeddingProperties embeddingProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AiKnowledgeSearchResponse search(AiKnowledgeSearchRequest request) {
        AiKnowledgeSearchResponse response = new AiKnowledgeSearchResponse();
        decorateEmbeddingMeta(response);

        String query = request == null ? "" : safe(request.getQuery());
        response.setQuery(query);
        if (query.length() == 0) {
            response.setSearchMode("NO_DATA");
            response.setFallback(true);
            response.setFallbackReason("query is empty");
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
            if (!vectorEnabled()) {
                throw new IllegalStateException("pgvector extension not available");
            }
            String tenantId = tenantId(request);
            long chunkCount = chunkCount(tenantId);
            long embeddedCount = embeddedCount(tenantId);
            String fallbackReason = vectorFallbackReason(chunkCount, embeddedCount);
            if (fallbackReason.length() > 0) {
                throw new IllegalStateException(fallbackReason);
            }
            List<AiKnowledgeSearchHit> hits = vectorSearch(request, query, topK);
            response.setHits(hits);
            response.setSearchMode("VECTOR");
            response.setRetrievalStrategy("VECTOR");
            response.setVectorUsed(true);
            response.setFallback(false);
            response.setRewrittenQuery(query);
            fillHitMeta(response);
            return response;
        } catch (Exception e) {
            log.warn("[AI-KNOWLEDGE] vector search fallback to keyword, error={}", e.getMessage());
            List<AiKnowledgeSearchHit> hits = keywordSearch(request, query, topK);
            response.setHits(hits);
            response.setSearchMode("KEYWORD_FALLBACK");
            response.setRetrievalStrategy("KEYWORD_FALLBACK");
            response.setVectorUsed(false);
            response.setFallback(true);
            response.setFallbackReason(e.getMessage());
            response.setRewrittenQuery(query);
            fillHitMeta(response);
            return response;
        }
    }

    @Override
    public AiKnowledgeStatsResponse stats(String tenantId) {
        String actualTenantId = safe(tenantId).length() > 0 ? tenantId.trim() : TenantContextHolder.getTenantId();
        AiKnowledgeStatsResponse response = new AiKnowledgeStatsResponse();
        response.setEmbeddingProvider(embeddingProperties == null ? null : embeddingProperties.getProvider());
        response.setEmbeddingModel(embeddingClient == null ? null : embeddingClient.model());
        response.setEmbeddingDimensions(embeddingClient == null ? null : embeddingClient.dimensions());
        response.setVectorEnabled(vectorEnabled());

        try {
            MapSqlParameterSource p = new MapSqlParameterSource().addValue("tenantId", actualTenantId);
            response.setDocumentCount(queryLong("select count(*) from ai_knowledge_document where tenant_id=:tenantId", p));
            response.setChunkCount(queryLong("select count(*) from ai_knowledge_chunk where tenant_id=:tenantId", p));
            response.setEmbeddedChunkCount(queryLong("select count(*) from ai_knowledge_chunk where tenant_id=:tenantId and embedding is not null", p));
            response.setSourceTypes(querySourceTypes(actualTenantId));
            response.setChunkEmbeddingProviders(queryChunkEmbeddingProviders(actualTenantId));
            response.setStatus("OK");
            if (response.getChunkCount() <= 0) {
                response.setMessage("暂无知识切片，请先导入知识数据");
            } else if (response.getEmbeddedChunkCount() <= 0) {
                response.setMessage("已有知识切片，但 embedding 为空，向量检索无法生效");
            } else if (!Boolean.TRUE.equals(response.getVectorEnabled())) {
                response.setMessage("已有 embedding，但 pgvector 扩展不可用，将走关键词兜底");
            } else {
                response.setMessage("知识库已就绪，可验证向量检索链路");
            }
        } catch (Exception e) {
            response.setStatus("ERROR");
            response.setMessage(e.getMessage());
        }
        return response;
    }

    private List<AiKnowledgeSearchHit> vectorSearch(AiKnowledgeSearchRequest request, String query, int topK) {
        List<Float> embedding = embeddingClient.embed(query);
        MapSqlParameterSource p = baseParams(request, topK);
        p.addValue("embedding", vectorLiteral(embedding));
        String sql = "select c.id chunk_id, c.document_id, c.title, c.section_title, c.source_type, c.source_id, c.content, c.metadata, " +
                "(c.embedding <=> cast(:embedding as vector)) as distance " +
                "from ai_knowledge_chunk c join ai_knowledge_document d on d.id=c.document_id and d.tenant_id=c.tenant_id " +
                "where c.tenant_id=:tenantId and d.status='ACTIVE' and c.embedding is not null " + sourceTypeFilter(request) +
                " order by c.embedding <=> cast(:embedding as vector) limit :limit";
        return namedParameterJdbcTemplate.query(sql, p, (rs, rowNum) -> mapVectorHit(rs));
    }

    private List<AiKnowledgeSearchHit> keywordSearch(AiKnowledgeSearchRequest request, String query, int topK) {
        MapSqlParameterSource p = baseParams(request, topK);
        p.addValue("kw", "%" + query.toLowerCase() + "%");
        String sql = baseSelect() +
                " where c.tenant_id=:tenantId and d.status='ACTIVE' " + sourceTypeFilter(request) +
                " and (lower(c.content) like :kw or lower(coalesce(c.title,'')) like :kw or lower(coalesce(c.section_title,'')) like :kw) " +
                " order by c.updated_at desc limit :limit";
        return namedParameterJdbcTemplate.query(sql, p, (rs, rowNum) -> mapKeywordHit(rs));
    }

    private String baseSelect() {
        return "select c.id chunk_id, c.document_id, c.title, c.section_title, c.source_type, c.source_id, c.content, c.metadata " +
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

    private AiKnowledgeSearchHit mapVectorHit(ResultSet rs) throws SQLException {
        AiKnowledgeSearchHit hit = mapBaseHit(rs);
        double distance = rs.getDouble("distance");
        if (rs.wasNull()) {
            hit.setScore(0.0d);
        } else {
            hit.setScore(Math.max(0.0d, 1.0d - distance));
        }
        return hit;
    }

    private AiKnowledgeSearchHit mapKeywordHit(ResultSet rs) throws SQLException {
        AiKnowledgeSearchHit hit = mapBaseHit(rs);
        hit.setScore(0.5d);
        return hit;
    }

    private AiKnowledgeSearchHit mapBaseHit(ResultSet rs) throws SQLException {
        AiKnowledgeSearchHit hit = new AiKnowledgeSearchHit();
        hit.setChunkId(rs.getString("chunk_id"));
        hit.setDocumentId(rs.getString("document_id"));
        hit.setTitle(rs.getString("title"));
        hit.setSectionTitle(rs.getString("section_title"));
        hit.setSourceType(rs.getString("source_type"));
        hit.setSourceId(rs.getString("source_id"));
        hit.setContent(rs.getString("content"));
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

    private boolean vectorEnabled() {
        try {
            MapSqlParameterSource p = new MapSqlParameterSource();
            Long count = namedParameterJdbcTemplate.queryForObject(
                    "select count(*) from pg_extension where extname='vector'",
                    p,
                    Long.class
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private long embeddedCount(String tenantId) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("tenantId", tenantId);
        return queryLong("select count(*) from ai_knowledge_chunk where tenant_id=:tenantId and embedding is not null", p);
    }

    private long chunkCount(String tenantId) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("tenantId", tenantId);
        return queryLong("select count(*) from ai_knowledge_chunk where tenant_id=:tenantId", p);
    }

    static String vectorFallbackReason(long chunkCount, long embeddedCount) {
        if (chunkCount <= 0) {
            return "no knowledge chunks";
        }
        if (embeddedCount <= 0) {
            return "no embedded chunks";
        }
        return "";
    }

    private long queryLong(String sql, MapSqlParameterSource p) {
        try {
            Long value = namedParameterJdbcTemplate.queryForObject(sql, p, Long.class);
            return value == null ? 0L : value;
        } catch (DataAccessException e) {
            return 0L;
        }
    }

    private Map<String, Long> querySourceTypes(String tenantId) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("tenantId", tenantId);
        Map<String, Long> result = new LinkedHashMap<>();
        try {
            namedParameterJdbcTemplate.query(
                    "select coalesce(source_type,'UNKNOWN') source_type, count(*) cnt from ai_knowledge_chunk where tenant_id=:tenantId group by coalesce(source_type,'UNKNOWN') order by cnt desc",
                    p,
                    rs -> {
                        result.put(rs.getString("source_type"), rs.getLong("cnt"));
                    }
            );
        } catch (Exception ignored) {
        }
        return result;
    }

    private Map<String, Long> queryChunkEmbeddingProviders(String tenantId) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("tenantId", tenantId);
        Map<String, Long> result = new LinkedHashMap<>();
        try {
            namedParameterJdbcTemplate.query(
                    "select " +
                            "coalesce(embedding_provider,'UNKNOWN') || ':' || " +
                            "coalesce(embedding_model,'UNKNOWN') || ':' || " +
                            "coalesce(embedding_dimensions::text,'UNKNOWN') as provider_key, " +
                            "count(*) cnt " +
                            "from ai_knowledge_chunk " +
                            "where tenant_id=:tenantId and embedding is not null " +
                            "group by provider_key order by cnt desc",
                    p,
                    (org.springframework.jdbc.core.RowCallbackHandler) (rs -> result.put(rs.getString("provider_key"), rs.getLong("cnt")))
            );
        } catch (Exception ignored) {
        }
        return result;
    }

    private void decorateEmbeddingMeta(AiKnowledgeSearchResponse response) {
        response.setEmbeddingProvider(embeddingProperties == null ? null : embeddingProperties.getProvider());
        response.setEmbeddingModel(embeddingClient == null ? null : embeddingClient.model());
        response.setEmbeddingDimensions(embeddingClient == null ? null : embeddingClient.dimensions());
    }

    private void fillHitMeta(AiKnowledgeSearchResponse response) {
        int count = response.getHits() == null ? 0 : response.getHits().size();
        response.setHitCount(count);
        if (count > 0 && response.getHits().get(0) != null) {
            response.setTopScore(response.getHits().get(0).getScore());
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
