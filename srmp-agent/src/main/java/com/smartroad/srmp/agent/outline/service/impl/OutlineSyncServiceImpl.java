package com.smartroad.srmp.agent.outline.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeIngestMarkdownRequest;
import com.smartroad.srmp.agent.knowledge.service.AiKnowledgeIngestService;
import com.smartroad.srmp.agent.outline.config.OutlineProperties;
import com.smartroad.srmp.agent.outline.dto.OutlineCollectionDTO;
import com.smartroad.srmp.agent.outline.dto.OutlineDocumentDTO;
import com.smartroad.srmp.agent.outline.dto.OutlineSyncRequest;
import com.smartroad.srmp.agent.outline.service.OutlineSyncService;
import com.smartroad.srmp.agent.outline.support.OutlineKnowledgeDocumentPreprocessor;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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

    private volatile RestTemplate restTemplate;

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
                dto.setUrl(outlineUrl(item.path("url").asText(null)));
                result.add(dto);
            }
        }
        return result;
    }

    @Override
    public List<OutlineDocumentDTO> documents(String collectionId, Integer limit, Integer offset) {
        if (!properties.usable()) return Collections.emptyList();
        String resolvedCollectionId = resolveCollectionId(collectionId);
        int size = normalizeLimit(limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("limit", size);
        body.put("offset", offset == null ? 0 : offset);
        if (notBlank(resolvedCollectionId)) body.put("collectionId", resolvedCollectionId);
        JsonNode root = post("/api/documents.list", body);
        JsonNode data = root == null ? null : root.path("data");
        List<OutlineDocumentDTO> result = new ArrayList<>();
        if (data != null && data.isArray()) {
            for (JsonNode item : data) {
                OutlineDocumentDTO dto = toDocumentDTO(item);
                if (!notBlank(dto.getCollectionId())) dto.setCollectionId(resolvedCollectionId);
                result.add(dto);
            }
        }
        enrichDocumentsWithKnowledgeStatus(result, TenantContextHolder.getTenantId());
        return result;
    }

    private void enrichDocumentsWithKnowledgeStatus(List<OutlineDocumentDTO> documents, String tenantId) {
        if (documents == null || documents.isEmpty() || !notBlank(tenantId)) {
            return;
        }
        List<String> sourceIds = new ArrayList<>();
        for (OutlineDocumentDTO doc : documents) {
            if (notBlank(doc.getId())) {
                sourceIds.add(doc.getId().trim());
            }
        }
        if (sourceIds.isEmpty()) {
            return;
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("sourceType", "OUTLINE")
                .addValue("sourceIds", sourceIds);
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                "select d.source_id, d.id as knowledge_document_id, d.status, " +
                        "count(c.id) as chunk_count, " +
                        "sum(case when c.embedding is not null then 1 else 0 end) as embedded_chunk_count " +
                        "from ai_knowledge_document d " +
                        "left join ai_knowledge_chunk c on c.tenant_id=d.tenant_id and c.document_id=d.id " +
                        "where d.tenant_id=:tenantId and d.source_type=:sourceType and d.source_id in (:sourceIds) " +
                        "group by d.source_id, d.id, d.status",
                params);
        Map<String, Map<String, Object>> bySourceId = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String sourceId = row.get("source_id") == null ? null : String.valueOf(row.get("source_id"));
            if (notBlank(sourceId)) {
                bySourceId.put(sourceId, row);
            }
        }
        for (OutlineDocumentDTO doc : documents) {
            Map<String, Object> kb = bySourceId.get(doc.getId());
            if (kb == null) {
                doc.setKbSyncStatus("NOT_SYNCED");
                doc.setRagReady(false);
                continue;
            }
            doc.setKnowledgeDocumentId(kb.get("knowledge_document_id") == null ? null : String.valueOf(kb.get("knowledge_document_id")));
            int chunkCount = toInt(kb.get("chunk_count"));
            int embeddedCount = toInt(kb.get("embedded_chunk_count"));
            doc.setChunkCount(chunkCount);
            doc.setEmbeddedChunkCount(embeddedCount);
            String docStatus = kb.get("status") == null ? "" : String.valueOf(kb.get("status"));
            if (!"ACTIVE".equalsIgnoreCase(docStatus)) {
                doc.setKbSyncStatus("INACTIVE");
                doc.setRagReady(false);
            } else if (chunkCount > 0 && embeddedCount >= chunkCount) {
                doc.setKbSyncStatus("RAG_READY");
                doc.setRagReady(true);
            } else {
                doc.setKbSyncStatus("PENDING_VECTOR");
                doc.setRagReady(false);
            }
        }
    }

    private int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public Map sync(OutlineSyncRequest request) {
        OutlineSyncRequest req = request == null ? new OutlineSyncRequest() : request;
        String tenantId = TenantContextHolder.getTenantId();
        String taskId = uuid();
        String collectionId = resolveCollectionId(req.getCollectionId());
        boolean force = Boolean.TRUE.equals(req.getForce());
        int limit = normalizeMaxDocuments(req.getLimit());
        boolean dryRun = Boolean.TRUE.equals(req.getDryRun());

        insertTask(taskId, tenantId, collectionId);

        int total = 0, success = 0, skipped = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        try {
            List<OutlineDocumentDTO> docs = fetchDocumentsForSync(req, collectionId, limit);
            total = docs.size();
            for (OutlineDocumentDTO doc : docs) {
                OutlineDocumentDTO detail = doc;
                try {
                    if (!notBlank(detail.getText()) && notBlank(detail.getId())) {
                        detail = fetchDocumentDetail(detail.getId());
                    }
                    if (!notBlank(detail.getCollectionId())) detail.setCollectionId(collectionId);
                    if (shouldSkipOutlineSystemDoc(detail)) {
                        insertDocDetail(taskId, tenantId, detail, "SKIP", "SKIPPED", "系统文档跳过", null, null, 0, null, null, null);
                        skipped++;
                        continue;
                    }
                    OutlineKnowledgeDocumentPreprocessor.PreparedDocument prepared =
                            OutlineKnowledgeDocumentPreprocessor.prepare(detail.getText());
                    if (!prepared.isRagEnabled()) {
                        int removedChunks = dryRun ? 0 : deactivateOutlineKnowledgeDocument(tenantId, detail.getId());
                        insertDocDetail(taskId, tenantId, detail, "SKIP", "SKIPPED", "ragEnabled=false，不进入 RAG", null, null, removedChunks, null, null, "已按 SRMP 元数据跳过 RAG 入库");
                        skipped++;
                        continue;
                    }
                    Map<String, Object> ingestResult;
                    if (dryRun) {
                        Map<String, Object> dryRunSkipped = new LinkedHashMap<>();
                        dryRunSkipped.put("skipped", true);
                        dryRunSkipped.put("skipReason", "dryRun 模式不写入知识库");
                        ingestResult = dryRunSkipped;
                    } else {
                        ingestResult = upsertOutlineDocumentWithResult(tenantId, detail, prepared, force);
                    }
                    if (ingestResult == null) {
                        insertDocDetail(taskId, tenantId, detail, "UPSERT", "FAILED", null, "ingest result is null", null, 0, null, null, null);
                        failed++;
                        appendError(errors, docLabel(detail) + ": ingest result is null");
                    } else if (Boolean.TRUE.equals(ingestResult.get("skipped"))) {
                        insertDocDetail(taskId, tenantId, detail, "UPSERT", "SKIPPED", safe((String) ingestResult.get("skipReason"), "内容未变化"), null, safe((String) ingestResult.get("documentId"), null), 0, safe((String) ingestResult.get("contentHash"), null), null, safe((String) ingestResult.get("skipReason"), "内容未变化"));
                        skipped++;
                    } else {
                        insertDocDetail(taskId, tenantId, detail, "UPSERT", "SUCCESS", null, null, safe((String) ingestResult.get("documentId"), null), ((Number) ingestResult.getOrDefault("chunkCount", Integer.valueOf(0))).intValue(), safe((String) ingestResult.get("contentHash"), null), null, null);
                        success++;
                        Object embeddingFailedCount = ingestResult.get("embeddingFailedCount");
                        if (embeddingFailedCount instanceof Number && ((Number) embeddingFailedCount).intValue() > 0) {
                            appendError(errors, docLabel(detail) + ": 文档已同步，但 " + embeddingFailedCount + " 个 chunk 向量化失败");
                        }
                    }
                } catch (Exception e) {
                    String msg = rootMessage(e);
                    insertDocDetail(taskId, tenantId, detail, "UPSERT", "FAILED", null, msg, null, 0, null, null, null);
                    failed++;
                    appendError(errors, docLabel(detail) + ": " + msg);
                }
            }
            String finalStatus = dryRun ? ((failed == 0) ? "DRY_RUN" : "DRY_RUN_PARTIAL") : computeStatus(total, success, skipped, failed);
            updateTask(taskId, total, success, skipped, failed, finalStatus, joinErrors(errors));
        } catch (Exception e) {
            updateTask(taskId, total, success, skipped, failed, "FAILED", rootMessage(e));
        }
        return task(taskId);
    }



    @Override
    public Map<String, Object> knowledgeStats() {
        String tenantId = TenantContextHolder.getTenantId();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("sourceType", "OUTLINE");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantId", tenantId);
        result.put("sourceType", "OUTLINE");
        result.put("documentCount", queryLong("select count(*) from ai_knowledge_document where tenant_id=:tenantId and source_type=:sourceType and status='ACTIVE'", params));
        result.put("chunkCount", queryLong("select count(*) from ai_knowledge_chunk where tenant_id=:tenantId and source_type=:sourceType", params));
        result.put("embeddedChunkCount", queryLong("select count(*) from ai_knowledge_chunk where tenant_id=:tenantId and source_type=:sourceType and embedding is not null", params));
        result.put("pendingEmbeddingChunkCount", queryLong("select count(*) from ai_knowledge_chunk where tenant_id=:tenantId and source_type=:sourceType and embedding is null", params));
        result.put("documents", namedParameterJdbcTemplate.queryForList(
                "select d.id, d.source_id, d.title, d.updated_at, " +
                        "count(c.id) as chunk_count, " +
                        "count(c.embedding) as embedded_chunk_count " +
                        "from ai_knowledge_document d " +
                        "left join ai_knowledge_chunk c on c.tenant_id=d.tenant_id and c.document_id=d.id " +
                        "where d.tenant_id=:tenantId and d.source_type=:sourceType and d.status='ACTIVE' " +
                        "group by d.id, d.source_id, d.title, d.updated_at " +
                        "order by d.updated_at desc limit 50",
                params));
        return result;
    }

    private Long queryLong(String sql, MapSqlParameterSource params) {
        Long value = namedParameterJdbcTemplate.queryForObject(sql, params, Long.class);
        return value == null ? 0L : value;
    }

    private String computeStatus(int total, int success, int skipped, int failed) {
        if (failed == 0) return "SUCCESS";
        if (failed == total) return "FAILED";
        return "PARTIAL_SUCCESS";
    }

    private void insertDocDetail(String taskId, String tenantId, OutlineDocumentDTO doc, String action, String status, String skipReason, String error, String knowledgeDocId, int chunkCount, String contentHash, String oldContentHash, String detailMessage) {
        namedParameterJdbcTemplate.update(
                "insert into outline_sync_task_detail(id, tenant_id, task_id, outline_document_id, outline_title, collection_id, action, status, skip_reason, error_message, knowledge_document_id, chunk_count, content_hash, old_content_hash, detail_message, outline_url, outline_updated_at, created_at) " +
                        "values(:id,:tenantId,:taskId,:outlineDocId,:outlineTitle,:collectionId,:action,:status,:skipReason,:error,:knowledgeDocId,:chunkCount,:contentHash,:oldContentHash,:detailMessage,:outlineUrl,:outlineUpdatedAt,now())",
                new MapSqlParameterSource()
                        .addValue("id", uuid()).addValue("tenantId", tenantId).addValue("taskId", taskId)
                        .addValue("outlineDocId", doc.getId()).addValue("outlineTitle", doc.getTitle())
                        .addValue("collectionId", doc.getCollectionId()).addValue("action", action)
                        .addValue("status", status).addValue("skipReason", skipReason)
                        .addValue("error", error).addValue("knowledgeDocId", knowledgeDocId)
                        .addValue("chunkCount", chunkCount).addValue("contentHash", contentHash)
                        .addValue("oldContentHash", oldContentHash).addValue("detailMessage", detailMessage)
                        .addValue("outlineUrl", doc.getUrl()).addValue("outlineUpdatedAt", doc.getUpdatedAt())
        );
    }

    private Map<String, Object> upsertOutlineDocumentWithResult(String tenantId, OutlineDocumentDTO doc,
                                                                OutlineKnowledgeDocumentPreprocessor.PreparedDocument prepared,
                                                                boolean force) {
        if (!notBlank(doc.getId()) || prepared == null || !notBlank(prepared.getSearchableText())) {
            return null;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("url", doc.getUrl());
        metadata.put("sourceUrl", doc.getUrl());
        metadata.put("outlineDocumentId", doc.getId());
        metadata.put("outlineCollectionId", doc.getCollectionId());
        metadata.put("outlineUpdatedAt", doc.getUpdatedAt());
        metadata.put("syncSource", "OUTLINE");
        metadata.putAll(prepared.getMetadata());

        AiKnowledgeIngestMarkdownRequest ingestRequest = new AiKnowledgeIngestMarkdownRequest();
        ingestRequest.setTenantId(tenantId);
        ingestRequest.setTitle(safe(doc.getTitle(), "未命名 Outline 文档"));
        ingestRequest.setSourceType("OUTLINE");
        ingestRequest.setSourceId(doc.getId());
        ingestRequest.setContent(prepared.getSearchableText());
        ingestRequest.setUrl(doc.getUrl());
        ingestRequest.setMetadata(metadata);
        ingestRequest.setForce(force);
        ingestRequest.setVectorize(true);
        ingestRequest.setFailOnEmbeddingError(false);

        return aiKnowledgeIngestService.ingestMarkdown(ingestRequest);
    }

    private int deactivateOutlineKnowledgeDocument(String tenantId, String outlineDocumentId) {
        if (!notBlank(tenantId) || !notBlank(outlineDocumentId)) {
            return 0;
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("sourceType", "OUTLINE")
                .addValue("sourceId", outlineDocumentId);
        List<String> documentIds = namedParameterJdbcTemplate.queryForList(
                "select id from ai_knowledge_document where tenant_id=:tenantId and source_type=:sourceType and source_id=:sourceId and status='ACTIVE'",
                params,
                String.class
        );
        if (documentIds == null || documentIds.isEmpty()) {
            return 0;
        }
        MapSqlParameterSource update = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("documentIds", documentIds);
        int removedChunks = namedParameterJdbcTemplate.update(
                "delete from ai_knowledge_chunk where tenant_id=:tenantId and document_id in (:documentIds)",
                update
        );
        namedParameterJdbcTemplate.update(
                "update ai_knowledge_document set status='INACTIVE', updated_at=now() where tenant_id=:tenantId and id in (:documentIds)",
                update
        );
        return removedChunks;
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
    public List<Map<String, Object>> details(String taskId, String status, Integer limit) {
        if (!notBlank(taskId)) return Collections.emptyList();
        int max = normalizeDetailLimit(limit);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("taskId", taskId)
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("limit", max);
        String sql = "select d.id, d.outline_document_id, d.outline_title, d.status, d.error_message, " +
                "d.outline_url, d.outline_updated_at, d.content_chars, d.content_hash, d.old_content_hash, " +
                "d.detail_message, d.document_status, d.knowledge_document_id, d.chunk_count, d.cost_ms, " +
                "d.action, d.skip_reason, d.error_type, d.created_at " +
                "from outline_sync_task_detail d where d.task_id=:taskId and d.tenant_id=:tenantId";
        if (notBlank(status)) {
            sql += " and d.status=:status";
            params.addValue("status", status);
        }
        sql += " order by d.created_at limit :limit";
        return namedParameterJdbcTemplate.queryForList(sql, params);
    }

    @Override
    public Map<String, Object> retryFailed(String taskId, boolean force) {
        String tenantId = TenantContextHolder.getTenantId();
        List<Map<String, Object>> failedDetails = details(taskId, "FAILED", 1000);
        if (failedDetails == null || failedDetails.isEmpty()) {
            return task(taskId);
        }
        String newTaskId = uuid();
        insertTask(newTaskId, tenantId, null);
        int total = 0, success = 0, skipped = 0, failed = 0;
        for (Map<String, Object> detail : failedDetails) {
            String outlineDocId = String.valueOf(detail.get("outline_document_id"));
            if (!notBlank(outlineDocId) || "null".equals(outlineDocId)) continue;
            try {
                OutlineDocumentDTO docDetail = fetchDocumentDetail(outlineDocId);
                if (docDetail == null || !notBlank(docDetail.getId())) {
                    failed++;
                    continue;
                }
                OutlineKnowledgeDocumentPreprocessor.PreparedDocument prepared =
                        OutlineKnowledgeDocumentPreprocessor.prepare(docDetail.getText());
                Map<String, Object> ingestResult;
                if (!prepared.isRagEnabled()) {
                    int removedChunks = deactivateOutlineKnowledgeDocument(tenantId, docDetail.getId());
                    ingestResult = new LinkedHashMap<>();
                    ingestResult.put("skipped", true);
                    ingestResult.put("skipReason", "ragEnabled=false，不进入 RAG，已清理 chunk " + removedChunks);
                } else {
                    ingestResult = upsertOutlineDocumentWithResult(tenantId, docDetail, prepared, force);
                }
                if (ingestResult == null) {
                    failed++;
                } else if (Boolean.TRUE.equals(ingestResult.get("skipped"))) {
                    skipped++;
                } else {
                    success++;
                }
                total++;
            } catch (Exception e) {
                failed++;
                total++;
            }
        }
        String finalStatus = computeStatus(total, success, skipped, failed);
        updateTask(newTaskId, total, success, skipped, failed, finalStatus, null);
        return task(newTaskId);
    }

    private OutlineDocumentDTO fetchDocumentDetail(String id) {
        JsonNode root = post("/api/documents.info", Collections.singletonMap("id", id));
        return toDocumentDTO(root == null ? null : root.path("data"));
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

    private List<OutlineDocumentDTO> fetchDocumentsForSync(OutlineSyncRequest req, String collectionId, int limit) {
        List<String> ids = req == null ? null : req.getDocumentIds();
        if (ids != null && !ids.isEmpty()) {
            List<OutlineDocumentDTO> result = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (String id : ids) {
                if (!notBlank(id) || !seen.add(id.trim())) {
                    continue;
                }
                OutlineDocumentDTO detail = fetchDocumentDetail(id.trim());
                if (detail != null && notBlank(detail.getId())) {
                    if (!notBlank(detail.getCollectionId())) {
                        detail.setCollectionId(collectionId);
                    }
                    result.add(detail);
                }
            }
            return result;
        }
        return documents(collectionId, limit, 0);
    }

    private String resolveCollectionId(String collectionId) {
        if (notBlank(collectionId)) {
            return collectionId.trim();
        }
        String defaultCollectionId = properties == null ? null : properties.getDefaultCollectionId();
        return notBlank(defaultCollectionId) ? defaultCollectionId.trim() : null;
    }

    private RestTemplate restTemplate() {
        RestTemplate local = restTemplate;
        if (local == null) {
            synchronized (this) {
                local = restTemplate;
                if (local == null) {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(positive(properties == null ? null : properties.getConnectTimeoutMs(), 10000));
                    factory.setReadTimeout(positive(properties == null ? null : properties.getReadTimeoutMs(), 60000));
                    local = new RestTemplate(factory);
                    restTemplate = local;
                }
            }
        }
        return local;
    }

    private JsonNode post(String path, Map<String, Object> body) {
        String baseUrl = properties.getBaseUrl();
        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) + path : baseUrl + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiToken());
        return restTemplate().exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class).getBody();
    }

    private OutlineDocumentDTO toDocumentDTO(JsonNode node) {
        OutlineDocumentDTO dto = new OutlineDocumentDTO();
        if (node == null || node.isMissingNode() || node.isNull()) return dto;
        dto.setId(node.path("id").asText(null));
        dto.setCollectionId(node.path("collectionId").asText(null));
        dto.setTitle(node.path("title").asText(""));
        dto.setText(node.path("text").asText(""));
        dto.setUrl(outlineUrl(node.path("url").asText(null)));
        dto.setUpdatedAt(node.path("updatedAt").asText(null));
        return dto;
    }

    private String outlineUrl(String rawUrl) {
        if (!notBlank(rawUrl)) {
            return null;
        }
        String url = rawUrl.trim();
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        String baseUrl = properties == null ? "" : properties.getBaseUrl();
        if (!notBlank(baseUrl)) {
            return url;
        }
        String base = baseUrl.trim().replaceAll("/+$", "");
        return url.startsWith("/") ? base + url : base + "/" + url;
    }

    private int normalizeLimit(Integer limit) {
        int size = limit == null ? 50 : limit;
        if (size <= 0) size = 50;
        return Math.min(size, 100);
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

    private int positive(Integer value, int defaultValue) { return value != null && value > 0 ? value : defaultValue; }
    private String docLabel(OutlineDocumentDTO doc) { return safe(doc == null ? null : doc.getTitle(), safe(doc == null ? null : doc.getId(), "unknown")); }
    private void appendError(List<String> errors, String msg) { if (errors != null && errors.size() < 20 && notBlank(msg)) errors.add(msg); }
    private String joinErrors(List<String> errors) { return errors == null || errors.isEmpty() ? null : String.join("\n", errors); }
    private String rootMessage(Throwable e) { Throwable c = e; while (c != null && c.getCause() != null) c = c.getCause(); return c == null ? "" : String.valueOf(c.getMessage()); }
    private boolean notBlank(String value) { return value != null && value.trim().length() > 0; }
    private String safe(String value, String def) { return notBlank(value) ? value.trim() : def; }
    private String uuid() { return UUID.randomUUID().toString().replace("-", ""); }
}
