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
