package com.smartroad.srmp.agent.outline.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartroad.srmp.agent.knowledge.splitter.TextChunkSplitter;
import com.smartroad.srmp.agent.outline.config.OutlineProperties;
import com.smartroad.srmp.agent.outline.dto.OutlineCollectionDTO;
import com.smartroad.srmp.agent.outline.dto.OutlineDocumentDTO;
import com.smartroad.srmp.agent.outline.dto.OutlineSyncRequest;
import com.smartroad.srmp.agent.outline.service.OutlineSyncService;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.http.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class OutlineSyncServiceImpl implements OutlineSyncService {

    @Resource private OutlineProperties properties;
    @Resource private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Resource private TextChunkSplitter textChunkSplitter;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public List<OutlineCollectionDTO> collections() {
        if (!properties.usable()) return Collections.emptyList();
        JsonNode root = post("/api/collections.list", Collections.singletonMap("limit", 100));
        JsonNode data = root == null ? null : root.path("data");
        List<OutlineCollectionDTO> result = new ArrayList<>();
        if (data != null && data.isArray()) {
            for (JsonNode item : data) {
                OutlineCollectionDTO dto = new OutlineCollectionDTO();
                dto.setId(item.path("id").asText(null));
                dto.setName(item.path("name").asText(""));
                dto.setDescription(item.path("description").asText(""));
                dto.setUrl(item.path("url").asText(null));
                result.add(dto);
            }
        }
        return result;
    }

    @Override
    public List<OutlineDocumentDTO> documents(String collectionId, Integer limit, Integer offset) {
        if (!properties.usable()) return Collections.emptyList();
        int size = normalizeLimit(limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("limit", size);
        body.put("offset", offset == null ? 0 : offset);
        if (notBlank(collectionId)) body.put("collectionId", collectionId);
        JsonNode root = post("/api/documents.list", body);
        JsonNode data = root == null ? null : root.path("data");
        List<OutlineDocumentDTO> result = new ArrayList<>();
        if (data != null && data.isArray()) {
            for (JsonNode item : data) result.add(toDocumentDTO(item));
        }
        return result;
    }

    @Override
    public Map sync(OutlineSyncRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        String taskId = uuid();
        String collectionId = request == null ? null : request.getCollectionId();
        boolean force = request != null && Boolean.TRUE.equals(request.getForce());
        int limit = normalizeLimit(request == null ? null : request.getLimit());

        insertTask(taskId, tenantId, collectionId);

        int total = 0, success = 0, skipped = 0, failed = 0;
        try {
            List<OutlineDocumentDTO> docs = documents(collectionId, limit, 0);
            total = docs.size();
            for (OutlineDocumentDTO doc : docs) {
                try {
                    OutlineDocumentDTO detail = fetchDocumentDetail(doc.getId());
                    if (!notBlank(detail.getCollectionId())) detail.setCollectionId(collectionId);
                    if (upsertOutlineDocument(tenantId, detail, force)) success++; else skipped++;
                } catch (Exception e) {
                    failed++;
                }
            }
            updateTask(taskId, total, success, skipped, failed, "SUCCESS", null);
        } catch (Exception e) {
            updateTask(taskId, total, success, skipped, failed, "FAILED", e.getMessage());
        }
        return task(taskId);
    }

    @Override
    public List<Map<String, Object>> tasks(Integer limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("limit", normalizeTaskLimit(limit));
        return namedParameterJdbcTemplate.queryForList(
                "select id, tenant_id, collection_id, collection_name, sync_mode, status, total_count, success_count, skip_count, fail_count, error_message, started_at, finished_at, created_at " +
                        "from outline_sync_task where tenant_id=:tenantId order by created_at desc limit :limit",
                params);
    }

    @Override
    public Map<String, Object> task(String id) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("id", id);
        List<Map<String, Object>> list = namedParameterJdbcTemplate.queryForList(
                "select id, tenant_id, collection_id, collection_name, sync_mode, status, total_count, success_count, skip_count, fail_count, error_message, started_at, finished_at, created_at " +
                        "from outline_sync_task where tenant_id=:tenantId and id=:id",
                params);
        return list.isEmpty() ? new LinkedHashMap<>() : list.get(0);
    }

    private OutlineDocumentDTO fetchDocumentDetail(String id) {
        JsonNode root = post("/api/documents.info", Collections.singletonMap("id", id));
        return toDocumentDTO(root == null ? null : root.path("data"));
    }

    private boolean upsertOutlineDocument(String tenantId, OutlineDocumentDTO doc, boolean force) {
        String sourceId = doc.getId();
        String content = safe(doc.getText(), "");
        String hash = DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
        String documentId = "outline-" + sourceId;

        MapSqlParameterSource find = new MapSqlParameterSource()
                .addValue("tenantId", tenantId).addValue("sourceType", "OUTLINE").addValue("sourceId", sourceId);
        List<Map<String, Object>> exists = namedParameterJdbcTemplate.queryForList(
                "select id, content_hash from knowledge_document where tenant_id=:tenantId and source_type=:sourceType and source_id=:sourceId and deleted=false",
                find);

        if (!force && !exists.isEmpty() && hash.equals(String.valueOf(exists.get(0).get("content_hash")))) return false;

        if (exists.isEmpty()) {
            namedParameterJdbcTemplate.update(
                    "insert into knowledge_document(id, tenant_id, source_type, source_id, title, doc_type, category, content_hash, url, status, synced_at, created_at, updated_at, deleted) " +
                            "values(:id,:tenantId,'OUTLINE',:sourceId,:title,'MARKDOWN','OUTLINE_DOC',:hash,:url,'ENABLED',now(),now(),now(),false)",
                    new MapSqlParameterSource().addValue("id", documentId).addValue("tenantId", tenantId)
                            .addValue("sourceId", sourceId).addValue("title", safe(doc.getTitle(), "未命名 Outline 文档"))
                            .addValue("hash", hash).addValue("url", doc.getUrl()));
        } else {
            documentId = String.valueOf(exists.get(0).get("id"));
            namedParameterJdbcTemplate.update(
                    "update knowledge_document set title=:title, content_hash=:hash, url=:url, synced_at=now(), updated_at=now() where tenant_id=:tenantId and id=:id",
                    new MapSqlParameterSource().addValue("id", documentId).addValue("tenantId", tenantId)
                            .addValue("title", safe(doc.getTitle(), "未命名 Outline 文档")).addValue("hash", hash).addValue("url", doc.getUrl()));
        }
        refreshChunks(tenantId, documentId, content, doc.getUrl());
        return true;
    }

    private void refreshChunks(String tenantId, String documentId, String content, String sourceUrl) {
        namedParameterJdbcTemplate.update("delete from knowledge_chunk where tenant_id=:tenantId and document_id=:documentId",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("documentId", documentId));
        int no = 1;
        for (String chunk : textChunkSplitter.split(content)) {
            namedParameterJdbcTemplate.update(
                    "insert into knowledge_chunk(id, tenant_id, document_id, chunk_no, heading, content, content_tokens, source_type, source_url, metadata, created_at, updated_at, deleted) " +
                            "values(:id,:tenantId,:documentId,:chunkNo,:heading,:content,:tokens,'OUTLINE',:sourceUrl,'{}'::jsonb,now(),now(),false)",
                    new MapSqlParameterSource().addValue("id", uuid()).addValue("tenantId", tenantId).addValue("documentId", documentId)
                            .addValue("chunkNo", no++).addValue("heading", extractHeading(chunk)).addValue("content", chunk)
                            .addValue("tokens", Math.max(1, chunk.length() / 2)).addValue("sourceUrl", sourceUrl));
        }
    }

    private void insertTask(String taskId, String tenantId, String collectionId) {
        namedParameterJdbcTemplate.update(
                "insert into outline_sync_task(id, tenant_id, collection_id, collection_name, sync_mode, status, total_count, success_count, skip_count, fail_count, started_at, created_at) " +
                        "values(:id,:tenantId,:collectionId,:collectionName,'COLLECTION','RUNNING',0,0,0,0,now(),now())",
                new MapSqlParameterSource().addValue("id", taskId).addValue("tenantId", tenantId)
                        .addValue("collectionId", collectionId).addValue("collectionName", collectionId));
    }

    private void updateTask(String taskId, int total, int success, int skipped, int failed, String status, String error) {
        namedParameterJdbcTemplate.update(
                "update outline_sync_task set total_count=:total, success_count=:success, skip_count=:skipped, fail_count=:failed, status=:status, error_message=:error, finished_at=now() " +
                        "where tenant_id=:tenantId and id=:id",
                new MapSqlParameterSource().addValue("id", taskId).addValue("tenantId", TenantContextHolder.getTenantId())
                        .addValue("total", total).addValue("success", success).addValue("skipped", skipped)
                        .addValue("failed", failed).addValue("status", status).addValue("error", error));
    }

    private JsonNode post(String path, Map<String, Object> body) {
        String baseUrl = properties.getBaseUrl();
        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) + path : baseUrl + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiToken());
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class).getBody();
    }

    private OutlineDocumentDTO toDocumentDTO(JsonNode node) {
        OutlineDocumentDTO dto = new OutlineDocumentDTO();
        if (node == null || node.isMissingNode() || node.isNull()) return dto;
        dto.setId(node.path("id").asText(null));
        dto.setCollectionId(node.path("collectionId").asText(null));
        dto.setTitle(node.path("title").asText(""));
        dto.setText(node.path("text").asText(""));
        dto.setUrl(node.path("url").asText(null));
        dto.setUpdatedAt(node.path("updatedAt").asText(null));
        return dto;
    }

    private int normalizeLimit(Integer limit) {
        int size = limit == null ? 50 : limit;
        if (size <= 0) size = 50;
        return Math.min(size, 200);
    }

    private int normalizeTaskLimit(Integer limit) {
        int size = limit == null ? 20 : limit;
        if (size <= 0) size = 20;
        return Math.min(size, 100);
    }

    private String extractHeading(String chunk) {
        if (chunk == null) return null;
        for (String line : chunk.split("\\n")) {
            String text = line.trim();
            if (text.startsWith("#")) return text.replace("#", "").trim();
        }
        return null;
    }

    private boolean notBlank(String value) { return value != null && value.trim().length() > 0; }
    private String safe(String value, String def) { return notBlank(value) ? value.trim() : def; }
    private String uuid() { return UUID.randomUUID().toString().replace("-", ""); }
}