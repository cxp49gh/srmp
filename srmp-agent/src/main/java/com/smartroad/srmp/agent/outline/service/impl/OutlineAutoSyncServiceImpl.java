package com.smartroad.srmp.agent.outline.service.impl;

import com.smartroad.srmp.agent.knowledge.dto.AiKnowledgeReindexRequest;
import com.smartroad.srmp.agent.knowledge.service.AiKnowledgeReindexService;
import com.smartroad.srmp.agent.knowledge.vo.AiKnowledgeReindexResponse;
import com.smartroad.srmp.agent.outline.dto.OutlineAutoSyncConfigRequest;
import com.smartroad.srmp.agent.outline.dto.OutlineAutoSyncRunRequest;
import com.smartroad.srmp.agent.outline.dto.OutlineSyncRequest;
import com.smartroad.srmp.agent.outline.service.OutlineAutoSyncService;
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

    @Resource
    private NamedParameterJdbcTemplate jdbc;

    @Resource
    private OutlineSyncService outlineSyncService;

    @Resource
    private AiKnowledgeReindexService aiKnowledgeReindexService;

    @Override
    public List<Map<String, Object>> configs() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from outline_auto_sync_config where tenant_id=:tenantId order by created_at desc",
                new MapSqlParameterSource().addValue("tenantId", TenantContextHolder.getTenantId()));
        return aliasRows(rows);
    }

    @Override
    public Map<String, Object> config(String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from outline_auto_sync_config where tenant_id=:tenantId and id=:id",
                new MapSqlParameterSource().addValue("tenantId", TenantContextHolder.getTenantId()).addValue("id", id));
        return rows.isEmpty() ? new LinkedHashMap<>() : aliasRow(rows.get(0));
    }

    @Override
    @Transactional
    public Map<String, Object> saveConfig(OutlineAutoSyncConfigRequest request) {
        if (request == null) request = new OutlineAutoSyncConfigRequest();
        String id = notBlank(request.getId()) ? request.getId() : uuid();
        String tenantId = TenantContextHolder.getTenantId();
        int interval = normalizeInterval(request.getIntervalMinutes());
        jdbc.update(
                "insert into outline_auto_sync_config(id, tenant_id, name, enabled, collection_id, interval_minutes, force, cleanup_missing, vectorize_after_sync, vector_force, vector_limit, webhook_enabled, webhook_secret, next_run_at, status, created_at, updated_at) " +
                        "values(:id,:tenantId,:name,:enabled,:collectionId,:interval,:force,:cleanupMissing,:vectorizeAfterSync,:vectorForce,:vectorLimit,:webhookEnabled,:webhookSecret,:nextRunAt,'IDLE',now(),now())",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("tenantId", tenantId)
                        .addValue("name", safe(request.getName(), "Outline 自动同步"))
                        .addValue("enabled", bool(request.getEnabled()))
                        .addValue("collectionId", blankToNull(request.getCollectionId()))
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
        if (request == null) request = new OutlineAutoSyncConfigRequest();
        Map<String, Object> old = config(id);
        if (old.isEmpty()) throw new IllegalArgumentException("Outline 自动同步配置不存在：" + id);
        int interval = request.getIntervalMinutes() == null ? intValue(old.get("interval_minutes"), 60) : normalizeInterval(request.getIntervalMinutes());
        jdbc.update(
                "update outline_auto_sync_config set name=:name, enabled=:enabled, collection_id=:collectionId, interval_minutes=:interval, force=:force, cleanup_missing=:cleanupMissing, " +
                        "vectorize_after_sync=:vectorizeAfterSync, vector_force=:vectorForce, vector_limit=:vectorLimit, webhook_enabled=:webhookEnabled, webhook_secret=:webhookSecret, " +
                        "next_run_at=case when :enabled=true and (next_run_at is null or interval_minutes<>:interval) then :nextRunAt else next_run_at end, updated_at=now() " +
                        "where tenant_id=:tenantId and id=:id",
                new MapSqlParameterSource()
                        .addValue("tenantId", TenantContextHolder.getTenantId())
                        .addValue("id", id)
                        .addValue("name", firstNonBlank(request.getName(), str(old.get("name")), "Outline 自动同步"))
                        .addValue("enabled", request.getEnabled() == null ? boolObj(old.get("enabled")) : bool(request.getEnabled()))
                        .addValue("collectionId", request.getCollectionId() == null ? old.get("collection_id") : blankToNull(request.getCollectionId()))
                        .addValue("interval", interval)
                        .addValue("force", request.getForce() == null ? boolObj(old.get("force")) : bool(request.getForce()))
                        .addValue("cleanupMissing", request.getCleanupMissing() == null ? boolObj(old.get("cleanup_missing")) : bool(request.getCleanupMissing()))
                        .addValue("vectorizeAfterSync", request.getVectorizeAfterSync() == null ? boolObj(old.get("vectorize_after_sync")) : bool(request.getVectorizeAfterSync()))
                        .addValue("vectorForce", request.getVectorForce() == null ? boolObj(old.get("vector_force")) : bool(request.getVectorForce()))
                        .addValue("vectorLimit", request.getVectorLimit() == null ? intValue(old.get("vector_limit"), 500) : normalizeVectorLimit(request.getVectorLimit()))
                        .addValue("webhookEnabled", request.getWebhookEnabled() == null ? boolObj(old.get("webhook_enabled")) : bool(request.getWebhookEnabled()))
                        .addValue("webhookSecret", request.getWebhookSecret() == null ? old.get("webhook_secret") : blankToNull(request.getWebhookSecret()))
                        .addValue("nextRunAt", nextRun(interval)));
        return config(id);
    }

    @Override
    public Map<String, Object> runNow(String id, OutlineAutoSyncRunRequest request) {
        String tenantId = TenantContextHolder.getTenantId();
        Map<String, Object> cfg = loadConfig(tenantId, id);
        if (cfg.isEmpty()) throw new IllegalArgumentException("Outline 自动同步配置不存在：" + id);
        return runConfig(tenantId, cfg, request == null ? new OutlineAutoSyncRunRequest() : request);
    }

    @Override
    public List<Map<String, Object>> runs(String configId, Integer limit) {
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
        String tenantId = TenantContextHolder.getTenantId();
        if (!notBlank(secret)) throw new IllegalArgumentException("缺少 X-Outline-Webhook-Secret");
        String event = firstNonBlank(extract(payload, "event", "type", "name"), "document.updated");
        String docId = firstNonBlank(extract(payload, "documentId", "document_id", "id"),
                extract(child(payload, "document"), "id"),
                extract(child(payload, "data"), "documentId", "document_id", "id"),
                extract(child(child(payload, "data"), "document"), "id"));
        String collectionId = firstNonBlank(extract(payload, "collectionId", "collection_id"),
                extract(child(payload, "collection"), "id"),
                extract(child(payload, "data"), "collectionId", "collection_id"),
                extract(child(child(payload, "data"), "collection"), "id"));

        Map<String, Object> cfg = findWebhookConfig(tenantId, secret, collectionId);
        if (cfg.isEmpty()) throw new IllegalArgumentException("未找到匹配的 webhook 配置，或 webhook 未启用");

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
    }

    @Override
    public Map<String, Object> runDueConfigs() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from outline_auto_sync_config where enabled=true and (next_run_at is null or next_run_at<=now()) order by next_run_at asc limit 20",
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
        String runId = insertRun(tenantId, configId, firstNonBlank(request.getTriggerType(), "MANUAL"), request.getOutlineEvent(), docId, collectionId);
        try {
            markConfigRunning(tenantId, configId);
            OutlineSyncRequest sync = new OutlineSyncRequest();
            sync.setCollectionId(blankToNull(collectionId));
            sync.setLimit(2000);
            sync.setForce(request.getForce() == null ? boolObj(cfg.get("force")) : request.getForce());
            sync.setCleanupMissing(boolObj(cfg.get("cleanup_missing")) && !notBlank(docId));
            sync.setDryRun(false);
            if (notBlank(docId)) sync.setDocumentIds(Collections.singletonList(docId));

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
                if (notBlank(docId)) reindex.setSourceIds(Collections.singletonList(docId));
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
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from outline_auto_sync_config where tenant_id=:tenantId and id=:id",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", id));
        return rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
    }

    private Map<String, Object> findWebhookConfig(String tenantId, String secret, String collectionId) {
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

    private void markConfigRunning(String tenantId, String id) {
        jdbc.update("update outline_auto_sync_config set status='RUNNING', error_message=null, updated_at=now() where tenant_id=:tenantId and id=:id",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", id));
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
        alias(r, "tenant_id", "tenantId"); alias(r, "collection_id", "collectionId"); alias(r, "interval_minutes", "intervalMinutes");
        alias(r, "cleanup_missing", "cleanupMissing"); alias(r, "vectorize_after_sync", "vectorizeAfterSync"); alias(r, "vector_force", "vectorForce");
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
