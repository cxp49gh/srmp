package com.smartroad.srmp.agent.outline.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeIngestMarkdownRequest;
import com.smartroad.srmp.agent.knowledge.service.AiKnowledgeIngestService;
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
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.*;

@Service
public class OutlineSyncServiceImpl implements OutlineSyncService {

    @Resource private OutlineProperties properties;
    @Resource private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Resource private AiKnowledgeIngestService aiKnowledgeIngestService;

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
        int limit = normalizeMaxDocuments(request == null ? null : request.getLimit());

        insertTask(taskId, tenantId, collectionId);

        int total = 0, success = 0, skipped = 0, failed = 0;
        try {
            List<OutlineDocumentDTO> docs = documents(collectionId, limit, 0);
            total = docs.size();
            for (OutlineDocumentDTO doc : docs) {
                String docId = doc.getId();
                String title = safe(doc.getTitle(), "未命名文档");
                try {
                    OutlineDocumentDTO detail = fetchDocumentDetail(docId);
                    if (!notBlank(detail.getCollectionId())) detail.setCollectionId(collectionId);
                    if (shouldSkipOutlineSystemDoc(detail)) {
                        insertDocDetail(taskId, tenantId, docId, title, "SKIPPED", "系统文档跳过", null, 0, null);
                        skipped++;
                        continue;
                    }
                    Map<String, Object> ingestResult = upsertOutlineDocumentWithResult(tenantId, detail, force);
                    if (ingestResult == null) {
                        insertDocDetail(taskId, tenantId, docId, title, "FAILED", "ingest result is null", null, 0, null);
                        failed++;
                    } else if (Boolean.TRUE.equals(ingestResult.get("skipped"))) {
                        insertDocDetail(taskId, tenantId, docId, title, "SKIPPED", safe((String) ingestResult.get("skipReason"), "内容未变化"), safe((String) ingestResult.get("documentId"), null), 0, safe((String) ingestResult.get("contentHash"), null));
                        skipped++;
                    } else {
                        insertDocDetail(taskId, tenantId, docId, title, "SUCCESS", null, safe((String) ingestResult.get("documentId"), null), ((Number) ingestResult.getOrDefault("chunkCount", Integer.valueOf(0))).intValue(), safe((String) ingestResult.get("contentHash"), null));
                        success++;
                    }
                } catch (Exception e) {
                    insertDocDetail(taskId, tenantId, docId, title, "FAILED", e.getMessage(), null, 0, null);
                    failed++;
                }
            }
            String finalStatus = computeStatus(total, success, skipped, failed);
            updateTask(taskId, total, success, skipped, failed, finalStatus, null);
        } catch (Exception e) {
            updateTask(taskId, total, success, skipped, failed, "FAILED", e.getMessage());
        }
        return task(taskId);
    }

    private String computeStatus(int total, int success, int skipped, int failed) {
        if (failed == 0) return "SUCCESS";
        if (failed == total) return "FAILED";
        return "PARTIAL_SUCCESS";
    }

    private void insertDocDetail(String taskId, String tenantId, String sourceId, String title, String status, String error, String documentId, int chunkCount, String contentHash) {
        namedParameterJdbcTemplate.update(
                "insert into outline_sync_document(id, tenant_id, task_id, source_id, title, status, error_message, document_id, chunk_count, content_hash, created_at) " +
                        "values(:id,:tenantId,:taskId,:sourceId,:title,:status,:error,:documentId,:chunkCount,:contentHash,now())",
                new MapSqlParameterSource()
                        .addValue("id", uuid()).addValue("tenantId", tenantId).addValue("taskId", taskId)
                        .addValue("sourceId", sourceId).addValue("title", title).addValue("status", status)
                        .addValue("error", error).addValue("documentId", documentId)
                        .addValue("chunkCount", chunkCount).addValue("contentHash", contentHash == null ? null : contentHash)
        );
    }

    private Map<String, Object> upsertOutlineDocumentWithResult(String tenantId, OutlineDocumentDTO doc, boolean force) {
        if (!notBlank(doc.getId()) || !notBlank(doc.getText())) {
            return null;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("url", doc.getUrl());
        metadata.put("sourceUrl", doc.getUrl());
        metadata.put("outlineDocumentId", doc.getId());
        metadata.put("outlineCollectionId", doc.getCollectionId());
        metadata.put("outlineUpdatedAt", doc.getUpdatedAt());
        metadata.put("syncSource", "OUTLINE");

        AiKnowledgeIngestMarkdownRequest ingestRequest = new AiKnowledgeIngestMarkdownRequest();
        ingestRequest.setTenantId(tenantId);
        ingestRequest.setTitle(safe(doc.getTitle(), "未命名 Outline 文档"));
        ingestRequest.setSourceType("OUTLINE");
        ingestRequest.setSourceId(doc.getId());
        ingestRequest.setContent(doc.getText());
        ingestRequest.setUrl(doc.getUrl());
        ingestRequest.setMetadata(metadata);
        ingestRequest.setForce(force);

        return aiKnowledgeIngestService.ingestMarkdown(ingestRequest);
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

    @Override
    public List<Map<String, Object>> details(String taskId, Integer limit) {
        if (!notBlank(taskId)) return Collections.emptyList();
        int max = normalizeDetailLimit(limit);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("taskId", taskId)
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("limit", max);
        return namedParameterJdbcTemplate.queryForList(
                "select d.id, d.source_id, d.title, d.status, d.error_message, d.document_id, d.chunk_count, d.created_at " +
                        "from outline_sync_document d where d.task_id=:taskId and d.tenant_id=:tenantId order by d.created_at limit :limit",
                params);
    }

    private OutlineDocumentDTO fetchDocumentDetail(String id) {
        JsonNode root = post("/api/documents.info", Collections.singletonMap("id", id));
        return toDocumentDTO(root == null ? null : root.path("data"));
    }

    private boolean upsertOutlineDocument(String tenantId, OutlineDocumentDTO doc, boolean force) {
        if (!notBlank(doc.getId()) || !notBlank(doc.getText())) {
            return false;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("url", doc.getUrl());
        metadata.put("sourceUrl", doc.getUrl());
        metadata.put("outlineDocumentId", doc.getId());
        metadata.put("outlineCollectionId", doc.getCollectionId());
        metadata.put("outlineUpdatedAt", doc.getUpdatedAt());
        metadata.put("syncSource", "OUTLINE");

        AiKnowledgeIngestMarkdownRequest ingestRequest = new AiKnowledgeIngestMarkdownRequest();
        ingestRequest.setTenantId(tenantId);
        ingestRequest.setTitle(safe(doc.getTitle(), "未命名 Outline 文档"));
        ingestRequest.setSourceType("OUTLINE");
        ingestRequest.setSourceId(doc.getId());
        ingestRequest.setContent(doc.getText());
        ingestRequest.setUrl(doc.getUrl());
        ingestRequest.setMetadata(metadata);
        ingestRequest.setForce(force);

        Map<String, Object> result = aiKnowledgeIngestService.ingestMarkdown(ingestRequest);
        return result == null || !Boolean.TRUE.equals(result.get("skipped"));
    }

    private boolean shouldSkipOutlineSystemDoc(OutlineDocumentDTO doc) {
        String title = safe(doc == null ? null : doc.getTitle(), "").toLowerCase(Locale.ROOT);
        if (title.length() == 0) {
            return false;
        }
        return "our editor".equals(title)
                || "what is outline".equals(title)
                || "getting started".equals(title)
                || "integrations & api".equals(title)
                || "integrations and api".equals(title);
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

    private int normalizeMaxDocuments(Integer limit) {
        int size = limit == null ? 50 : limit;
        if (size <= 0) size = 50;
        return Math.min(size, 100);
    }

    private int normalizeDetailLimit(Integer limit) {
        int size = limit == null ? 200 : limit;
        if (size <= 0) size = 200;
        return Math.min(size, 2000);
    }

    private boolean notBlank(String value) { return value != null && value.trim().length() > 0; }
    private String safe(String value, String def) { return notBlank(value) ? value.trim() : def; }
    private String uuid() { return UUID.randomUUID().toString().replace("-", ""); }
}
