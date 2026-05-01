package com.smartroad.srmp.agent.knowledge.service.impl;

import com.smartroad.srmp.agent.embedding.EmbeddingClient;
import com.smartroad.srmp.agent.embedding.EmbeddingProperties;
import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeReindexRequest;
import com.smartroad.srmp.agent.knowledge.service.AiKnowledgeReindexService;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeReindexResponse;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AiKnowledgeReindexServiceImpl implements AiKnowledgeReindexService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Resource
    private EmbeddingClient embeddingClient;

    @Resource
    private EmbeddingProperties embeddingProperties;

    @Override
    public AiKnowledgeReindexResponse reindex(AiKnowledgeReindexRequest request) {
        long start = System.currentTimeMillis();
        AiKnowledgeReindexRequest req = request == null ? new AiKnowledgeReindexRequest() : request;
        String tenantId = safe(req.getTenantId()).length() > 0 ? req.getTenantId().trim() : TenantContextHolder.getTenantId();
        boolean force = Boolean.TRUE.equals(req.getForce());

        AiKnowledgeReindexResponse response = new AiKnowledgeReindexResponse();
        response.setTenantId(tenantId);
        response.setForce(force);
        response.setSourceType(req.getSourceType());
        response.setEmbeddingProvider(embeddingProperties == null ? null : embeddingProperties.getProvider());
        response.setEmbeddingModel(embeddingClient == null ? null : embeddingClient.model());
        response.setEmbeddingDimensions(embeddingClient == null ? null : embeddingClient.dimensions());

        List<ChunkRow> chunks = queryChunks(req, tenantId, force);
        response.setTotal(chunks.size());

        for (ChunkRow chunk : chunks) {
            try {
                List<Float> embedding = embeddingClient.embed(chunk.getContent());
                MapSqlParameterSource p = new MapSqlParameterSource()
                        .addValue("id", chunk.getId())
                        .addValue("embedding", vectorLiteral(embedding))
                        .addValue("embeddingProvider", response.getEmbeddingProvider())
                        .addValue("embeddingModel", response.getEmbeddingModel())
                        .addValue("embeddingDimensions", response.getEmbeddingDimensions());
                namedParameterJdbcTemplate.update(
                        "update ai_knowledge_chunk " +
                                "set embedding=cast(:embedding as vector), " +
                                "embedding_provider=:embeddingProvider, " +
                                "embedding_model=:embeddingModel, " +
                                "embedding_dimensions=:embeddingDimensions, " +
                                "embedded_at=now(), updated_at=now() " +
                                "where id=:id",
                        p
                );
                response.setSuccess(response.getSuccess() + 1);
            } catch (Exception e) {
                response.setFailed(response.getFailed() + 1);
                String msg = chunk.getId() + ": " + e.getMessage();
                response.getFailedMessages().add(msg);
                log.warn("[AI-KNOWLEDGE] reindex chunk failed, {}", msg, e);
            }
        }

        response.setCostMs(System.currentTimeMillis() - start);
        return response;
    }

    private List<ChunkRow> queryChunks(AiKnowledgeReindexRequest req, String tenantId, boolean force) {
        StringBuilder sql = new StringBuilder();
        sql.append("select c.id, c.content ")
                .append("from ai_knowledge_chunk c join ai_knowledge_document d on d.id=c.document_id and d.tenant_id=c.tenant_id ")
                .append("where c.tenant_id=:tenantId and d.status='ACTIVE' ");

        MapSqlParameterSource p = new MapSqlParameterSource().addValue("tenantId", tenantId);

        if (safe(req.getSourceType()).length() > 0) {
            sql.append("and c.source_type=:sourceType ");
            p.addValue("sourceType", req.getSourceType().trim());
        }

        if (!CollectionUtils.isEmpty(req.getDocumentIds())) {
            sql.append("and c.document_id in (:documentIds) ");
            p.addValue("documentIds", req.getDocumentIds());
        }

        if (!force) {
            sql.append("and c.embedding is null ");
        }

        sql.append("order by c.updated_at desc, c.id asc ");

        if (req.getLimit() != null && req.getLimit() > 0) {
            sql.append("limit :limit ");
            p.addValue("limit", req.getLimit());
        }

        return namedParameterJdbcTemplate.query(sql.toString(), p, (rs, rowNum) -> mapChunk(rs));
    }

    private ChunkRow mapChunk(ResultSet rs) throws SQLException {
        ChunkRow row = new ChunkRow();
        row.setId(rs.getString("id"));
        row.setContent(rs.getString("content"));
        return row;
    }

    private String vectorLiteral(List<Float> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values.get(i));
        }
        sb.append(']');
        return sb.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    @Data
    private static class ChunkRow {
        private String id;
        private String content;
    }
}
