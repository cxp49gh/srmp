package com.smartroad.srmp.agent.solution.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.agent.solution.dto.AiSolutionAiContextUpdateRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTaskVersionRestoreRequest;
import com.smartroad.srmp.agent.solution.service.AiSolutionTaskClosureService;
import com.smartroad.srmp.agent.solution.support.AiSolutionFallbackTemplateSupport;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

@Service
public class AiSolutionTaskClosureServiceImpl implements AiSolutionTaskClosureService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public Map<String, Object> updateAiContext(String taskId, AiSolutionAiContextUpdateRequest request) {
        if (safe(taskId).isEmpty()) throw new IllegalArgumentException("taskId 不能为空");
        if (request == null) request = new AiSolutionAiContextUpdateRequest();

        String tenantId = TenantContextHolder.getTenantId();
        Map<String, Object> task = loadTask(tenantId, taskId);
        if (task.isEmpty()) throw new IllegalArgumentException("方案任务不存在：" + taskId);

        Map<String, Object> aiContext = buildAiContext(request);
        namedParameterJdbcTemplate.update(
                "update ai_solution_task set ai_trace_id=:aiTraceId, ai_answer=:aiAnswer, ai_sources=cast(:aiSources as jsonb), " +
                        "ai_tool_results=cast(:aiToolResults as jsonb), ai_evidence=cast(:aiEvidence as jsonb), ai_context=cast(:aiContext as jsonb), " +
                        "generation_mode=:generationMode, updated_at=now() where tenant_id=:tenantId and id=:id",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("id", taskId)
                        .addValue("aiTraceId", safe(request.getAiTraceId()))
                        .addValue("aiAnswer", safe(request.getAiAnswer()))
                        .addValue("aiSources", toJson(request.getAiSources(), "[]"))
                        .addValue("aiToolResults", toJson(request.getAiToolResults(), "[]"))
                        .addValue("aiEvidence", toJson(request.getAiEvidence(), "{}"))
                        .addValue("aiContext", toJson(aiContext, "{}"))
                        .addValue("generationMode", safe(request.getGenerationMode()).isEmpty() ? "MAP_AI_ANALYSIS" : safe(request.getGenerationMode()))
        );
        syncLatestVersionAiContext(tenantId, taskId, aiContext);
        return aiContext(taskId);
    }

    @Override
    public Map<String, Object> aiContext(String taskId) {
        if (safe(taskId).isEmpty()) throw new IllegalArgumentException("taskId 不能为空");
        String tenantId = TenantContextHolder.getTenantId();
        Map<String, Object> task = loadTask(tenantId, taskId);
        if (task.isEmpty()) throw new IllegalArgumentException("方案任务不存在：" + taskId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("aiTraceId", safe(task.get("ai_trace_id")));
        result.put("aiAnswer", safe(task.get("ai_answer")));
        result.put("aiSources", objectValue(task.get("ai_sources"), new ArrayList<>()));
        result.put("aiToolResults", objectValue(task.get("ai_tool_results"), new ArrayList<>()));
        result.put("aiEvidence", objectValue(task.get("ai_evidence"), new LinkedHashMap<>()));
        result.put("aiContext", objectValue(task.get("ai_context"), new LinkedHashMap<>()));
        result.put("generationMode", safe(task.get("generation_mode")));
        return result;
    }

    @Override
    public List<Map<String, Object>> statusTimeline(String taskId) {
        if (safe(taskId).isEmpty()) throw new IllegalArgumentException("taskId 不能为空");
        String tenantId = TenantContextHolder.getTenantId();
        List<Map<String, Object>> logs = namedParameterJdbcTemplate.queryForList(
                "select id, tenant_id, task_id, from_status, to_status, action, operator, note, created_at " +
                        "from ai_solution_task_status_log where tenant_id=:tenantId and task_id=:taskId order by created_at asc",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("taskId", taskId));
        if (!logs.isEmpty()) return logs;

        Map<String, Object> task = loadTask(tenantId, taskId);
        List<Map<String, Object>> fallback = new ArrayList<>();
        if (!task.isEmpty()) {
            Map<String, Object> created = new LinkedHashMap<>();
            created.put("id", "created");
            created.put("task_id", taskId);
            created.put("from_status", "");
            created.put("to_status", "DRAFT");
            created.put("action", "CREATE_DRAFT");
            created.put("operator", "system");
            created.put("note", "创建方案草稿");
            created.put("created_at", task.get("created_at"));
            fallback.add(created);
            if (!"DRAFT".equals(safe(task.get("draft_status")))) {
                Map<String, Object> current = new LinkedHashMap<>();
                current.put("id", "current");
                current.put("task_id", taskId);
                current.put("from_status", "DRAFT");
                current.put("to_status", task.get("draft_status"));
                current.put("action", "CURRENT_STATUS");
                current.put("operator", "system");
                current.put("note", "当前状态");
                current.put("created_at", task.get("updated_at"));
                fallback.add(current);
            }
        }
        return fallback;
    }

    @Override
    @Transactional
    public Map<String, Object> restoreVersion(String taskId, Integer versionNo, AiSolutionTaskVersionRestoreRequest request) {
        if (safe(taskId).isEmpty()) throw new IllegalArgumentException("taskId 不能为空");
        if (versionNo == null || versionNo <= 0) throw new IllegalArgumentException("versionNo 无效");

        String tenantId = TenantContextHolder.getTenantId();
        Map<String, Object> task = lockTask(tenantId, taskId);
        if (task.isEmpty()) throw new IllegalArgumentException("方案任务不存在：" + taskId);
        if (!"DRAFT".equals(safe(task.get("draft_status")))) throw new IllegalArgumentException("只有草稿状态可以恢复历史版本");

        Map<String, Object> version = loadVersion(tenantId, taskId, versionNo);
        if (version.isEmpty()) throw new IllegalArgumentException("历史版本不存在：v" + versionNo);

        int nextVersion = intValue(task.get("current_version_no"), 1) + 1;
        String title = safe(version.get("title")).isEmpty() ? safe(task.get("title")) : safe(version.get("title"));
        String content = safe(version.get("result_content"));
        Object aiContext = firstPresent(version.get("ai_context"), task.get("ai_context"));

        namedParameterJdbcTemplate.update(
                "update ai_solution_task set title=:title, result_content=:resultContent, current_version_no=:currentVersionNo, updated_at=now() where tenant_id=:tenantId and id=:id",
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("id", taskId)
                        .addValue("title", title)
                        .addValue("resultContent", content)
                        .addValue("currentVersionNo", nextVersion)
        );
        insertVersion(tenantId, taskId, nextVersion, title, content, version.get("quality_result"), version.get("map_object"),
                version.get("object_summary"), version.get("source_snapshot"),
                safe(request == null ? null : request.getChangeNote()).isEmpty() ? "从 v" + versionNo + " 恢复" : safe(request.getChangeNote()),
                aiContext);
        return loadTask(tenantId, taskId);
    }

    @Override
    public String exportMarkdownV2(String taskId) {
        if (safe(taskId).isEmpty()) throw new IllegalArgumentException("taskId 不能为空");
        String tenantId = TenantContextHolder.getTenantId();
        Map<String, Object> task = loadTask(tenantId, taskId);
        if (task.isEmpty()) throw new IllegalArgumentException("方案任务不存在：" + taskId);
        List<Map<String, Object>> sources = loadSources(tenantId, taskId);
        Map<String, Object> ai = aiContext(taskId);

        StringBuilder md = new StringBuilder();
        md.append("# ").append(safe(task.get("title"))).append("\n\n");
        md.append("> 本文档由智路养护平台 AI 生成，作为方案草稿，需人工审核后使用。\n\n");
        md.append("## 一、任务信息\n\n");
        md.append("| 字段 | 内容 |\n|---|---|\n");
        md.append("| 任务ID | ").append(safe(task.get("id"))).append(" |\n");
        md.append("| 方案类型 | ").append(safe(task.get("solution_type"))).append(" |\n");
        md.append("| 路线编号 | ").append(safe(task.get("route_code"))).append(" |\n");
        md.append("| 年度 | ").append(safe(task.get("year"))).append(" |\n");
        md.append("| 来源类型 | ").append(safe(task.get("origin_type"))).append(" |\n");
        md.append("| 对象类型 | ").append(safe(task.get("object_type"))).append(" |\n");
        md.append("| 草稿状态 | ").append(safe(task.get("draft_status"))).append(" |\n");
        md.append("| 当前版本 | ").append(safe(task.get("current_version_no"))).append(" |\n");
        md.append("| AI Trace | ").append(safe(ai.get("aiTraceId"))).append(" |\n");
        md.append("| 生成模式 | ").append(safe(ai.get("generationMode"))).append(" |\n");
        md.append("| 创建时间 | ").append(safe(task.get("created_at"))).append(" |\n\n");

        String aiAnswer = safe(ai.get("aiAnswer"));
        if (!aiAnswer.isEmpty()) {
            md.append("## 二、AI 分析摘要\n\n").append(aiAnswer).append("\n\n");
        }

        String fixedResultContent = AiSolutionFallbackTemplateSupport.repairFallbackContentIfNeeded(
                safe(task.get("result_content")),
                task,
                safe(ai.get("aiAnswer"))
        );
        md.append("## 三、方案正文\n\n").append(fixedResultContent).append("\n\n");

        md.append("## 四、AI 依据摘要\n\n");
        md.append("- AI Trace：").append(safe(ai.get("aiTraceId"))).append("\n");
        md.append("- 生成模式：").append(safe(ai.get("generationMode"))).append("\n");
        md.append("- Evidence：").append(shortText(toJson(ai.get("aiEvidence"), "{}"), 1200)).append("\n");
        md.append("- Tool Results：").append(shortText(toJson(ai.get("aiToolResults"), "[]"), 1200)).append("\n");
        md.append("- AI Sources：").append(shortText(toJson(ai.get("aiSources"), "[]"), 1200)).append("\n\n");

        md.append("## 五、引用来源\n\n");
        if (sources.isEmpty()) {
            md.append("暂无引用来源。\n\n");
        } else {
            int i = 1;
            for (Map<String, Object> source : sources) {
                md.append(i++).append(". **").append(safe(source.get("source_title"))).append("**（").append(safe(source.get("source_type"))).append("）\n\n");
                if (!safe(source.get("source_url")).isEmpty()) md.append("   - 链接：").append(safe(source.get("source_url"))).append("\n");
                md.append("   - 摘要：").append(safe(source.get("content_excerpt"))).append("\n\n");
            }
        }
        md.append("## 六、审核提示\n\n");
        md.append("- 本方案为 AI 生成草稿，需结合现场复核、规范要求和管理审批后使用。\n");
        md.append("- 若涉及路基、基层、排水或交通安全风险，应优先安排现场核查。\n");
        return md.toString();
    }

    private Map<String, Object> buildAiContext(AiSolutionAiContextUpdateRequest request) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("aiTraceId", safe(request.getAiTraceId()));
        ctx.put("aiAnswer", safe(request.getAiAnswer()));
        ctx.put("aiSources", request.getAiSources() == null ? new ArrayList<>() : request.getAiSources());
        ctx.put("aiToolResults", request.getAiToolResults() == null ? new ArrayList<>() : request.getAiToolResults());
        ctx.put("aiEvidence", request.getAiEvidence() == null ? new LinkedHashMap<>() : request.getAiEvidence());
        ctx.put("generationMode", safe(request.getGenerationMode()).isEmpty() ? "MAP_AI_ANALYSIS" : safe(request.getGenerationMode()));
        ctx.put("raw", request.getAiContext() == null ? new LinkedHashMap<>() : request.getAiContext());
        ctx.put("savedAt", new Date());
        return ctx;
    }

    private void syncLatestVersionAiContext(String tenantId, String taskId, Object aiContext) {
        try {
            namedParameterJdbcTemplate.update(
                    "update ai_solution_task_version set ai_context=cast(:aiContext as jsonb) where tenant_id=:tenantId and task_id=:taskId and version_no=(select max(version_no) from ai_solution_task_version where tenant_id=:tenantId and task_id=:taskId)",
                    new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("taskId", taskId).addValue("aiContext", toJson(aiContext, "{}")));
        } catch (Exception ignored) {}
    }

    private Map<String, Object> loadTask(String tenantId, String taskId) {
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                "select * from ai_solution_task where tenant_id=:tenantId and id=:id",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", taskId));
        return rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
    }

    private Map<String, Object> lockTask(String tenantId, String taskId) {
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                "select * from ai_solution_task where tenant_id=:tenantId and id=:id for update",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", taskId));
        return rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
    }

    private Map<String, Object> loadVersion(String tenantId, String taskId, Integer versionNo) {
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                "select * from ai_solution_task_version where tenant_id=:tenantId and task_id=:taskId and version_no=:versionNo",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("taskId", taskId).addValue("versionNo", versionNo));
        return rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
    }

    private List<Map<String, Object>> loadSources(String tenantId, String taskId) {
        return namedParameterJdbcTemplate.queryForList(
                "select * from ai_solution_source where tenant_id=:tenantId and task_id=:taskId order by created_at asc",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("taskId", taskId));
    }

    private void insertVersion(String tenantId, String taskId, int versionNo, String title, String content, Object quality,
                               Object mapObject, Object objectSummary, Object sourceSnapshot, String changeNote, Object aiContext) {
        namedParameterJdbcTemplate.update(
                "insert into ai_solution_task_version(id, tenant_id, task_id, version_no, title, result_content, quality_result, map_object, object_summary, source_snapshot, change_note, ai_context, created_at) values (:id, :tenantId, :taskId, :versionNo, :title, :resultContent, cast(:qualityResult as jsonb), cast(:mapObject as jsonb), cast(:objectSummary as jsonb), cast(:sourceSnapshot as jsonb), :changeNote, cast(:aiContext as jsonb), now())",
                new MapSqlParameterSource()
                        .addValue("id", uuid()).addValue("tenantId", tenantId).addValue("taskId", taskId).addValue("versionNo", versionNo)
                        .addValue("title", safe(title)).addValue("resultContent", content).addValue("qualityResult", toJson(quality, "{}"))
                        .addValue("mapObject", toJson(mapObject, "{}")).addValue("objectSummary", toJson(objectSummary, "{}"))
                        .addValue("sourceSnapshot", toJson(sourceSnapshot, "[]")).addValue("changeNote", safe(changeNote))
                        .addValue("aiContext", toJson(aiContext, "{}")));
    }

    private Object objectValue(Object value, Object defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Map || value instanceof List) return value;
        String text = safe(value);
        try {
            if (text.startsWith("{")) return objectMapper.readValue(text, Map.class);
            if (text.startsWith("[")) return objectMapper.readValue(text, List.class);
        } catch (Exception ignored) {}
        return defaultValue;
    }

    private Object firstPresent(Object a, Object b) { return a == null ? b : a; }

    private int intValue(Object value, int defaultValue) {
        try { return value == null ? defaultValue : Integer.parseInt(String.valueOf(value)); } catch (Exception e) { return defaultValue; }
    }

    private String toJson(Object value, String defaultJson) {
        try {
            if (value == null) return defaultJson;
            if (value instanceof String) {
                String text = (String) value;
                return text.trim().isEmpty() ? defaultJson : text;
            }
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) { return defaultJson; }
    }

    private String safe(Object value) { return value == null ? "" : String.valueOf(value); }

    private String shortText(String text, int max) {
        if (text == null || text.length() <= max) return text == null ? "" : text;
        return text.substring(0, max) + "...";
    }

    private String uuid() { return UUID.randomUUID().toString().replace("-", ""); }
}
