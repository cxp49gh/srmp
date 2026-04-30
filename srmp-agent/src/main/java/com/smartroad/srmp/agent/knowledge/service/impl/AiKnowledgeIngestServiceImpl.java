package com.smartroad.srmp.agent.knowledge.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.agent.embedding.EmbeddingClient;
import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeIngestMarkdownRequest;
import com.smartroad.srmp.agent.knowledge.service.AiKnowledgeIngestService;
import com.smartroad.srmp.agent.knowledge.splitter.TextChunkSplitter;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> ingestMarkdown(AiKnowledgeIngestMarkdownRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("入库请求不能为空");
        }
        String content = safe(request.getContent());
        if (content.length() == 0) {
            throw new IllegalArgumentException("知识内容不能为空");
        }
        String tenantId = safe(firstNonBlank(request.getTenantId(), TenantContextHolder.getTenantId()));
        String documentId = uuid();
        String title = defaultString(request.getTitle(), "未命名知识文档");
        String sourceType = defaultString(request.getSourceType(), "MANUAL");
        String sourceId = request.getSourceId();
        String metadataJson = toJson(request.getMetadata() == null ? new LinkedHashMap<String, Object>() : request.getMetadata());

        MapSqlParameterSource doc = new MapSqlParameterSource()
                .addValue("id", documentId)
                .addValue("tenantId", tenantId)
                .addValue("sourceType", sourceType)
                .addValue("sourceId", sourceId)
                .addValue("title", title)
                .addValue("status", "ACTIVE")
                .addValue("metadata", metadataJson)
                .addValue("contentHash", DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8)));

        namedParameterJdbcTemplate.update(
                "insert into ai_knowledge_document(id, tenant_id, source_type, source_id, title, status, metadata, content_hash, created_at, updated_at) " +
                        "values(:id,:tenantId,:sourceType,:sourceId,:title,:status,cast(:metadata as jsonb),:contentHash,now(),now())",
                doc
        );

        List<String> chunks = textChunkSplitter.split(content);
        if (chunks == null || chunks.isEmpty()) {
            chunks = Collections.singletonList(content);
        }
        int index = 0;
        for (String chunk : chunks) {
            String chunkText = safe(chunk);
            if (chunkText.length() == 0) {
                continue;
            }
            List<Float> embedding = embeddingClient.embed(chunkText);
            MapSqlParameterSource p = new MapSqlParameterSource()
                    .addValue("id", uuid())
                    .addValue("tenantId", tenantId)
                    .addValue("documentId", documentId)
                    .addValue("sourceType", sourceType)
                    .addValue("sourceId", sourceId)
                    .addValue("title", title)
                    .addValue("sectionTitle", extractHeading(chunkText))
                    .addValue("chunkIndex", index++)
                    .addValue("content", chunkText)
                    .addValue("metadata", metadataJson)
                    .addValue("embedding", vectorLiteral(embedding))
                    .addValue("embeddingModel", embeddingClient.model());
            namedParameterJdbcTemplate.update(
                    "insert into ai_knowledge_chunk(id, tenant_id, document_id, source_type, source_id, title, section_title, chunk_index, content, metadata, embedding, embedding_model, created_at, updated_at) " +
                            "values(:id,:tenantId,:documentId,:sourceType,:sourceId,:title,:sectionTitle,:chunkIndex,:content,cast(:metadata as jsonb),cast(:embedding as vector),:embeddingModel,now(),now())",
                    p
            );
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", documentId);
        result.put("chunkCount", index);
        result.put("embeddingModel", embeddingClient.model());
        result.put("embeddingDimensions", embeddingClient.dimensions());
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
}
