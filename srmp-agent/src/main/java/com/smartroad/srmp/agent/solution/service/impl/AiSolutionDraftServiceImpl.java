package com.smartroad.srmp.agent.solution.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.agent.solution.dto.AiSolutionDraftSaveRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionDraftStatusUpdateRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionDraftUpdateRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionSourceSummaryRequest;
import com.smartroad.srmp.agent.solution.service.AiSolutionDraftService;
import com.smartroad.srmp.agent.solution.support.AiSolutionReferenceSourceGuard;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

@Service
public class AiSolutionDraftServiceImpl implements AiSolutionDraftService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public Map<String, Object> saveMapObjectDraft(AiSolutionDraftSaveRequest request) {
        return saveDraft(request, "MAP_OBJECT");
    }

    @Override
    @Transactional
    public Map<String, Object> saveMapRegionDraft(AiSolutionDraftSaveRequest request) {
        return saveDraft(request, "MAP_REGION");
    }

    private Map<String, Object> saveDraft(AiSolutionDraftSaveRequest request, String originType) {
        validateSave(request, originType);

        String tenantId = TenantContextHolder.getTenantId();
        String taskId = uuid();
        Map<String, Object> mapObject = request.getMapObject() == null ? new LinkedHashMap<>() : request.getMapObject();
        Map<String, Object> objectSummary = request.getRegionSummary() != null
                ? request.getRegionSummary()
                : (request.getObjectSummary() == null ? new LinkedHashMap<>() : request.getObjectSummary());
        Map<String, Object> quality = "MAP_REGION".equals(originType)
                ? normalizeRegionQuality(request.getQualityCheck())
                : normalizeMapObjectQuality(request.getQualityCheck());
        List<Map<String, Object>> sourceSnapshot = buildSourceSnapshot(request);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", taskId)
                .addValue("tenantId", tenantId)
                .addValue("solutionType", safe(request.getSolutionType()))
                .addValue("title", safe(request.getTitle()))
                .addValue("routeCode", safe(request.getRouteCode()))
                .addValue("year", request.getYear())
                .addValue("templateId", safe(request.getTemplateId()))
                .addValue("templateVersion", safe(request.getTemplateVersion()))
                .addValue("status", "SUCCESS")
                .addValue("requestJson", toJson(buildRequestJson(request), "{}"))
                .addValue("resultContent", request.getMarkdown())
                .addValue("qualityResult", toJson(quality, "{}"))
                .addValue("originType", originType)
                .addValue("objectType", "MAP_REGION".equals(originType) ? "MAP_REGION" : firstString(objectSummary, mapObject, "objectType", "object_type", "type", "layerType", "assessment_object_type"))
                .addValue("objectId", "MAP_REGION".equals(originType) ? firstString(request.getTrace(), mapObject, "traceId", "id") : firstString(objectSummary, mapObject, "objectId", "object_id", "id"))
                .addValue("mapObject", toJson(mapObject, "{}"))
                .addValue("objectSummary", toJson(objectSummary, "{}"))
                .addValue("templateMeta", toJson(request.getTemplateMeta(), "{}"))
                .addValue("draftStatus", "DRAFT")
                .addValue("currentVersionNo", 1);

        namedParameterJdbcTemplate.update(
                "insert into ai_solution_task(" +
                        "id, tenant_id, solution_type, title, route_code, year, template_id, template_version, status, request_json, result_content, quality_result, created_at, updated_at, origin_type, object_type, object_id, map_object, object_summary, template_meta, draft_status, current_version_no" +
                        ") values (" +
                        ":id, :tenantId, :solutionType, :title, :routeCode, :year, :templateId, :templateVersion, :status, cast(:requestJson as jsonb), :resultContent, cast(:qualityResult as jsonb), now(), now(), :originType, :objectType, :objectId, cast(:mapObject as jsonb), cast(:objectSummary as jsonb), cast(:templateMeta as jsonb), :draftStatus, :currentVersionNo" +
                        ")",
                params
        );

        insertSources(tenantId, taskId, sourceSnapshot);
        insertVersion(tenantId, taskId, 1, request.getTitle(), request.getMarkdown(), quality, mapObject, objectSummary, sourceSnapshot, "创建草稿");
        return loadTask(tenantId, taskId);
    }

    @Override
    @Transactional
    public Map<String, Object> updateDraft(String taskId, AiSolutionDraftUpdateRequest request) {
        if (safe(taskId).isEmpty()) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        if (request == null || safe(request.getMarkdown()).isEmpty()) {
            throw new IllegalArgumentException("markdown 不能为空");
        }

        String tenantId = TenantContextHolder.getTenantId();
        Map<String, Object> task = lockTask(tenantId, taskId);
        if (task.isEmpty()) {
            throw new IllegalArgumentException("方案任务不存在：" + taskId);
        }
        if (!"DRAFT".equals(safe(task.get("draft_status")))) {
            throw new IllegalArgumentException("只有草稿状态可编辑");
        }

        int nextVersion = intValue(task.get("current_version_no"), 1) + 1;
        String title = safe(request.getTitle()).isEmpty() ? safe(task.get("title")) : safe(request.getTitle());

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", taskId)
                .addValue("title", title)
                .addValue("resultContent", request.getMarkdown())
                .addValue("currentVersionNo", nextVersion);
        namedParameterJdbcTemplate.update(
                "update ai_solution_task set title=:title, result_content=:resultContent, current_version_no=:currentVersionNo, updated_at=now() where tenant_id=:tenantId and id=:id",
                params
        );

        insertVersion(
                tenantId,
                taskId,
                nextVersion,
                title,
                request.getMarkdown(),
                task.get("quality_result"),
                task.get("map_object"),
                task.get("object_summary"),
                sourceSnapshotWithTemplateMeta(loadSources(tenantId, taskId), task.get("template_meta")),
                safe(request.getChangeNote()).isEmpty() ? "更新草稿" : safe(request.getChangeNote())
        );
        return loadTask(tenantId, taskId);
    }

    @Override
    @Transactional
    public Map<String, Object> updateDraftStatus(String taskId, AiSolutionDraftStatusUpdateRequest request) {
        if (safe(taskId).isEmpty()) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        String next = request == null ? "" : safe(request.getDraftStatus()).toUpperCase(Locale.ROOT);
        if (!Arrays.asList("DRAFT", "CONFIRMED", "ARCHIVED").contains(next)) {
            throw new IllegalArgumentException("draftStatus 只支持 DRAFT / CONFIRMED / ARCHIVED");
        }

        String tenantId = TenantContextHolder.getTenantId();
        Map<String, Object> task = lockTask(tenantId, taskId);
        if (task.isEmpty()) {
            throw new IllegalArgumentException("方案任务不存在：" + taskId);
        }
        String current = safe(task.get("draft_status")).isEmpty() ? "DRAFT" : safe(task.get("draft_status"));
        if (!canTransition(current, next)) {
            throw new IllegalArgumentException("不允许从 " + current + " 流转到 " + next);
        }

        namedParameterJdbcTemplate.update(
                "update ai_solution_task set draft_status=:draftStatus, updated_at=now() where tenant_id=:tenantId and id=:id",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("id", taskId)
                        .addValue("draftStatus", next)
        );
        return loadTask(tenantId, taskId);
    }

    @Override
    public List<Map<String, Object>> versions(String taskId) {
        if (safe(taskId).isEmpty()) {
            throw new IllegalArgumentException("taskId 不能为空");
        }
        return namedParameterJdbcTemplate.queryForList(
                "select id, tenant_id, task_id, version_no, title, result_content, quality_result, map_object, object_summary, source_snapshot, change_note, created_at " +
                        "from ai_solution_task_version where tenant_id=:tenantId and task_id=:taskId order by version_no desc",
                new MapSqlParameterSource()
                        .addValue("tenantId", TenantContextHolder.getTenantId())
                        .addValue("taskId", taskId)
        );
    }

    private void validateSave(AiSolutionDraftSaveRequest request, String originType) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        if (safe(request.getSolutionType()).isEmpty()) {
            throw new IllegalArgumentException("solutionType 不能为空");
        }
        if (safe(request.getTitle()).isEmpty()) {
            throw new IllegalArgumentException("title 不能为空");
        }
        if (safe(request.getMarkdown()).isEmpty()) {
            throw new IllegalArgumentException("markdown 不能为空");
        }
        if (request.getMapObject() == null || request.getMapObject().isEmpty()) {
            throw new IllegalArgumentException("mapObject 不能为空");
        }
        if ("MAP_REGION".equals(originType) && (request.getRegionSummary() == null || request.getRegionSummary().isEmpty())) {
            throw new IllegalArgumentException("regionSummary 不能为空");
        }
    }

    private Map<String, Object> normalizeMapObjectQuality(Map<String, Object> qualityCheck) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        int score = 100;

        Object rawItems = qualityCheck == null ? null : qualityCheck.get("items");
        if (rawItems instanceof List) {
            for (Object rawItem : (List<?>) rawItems) {
                Map<String, Object> converted = convertQualityItem(rawItem);
                if (!converted.isEmpty()) {
                    items.add(converted);
                    score -= intValue(converted.get("penalty"), 0);
                }
            }
        }

        Object rawWarnings = qualityCheck == null ? null : qualityCheck.get("warnings");
        if (rawWarnings instanceof List) {
            for (Object warning : (List<?>) rawWarnings) {
                String message = safe(warning);
                if (!message.isEmpty()) {
                    Map<String, Object> item = item("WARN", "MAP_OBJECT_WARNING", message, 8);
                    items.add(item);
                    score -= 8;
                }
            }
        }

        if (qualityCheck != null && qualityCheck.get("score") != null) {
            score = intValue(qualityCheck.get("score"), score);
        }
        if (score < 0) {
            score = 0;
        }
        boolean passed = qualityCheck != null && qualityCheck.get("passed") instanceof Boolean
                ? (Boolean) qualityCheck.get("passed")
                : score >= 80 && items.stream().noneMatch(it -> "ERROR".equals(it.get("level")));

        result.put("passed", passed);
        result.put("score", score);
        result.put("level", score >= 90 ? "A" : score >= 80 ? "B" : score >= 60 ? "C" : "D");
        result.put("summary", "地图对象方案质量校验" + (passed ? "通过" : "需复核") + "，评分 " + score + "。");
        result.put("items", items);
        result.put("originType", "MAP_OBJECT");
        result.put("raw", qualityCheck == null ? new LinkedHashMap<>() : qualityCheck);
        result.put("checkedAt", new Date());
        return result;
    }

    private Map<String, Object> normalizeRegionQuality(Map<String, Object> qualityCheck) {
        if (qualityCheck != null && qualityCheck.get("score") != null && qualityCheck.get("items") instanceof List) {
            Map<String, Object> result = new LinkedHashMap<>(qualityCheck);
            result.put("originType", "MAP_REGION");
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item("WARN", "REGION_QUALITY_IMPORTED", "保存时未收到完整区域质量结果，需重新校验", 10));
        result.put("passed", false);
        result.put("score", 70);
        result.put("level", "C");
        result.put("summary", "区域方案质量结果不完整，建议重新校验。");
        result.put("items", items);
        result.put("originType", "MAP_REGION");
        result.put("raw", qualityCheck == null ? new LinkedHashMap<>() : qualityCheck);
        result.put("checkedAt", new Date());
        return result;
    }

    private Map<String, Object> convertQualityItem(Object rawItem) {
        if (!(rawItem instanceof Map)) {
            return new LinkedHashMap<>();
        }
        Map<?, ?> source = (Map<?, ?>) rawItem;
        String name = safe(source.get("name"));
        String level = safe(source.get("level"));
        String message = safe(source.get("message"));
        boolean passed = readBoolean(source.get("passed"), "OK".equals(level));
        if (level.isEmpty()) {
            level = passed ? "OK" : "WARN";
        }
        int penalty = passed ? 0 : ("ERROR".equals(level) ? 15 : 10);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("level", level);
        item.put("code", name.isEmpty() ? "MAP_OBJECT_QUALITY" : "MAP_OBJECT_" + name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_"));
        item.put("message", message.isEmpty() ? name : message);
        item.put("penalty", penalty);
        return item;
    }

    private List<Map<String, Object>> buildSourceSnapshot(AiSolutionDraftSaveRequest request) {
        List<Map<String, Object>> sources = new ArrayList<>();
        if (request.getSourceSummaries() != null) {
            for (AiSolutionSourceSummaryRequest source : request.getSourceSummaries()) {
                Map<String, Object> item = sourceToMap(source);
                if (!item.isEmpty()) {
                    sources.add(item);
                }
            }
        }
        if (!safe(request.getTemplateId()).isEmpty() || !safe(request.getTemplateName()).isEmpty()) {
            Map<String, Object> template = new LinkedHashMap<>();
            template.put("sourceType", "TEMPLATE");
            template.put("sourceTitle", safe(request.getTemplateName()).isEmpty() ? "方案模板" : safe(request.getTemplateName()));
            template.put("sourceId", safe(request.getTemplateId()));
            template.put("sourceUrl", "");
            template.put("contentExcerpt", safe(request.getTemplateVersion()).isEmpty() ? "" : "模板版本：" + safe(request.getTemplateVersion()));
            sources.add(template);
        }
        if (request.getTemplateMeta() != null && !request.getTemplateMeta().isEmpty()) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("sourceType", "TEMPLATE_META");
            meta.put("sourceTitle", "模板元信息");
            meta.put("sourceId", safe(firstPresent(request.getTemplateMeta().get("templateId"), request.getTemplateId())));
            meta.put("sourceUrl", "");
            meta.put("contentExcerpt", shortText(toJson(request.getTemplateMeta(), "{}"), 500));
            meta.put("templateMeta", request.getTemplateMeta());
            sources.add(meta);
        }
        return AiSolutionReferenceSourceGuard.filterIrrelevantOutlineSources(sources);
    }

    private Map<String, Object> sourceToMap(AiSolutionSourceSummaryRequest source) {
        Map<String, Object> item = new LinkedHashMap<>();
        if (source == null || safe(source.getSourceType()).isEmpty()) {
            return item;
        }
        item.put("sourceType", safe(source.getSourceType()).toUpperCase(Locale.ROOT));
        item.put("sourceTitle", safe(source.getSourceTitle()));
        item.put("sourceId", safe(source.getSourceId()));
        item.put("sourceUrl", safe(source.getSourceUrl()));
        item.put("contentExcerpt", safe(source.getContentExcerpt()));
        return item;
    }

    private Map<String, Object> buildRequestJson(AiSolutionDraftSaveRequest request) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("solutionType", request.getSolutionType());
        json.put("routeCode", request.getRouteCode());
        json.put("year", request.getYear());
        json.put("originType", request.getOriginType());
        json.put("objectType", request.getObjectType());
        json.put("objectId", request.getObjectId());
        json.put("regionSummary", request.getRegionSummary() == null ? new LinkedHashMap<>() : request.getRegionSummary());
        json.put("trace", request.getTrace() == null ? new LinkedHashMap<>() : request.getTrace());
        json.put("templateId", request.getTemplateId());
        json.put("templateVersion", request.getTemplateVersion());
        json.put("templateName", request.getTemplateName());
        json.put("templateMeta", request.getTemplateMeta() == null ? new LinkedHashMap<>() : request.getTemplateMeta());
        json.put("options", request.getOptions() == null ? new LinkedHashMap<>() : request.getOptions());
        json.put("requestContext", request.getRequestContext() == null ? new LinkedHashMap<>() : request.getRequestContext());
        json.put("sourceSummaries", buildSourceSnapshot(request));
        return json;
    }

    private void insertSources(String tenantId, String taskId, List<Map<String, Object>> sources) {
        for (Map<String, Object> source : sources) {
            namedParameterJdbcTemplate.update(
                    "insert into ai_solution_source(" +
                            "id, tenant_id, task_id, source_type, source_title, source_id, source_url, content_excerpt, created_at" +
                            ") values (" +
                            ":id, :tenantId, :taskId, :sourceType, :sourceTitle, :sourceId, :sourceUrl, :contentExcerpt, now()" +
                            ")",
                    new MapSqlParameterSource()
                            .addValue("id", uuid())
                            .addValue("tenantId", tenantId)
                            .addValue("taskId", taskId)
                            .addValue("sourceType", safe(source.get("sourceType")))
                            .addValue("sourceTitle", safe(source.get("sourceTitle")))
                            .addValue("sourceId", safe(source.get("sourceId")))
                            .addValue("sourceUrl", safe(source.get("sourceUrl")))
                            .addValue("contentExcerpt", shortText(safe(source.get("contentExcerpt")), 500))
            );
        }
    }

    private void insertVersion(String tenantId,
                               String taskId,
                               int versionNo,
                               String title,
                               String content,
                               Object quality,
                               Object mapObject,
                               Object objectSummary,
                               Object sourceSnapshot,
                               String changeNote) {
        namedParameterJdbcTemplate.update(
                "insert into ai_solution_task_version(" +
                        "id, tenant_id, task_id, version_no, title, result_content, quality_result, map_object, object_summary, source_snapshot, change_note, created_at" +
                        ") values (" +
                        ":id, :tenantId, :taskId, :versionNo, :title, :resultContent, cast(:qualityResult as jsonb), cast(:mapObject as jsonb), cast(:objectSummary as jsonb), cast(:sourceSnapshot as jsonb), :changeNote, now()" +
                        ")",
                new MapSqlParameterSource()
                        .addValue("id", uuid())
                        .addValue("tenantId", tenantId)
                        .addValue("taskId", taskId)
                        .addValue("versionNo", versionNo)
                        .addValue("title", safe(title))
                        .addValue("resultContent", content)
                        .addValue("qualityResult", toJson(quality, "{}"))
                        .addValue("mapObject", toJson(mapObject, "{}"))
                        .addValue("objectSummary", toJson(objectSummary, "{}"))
                        .addValue("sourceSnapshot", toJson(sourceSnapshot, "[]"))
                        .addValue("changeNote", safe(changeNote))
        );
    }

    private Map<String, Object> loadTask(String tenantId, String taskId) {
        List<Map<String, Object>> list = namedParameterJdbcTemplate.queryForList(
                "select id, tenant_id, solution_type, title, route_code, year, template_id, template_version, status, request_json, result_content, quality_result, created_at, updated_at, origin_type, object_type, object_id, map_object, object_summary, template_meta, draft_status, current_version_no " +
                        "from ai_solution_task where tenant_id=:tenantId and id=:id",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("id", taskId)
        );
        return list.isEmpty() ? new LinkedHashMap<>() : list.get(0);
    }

    private Map<String, Object> lockTask(String tenantId, String taskId) {
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                "select id, tenant_id, title, result_content, quality_result, map_object, object_summary, template_meta, draft_status, current_version_no " +
                        "from ai_solution_task where tenant_id=:tenantId and id=:id for update",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("id", taskId)
        );
        return rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
    }

    private List<Map<String, Object>> loadSources(String tenantId, String taskId) {
        return namedParameterJdbcTemplate.queryForList(
                "select source_type, source_title, source_id, source_url, content_excerpt, created_at " +
                        "from ai_solution_source where tenant_id=:tenantId and task_id=:taskId order by created_at asc",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("taskId", taskId)
        );
    }

    private List<Map<String, Object>> sourceSnapshotWithTemplateMeta(List<Map<String, Object>> sources, Object templateMetaRaw) {
        List<Map<String, Object>> snapshot = new ArrayList<>();
        if (sources != null) {
            for (Map<String, Object> source : sources) {
                if (!"TEMPLATE_META".equals(safe(firstPresent(source.get("sourceType"), source.get("source_type"))))) {
                    snapshot.add(new LinkedHashMap<>(source));
                }
            }
        }
        Map<String, Object> templateMeta = objectMap(templateMetaRaw);
        if (!templateMeta.isEmpty()) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("sourceType", "TEMPLATE_META");
            meta.put("sourceTitle", "模板元信息");
            meta.put("sourceId", safe(templateMeta.get("templateId")));
            meta.put("sourceUrl", "");
            meta.put("contentExcerpt", shortText(toJson(templateMeta, "{}"), 500));
            meta.put("templateMeta", templateMeta);
            snapshot.add(meta);
        }
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) value);
        }
        String text = safe(value);
        if (text.startsWith("{")) {
            try {
                return objectMapper.readValue(text, Map.class);
            } catch (Exception ignored) {
                return new LinkedHashMap<>();
            }
        }
        return new LinkedHashMap<>();
    }

    private boolean canTransition(String current, String next) {
        if (current.equals(next)) {
            return true;
        }
        if ("DRAFT".equals(current)) {
            return "CONFIRMED".equals(next) || "ARCHIVED".equals(next);
        }
        if ("CONFIRMED".equals(current)) {
            return "ARCHIVED".equals(next);
        }
        return false;
    }

    private Map<String, Object> item(String level, String code, String message, int penalty) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("level", level);
        item.put("code", code);
        item.put("message", message);
        item.put("penalty", penalty);
        return item;
    }

    private String firstString(Map<String, Object> primary, Map<String, Object> secondary, String... keys) {
        for (String key : keys) {
            String value = safe(primary == null ? null : primary.get(key));
            if (!value.isEmpty()) {
                return value;
            }
            value = safe(secondary == null ? null : secondary.get(key));
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private boolean readBoolean(Object value, boolean def) {
        if (value == null) {
            return def;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private Object firstPresent(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null && String.valueOf(value).trim().length() > 0) {
                return value;
            }
        }
        return null;
    }

    private int intValue(Object value, int def) {
        if (value == null) {
            return def;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return def;
        }
    }

    private String shortText(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String toJson(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return fallback;
            }
            if (text.startsWith("{") || text.startsWith("[")) {
                return text;
            }
        }
        String text = String.valueOf(value).trim();
        if ((text.startsWith("{") || text.startsWith("[")) && !(value instanceof Map) && !(value instanceof List)) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
