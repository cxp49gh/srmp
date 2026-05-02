package com.smartroad.srmp.agent.knowledge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.agent.embedding.EmbeddingClient;
import com.smartroad.srmp.agent.embedding.EmbeddingProperties;
import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeIngestMarkdownRequest;
import com.smartroad.srmp.agent.knowledge.service.AiKnowledgeIngestService;
import com.smartroad.srmp.agent.knowledge.splitter.TextChunkSplitter;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class AiKnowledgeIngestServiceImpl implements AiKnowledgeIngestService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Resource
    private TextChunkSplitter textChunkSplitter;

    @Resource
    private EmbeddingClient embeddingClient;

    @Resource
    private EmbeddingProperties embeddingProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestMarkdown(AiKnowledgeIngestMarkdownRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("入库请求不能为空");
        }
        String content = safe(request.getContent());
        if (content.length() == 0) {
            throw new IllegalArgumentException("知识内容不能为空");
        }
        String tenantId = safe(firstNonBlank(request.getTenantId(), TenantContextHolder.getTenantId()));
        String title = defaultString(request.getTitle(), "未命名知识文档");
        String sourceType = defaultString(request.getSourceType(), "MANUAL");
        String sourceId = safe(request.getSourceId());
        boolean force = Boolean.TRUE.equals(request.getForce());

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (request.getMetadata() != null) {
            metadata.putAll(request.getMetadata());
        }
        if (safe(request.getUrl()).length() > 0) {
            metadata.put("url", request.getUrl());
            metadata.put("sourceUrl", request.getUrl());
        }
        String metadataJson = toJson(metadata);
        String contentHash = DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));

        ExistingDocument existingDocument = findExistingDocument(tenantId, sourceType, sourceId);
        if (existingDocument != null && !force && contentHash.equals(existingDocument.getContentHash())) {
            Map<String, Object> skipped = baseResult(existingDocument.getId(), sourceType, sourceId, contentHash);
            skipped.put("chunkCount", 0);
            skipped.put("skipped", true);
            skipped.put("skipReason", "内容未变化，跳过重建向量");
            skipped.put("embeddedChunkCount", 0);
            skipped.put("embeddingFailedCount", 0);
            skipped.put("embeddingErrors", Collections.emptyList());
            skipped.put("embeddingModel", embeddingClient == null ? null : embeddingClient.model());
            skipped.put("embeddingDimensions", embeddingClient == null ? null : embeddingClient.dimensions());
            return skipped;
        }

        String documentId = existingDocument == null ? uuid() : existingDocument.getId();
        if (existingDocument == null) {
            insertDocument(documentId, tenantId, sourceType, sourceId, title, metadataJson, contentHash);
        } else {
            updateDocument(documentId, tenantId, title, metadataJson, contentHash);
            namedParameterJdbcTemplate.update(
                    "delete from ai_knowledge_chunk where tenant_id=:tenantId and document_id=:documentId",
                    new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("documentId", documentId)
            );
        }

        boolean vectorize = request.getVectorize() == null || Boolean.TRUE.equals(request.getVectorize());
        boolean failOnEmbeddingError = request.getFailOnEmbeddingError() == null || Boolean.TRUE.equals(request.getFailOnEmbeddingError());
        IngestChunkStats chunkStats = insertChunks(tenantId, documentId, sourceType, sourceId, title, content, metadataJson, vectorize, failOnEmbeddingError);

        Map<String, Object> result = baseResult(documentId, sourceType, sourceId, contentHash);
        result.put("chunkCount", chunkStats.totalCount);
        result.put("skipped", false);
        result.put("updated", existingDocument != null);
        result.put("embeddedChunkCount", chunkStats.embeddedCount);
        result.put("embeddingFailedCount", chunkStats.failedCount);
        result.put("embeddingErrors", chunkStats.errors);
        result.put("embeddingModel", embeddingClient == null ? null : embeddingClient.model());
        result.put("embeddingDimensions", embeddingClient == null ? null : embeddingClient.dimensions());
        return result;
    }

    private ExistingDocument findExistingDocument(String tenantId, String sourceType, String sourceId) {
        if (safe(sourceId).length() == 0) {
            return null;
        }
        List<ExistingDocument> list = namedParameterJdbcTemplate.query(
                "select id, content_hash from ai_knowledge_document " +
                        "where tenant_id=:tenantId and source_type=:sourceType and source_id=:sourceId and status='ACTIVE' " +
                        "order by updated_at desc limit 1",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("sourceType", sourceType)
                        .addValue("sourceId", sourceId),
                (rs, rowNum) -> {
                    ExistingDocument row = new ExistingDocument();
                    row.setId(rs.getString("id"));
                    row.setContentHash(rs.getString("content_hash"));
                    return row;
                }
        );
        return list.isEmpty() ? null : list.get(0);
    }

    private void insertDocument(String documentId, String tenantId, String sourceType, String sourceId, String title, String metadataJson, String contentHash) {
        MapSqlParameterSource doc = new MapSqlParameterSource()
                .addValue("id", documentId)
                .addValue("tenantId", tenantId)
                .addValue("sourceType", sourceType)
                .addValue("sourceId", sourceId)
                .addValue("title", title)
                .addValue("status", "ACTIVE")
                .addValue("metadata", metadataJson)
                .addValue("contentHash", contentHash);

        namedParameterJdbcTemplate.update(
                "insert into ai_knowledge_document(id, tenant_id, source_type, source_id, title, status, metadata, content_hash, created_at, updated_at) " +
                        "values(:id,:tenantId,:sourceType,:sourceId,:title,:status,cast(:metadata as jsonb),:contentHash,now(),now())",
                doc
        );
    }

    private void updateDocument(String documentId, String tenantId, String title, String metadataJson, String contentHash) {
        namedParameterJdbcTemplate.update(
                "update ai_knowledge_document " +
                        "set title=:title, metadata=cast(:metadata as jsonb), content_hash=:contentHash, status='ACTIVE', updated_at=now() " +
                        "where tenant_id=:tenantId and id=:id",
                new MapSqlParameterSource()
                        .addValue("id", documentId)
                        .addValue("tenantId", tenantId)
                        .addValue("title", title)
                        .addValue("metadata", metadataJson)
                        .addValue("contentHash", contentHash)
        );
    }

    
    private IngestChunkStats insertChunks(String tenantId, String documentId, String sourceType, String sourceId, String title, String content, String metadataJson, boolean vectorize, boolean failOnEmbeddingError) {
        List<String> chunks = textChunkSplitter.split(content);
        if (chunks == null || chunks.isEmpty()) {
            chunks = Collections.singletonList(content);
        }
        int total = 0, embedded = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        for (String chunk : chunks) {
            String chunkText = safe(chunk);
            if (chunkText.length() == 0) continue;
            String chunkHeading = extractHeading(chunkText);
            total++;
            try {
                List<Float> embedding = null;
                if (vectorize) {
                    embedding = embeddingClient.embed(chunkText);
                }
                MapSqlParameterSource p = new MapSqlParameterSource()
                        .addValue("id", uuid())
                        .addValue("tenantId", tenantId)
                        .addValue("documentId", documentId)
                        .addValue("sourceType", sourceType)
                        .addValue("sourceId", sourceId)
                        .addValue("title", title)
                        .addValue("sectionTitle", chunkHeading)
                        .addValue("chunkIndex", total - 1)
                        .addValue("content", chunkText)
                        .addValue("metadata", metadataJson)
                        .addValue("embedding", embedding != null ? vectorLiteral(embedding) : null)
                        .addValue("embeddingProvider", embedding != null ? (embeddingProperties == null ? null : embeddingProperties.getProvider()) : null)
                        .addValue("embeddingModel", embedding != null ? embeddingClient.model() : null)
                        .addValue("embeddingDimensions", embedding != null ? embeddingClient.dimensions() : null);
                namedParameterJdbcTemplate.update(
                        "insert into ai_knowledge_chunk(id, tenant_id, document_id, source_type, source_id, title, section_title, chunk_index, content, metadata, embedding, embedding_provider, embedding_model, embedding_dimensions, embedded_at, created_at, updated_at) " +
                                "values(:id,:tenantId,:documentId,:sourceType,:sourceId,:title,:sectionTitle,:chunkIndex,:content,cast(:metadata as jsonb),cast(:embedding as vector),:embeddingProvider,:embeddingModel,:embeddingDimensions,now(),now(),now())",
                        p
                );
                if (embedding != null) embedded++;
            } catch (Exception e) {
                failed++;
                String errMsg = "chunk[" + (total - 1) + "] " + e.getMessage();
                errors.add(errMsg);
                if (failOnEmbeddingError) {
                    throw new RuntimeException("向量生成失败: " + errMsg, e);
                }
            }
        }
        return new IngestChunkStats(total, embedded, failed, errors);
    }

    private static class IngestChunkStats {
        final int totalCount;
        final int embeddedCount;
        final int failedCount;
        final List<String> errors;
        IngestChunkStats(int total, int embedded, int failed, List<String> errors) {
            this.totalCount = total; this.embeddedCount = embedded; this.failedCount = failed; this.errors = errors;
        }
    }

    private Map<String, Object> baseResult(String documentId, String sourceType, String sourceId, String contentHash) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", documentId);
        result.put("sourceType", sourceType);
        result.put("sourceId", sourceId);
        result.put("contentHash", contentHash);
        return result;
    }

    private String extractHeading(String chunk) {
        String[] lines = chunk.split("\\n");
        for (String line : lines) {
            String text = line == null ? "" : line.trim();
            if (text.startsWith("#")) {
                return text.replace("#", "").trim();
            }
        }
        return null;
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? new LinkedHashMap<String, Object>() : value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String firstNonBlank(String a, String b) {
        return safe(a).length() > 0 ? a : b;
    }

    private String defaultString(String value, String defaultValue) {
        return safe(value).length() == 0 ? defaultValue : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static class ExistingDocument {
        private String id;
        private String contentHash;
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getContentHash() { return contentHash; }
        public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    }
}
