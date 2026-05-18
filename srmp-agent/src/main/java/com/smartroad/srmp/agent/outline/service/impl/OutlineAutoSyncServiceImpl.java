package com.smartroad.srmp.agent.outline.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeReindexRequest;
import com.smartroad.srmp.agent.knowledge.service.AiKnowledgeReindexService;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeReindexResponse;
import com.smartroad.srmp.agent.outline.dto.OutlineAutoSyncConfigRequest;
import com.smartroad.srmp.agent.outline.dto.OutlineAutoSyncRunRequest;
import com.smartroad.srmp.agent.outline.dto.OutlineSyncRequest;
import com.smartroad.srmp.agent.outline.service.OutlineAutoSyncService;
import com.smartroad.srmp.agent.outline.service.OutlineService;
import com.smartroad.srmp.agent.outline.service.OutlineSyncService;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.sql.Timestamp;
import java.util.*;

@Service
public class OutlineAutoSyncServiceImpl implements OutlineAutoSyncService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SCOPE_COLLECTION = "COLLECTION";
    private static final String SCOPE_SINGLE_DOCUMENT = "SINGLE_DOCUMENT";
    private static final String SCOPE_MULTIPLE_DOCUMENTS = "MULTIPLE_DOCUMENTS";

    @Resource
    private NamedParameterJdbcTemplate jdbc;

    @Resource
    private OutlineSyncService outlineSyncService;

    @Resource
    private OutlineService outlineService;

    @Resource
    private AiKnowledgeReindexService aiKnowledgeReindexService;

    @Override
    public List<Map<String, Object>> configs() {
        ensureConfigScopeColumns();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from outline_auto_sync_config where tenant_id=:tenantId order by created_at desc",
                new MapSqlParameterSource().addValue("tenantId", TenantContextHolder.getTenantId()));
        return aliasRows(rows);
    }

    @Override
    public Map<String, Object> config(String id) {
        ensureConfigScopeColumns();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from outline_auto_sync_config where tenant_id=:tenantId and id=:id",
                new MapSqlParameterSource().addValue("tenantId", TenantContextHolder.getTenantId()).addValue("id", id));
        return rows.isEmpty() ? new LinkedHashMap<>() : aliasRow(rows.get(0));
    }

    @Override
    @Transactional
    public Map<String, Object> saveConfig(OutlineAutoSyncConfigRequest request) {
        ensureConfigScopeColumns();
        if (request == null) request = new OutlineAutoSyncConfigRequest();
        String id = notBlank(request.getId()) ? request.getId() : uuid();
        String tenantId = TenantContextHolder.getTenantId();
        int interval = normalizeInterval(request.getIntervalMinutes());
        String syncScope = normalizeSyncScope(request.getSyncScope());
        List<String> documentIds = normalizeDocumentIds(syncScope, request.getDocumentIds());
        validateNoScopeConflict(tenantId, id, syncScope, blankToNull(request.getCollectionId()), documentIds, bool(request.getEnabled()), bool(request.getWebhookEnabled()));
        jdbc.update(
                "insert into outline_auto_sync_config(id, tenant_id, name, enabled, collection_id, sync_scope, document_ids, interval_minutes, force, cleanup_missing, vectorize_after_sync, vector_force, vector_limit, webhook_enabled, webhook_secret, next_run_at, status, created_at, updated_at) " +
                        "values(:id,:tenantId,:name,:enabled,:collectionId,:syncScope,cast(:documentIds as jsonb),:interval,:force,:cleanupMissing,:vectorizeAfterSync,:vectorForce,:vectorLimit,:webhookEnabled,:webhookSecret,:nextRunAt,'IDLE',now(),now())",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("tenantId", tenantId)
                        .addValue("name", safe(request.getName(), "Outline 自动同步"))
                        .addValue("enabled", bool(request.getEnabled()))
                        .addValue("collectionId", blankToNull(request.getCollectionId()))
                        .addValue("syncScope", syncScope)
                        .addValue("documentIds", toJson(documentIds))
                        .addValue("interval", interval)
                        .addValue("force", bool(request.getForce()))
                        .addValue("cleanupMissing", bool(request.getCleanupMissing()))
                        .addValue("vectorizeAfterSync", request.getVectorizeAfterSync() == null ? true : bool(request.getVectorizeAfterSync()))
                        .addValue("vectorForce", bool(request.getVectorForce()))
                        .addValue("vectorLimit", normalizeVectorLimit(request.getVectorLimit()))
                        .addValue("webhookEnabled", bool(request.getWebhookEnabled()))
                        .addValue("webhookSecret", blankToNull(request.getWebhookSecret()))
                        .addValue("nextRunAt", nextRun(interval)));
        return config(id);
    }

    @Override
    @Transactional
    public Map<String, Object> updateConfig(String id, OutlineAutoSyncConfigRequest request) {
        ensureConfigScopeColumns();
        if (request == null) request = new OutlineAutoSyncConfigRequest();
        Map<String, Object> old = config(id);
        if (old.isEmpty()) throw new IllegalArgumentException("Outline 自动同步配置不存在：" + id);
        int interval = request.getIntervalMinutes() == null ? intValue(old.get("interval_minutes"), 60) : normalizeInterval(request.getIntervalMinutes());
        String syncScope = request.getSyncScope() == null ? normalizeSyncScope(str(old.get("sync_scope"))) : normalizeSyncScope(request.getSyncScope());
        List<String> documentIds = request.getDocumentIds() == null ? normalizeDocumentIds(syncScope, parseDocumentIds(old.get("document_ids"))) : normalizeDocumentIds(syncScope, request.getDocumentIds());
        Boolean nextEnabled = request.getEnabled() == null ? boolObj(old.get("enabled")) : bool(request.getEnabled());
        Boolean nextWebhookEnabled = request.getWebhookEnabled() == null ? boolObj(old.get("webhook_enabled")) : bool(request.getWebhookEnabled());
        Object nextCollectionId = request.getCollectionId() == null ? old.get("collection_id") : blankToNull(request.getCollectionId());
        validateNoScopeConflict(TenantContextHolder.getTenantId(), id, syncScope, nextCollectionId == null ? null : String.valueOf(nextCollectionId), documentIds, boolObj(nextEnabled), boolObj(nextWebhookEnabled));
        jdbc.update(
                "update outline_auto_sync_config set name=:name, enabled=:enabled, collection_id=:collectionId, interval_minutes=:interval, force=:force, cleanup_missing=:cleanupMissing, " +
                        "sync_scope=:syncScope, document_ids=cast(:documentIds as jsonb), vectorize_after_sync=:vectorizeAfterSync, vector_force=:vectorForce, vector_limit=:vectorLimit, webhook_enabled=:webhookEnabled, webhook_secret=:webhookSecret, " +
                        "next_run_at=case when :enabled=true and (next_run_at is null or interval_minutes<>:interval) then :nextRunAt else next_run_at end, updated_at=now() " +
                        "where tenant_id=:tenantId and id=:id",
                new MapSqlParameterSource()
                        .addValue("tenantId", TenantContextHolder.getTenantId())
                        .addValue("id", id)
                        .addValue("name", firstNonBlank(request.getName(), str(old.get("name")), "Outline 自动同步"))
                        .addValue("enabled", nextEnabled)
                        .addValue("collectionId", nextCollectionId)
                        .addValue("syncScope", syncScope)
                        .addValue("documentIds", toJson(documentIds))
                        .addValue("interval", interval)
                        .addValue("force", request.getForce() == null ? boolObj(old.get("force")) : bool(request.getForce()))
                        .addValue("cleanupMissing", request.getCleanupMissing() == null ? boolObj(old.get("cleanup_missing")) : bool(request.getCleanupMissing()))
                        .addValue("vectorizeAfterSync", request.getVectorizeAfterSync() == null ? boolObj(old.get("vectorize_after_sync")) : bool(request.getVectorizeAfterSync()))
                        .addValue("vectorForce", request.getVectorForce() == null ? boolObj(old.get("vector_force")) : bool(request.getVectorForce()))
                        .addValue("vectorLimit", request.getVectorLimit() == null ? intValue(old.get("vector_limit"), 500) : normalizeVectorLimit(request.getVectorLimit()))
                        .addValue("webhookEnabled", nextWebhookEnabled)
                        .addValue("webhookSecret", request.getWebhookSecret() == null ? old.get("webhook_secret") : blankToNull(request.getWebhookSecret()))
                        .addValue("nextRunAt", nextRun(interval)));
        return config(id);
    }

    @Override
    @Transactional
    public Map<String, Object> stopConfig(String id) {
        ensureConfigScopeColumns();
        String tenantId = TenantContextHolder.getTenantId();
        Map<String, Object> old = loadConfig(tenantId, id);
        if (old.isEmpty()) throw new IllegalArgumentException("Outline 自动同步配置不存在：" + id);
        int stoppedRuns = stopRunningRuns(tenantId, id, "管理员手动停止自动同步配置");
        jdbc.update(
                "update outline_auto_sync_config set enabled=false, webhook_enabled=false, next_run_at=null, status='STOPPED', error_message=null, updated_at=now() where tenant_id=:tenantId and id=:id",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", id));
        Map<String, Object> result = config(id);
        result.put("stoppedRuns", stoppedRuns);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> deleteConfig(String id) {
        ensureConfigScopeColumns();
        String tenantId = TenantContextHolder.getTenantId();
        Map<String, Object> old = loadConfig(tenantId, id);
        if (old.isEmpty()) throw new IllegalArgumentException("Outline 自动同步配置不存在：" + id);
        int stoppedRuns = stopRunningRuns(tenantId, id, "管理员删除自动同步配置，运行记录已终止");
        int deleted = jdbc.update(
                "delete from outline_auto_sync_config where tenant_id=:tenantId and id=:id",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", id));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("tenantId", tenantId);
        result.put("deleted", deleted);
        result.put("stoppedRuns", stoppedRuns);
        return result;
    }

    @Override
    public Map<String, Object> runNow(String id, OutlineAutoSyncRunRequest request) {
        ensureConfigScopeColumns();
        String tenantId = TenantContextHolder.getTenantId();
        Map<String, Object> cfg = loadConfig(tenantId, id);
        if (cfg.isEmpty()) throw new IllegalArgumentException("Outline 自动同步配置不存在：" + id);
        return runConfig(tenantId, cfg, request == null ? new OutlineAutoSyncRunRequest() : request);
    }

    @Override
    public List<Map<String, Object>> runs(String configId, Integer limit) {
        ensureConfigScopeColumns();
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("tenantId", TenantContextHolder.getTenantId()).addValue("limit", normalizeRunLimit(limit));
        String sql = "select * from outline_auto_sync_run where tenant_id=:tenantId ";
        if (notBlank(configId)) {
            sql += "and config_id=:configId ";
            p.addValue("configId", configId);
        }
        sql += "order by created_at desc limit :limit";
        return aliasRows(jdbc.queryForList(sql, p));
    }

    @Override
    public Map<String, Object> handleWebhook(String secret, Map<String, Object> payload) {
        ensureConfigScopeColumns();
        String requestTenantId = TenantContextHolder.getTenantId();
        if (!notBlank(secret)) throw new IllegalArgumentException("缺少 X-Outline-Webhook-Secret");
        String event = firstNonBlank(extract(payload, "event", "type", "name"), "document.updated");
        String docId = firstNonBlank(extract(payload, "documentId", "document_id", "id"),
                extract(child(payload, "document"), "id"),
                extract(child(payload, "model"), "id"),
                extract(child(payload, "data"), "documentId", "document_id", "id"),
                extract(child(child(payload, "data"), "document"), "id"),
                extract(child(child(payload, "data"), "model"), "id"));
        String collectionId = firstNonBlank(extract(payload, "collectionId", "collection_id"),
                extract(child(payload, "collection"), "id"),
                extract(child(payload, "model"), "collectionId", "collection_id"),
                extract(child(payload, "data"), "collectionId", "collection_id"),
                extract(child(child(payload, "data"), "collection"), "id"),
                extract(child(child(payload, "data"), "model"), "collectionId", "collection_id"));

        Map<String, Object> cfg = findWebhookConfig(requestTenantId, secret, collectionId);
        if (cfg.isEmpty()) {
            cfg = findWebhookConfigAcrossTenants(secret, collectionId);
        }
        if (cfg.isEmpty()) throw new IllegalArgumentException("未找到匹配的 webhook 配置，或 webhook 未启用");
        String tenantId = firstNonBlank(str(cfg.get("tenant_id")), requestTenantId);
        TenantContextHolder.setTenantId(tenantId);

        try {
            if (isDeleteOrArchive(event)) {
                int inactive = markInactive(tenantId, docId);
                String runId = insertRun(tenantId, str(cfg.get("id")), "WEBHOOK", event, docId, collectionId);
                finishRun(runId, tenantId, "SUCCESS", null, null, false, "SKIPPED", "文档已标记 INACTIVE：" + inactive);
                Map<String, Object> r = loadRun(tenantId, runId);
                r.put("inactiveCount", inactive);
                return aliasRow(r);
            }

            OutlineAutoSyncRunRequest req = new OutlineAutoSyncRunRequest();
            req.setTriggerType("WEBHOOK");
            req.setOutlineEvent(event);
            req.setOutlineDocumentId(docId);
            req.setOutlineCollectionId(collectionId);
            return runConfig(tenantId, cfg, req);
        } finally {
            TenantContextHolder.setTenantId(requestTenantId);
        }
    }

    @Override
    public Map<String, Object> runDueConfigs() {
        ensureConfigScopeColumns();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from outline_auto_sync_config where enabled=true and (next_run_at is null or next_run_at<=now()) and (status is null or status <> 'RUNNING' or updated_at < now() - interval '2 hours') order by next_run_at asc limit 20",
                new MapSqlParameterSource());
        List<Map<String, Object>> runs = new ArrayList<>();
        for (Map<String, Object> cfg : rows) {
            String tenantId = str(cfg.get("tenant_id"));
            if (!notBlank(tenantId)) continue;
            TenantContextHolder.setTenantId(tenantId);
            try {
                OutlineAutoSyncRunRequest req = new OutlineAutoSyncRunRequest();
                req.setTriggerType("SCHEDULE");
                runs.add(runConfig(tenantId, cfg, req));
            } catch (Exception e) {
                markConfigError(tenantId, str(cfg.get("id")), rootMessage(e));
            } finally {
                TenantContextHolder.clear();
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("count", runs.size());
        r.put("runs", runs);
        return r;
    }

    private Map<String, Object> runConfig(String tenantId, Map<String, Object> cfg, OutlineAutoSyncRunRequest request) {
        String configId = str(cfg.get("id"));
        String docId = request.getOutlineDocumentId();
        String collectionId = firstNonBlank(request.getOutlineCollectionId(), str(cfg.get("collection_id")));
        String triggerType = firstNonBlank(request.getTriggerType(), "MANUAL");
        String syncScope = normalizeSyncScope(str(cfg.get("sync_scope")));
        List<String> configuredDocumentIds = parseDocumentIds(cfg.get("document_ids"));
        String runId = insertRun(tenantId, configId, triggerType, request.getOutlineEvent(), docId, collectionId);
        try {
            if (notBlank(docId) && isDocumentScope(syncScope) && !configuredDocumentIds.contains(docId)) {
                String msg = "Webhook 文档不在当前配置选择范围内，已跳过";
                finishRun(runId, tenantId, "SKIPPED", msg, null, false, "SKIPPED", msg);
                return aliasRow(loadRun(tenantId, runId));
            }
            if (!notBlank(docId) && isDocumentScope(syncScope) && configuredDocumentIds.isEmpty()) {
                String msg = "当前配置为单篇/多篇文档同步，但未配置文档 ID";
                finishRun(runId, tenantId, "FAILED", msg, null, false, "FAILED", msg);
                markConfigError(tenantId, configId, msg);
                return aliasRow(loadRun(tenantId, runId));
            }
            if (!tryMarkConfigRunning(tenantId, configId)) {
                String msg = "配置已有运行中任务，本次触发已跳过";
                finishRun(runId, tenantId, "SKIPPED", msg, null, false, "SKIPPED", msg);
                return aliasRow(loadRun(tenantId, runId));
            }
            OutlineSyncRequest sync = new OutlineSyncRequest();
            sync.setCollectionId(blankToNull(collectionId));
            sync.setLimit(2000);
            sync.setForce(request.getForce() == null ? boolObj(cfg.get("force")) : request.getForce());
            sync.setCleanupMissing(boolObj(cfg.get("cleanup_missing")) && SCOPE_COLLECTION.equals(syncScope) && !notBlank(docId));
            sync.setDryRun(false);
            List<String> runDocumentIds = notBlank(docId) ? Collections.singletonList(docId) : (isDocumentScope(syncScope) ? configuredDocumentIds : Collections.emptyList());
            if (!runDocumentIds.isEmpty()) sync.setDocumentIds(runDocumentIds);

            Map task = outlineSyncService.sync(sync);
            String taskId = str(task.get("id"));
            String syncStatus = str(task.get("status"));
            boolean syncFailed = syncStatus.contains("FAILED");

            boolean vectorize = request.getVectorizeAfterSync() == null ? boolObj(cfg.get("vectorize_after_sync")) : request.getVectorizeAfterSync();
            String vectorStatus = "SKIPPED";
            String vectorMessage = "vectorizeAfterSync=false";
            if (vectorize && !syncFailed) {
                AiKnowledgeReindexRequest reindex = new AiKnowledgeReindexRequest();
                reindex.setSourceType("OUTLINE");
                reindex.setForce(boolObj(cfg.get("vector_force")));
                reindex.setLimit(intValue(cfg.get("vector_limit"), 500));
                if (!runDocumentIds.isEmpty()) reindex.setSourceIds(runDocumentIds);
                AiKnowledgeReindexResponse resp = aiKnowledgeReindexService.reindex(reindex);
                vectorStatus = resp.getFailed() != null && resp.getFailed() > 0 ? "PARTIAL_SUCCESS" : "SUCCESS";
                vectorMessage = "total=" + resp.getTotal() + ", success=" + resp.getSuccess() + ", failed=" + resp.getFailed() + ", skipped=" + resp.getSkipped();
            }

            String status = syncFailed ? ("PARTIAL_SUCCESS".equals(syncStatus) ? "PARTIAL_SUCCESS" : "FAILED") : "SUCCESS";
            finishRun(runId, tenantId, status, syncFailed ? str(task.get("error_message")) : null, taskId, vectorize, vectorStatus, vectorMessage);
            updateConfigAfterRun(tenantId, configId, taskId, vectorStatus, status, syncFailed ? str(task.get("error_message")) : null);
            return aliasRow(loadRun(tenantId, runId));
        } catch (Exception e) {
            String msg = rootMessage(e);
            finishRun(runId, tenantId, "FAILED", msg, null, false, "FAILED", msg);
            markConfigError(tenantId, configId, msg);
            return aliasRow(loadRun(tenantId, runId));
        }
    }

    private Map<String, Object> loadConfig(String tenantId, String id) {
        ensureConfigScopeColumns();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from outline_auto_sync_config where tenant_id=:tenantId and id=:id",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", id));
        return rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
    }

    private int stopRunningRuns(String tenantId, String configId, String message) {
        return jdbc.update(
                "update outline_auto_sync_run set status='STOPPED', error_message=:message, vectorize_status='STOPPED', vectorize_message=:message, finished_at=now() " +
                        "where tenant_id=:tenantId and config_id=:configId and status='RUNNING'",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("configId", configId)
                        .addValue("message", message));
    }

    private Map<String, Object> findWebhookConfig(String tenantId, String secret, String collectionId) {
        ensureConfigScopeColumns();
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("secret", secret);
        String sql;
        if (notBlank(collectionId)) {
            sql = "select * from outline_auto_sync_config where tenant_id=:tenantId and webhook_enabled=true and webhook_secret=:secret and (collection_id=:collectionId or collection_id is null or collection_id='') order by case when collection_id=:collectionId then 0 else 1 end, updated_at desc limit 1";
            p.addValue("collectionId", collectionId);
        } else {
            sql = "select * from outline_auto_sync_config where tenant_id=:tenantId and webhook_enabled=true and webhook_secret=:secret order by updated_at desc limit 1";
        }
        List<Map<String, Object>> rows = jdbc.queryForList(sql, p);
        return rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
    }

    private Map<String, Object> findWebhookConfigAcrossTenants(String secret, String collectionId) {
        ensureConfigScopeColumns();
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("secret", secret);
        String sql;
        if (notBlank(collectionId)) {
            sql = "select * from outline_auto_sync_config where webhook_enabled=true and webhook_secret=:secret and (collection_id=:collectionId or collection_id is null or collection_id='') order by case when collection_id=:collectionId then 0 else 1 end, updated_at desc limit 1";
            p.addValue("collectionId", collectionId);
        } else {
            sql = "select * from outline_auto_sync_config where webhook_enabled=true and webhook_secret=:secret order by updated_at desc limit 1";
        }
        List<Map<String, Object>> rows = jdbc.queryForList(sql, p);
        return rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
    }

    private String insertRun(String tenantId, String configId, String trigger, String event, String docId, String collectionId) {
        String id = uuid();
        jdbc.update(
                "insert into outline_auto_sync_run(id, tenant_id, config_id, trigger_type, outline_event, outline_document_id, outline_collection_id, status, started_at, created_at) values(:id,:tenantId,:configId,:trigger,:event,:docId,:collectionId,'RUNNING',now(),now())",
                new MapSqlParameterSource().addValue("id", id).addValue("tenantId", tenantId).addValue("configId", configId)
                        .addValue("trigger", trigger).addValue("event", event).addValue("docId", docId).addValue("collectionId", collectionId));
        return id;
    }

    private void finishRun(String id, String tenantId, String status, String error, String taskId, boolean vectorize, String vectorStatus, String vectorMessage) {
        jdbc.update(
                "update outline_auto_sync_run set status=:status, error_message=:error, sync_task_id=:taskId, vectorize_triggered=:vectorize, vectorize_status=:vectorStatus, vectorize_message=:vectorMessage, finished_at=now() where tenant_id=:tenantId and id=:id",
                new MapSqlParameterSource().addValue("id", id).addValue("tenantId", tenantId).addValue("status", status).addValue("error", error)
                        .addValue("taskId", taskId).addValue("vectorize", vectorize).addValue("vectorStatus", vectorStatus).addValue("vectorMessage", vectorMessage));
    }

    private Map<String, Object> loadRun(String tenantId, String runId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from outline_auto_sync_run where tenant_id=:tenantId and id=:id",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", runId));
        return rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
    }

    private boolean tryMarkConfigRunning(String tenantId, String id) {
        int updated = jdbc.update("update outline_auto_sync_config set status='RUNNING', error_message=null, updated_at=now() " +
                        "where tenant_id=:tenantId and id=:id and (status is null or status <> 'RUNNING' or updated_at < now() - interval '2 hours')",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", id));
        return updated > 0;
    }

    private void updateConfigAfterRun(String tenantId, String id, String taskId, String vectorStatus, String status, String error) {
        Map<String, Object> cfg = loadConfig(tenantId, id);
        int interval = intValue(cfg.get("interval_minutes"), 60);
        jdbc.update(
                "update outline_auto_sync_config set last_run_at=now(), next_run_at=:nextRun, last_task_id=:taskId, last_vector_status=:vectorStatus, status=:status, error_message=:error, updated_at=now() where tenant_id=:tenantId and id=:id",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", id).addValue("nextRun", nextRun(interval))
                        .addValue("taskId", taskId).addValue("vectorStatus", vectorStatus).addValue("status", status).addValue("error", error));
    }

    private void markConfigError(String tenantId, String id, String error) {
        Map<String, Object> cfg = loadConfig(tenantId, id);
        int interval = intValue(cfg.get("interval_minutes"), 60);
        jdbc.update("update outline_auto_sync_config set status='FAILED', error_message=:error, next_run_at=:nextRun, updated_at=now() where tenant_id=:tenantId and id=:id",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", id).addValue("error", error).addValue("nextRun", nextRun(interval)));
    }

    private int markInactive(String tenantId, String documentId) {
        if (!notBlank(documentId)) return 0;
        return jdbc.update("update ai_knowledge_document set status='INACTIVE', updated_at=now() where tenant_id=:tenantId and source_type='OUTLINE' and source_id=:sourceId and status='ACTIVE'",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("sourceId", documentId));
    }

    private List<Map<String, Object>> aliasRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) out.add(aliasRow(row));
        return out;
    }

    private Map<String, Object> aliasRow(Map<String, Object> row) {
        Map<String, Object> r = new LinkedHashMap<>(row);
        List<String> documentIds = parseDocumentIds(firstPresent(r, "document_ids", "documentIds"));
        r.put("document_ids", documentIds);
        r.put("documentIds", documentIds);
        if (!notBlank(str(firstPresent(r, "sync_scope", "syncScope")))) {
            r.put("sync_scope", SCOPE_COLLECTION);
            r.put("syncScope", SCOPE_COLLECTION);
        }
        alias(r, "tenant_id", "tenantId"); alias(r, "collection_id", "collectionId"); alias(r, "interval_minutes", "intervalMinutes");
        alias(r, "sync_scope", "syncScope"); alias(r, "cleanup_missing", "cleanupMissing"); alias(r, "vectorize_after_sync", "vectorizeAfterSync"); alias(r, "vector_force", "vectorForce");
        alias(r, "vector_limit", "vectorLimit"); alias(r, "webhook_enabled", "webhookEnabled"); alias(r, "webhook_secret", "webhookSecret");
        alias(r, "last_run_at", "lastRunAt"); alias(r, "next_run_at", "nextRunAt"); alias(r, "last_task_id", "lastTaskId");
        alias(r, "last_vector_status", "lastVectorStatus"); alias(r, "error_message", "errorMessage"); alias(r, "created_at", "createdAt"); alias(r, "updated_at", "updatedAt");
        alias(r, "config_id", "configId"); alias(r, "trigger_type", "triggerType"); alias(r, "outline_event", "outlineEvent");
        alias(r, "outline_document_id", "outlineDocumentId"); alias(r, "outline_collection_id", "outlineCollectionId"); alias(r, "sync_task_id", "syncTaskId");
        alias(r, "vectorize_triggered", "vectorizeTriggered"); alias(r, "vectorize_status", "vectorizeStatus"); alias(r, "vectorize_message", "vectorizeMessage");
        alias(r, "started_at", "startedAt"); alias(r, "finished_at", "finishedAt");
        return r;
    }

    private void alias(Map<String, Object> map, String snake, String camel) { if (map.containsKey(snake) && !map.containsKey(camel)) map.put(camel, map.get(snake)); }

    private Object firstPresent(Map<String, Object> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) if (map.containsKey(key)) return map.get(key);
        return null;
    }

    private void ensureConfigScopeColumns() {
        try {
            jdbc.getJdbcTemplate().execute("alter table outline_auto_sync_config add column if not exists sync_scope varchar(32) default 'COLLECTION'");
            jdbc.getJdbcTemplate().execute("alter table outline_auto_sync_config add column if not exists document_ids jsonb default '[]'::jsonb");
            jdbc.getJdbcTemplate().execute("update outline_auto_sync_config set sync_scope='COLLECTION' where sync_scope is null");
            jdbc.getJdbcTemplate().execute("update outline_auto_sync_config set document_ids='[]'::jsonb where document_ids is null");
        } catch (Exception ignored) {
        }
    }

    private String normalizeSyncScope(String scope) {
        String s = safe(scope, SCOPE_COLLECTION).trim().toUpperCase(Locale.ROOT);
        if (SCOPE_SINGLE_DOCUMENT.equals(s) || SCOPE_MULTIPLE_DOCUMENTS.equals(s) || SCOPE_COLLECTION.equals(s)) return s;
        return SCOPE_COLLECTION;
    }

    private boolean isDocumentScope(String scope) {
        return SCOPE_SINGLE_DOCUMENT.equals(scope) || SCOPE_MULTIPLE_DOCUMENTS.equals(scope);
    }

    private List<String> normalizeDocumentIds(String syncScope, List<String> ids) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (ids != null) {
            for (String id : ids) {
                if (notBlank(id)) set.add(id.trim());
            }
        }
        if (SCOPE_COLLECTION.equals(syncScope)) return new ArrayList<>();
        if (set.isEmpty()) throw new IllegalArgumentException("选择单文档或多文档同步时，必须选择至少一篇 Outline 文档");
        if (SCOPE_SINGLE_DOCUMENT.equals(syncScope) && set.size() > 1) {
            String first = set.iterator().next();
            return new ArrayList<>(Collections.singletonList(first));
        }
        return new ArrayList<>(set);
    }

    private List<String> parseDocumentIds(Object raw) {
        if (raw == null) return new ArrayList<>();
        if (raw instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) raw) if (item != null && notBlank(String.valueOf(item))) result.add(String.valueOf(item).trim());
            return result;
        }
        String text = String.valueOf(raw).trim();
        if (!notBlank(text) || "null".equalsIgnoreCase(text)) return new ArrayList<>();
        try {
            return normalizeDocumentIds(SCOPE_MULTIPLE_DOCUMENTS, MAPPER.readValue(text, new TypeReference<List<String>>() {}));
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value == null ? Collections.emptyList() : value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private void validateNoScopeConflict(String tenantId, String currentId, String syncScope, String collectionId, List<String> documentIds, boolean enabled, boolean webhookEnabled) {
        if (!enabled && !webhookEnabled) return;
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, name, collection_id, sync_scope, document_ids from outline_auto_sync_config " +
                        "where tenant_id=:tenantId and id<>:id and (enabled=true or webhook_enabled=true) and coalesce(status,'') <> 'STOPPED'",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", currentId));
        for (Map<String, Object> row : rows) {
            String otherScope = normalizeSyncScope(str(row.get("sync_scope")));
            String otherCollectionId = str(row.get("collection_id"));
            if (!sameCollection(collectionId, otherCollectionId)) continue;
            if (SCOPE_COLLECTION.equals(syncScope) || SCOPE_COLLECTION.equals(otherScope)) {
                throw new IllegalArgumentException("同步范围与已启用配置「" + safe(str(row.get("name")), str(row.get("id"))) + "」冲突，请先停用或调整该配置");
            }
            List<String> otherIds = parseDocumentIds(row.get("document_ids"));
            for (String id : documentIds) {
                if (otherIds.contains(id)) {
                    throw new IllegalArgumentException("文档「" + outlineDocumentLabel(tenantId, id) + "」已存在于配置「" + safe(str(row.get("name")), str(row.get("id"))) + "」中，不能重复配置自动同步任务");
                }
            }
        }
    }

    private String outlineDocumentLabel(String tenantId, String outlineDocumentId) {
        if (!notBlank(outlineDocumentId)) return "";
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "select title from ai_knowledge_document where tenant_id=:tenantId and source_type='OUTLINE' and source_id=:sourceId order by updated_at desc limit 1",
                    new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("sourceId", outlineDocumentId));
            if (!rows.isEmpty() && notBlank(str(rows.get(0).get("title")))) {
                return str(rows.get(0).get("title"));
            }
        } catch (Exception ignored) {
        }
        try {
            Map doc = outlineService.document(outlineDocumentId);
            if (doc != null && notBlank(str(doc.get("title")))) {
                return str(doc.get("title"));
            }
        } catch (Exception ignored) {
        }
        try {
            com.smartroad.srmp.agent.outline.dto.OutlineDocumentDTO doc = outlineSyncService.documents(null, 100, 0).stream()
                    .filter(item -> outlineDocumentId.equals(item.getId()))
                    .findFirst()
                    .orElse(null);
            if (doc != null && notBlank(doc.getTitle())) {
                return doc.getTitle();
            }
        } catch (Exception ignored) {
        }
        return outlineDocumentId;
    }

    private boolean sameCollection(String a, String b) {
        return safe(a, "").equals(safe(b, ""));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> child(Map<String, Object> map, String key) { return map != null && map.get(key) instanceof Map ? (Map<String, Object>) map.get(key) : new LinkedHashMap<>(); }
    private String extract(Map<String, Object> map, String... keys) { if (map == null) return ""; for (String k : keys) { Object v = map.get(k); if (v != null && notBlank(String.valueOf(v))) return String.valueOf(v); } return ""; }
    private boolean isDeleteOrArchive(String event) { String e = safe(event, "").toLowerCase(Locale.ROOT); return e.contains("delete") || e.contains("archive"); }
    private Timestamp nextRun(int interval) { return new Timestamp(System.currentTimeMillis() + interval * 60000L); }
    private int normalizeInterval(Integer v) { int x = v == null ? 60 : v; return Math.min(Math.max(x, 1), 1440); }
    private int normalizeVectorLimit(Integer v) { int x = v == null ? 500 : v; return Math.min(Math.max(x, 1), 2000); }
    private int normalizeRunLimit(Integer v) { int x = v == null ? 50 : v; return Math.min(Math.max(x, 1), 200); }
    private String uuid() { return UUID.randomUUID().toString().replace("-", ""); }
    private boolean bool(Boolean v) { return Boolean.TRUE.equals(v); }
    private boolean boolObj(Object v) { return v instanceof Boolean ? (Boolean) v : v != null && "true".equalsIgnoreCase(String.valueOf(v)); }
    private int intValue(Object v, int d) { try { return v == null ? d : Integer.parseInt(String.valueOf(v)); } catch(Exception e) { return d; } }
    private boolean notBlank(String v) { return v != null && v.trim().length() > 0; }
    private String blankToNull(String v) { return notBlank(v) ? v.trim() : null; }
    private String str(Object v) { return v == null ? "" : String.valueOf(v); }
    private String safe(String v, String d) { return notBlank(v) ? v.trim() : d; }
    private String firstNonBlank(String... vs) { if (vs == null) return ""; for (String v : vs) if (notBlank(v)) return v.trim(); return ""; }
    private String rootMessage(Throwable e) { Throwable c = e; while (c != null && c.getCause() != null) c = c.getCause(); return c == null ? "" : String.valueOf(c.getMessage()); }
}
