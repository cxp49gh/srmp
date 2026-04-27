package com.smartroad.srmp.agent.solution.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.agent.dto.AgentAnalysisRequest;
import com.smartroad.srmp.agent.knowledge.dto.KnowledgeSearchRequest;
import com.smartroad.srmp.agent.knowledge.service.KnowledgeService;
import com.smartroad.srmp.agent.knowledge.vo.KnowledgeSearchResult;
import com.smartroad.srmp.agent.llm.LlmClient;
import com.smartroad.srmp.agent.service.AgentAnalysisService;
import com.smartroad.srmp.agent.solution.dto.AiSolutionGenerateRequest;
import com.smartroad.srmp.agent.solution.dto.AiSolutionTaskQuery;
import com.smartroad.srmp.agent.solution.service.AiSolutionGenerateService;
import com.smartroad.srmp.agent.solution.template.MarkdownTemplateRenderer;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

@Service
public class AiSolutionGenerateServiceImpl implements AiSolutionGenerateService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Resource
    private AgentAnalysisService agentAnalysisService;

    @Resource
    private KnowledgeService knowledgeService;

    @Resource
    private LlmClient llmClient;

    @Resource
    private MarkdownTemplateRenderer templateRenderer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> generate(AiSolutionGenerateRequest request) {
        validate(request);

        String tenantId = TenantContextHolder.getTenantId();
        Map<String, Object> template = resolveTemplate(tenantId, request);
        if (template.isEmpty()) {
            throw new IllegalArgumentException("未找到可用方案模板，请先在方案模板页面创建模板");
        }

        String templateId = String.valueOf(template.get("id"));
        String templateVersion = String.valueOf(template.get("current_version"));
        String templateContent = String.valueOf(template.get("content"));

        AgentAnalysisRequest analysisRequest = new AgentAnalysisRequest();
        analysisRequest.setRouteCode(request.getRouteCode());
        analysisRequest.setYear(request.getYear());

        String routeSummary = safe(agentAnalysisService.analyzeRoute(analysisRequest).getMarkdown());
        String assessmentSummary = safe(agentAnalysisService.analyzeAssessment(analysisRequest).getMarkdown());
        String diseaseSummary = safe(agentAnalysisService.analyzeDisease(analysisRequest).getMarkdown());

        List<KnowledgeSearchResult> knowledgeSources = searchKnowledge(request, assessmentSummary + "\n" + diseaseSummary);

        String lowScoreSections = buildLowScoreSections(assessmentSummary);
        String problemAnalysis = buildProblemAnalysis(assessmentSummary, diseaseSummary, knowledgeSources);
        String maintenanceSuggestion = buildMaintenanceSuggestion(request, knowledgeSources);
        String riskNotice = "本方案由 AI 根据业务数据和知识库资料生成，仅作为草稿，正式养护计划和工单需由管理人员审核确认。";

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("routeCode", request.getRouteCode());
        variables.put("year", request.getYear());
        variables.put("routeSummary", routeSummary);
        variables.put("assessmentSummary", assessmentSummary);
        variables.put("diseaseSummary", diseaseSummary);
        variables.put("lowScoreSections", lowScoreSections);
        variables.put("problemAnalysis", problemAnalysis);
        variables.put("maintenanceSuggestion", maintenanceSuggestion);
        variables.put("riskNotice", riskNotice);

        String draft = templateRenderer.render(templateContent, variables);
        String finalContent = polishWithLlm(draft, knowledgeSources);

        String taskId = uuid();
        String title = request.getRouteCode() + " " + request.getYear() + " 年" + readableSolutionType(request.getSolutionType());

        insertTask(tenantId, taskId, request, title, templateId, templateVersion, finalContent);
        insertSources(tenantId, taskId, template, knowledgeSources, routeSummary, assessmentSummary, diseaseSummary);

        Map<String, Object> result = task(taskId);
        result.put("sources", sources(taskId));
        return result;
    }

    @Override
    public Map<String, Object> task(String id) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("id", id);

        List<Map<String, Object>> list = namedParameterJdbcTemplate.queryForList(
                "select id, tenant_id, solution_type, title, route_code, year, template_id, template_version, status, " +
                        "request_json, result_content, quality_result, created_at, updated_at " +
                        "from ai_solution_task where tenant_id=:tenantId and id=:id",
                params
        );
        return list.isEmpty() ? new LinkedHashMap<>() : list.get(0);
    }

    @Override
    public List<Map<String, Object>> tasks(AiSolutionTaskQuery query) {
        int limit = query == null || query.getLimit() == null ? 50 : query.getLimit();
        if (limit <= 0) {
            limit = 50;
        }
        if (limit > 200) {
            limit = 200;
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("solutionType", safe(query == null ? null : query.getSolutionType()))
                .addValue("routeCode", safe(query == null ? null : query.getRouteCode()))
                .addValue("year", query != null && query.getYear() != null ? query.getYear() : -1)
                .addValue("status", safe(query == null ? null : query.getStatus()))
                .addValue("limit", limit);

        return namedParameterJdbcTemplate.queryForList(
                "select id, tenant_id, solution_type, title, route_code, year, template_id, template_version, status, created_at, updated_at " +
                        "from ai_solution_task " +
                        "where tenant_id=:tenantId " +
                        "and (:solutionType='' or solution_type=:solutionType) " +
                        "and (:routeCode='' or route_code=:routeCode) " +
                        "and (:year=-1 or year=:year) " +
                        "and (:status='' or status=:status) " +
                        "order by created_at desc limit :limit",
                params
        );
    }

    @Override
    public List<Map<String, Object>> sources(String taskId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("taskId", taskId);

        return namedParameterJdbcTemplate.queryForList(
                "select id, tenant_id, task_id, source_type, source_title, source_id, source_url, content_excerpt, created_at " +
                        "from ai_solution_source where tenant_id=:tenantId and task_id=:taskId order by created_at asc",
                params
        );
    }

    private Map<String, Object> resolveTemplate(String tenantId, AiSolutionGenerateRequest request) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("templateId", safe(request.getTemplateId()))
                .addValue("templateCode", safe(request.getTemplateCode()))
                .addValue("solutionType", safe(request.getSolutionType()));

        String sql =
                "select t.id, t.tenant_id, t.template_code, t.template_name, t.solution_type, t.source_type, t.source_id, " +
                        "t.category, t.current_version, t.status, v.content, v.variables, v.source_url " +
                        "from ai_solution_template t " +
                        "left join ai_solution_template_version v on v.tenant_id=t.tenant_id and v.template_id=t.id and v.version=t.current_version " +
                        "where t.tenant_id=:tenantId and t.deleted=false and t.status='ENABLED' ";

        if (!safe(request.getTemplateId()).isEmpty()) {
            sql += "and t.id=:templateId ";
        } else if (!safe(request.getTemplateCode()).isEmpty()) {
            sql += "and t.template_code=:templateCode ";
        } else {
            sql += "and t.solution_type=:solutionType ";
        }

        sql += "order by t.updated_at desc limit 1";

        List<Map<String, Object>> list = namedParameterJdbcTemplate.queryForList(sql, params);
        return list.isEmpty() ? new LinkedHashMap<>() : list.get(0);
    }

    private List<KnowledgeSearchResult> searchKnowledge(AiSolutionGenerateRequest request, String context) {
        if (!readBoolean(request.getOptions(), "useKnowledge", true)) {
            return Collections.emptyList();
        }

        KnowledgeSearchRequest searchRequest = new KnowledgeSearchRequest();
        searchRequest.setQuery(request.getRouteCode() + " " + request.getYear() + " 技术状况 评定 病害 PCI MQI 养护建议 " + context);
        searchRequest.setTopK(readInt(request.getOptions(), "topK", 5));
        return knowledgeService.search(searchRequest);
    }

    private String buildLowScoreSections(String assessmentSummary) {
        return "低分路段应结合评定结果中的 PCI、PQI、MQI 低值对象进行筛选。当前阶段先引用评定分析摘要，后续可扩展 LowScoreSectionTool 精确输出路段清单。\n\n" + assessmentSummary;
    }

    private String buildProblemAnalysis(String assessmentSummary, String diseaseSummary, List<KnowledgeSearchResult> knowledgeSources) {
        StringBuilder sb = new StringBuilder();
        sb.append("综合评定结果和病害统计，重点关注 MQI/PQI/PCI 偏低路段，以及裂缝、坑槽、沉陷、车辙等影响 PCI 的损坏类病害。\n\n");
        sb.append("【评定摘要】\n").append(assessmentSummary).append("\n\n");
        sb.append("【病害摘要】\n").append(diseaseSummary).append("\n\n");
        if (!knowledgeSources.isEmpty()) {
            sb.append("【知识库依据】\n");
            for (int i = 0; i < Math.min(knowledgeSources.size(), 3); i++) {
                KnowledgeSearchResult item = knowledgeSources.get(i);
                sb.append(i + 1).append(". ").append(item.getTitle()).append("：").append(shortText(item.getContent(), 180)).append("\n");
            }
        }
        return sb.toString();
    }

    private String buildMaintenanceSuggestion(AiSolutionGenerateRequest request, List<KnowledgeSearchResult> knowledgeSources) {
        StringBuilder sb = new StringBuilder();
        sb.append("建议按照\"优先处置安全风险高、影响 PCI 明显、病害集中分布的路段\"的原则制定养护计划。");
        sb.append("对重度坑槽、沉陷、连续裂缝等病害建议优先安排修复；对轻度裂缝和局部病害可结合预防性养护措施处理。");
        if (!knowledgeSources.isEmpty()) {
            sb.append("\n\n参考知识库：\n");
            for (int i = 0; i < Math.min(knowledgeSources.size(), 5); i++) {
                KnowledgeSearchResult item = knowledgeSources.get(i);
                sb.append(i + 1).append(". ").append(item.getTitle()).append("\n");
            }
        }
        return sb.toString();
    }

    private String polishWithLlm(String draft, List<KnowledgeSearchResult> knowledgeSources) {
        StringBuilder sources = new StringBuilder();
        for (int i = 0; i < Math.min(knowledgeSources.size(), 5); i++) {
            KnowledgeSearchResult item = knowledgeSources.get(i);
            sources.append(i + 1).append(". ").append(item.getTitle()).append("：").append(shortText(item.getContent(), 220)).append("\n");
        }

        String prompt = "请在不改变事实依据的前提下，润色以下公路养护分析方案草稿，使其结构清晰、语气专业，并保留 Markdown 章节。资料不足时不要编造。\n\n" +
                "【知识库依据】\n" + sources + "\n\n【方案草稿】\n" + draft;
        String answer = llmClient.chat("你是智路养护平台 AI 方案生成助手。", prompt);
        return answer == null || answer.trim().isEmpty() ? draft : answer;
    }

    private void insertTask(String tenantId,
                            String taskId,
                            AiSolutionGenerateRequest request,
                            String title,
                            String templateId,
                            String templateVersion,
                            String content) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", taskId)
                .addValue("tenantId", tenantId)
                .addValue("solutionType", request.getSolutionType())
                .addValue("title", title)
                .addValue("routeCode", request.getRouteCode())
                .addValue("year", request.getYear())
                .addValue("templateId", templateId)
                .addValue("templateVersion", templateVersion)
                .addValue("status", "SUCCESS")
                .addValue("requestJson", toJson(request))
                .addValue("resultContent", content)
                .addValue("qualityResult", "{\"passed\":true,\"warnings\":[]}");

        namedParameterJdbcTemplate.update(
                "insert into ai_solution_task(" +
                        "id, tenant_id, solution_type, title, route_code, year, template_id, template_version, status, request_json, result_content, quality_result, created_at, updated_at" +
                        ") values (" +
                        ":id, :tenantId, :solutionType, :title, :routeCode, :year, :templateId, :templateVersion, :status, cast(:requestJson as jsonb), :resultContent, cast(:qualityResult as jsonb), now(), now()" +
                        ")",
                params
        );
    }

    private void insertSources(String tenantId,
                               String taskId,
                               Map<String, Object> template,
                               List<KnowledgeSearchResult> knowledgeSources,
                               String routeSummary,
                               String assessmentSummary,
                               String diseaseSummary) {
        insertSource(tenantId, taskId, "TEMPLATE", String.valueOf(template.get("template_name")), String.valueOf(template.get("id")), asString(template.get("source_url")), shortText(asString(template.get("content")), 500));
        insertSource(tenantId, taskId, "BUSINESS_DATA", "路线分析摘要", null, null, shortText(routeSummary, 500));
        insertSource(tenantId, taskId, "BUSINESS_DATA", "评定结果摘要", null, null, shortText(assessmentSummary, 500));
        insertSource(tenantId, taskId, "BUSINESS_DATA", "病害分析摘要", null, null, shortText(diseaseSummary, 500));

        for (KnowledgeSearchResult item : knowledgeSources) {
            insertSource(tenantId, taskId, "KNOWLEDGE", item.getTitle(), item.getChunkId(), item.getSourceUrl(), shortText(item.getContent(), 500));
        }
    }

    private void insertSource(String tenantId, String taskId, String type, String title, String sourceId, String url, String excerpt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", uuid())
                .addValue("tenantId", tenantId)
                .addValue("taskId", taskId)
                .addValue("sourceType", type)
                .addValue("sourceTitle", title)
                .addValue("sourceId", sourceId)
                .addValue("sourceUrl", url)
                .addValue("contentExcerpt", excerpt);

        namedParameterJdbcTemplate.update(
                "insert into ai_solution_source(" +
                        "id, tenant_id, task_id, source_type, source_title, source_id, source_url, content_excerpt, created_at" +
                        ") values (" +
                        ":id, :tenantId, :taskId, :sourceType, :sourceTitle, :sourceId, :sourceUrl, :contentExcerpt, now()" +
                        ")",
                params
        );
    }

    private void validate(AiSolutionGenerateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        if (safe(request.getSolutionType()).isEmpty()) {
            throw new IllegalArgumentException("solutionType 不能为空");
        }
        if (safe(request.getRouteCode()).isEmpty()) {
            throw new IllegalArgumentException("routeCode 不能为空");
        }
        if (request.getYear() == null) {
            throw new IllegalArgumentException("year 不能为空");
        }
    }

    private boolean readBoolean(Map<String, Object> options, String key, boolean def) {
        if (options == null || options.get(key) == null) {
            return def;
        }
        Object value = options.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private int readInt(Map<String, Object> options, String key, int def) {
        if (options == null || options.get(key) == null) {
            return def;
        }
        try {
            return Integer.parseInt(String.valueOf(options.get(key)));
        } catch (Exception e) {
            return def;
        }
    }

    private String readableSolutionType(String solutionType) {
        if ("ROAD_ASSESSMENT_REPORT".equals(solutionType)) {
            return "技术状况评定报告草稿";
        }
        if ("MAINTENANCE_SUGGESTION".equals(solutionType)) {
            return "养护建议方案草稿";
        }
        if ("DISEASE_TREATMENT_PLAN".equals(solutionType)) {
            return "病害治理方案草稿";
        }
        return "AI 方案草稿";
    }

    private String shortText(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
