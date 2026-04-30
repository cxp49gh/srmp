package com.smartroad.srmp.agent.solution.service.impl;

import com.smartroad.srmp.agent.solution.service.AiSolutionTemplatePipelineService;
import com.smartroad.srmp.agent.solution.template.MarkdownTemplateRenderer;
import com.smartroad.srmp.agent.solution.template.SolutionTemplateContext;
import com.smartroad.srmp.agent.solution.template.TemplatePipelineResult;
import com.smartroad.srmp.agent.trace.AiTraceContext;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiSolutionTemplatePipelineServiceImpl implements AiSolutionTemplatePipelineService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Resource
    private MarkdownTemplateRenderer markdownTemplateRenderer;

    @Override
    public TemplatePipelineResult generate(SolutionTemplateContext context) {
        SolutionTemplateContext safeContext = context == null ? new SolutionTemplateContext() : context;
        String contextTenantId = safe(safeContext.getTenantId());
        String tenantId = contextTenantId.isEmpty() ? safe(TenantContextHolder.getTenantId()) : contextTenantId;
        AiTraceContext trace = safeContext.getTrace();

        Map<String, Object> template = matchTemplate(trace, tenantId, safeContext);
        Map<String, Object> variables = buildVariables(trace, safeContext);
        TemplatePipelineResult result = renderTemplate(trace, safeContext, template, variables);
        validateTemplate(trace, result);
        return result;
    }

    private Map<String, Object> matchTemplate(AiTraceContext trace, String tenantId, SolutionTemplateContext context) {
        AiTraceContext.StepTimer timer = trace == null ? null : trace.step("template_match", "模板匹配");
        try {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("tenantId", tenantId)
                    .addValue("templateId", safe(context.getTemplateId()))
                    .addValue("templateCode", safe(context.getTemplateCode()))
                    .addValue("originType", safe(context.getOriginType()))
                    .addValue("objectType", safe(context.getObjectType()))
                    .addValue("solutionType", safe(context.getSolutionType()));
            List<Map<String, Object>> rows = queryMatchedTemplates(context, params);
            Map<String, Object> template;
            boolean fallback;
            if (rows.isEmpty()) {
                template = fallbackTemplate(context);
                fallback = true;
            } else {
                Map<String, Object> matchedTemplate = new LinkedHashMap<String, Object>(rows.get(0));
                if (safe(matchedTemplate.get("content")).isEmpty()) {
                    template = fallbackTemplate(context, matchedTemplate, "匹配模板缺少当前版本内容，使用系统兜底模板");
                    fallback = true;
                } else {
                    template = matchedTemplate;
                    fallback = false;
                }
            }
            if (timer != null) {
                timer.success(fallback ? 0 : 1, templateStepData(template, fallback));
            }
            return template;
        } catch (RuntimeException e) {
            if (timer != null) {
                timer.failed(e);
            }
            throw e;
        }
    }

    private List<Map<String, Object>> queryMatchedTemplates(SolutionTemplateContext context, MapSqlParameterSource params) {
        String baseSql = "select t.id,t.tenant_id,t.template_code,t.template_name,t.solution_type,t.origin_type,t.object_type,t.current_version,t.status,t.is_default,t.priority,v.content,v.variables,v.source_url " +
                "from ai_solution_template t " +
                "left join ai_solution_template_version v on v.tenant_id=t.tenant_id and v.template_id=t.id and v.version=t.current_version " +
                "where t.tenant_id=:tenantId and t.deleted=false and t.status='ENABLED' ";
        String orderSql = "order by coalesce(t.priority,0) desc, coalesce(t.is_default,false) desc, t.updated_at desc limit 1";

        if (!safe(context.getTemplateId()).isEmpty()) {
            return namedParameterJdbcTemplate.queryForList(baseSql + "and t.id=:templateId " + orderSql, params);
        }
        if (!safe(context.getTemplateCode()).isEmpty()) {
            return namedParameterJdbcTemplate.queryForList(baseSql + "and t.template_code=:templateCode " + orderSql, params);
        }

        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                baseSql + "and coalesce(t.origin_type,'')=:originType and coalesce(t.object_type,'')=:objectType and t.solution_type=:solutionType " + orderSql,
                params
        );
        if (!rows.isEmpty() || !isLegacyRouteContext(context)) {
            return rows;
        }

        rows = namedParameterJdbcTemplate.queryForList(
                baseSql + "and coalesce(t.origin_type,'')='' and coalesce(t.object_type,'')='' and t.solution_type=:solutionType " + orderSql,
                params
        );
        if (!rows.isEmpty()) {
            Map<String, Object> legacy = new LinkedHashMap<String, Object>(rows.get(0));
            legacy.put("matchReason", "按 legacy solutionType 兼容匹配路线模板，solutionType=" + safe(context.getSolutionType()));
            rows = new ArrayList<Map<String, Object>>();
            rows.add(legacy);
        }
        return rows;
    }

    private boolean isLegacyRouteContext(SolutionTemplateContext context) {
        return "ROUTE_REPORT".equals(safe(context.getOriginType()))
                && "ROAD_ROUTE".equals(safe(context.getObjectType()));
    }

    private Map<String, Object> buildVariables(AiTraceContext trace, SolutionTemplateContext context) {
        AiTraceContext.StepTimer timer = trace == null ? null : trace.step("template_variable_build", "模板变量构建");
        Map<String, Object> variables = new LinkedHashMap<String, Object>();
        putAll(variables, context.getMapObject());
        putAll(variables, context.getObjectSummary());
        putAll(variables, context.getRegionSummary());
        putAll(variables, context.getBusinessData());
        variables.put("originType", safe(context.getOriginType()));
        variables.put("objectType", safe(context.getObjectType()));
        variables.put("solutionType", safe(context.getSolutionType()));
        variables.put("routeCode", safe(context.getRouteCode()));
        variables.put("year", context.getYear());
        variables.put("title", safe(context.getTitle()));
        variables.put("fallbackMarkdown", safe(context.getFallbackMarkdown()));
        variables.put("knowledgeSources", context.getKnowledgeSources());
        variables.put("outlineSources", context.getOutlineSources());
        normalizeRegionVariables(variables, context.getRegionSummary());
        normalizeRegionTextVariables(variables, context.getRegionSummary());
        if (timer != null) {
            timer.success(variables.size());
        }
        return variables;
    }

    private TemplatePipelineResult renderTemplate(AiTraceContext trace,
                                                  SolutionTemplateContext context,
                                                  Map<String, Object> template,
                                                  Map<String, Object> variables) {
        AiTraceContext.StepTimer timer = trace == null ? null : trace.step("template_render", "模板渲染");
        TemplatePipelineResult result = new TemplatePipelineResult();
        boolean fallback = Boolean.TRUE.equals(template.get("fallback"));
        try {
            String content = safe(template.get("content"));
            MarkdownTemplateRenderer.RenderResult rendered = markdownTemplateRenderer.renderWithCheck(content, variables);
            result.setRenderedMarkdown(rendered.getRenderedMarkdown());
            result.setMarkdown(rendered.getRenderedMarkdown());
            result.setVariables(rendered.getVariables());
            result.setMissingVariables(rendered.getMissingVariables());
            result.setUnusedVariables(rendered.getUnusedVariables());
            result.setWarnings(rendered.getWarnings());
            result.setTemplateMeta(templateMeta(context, template, rendered));
            result.setSourceSummaries(sourceSummaries(template, rendered));
            if (timer != null) {
                timer.success(1, templateStepData(template, fallback));
            }
            return result;
        } catch (RuntimeException e) {
            if (timer != null) {
                timer.failed(e, templateStepData(template, fallback));
            }
            throw e;
        }
    }

    private void validateTemplate(AiTraceContext trace, TemplatePipelineResult result) {
        AiTraceContext.StepTimer timer = trace == null ? null : trace.step("template_validate", "模板变量校验");
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("missingVariables", result.getMissingVariables());
        data.put("unusedVariables", result.getUnusedVariables());
        data.put("warnings", result.getWarnings());
        if (timer != null) {
            timer.success(result.getMissingVariables().size() + result.getWarnings().size(), data);
        }
    }

    private Map<String, Object> fallbackTemplate(SolutionTemplateContext context) {
        Map<String, Object> template = new LinkedHashMap<String, Object>();
        template.put("id", "");
        template.put("template_code", "SYSTEM_FALLBACK_" + safe(context.getSolutionType()));
        template.put("template_name", "系统兜底模板");
        template.put("current_version", "fallback");
        template.put("origin_type", safe(context.getOriginType()));
        template.put("object_type", safe(context.getObjectType()));
        template.put("solution_type", safe(context.getSolutionType()));
        template.put("content", safe(context.getFallbackMarkdown()).isEmpty() ? defaultFallbackMarkdown(context) : safe(context.getFallbackMarkdown()));
        template.put("fallback", true);
        template.put("fallbackReason", "未找到启用的 " + safe(context.getOriginType()) + " + " + safe(context.getObjectType()) + " + " + safe(context.getSolutionType()) + " 模板，使用系统兜底模板");
        return template;
    }

    private Map<String, Object> fallbackTemplate(SolutionTemplateContext context,
                                                 Map<String, Object> matchedTemplate,
                                                 String fallbackReason) {
        Map<String, Object> template = fallbackTemplate(context);
        template.put("matchedTemplateId", safe(matchedTemplate.get("id")));
        template.put("matchedTemplateCode", safe(matchedTemplate.get("template_code")));
        template.put("matchedTemplateVersion", safe(matchedTemplate.get("current_version")));
        template.put("fallbackReason", safe(fallbackReason) + "，matchedTemplateCode=" + safe(matchedTemplate.get("template_code")) + "，matchedTemplateId=" + safe(matchedTemplate.get("id")));
        return template;
    }

    private String defaultFallbackMarkdown(SolutionTemplateContext context) {
        String title = safe(context.getTitle());
        if (title.isEmpty()) {
            title = "养护方案";
        }
        return "# " + title + "\n\n" +
                "当前未匹配到可用模板，系统已生成兜底方案。\n\n" +
                "请结合对象基础信息、区域统计结果和业务分析数据补充完整方案内容。";
    }

    private Map<String, Object> templateMeta(SolutionTemplateContext context,
                                             Map<String, Object> template,
                                             MarkdownTemplateRenderer.RenderResult rendered) {
        boolean fallback = Boolean.TRUE.equals(template.get("fallback"));
        Map<String, Object> meta = new LinkedHashMap<String, Object>();
        meta.put("matched", !fallback);
        meta.put("fallback", fallback);
        meta.put("templateId", safe(template.get("id")));
        meta.put("templateCode", safe(template.get("template_code")));
        meta.put("templateName", safe(template.get("template_name")));
        meta.put("templateVersion", safe(template.get("current_version")));
        meta.put("matchedTemplateId", safe(template.get("matchedTemplateId")));
        meta.put("matchedTemplateCode", safe(template.get("matchedTemplateCode")));
        meta.put("matchedTemplateVersion", safe(template.get("matchedTemplateVersion")));
        meta.put("solutionType", safe(context.getSolutionType()));
        meta.put("objectType", safe(context.getObjectType()));
        meta.put("originType", safe(context.getOriginType()));
        meta.put("matchReason", fallback ? "" : matchReason(template));
        meta.put("fallbackReason", safe(template.get("fallbackReason")));
        meta.put("missingVariables", rendered.getMissingVariables());
        meta.put("unusedVariables", rendered.getUnusedVariables());
        meta.put("warnings", rendered.getWarnings());
        return meta;
    }

    private String matchReason(Map<String, Object> template) {
        String explicit = safe(template.get("matchReason"));
        if (!explicit.isEmpty()) {
            return explicit;
        }
        return "按 originType + objectType + solutionType 匹配，priority=" + safe(template.get("priority"));
    }

    private List<Map<String, Object>> sourceSummaries(Map<String, Object> template,
                                                      MarkdownTemplateRenderer.RenderResult rendered) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        Map<String, Object> source = new LinkedHashMap<String, Object>();
        source.put("sourceType", "TEMPLATE");
        source.put("sourceTitle", safe(template.get("template_name")));
        source.put("sourceId", safe(template.get("id")));
        source.put("sourceUrl", safe(template.get("source_url")));
        source.put("contentExcerpt", "模板：" + safe(template.get("template_code")) + " / 版本：" + safe(template.get("current_version")));
        result.add(source);

        Map<String, Object> variableSource = new LinkedHashMap<String, Object>();
        variableSource.put("sourceType", "TEMPLATE_VARIABLE");
        variableSource.put("sourceTitle", "模板变量");
        variableSource.put("sourceId", safe(template.get("id")));
        variableSource.put("sourceUrl", "");
        variableSource.put("contentExcerpt", "已填充变量 " + rendered.getVariables().size() + " 个，缺失 " + rendered.getMissingVariables().size() + " 个");
        result.add(variableSource);
        return result;
    }

    private void putAll(Map<String, Object> target, Map<String, Object> source) {
        if (target == null || source == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && !String.valueOf(entry.getValue()).trim().isEmpty()) {
                target.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private void normalizeRegionVariables(Map<String, Object> variables, Map<String, Object> regionSummary) {
        if (variables == null || regionSummary == null) {
            return;
        }
        Object diseaseRaw = regionSummary.get("diseaseSummary");
        if (!(diseaseRaw instanceof Map)) {
            diseaseRaw = regionSummary.get("disease_summary");
        }
        Object assessmentRaw = regionSummary.get("assessmentSummary");
        if (!(assessmentRaw instanceof Map)) {
            assessmentRaw = regionSummary.get("assessment_summary");
        }
        Map<String, Object> disease = toMap(diseaseRaw);
        Map<String, Object> assessment = toMap(assessmentRaw);
        putIfPresent(variables, "diseaseCount", first(disease, "disease_count", "diseaseCount"));
        putIfPresent(variables, "heavyDiseaseCount", first(disease, "heavy_count", "heavyCount", "heavyDiseaseCount"));
        putIfPresent(variables, "mediumDiseaseCount", first(disease, "medium_count", "mediumCount", "mediumDiseaseCount"));
        putIfPresent(variables, "avgMqi", first(assessment, "avg_mqi", "avgMqi"));
        putIfPresent(variables, "avgPqi", first(assessment, "avg_pqi", "avgPqi"));
        putIfPresent(variables, "avgPci", first(assessment, "avg_pci", "avgPci"));
    }

    private void normalizeRegionTextVariables(Map<String, Object> variables, Map<String, Object> regionSummary) {
        if (variables == null || regionSummary == null || regionSummary.isEmpty()) {
            return;
        }
        String llmMarkdown = safe(variables.get("llmMarkdown"));
        List<?> hotspots = firstList(variables.get("hotspots"), regionSummary.get("hotspots"));

        putIfBlank(variables, "hotspotSummary", firstNonBlank(
                extractMarkdownSection(llmMarkdown, "热点识别", "重点路段", "病害热点"),
                buildHotspotSummary(hotspots)
        ));
        putIfBlank(variables, "regionSummary", firstNonBlank(
                extractMarkdownSection(llmMarkdown, "区域综合判断", "综合判断", "问题分析"),
                buildRegionSummaryText(regionSummary)
        ));
        putIfBlank(variables, "maintenanceSuggestion", firstNonBlank(
                extractMarkdownSection(llmMarkdown, "养护建议", "处治建议", "治理建议"),
                buildMaintenanceSuggestionText(regionSummary, hotspots)
        ));
        putIfBlank(variables, "riskNotice", firstNonBlank(
                extractMarkdownSection(llmMarkdown, "风险提示", "风险提醒", "审核提示"),
                "本建议由系统基于区域统计和 AI 分析生成，实施前需结合现场复核、交通组织、资金计划和最新检测数据进行人工复核。"
        ));
    }

    private List<?> firstList(Object first, Object second) {
        if (first instanceof List) {
            return (List<?>) first;
        }
        if (second instanceof List) {
            return (List<?>) second;
        }
        return new ArrayList<Object>();
    }

    private String buildHotspotSummary(List<?> hotspots) {
        if (hotspots == null || hotspots.isEmpty()) {
            return "未识别到明显集中热点，建议保持常规巡查，并结合现场复核确认零散病害处治优先级。";
        }
        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (Object raw : hotspots) {
            Map<String, Object> hotspot = toMap(raw);
            if (hotspot.isEmpty()) {
                continue;
            }
            if (index > 0) {
                builder.append('\n');
            }
            builder.append("- ")
                    .append(firstNonBlank(safe(first(hotspot, "route_code", "routeCode")), "路线"))
                    .append(" K")
                    .append(safe(first(hotspot, "start_stake", "startStake")))
                    .append("-K")
                    .append(safe(first(hotspot, "end_stake", "endStake")))
                    .append("：病害 ")
                    .append(firstNonBlank(safe(first(hotspot, "disease_count", "diseaseCount")), "0"))
                    .append(" 处，重度 ")
                    .append(firstNonBlank(safe(first(hotspot, "heavy_count", "heavyCount")), "0"))
                    .append(" 处，建议纳入近期重点巡查和优先处治。");
            index++;
        }
        return builder.length() == 0
                ? "未识别到明显集中热点，建议保持常规巡查，并结合现场复核确认零散病害处治优先级。"
                : builder.toString();
    }

    private String buildRegionSummaryText(Map<String, Object> regionSummary) {
        Map<String, Object> disease = toMap(first(regionSummary, "diseaseSummary", "disease_summary"));
        Map<String, Object> assessment = toMap(first(regionSummary, "assessmentSummary", "assessment_summary"));
        return "- 区域覆盖路线 " + firstNonBlank(safe(regionSummary.get("routeCount")), "0") +
                " 条、路段 " + firstNonBlank(safe(regionSummary.get("sectionCount")), "0") +
                " 段、评定单元 " + firstNonBlank(safe(regionSummary.get("unitCount")), "0") + " 个。\n" +
                "- 共识别病害 " + firstNonBlank(safe(first(disease, "disease_count", "diseaseCount")), "0") +
                " 处，其中重度 " + firstNonBlank(safe(first(disease, "heavy_count", "heavyCount")), "0") +
                " 处、中度 " + firstNonBlank(safe(first(disease, "medium_count", "mediumCount")), "0") + " 处。\n" +
                "- 平均 MQI " + firstNonBlank(safe(first(assessment, "avg_mqi", "avgMqi")), "-") +
                "，平均 PQI " + firstNonBlank(safe(first(assessment, "avg_pqi", "avgPqi")), "-") +
                "，平均 PCI " + firstNonBlank(safe(first(assessment, "avg_pci", "avgPci")), "-") +
                "，建议结合低分单元和重度病害分布确定处治重点。";
    }

    private String buildMaintenanceSuggestionText(Map<String, Object> regionSummary, List<?> hotspots) {
        Map<String, Object> disease = toMap(first(regionSummary, "diseaseSummary", "disease_summary"));
        String hotspotAction = hotspots == null || hotspots.isEmpty()
                ? "对零散病害开展现场复核，按安全影响和发展速度排序。"
                : "优先复核热点路段，形成重度病害处治清单。";
        return "- P1：针对重度病害 " + firstNonBlank(safe(first(disease, "heavy_count", "heavyCount")), "0") +
                " 处组织专项核查，优先处治影响行车安全的坑槽、沉陷和重度裂缝。\n" +
                "- P2：针对中度病害 " + firstNonBlank(safe(first(disease, "medium_count", "mediumCount")), "0") +
                " 处安排预防性养护，控制裂缝、松散等病害继续扩展。\n" +
                "- P3：" + hotspotAction + " 对低分评定单元同步建立复测计划，跟踪 MQI/PQI/PCI 改善效果。";
    }

    private String extractMarkdownSection(String markdown, String... keywords) {
        String source = safe(markdown);
        if (source.isEmpty() || keywords == null || keywords.length == 0) {
            return "";
        }
        String[] lines = source.split("\\r?\\n");
        StringBuilder builder = new StringBuilder();
        boolean capturing = false;
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (isMarkdownHeading(trimmed)) {
                if (capturing) {
                    break;
                }
                if (containsAny(trimmed, keywords)) {
                    capturing = true;
                }
                continue;
            }
            if (capturing) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
            }
        }
        return builder.toString().trim();
    }

    private boolean isMarkdownHeading(String line) {
        return line != null && line.matches("#{1,6}\\s+.*");
    }

    private boolean containsAny(String text, String... keywords) {
        String source = safe(text);
        if (source.isEmpty() || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (!safe(keyword).isEmpty() && source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>();
    }

    private Object first(Map<String, Object> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !String.valueOf(value).trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && !String.valueOf(value).trim().isEmpty()) {
            target.put(key, value);
        }
    }

    private void putIfBlank(Map<String, Object> target, String key, Object value) {
        if (target == null || key == null || !safe(target.get(key)).isEmpty()) {
            return;
        }
        putIfPresent(target, key, value);
    }

    private String firstNonBlank(String first, String second) {
        return !safe(first).isEmpty() ? safe(first) : safe(second);
    }

    private Map<String, Object> templateStepData(Map<String, Object> template, boolean fallback) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("templateCode", safe(template.get("template_code")));
        data.put("templateVersion", safe(template.get("current_version")));
        data.put("matched", !fallback);
        data.put("fallback", fallback);
        data.put("matchReason", safe(template.get("matchReason")));
        data.put("fallbackReason", safe(template.get("fallbackReason")));
        data.put("matchedTemplateId", safe(template.get("matchedTemplateId")));
        data.put("matchedTemplateCode", safe(template.get("matchedTemplateCode")));
        data.put("matchedTemplateVersion", safe(template.get("matchedTemplateVersion")));
        return data;
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
