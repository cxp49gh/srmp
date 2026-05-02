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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AiKnowledgeReindexServiceImpl implements AiKnowledgeReindexService {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 2000;
    private static final int MAX_ERROR_MESSAGES = 20;

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Resource
    private EmbeddingClient embeddingClient;

    @Resource
    private EmbeddingProperties embeddingProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiKnowledgeReindexResponse reindex(AiKnowledgeReindexRequest request) {
        long start = System.currentTimeMillis();
        AiKnowledgeReindexRequest req = request == null ? new AiKnowledgeReindexRequest() : request;
        String tenantId = safe(req.getTenantId()).length() > 0 ? req.getTenantId().trim() : TenantContextHolder.getTenantId();
        boolean force = Boolean.TRUE.equals(req.getForce());
        boolean dryRun = Boolean.TRUE.equals(req.getDryRun());

        AiKnowledgeReindexResponse response = new AiKnowledgeReindexResponse();
        response.setTenantId(tenantId);
        response.setForce(force);
        response.setDryRun(dryRun);
        response.setSourceType(req.getSourceType());
        response.setEmbeddingProvider(embeddingProperties == null ? null : embeddingProperties.getProvider());
        response.setEmbeddingModel(embeddingClient == null ? null : embeddingClient.model());
        response.setEmbeddingDimensions(embeddingClient == null ? null : embeddingClient.dimensions());
        response.setDatabaseVectorDimensions(databaseVectorDimensions());

        if (embeddingClient == null) {
            response.getWarnings().add("EmbeddingClient 未初始化，无法补齐向量");
            response.setCostMs(System.currentTimeMillis() - start);
            return response;
        }

        List<ChunkRow> chunks = queryChunks(req, tenantId, force);
        response.setTotal(chunks.size());
        if (dryRun) {
            response.getWarnings().add("dryRun=true，仅统计候选 chunk，不写入 embedding");
            response.setCostMs(System.currentTimeMillis() - start);
            return response;
        }

        for (ChunkRow chunk : chunks) {
            try {
                if (safe(chunk.getContent()).length() == 0) {
                    response.setSkipped(response.getSkipped() + 1);
                    continue;
                }
                List<Float> embedding = embeddingClient.embed(chunk.getContent());
                validateEmbeddingDimensions(embedding, response.getDatabaseVectorDimensions());
                MapSqlParameterSource p = new MapSqlParameterSource()
                        .addValue("id", chunk.getId())
                        .addValue("tenantId", tenantId)
                        .addValue("embedding", vectorLiteral(embedding))
                        .addValue("embeddingProvider", response.getEmbeddingProvider())
                        .addValue("embeddingModel", response.getEmbeddingModel())
                        .addValue("embeddingDimensions", embedding == null ? null : embedding.size());
                namedParameterJdbcTemplate.update(
                        "update ai_knowledge_chunk " +
                                "set embedding=cast(:embedding as vector), " +
                                "embedding_provider=:embeddingProvider, " +
                                "embedding_model=:embeddingModel, " +
                                "embedding_dimensions=:embeddingDimensions, " +
                                "embedded_at=now(), updated_at=now() " +
                                "where tenant_id=:tenantId and id=:id",
                        p
                );
                response.setSuccess(response.getSuccess() + 1);
            } catch (Exception e) {
                response.setFailed(response.getFailed() + 1);
                String msg = chunkLabel(chunk) + ": " + rootCause(e).getMessage();
                appendFailedMessage(response, msg);
                log.warn("[AI-KNOWLEDGE] reindex chunk failed, {}", msg, e);
            }
        }

        response.setCostMs(System.currentTimeMillis() - start);
        return response;
    }

    private List<ChunkRow> queryChunks(AiKnowledgeReindexRequest req, String tenantId, boolean force) {
        StringBuilder sql = new StringBuilder();
        sql.append("select c.id, c.document_id, c.source_type, c.source_id, c.title, c.chunk_index, c.content ")
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

        if (!CollectionUtils.isEmpty(req.getSourceIds())) {
            sql.append("and c.source_id in (:sourceIds) ");
            p.addValue("sourceIds", req.getSourceIds());
        }

        if (!force) {
            sql.append("and c.embedding is null ");
        }

        sql.append("order by c.updated_at desc, c.document_id asc, c.chunk_index asc ");
        sql.append("limit :limit ");
        p.addValue("limit", normalizeLimit(req.getLimit()));

        return namedParameterJdbcTemplate.query(sql.toString(), p, (rs, rowNum) -> mapChunk(rs));
    }

    private ChunkRow mapChunk(ResultSet rs) throws SQLException {
        ChunkRow row = new ChunkRow();
        row.setId(rs.getString("id"));
        row.setDocumentId(rs.getString("document_id"));
        row.setSourceType(rs.getString("source_type"));
        row.setSourceId(rs.getString("source_id"));
        row.setTitle(rs.getString("title"));
        row.setChunkIndex(rs.getInt("chunk_index"));
        row.setContent(rs.getString("content"));
        return row;
    }

    private Integer databaseVectorDimensions() {
        try {
            String type = namedParameterJdbcTemplate.queryForObject(
                    "select format_type(a.atttypid, a.atttypmod) " +
                            "from pg_attribute a " +
                            "where a.attrelid='ai_knowledge_chunk'::regclass " +
                            "and a.attname='embedding' and a.attnum > 0 and not a.attisdropped",
                    new MapSqlParameterSource(),
                    String.class
            );
            if (type == null) {
                return null;
            }
            int start = type.indexOf('(');
            int end = type.indexOf(')', start + 1);
            if (start < 0 || end <= start) {
                return null;
            }
            return Integer.parseInt(type.substring(start + 1, end));
        } catch (Exception ignored) {
            return null;
        }
    }

    private void validateEmbeddingDimensions(List<Float> embedding, Integer databaseVectorDimensions) {
        int actual = embedding == null ? 0 : embedding.size();
        if (actual <= 0) {
            throw new IllegalStateException("Embedding 返回为空，无法写入 ai_knowledge_chunk.embedding");
        }
        int expected = embeddingClient == null ? 0 : embeddingClient.dimensions();
        if (expected > 0 && actual != expected) {
            throw new IllegalStateException("Embedding 返回维度与配置不一致，actual=" + actual + ", configured=" + expected);
        }
        if (databaseVectorDimensions != null && databaseVectorDimensions > 0 && actual != databaseVectorDimensions) {
            throw new IllegalStateException("Embedding 返回维度与数据库 ai_knowledge_chunk.embedding 维度不一致，actual="
                    + actual + ", dbVector=" + databaseVectorDimensions
                    + "。请调整 srmp.ai.embedding.dimensions 或迁移 embedding 字段维度后重试");
        }
    }

    private int normalizeLimit(Integer limit) {
        int value = limit == null ? DEFAULT_LIMIT : limit;
        if (value <= 0) {
            value = DEFAULT_LIMIT;
        }
        return Math.min(value, MAX_LIMIT);
    }

    private void appendFailedMessage(AiKnowledgeReindexResponse response, String msg) {
        if (response.getFailedMessages().size() < MAX_ERROR_MESSAGES) {
            response.getFailedMessages().add(msg);
        }
    }

    private String chunkLabel(ChunkRow chunk) {
        StringBuilder sb = new StringBuilder();
        sb.append(safe(chunk.getTitle()).length() > 0 ? chunk.getTitle() : chunk.getId());
        sb.append("[").append(chunk.getChunkIndex()).append("]");
        if (safe(chunk.getSourceId()).length() > 0) {
            sb.append(" sourceId=").append(chunk.getSourceId());
        }
        return sb.toString();
    }

    private Throwable rootCause(Throwable e) {
        Throwable cur = e;
        while (cur != null && cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur == null ? e : cur;
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
        private String documentId;
        private String sourceType;
        private String sourceId;
        private String title;
        private Integer chunkIndex;
        private String content;
    }
}
