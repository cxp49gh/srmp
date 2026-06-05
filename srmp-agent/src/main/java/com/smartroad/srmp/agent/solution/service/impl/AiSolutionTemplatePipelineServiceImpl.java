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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        putIfPresent(variables, "routeCode", context.getRouteCode());
        putIfPresent(variables, "year", context.getYear());
        variables.put("title", safe(context.getTitle()));
        variables.put("fallbackMarkdown", safe(context.getFallbackMarkdown()));
        variables.put("knowledgeSources", context.getKnowledgeSources());
        variables.put("outlineSources", context.getOutlineSources());
        normalizeRegionVariables(variables, context.getRegionSummary());
        normalizeRegionIdentityVariables(variables, context.getRegionSummary());
        normalizeRegionTextVariables(variables, context.getRegionSummary());
        normalizeMapObjectTextVariables(variables);
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

    private void normalizeRegionIdentityVariables(Map<String, Object> variables, Map<String, Object> regionSummary) {
        if (variables == null || variables.isEmpty()) {
            return;
        }
        String originType = safe(variables.get("originType"));
        String objectType = safe(variables.get("objectType"));
        String solutionType = safe(variables.get("solutionType"));
        if (!"MAP_REGION".equals(originType) && !"MAP_REGION".equals(objectType)
                && !"REGION_MAINTENANCE_SUGGESTION".equals(solutionType)) {
            return;
        }
        String routeCode = firstNonBlank(
                safe(first(variables, "routeCode", "route_code")),
                firstNonBlank(safe(first(regionSummary, "routeCode", "route_code")), inferSingleRouteCode(regionSummary))
        );
        putIfBlank(variables, "regionLabel", routeCode.isEmpty() ? "框选区域" : routeCode);
        putIfBlank(variables, "routeCode", routeCode.isEmpty() ? "选区" : routeCode);
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

    private void normalizeMapObjectTextVariables(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return;
        }
        String originType = safe(variables.get("originType"));
        if (!"MAP_OBJECT".equals(originType)) {
            return;
        }
        putIfBlank(variables, "businessEvidenceSummary", buildBusinessEvidenceSummaryText(toMap(variables.get("businessEvidence"))));
        putIfBlank(variables, "routeSummary", buildMapObjectRouteSummaryText(variables));
        putIfBlank(variables, "assessmentSummary", buildMapObjectAssessmentSummaryText(variables));
        putIfBlank(variables, "diseaseSummary", buildMapObjectDiseaseSummaryText(variables));
        putIfBlank(variables, "problemAnalysis", buildMapObjectProblemAnalysisText(variables));
        putIfBlank(variables, "maintenanceSuggestion", buildMapObjectMaintenanceSuggestionText(variables));
        putIfBlank(variables, "treatmentAdvice", buildMapObjectTreatmentAdviceText(variables));
        putIfBlank(variables, "lowScoreSections", buildMapObjectLowScoreText(variables));
        putIfBlank(variables, "riskNotice", "本方案草稿由系统基于地图对象、业务查询证据和模板规则生成，实施前需结合现场复核、预算计划、交通组织和最新检测资料进行人工审核。");
    }

    private String buildMapObjectRouteSummaryText(Map<String, Object> variables) {
        StringBuilder builder = new StringBuilder();
        appendTextLine(builder, "路线", firstNonBlank(safe(variables.get("routeCode")), "当前路线"));
        appendTextLine(builder, "年度", safe(variables.get("year")));
        appendTextLine(builder, "桩号范围", safe(variables.get("stakeRange")));
        appendTextLine(builder, "路段", firstNonBlank(safe(variables.get("sectionName")), safe(variables.get("sectionCode"))));
        appendTextLine(builder, "里程", appendUnit(safe(variables.get("lengthKm")), "km"));
        appendTextLine(builder, "对象编号", safe(variables.get("objectId")));
        return builder.length() == 0 ? "当前对象基础信息不足，建议先核对路线、桩号和对象编号。" : builder.toString();
    }

    private String buildMapObjectAssessmentSummaryText(Map<String, Object> variables) {
        StringBuilder builder = new StringBuilder();
        appendTextLine(builder, "评定单元", safe(variables.get("unitCode")));
        appendTextLine(builder, "MQI", safe(variables.get("mqi")));
        appendTextLine(builder, "PQI", safe(variables.get("pqi")));
        appendTextLine(builder, "PCI", safe(variables.get("pci")));
        appendTextLine(builder, "当前指标", activeMetricText(variables));
        appendTextLine(builder, "等级", safe(variables.get("grade")));
        appendTextLine(builder, "评定证据", toolHitText(variables, "gis.queryAssessmentResults", "评定结果"));
        if (builder.length() == 0) {
            return "未取得完整评定指标，建议补充当前对象年度评定结果后再确定处置等级。";
        }
        return builder.toString();
    }

    private String buildMapObjectDiseaseSummaryText(Map<String, Object> variables) {
        StringBuilder builder = new StringBuilder();
        appendTextLine(builder, "病害类型", safe(variables.get("diseaseName")));
        appendTextLine(builder, "严重程度", safe(variables.get("severity")));
        appendTextLine(builder, "工程量", quantityText(variables));
        appendTextLine(builder, "病害证据", firstNonBlank(
                toolHitText(variables, "gis.queryDiseasesByStakeRange", "桩号范围病害"),
                toolHitText(variables, "gis.queryDiseases", "病害")
        ));
        if (builder.length() == 0) {
            return "当前模板未取得明确病害明细，建议结合关联病害图层和现场巡查结果补充病害类型、程度和工程量。";
        }
        return builder.toString();
    }

    private String buildMapObjectProblemAnalysisText(Map<String, Object> variables) {
        String solutionType = safe(variables.get("solutionType"));
        String objectType = safe(variables.get("objectType"));
        String metric = firstNonBlank(activeMetricText(variables), "MQI/PQI/PCI 指标");
        if ("LOW_SCORE_TREATMENT".equals(solutionType)) {
            return "当前评定对象存在差次或低值指标，应重点核查 " + metric + " 对应的裂缝、坑槽、沉陷、车辙等病害贡献，并判断是否存在连续损坏、排水不良或结构承载不足。";
        }
        if ("EVALUATION_UNIT_ADVICE".equals(solutionType) || "ASSESSMENT_RESULT".equals(objectType)) {
            return "当前评定对象应结合指标短板、关联病害和相邻单元表现综合判断；若指标处于良好区间，建议以预防性养护和跟踪复核为主，避免将正常评定结果误判为低分处置。";
        }
        if ("SECTION_PLAN".equals(solutionType) || "ROAD_SECTION".equals(objectType)) {
            return "当前路段需综合评定结果和病害分布识别主要短板，重点关注低 PCI/PQI 单元、重度病害和连续损坏区段，并从交通荷载、排水条件、材料老化和结构承载方面分析原因。";
        }
        if ("DISEASE".equals(objectType)) {
            return "当前病害需结合损坏范围、严重程度、周边同类病害和现场排水条件判断成因，避免只处理表层现象。";
        }
        return "建议结合当前对象基础信息、业务查询证据和现场复核资料，识别主要技术短板和处置触发因素。";
    }

    private String buildMapObjectMaintenanceSuggestionText(Map<String, Object> variables) {
        String solutionType = safe(variables.get("solutionType"));
        if ("LOW_SCORE_TREATMENT".equals(solutionType)) {
            return "- P1/P2：优先复核差次或低值指标对应桩号，核实关联病害和结构状态。\n" +
                    "- 按病害类型选择裂缝处治、坑槽修补、铣刨重铺、排水修复或结构性修复。\n" +
                    "- 与相邻低分单元统筹，形成连续处治清单，避免零散修补反复返工。";
        }
        if ("EVALUATION_UNIT_ADVICE".equals(solutionType)) {
            return "- 对良好或中等评定单元，以预防性养护、巡查跟踪和局部病害修补为主。\n" +
                    "- 若关联病害集中或指标持续下降，纳入近期复核并评估是否升级为专项处治。\n" +
                    "- 处置后跟踪 MQI/PQI/PCI 改善效果。";
        }
        if ("SECTION_PLAN".equals(solutionType)) {
            return "- 将路段按评定短板和病害集中程度分段，形成近期处置、预防养护和跟踪观察清单。\n" +
                    "- 优先处理安全风险高、病害密集和指标偏低区段；一般区段纳入年度计划统筹。\n" +
                    "- 实施前补充现场复核工程量、交通组织和预算测算。";
        }
        return "- 先完成现场复核，确认病害范围、结构状态和工程量。\n" +
                "- 按安全影响、发展速度和养护资金安排确定实施优先级。\n" +
                "- 处置后建立跟踪复评记录。";
    }

    private String buildMapObjectTreatmentAdviceText(Map<String, Object> variables) {
        String diseaseName = safe(variables.get("diseaseName"));
        if (diseaseName.isEmpty()) {
            return "根据现场复核结果选择局部修补、预防性养护或结构性修复；涉及结构层损坏时需先处理基层和排水问题。";
        }
        return "针对" + diseaseName + "，建议先复核损坏深度、范围和基层状态；表层病害可采用局部修补、裂缝处治或铣刨重铺，若发现基层松散、含水或排水不畅，应先处理结构和排水后恢复面层。";
    }

    private String buildMapObjectLowScoreText(Map<String, Object> variables) {
        String stakeRange = safe(variables.get("stakeRange"));
        String metric = firstNonBlank(activeMetricText(variables), "关键指标");
        if (stakeRange.isEmpty()) {
            return "当前对象未提供明确桩号范围，建议先补齐评定单元或路段桩号后再划定低分区段。";
        }
        return firstNonBlank(safe(variables.get("routeCode")), "当前路线") + " " + stakeRange + " 为重点复核范围，需结合 " + metric + " 与关联病害确认是否纳入低分处置清单。";
    }

    private String buildBusinessEvidenceSummaryText(Map<String, Object> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return "未取得结构化工具取证，建议先确认本次分析是否已执行评定、病害、路段或区域统计查询。";
        }
        StringBuilder builder = new StringBuilder();
        appendTextLine(builder, "取证概况", "成功工具 " + firstNonBlank(safe(evidence.get("toolSuccessCount")), "-") +
                " 个，业务命中 " + firstNonBlank(safe(evidence.get("businessHitCount")), "-"));
        Object raw = evidence.get("toolSummary");
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                Map<String, Object> tool = toMap(item);
                String toolName = safe(tool.get("toolName"));
                if (toolName.isEmpty()) {
                    continue;
                }
                appendTextLine(builder, toolName, firstNonBlank(safe(tool.get("summary")), "命中 " + firstNonBlank(safe(tool.get("hitCount")), "0") + " 条"));
            }
        }
        return builder.length() == 0
                ? "未取得结构化工具取证，建议先确认本次分析是否已执行评定、病害、路段或区域统计查询。"
                : builder.toString();
    }

    private String activeMetricText(Map<String, Object> variables) {
        String code = safe(first(variables, "activeMetricCode", "activeIndexCode"));
        String value = safe(first(variables, "activeMetricValue", "activeIndexValue"));
        String grade = safe(first(variables, "activeMetricGrade", "activeIndexGrade"));
        if (code.isEmpty() && value.isEmpty() && grade.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (!code.isEmpty()) {
            builder.append(code);
        }
        if (!value.isEmpty()) {
            if (builder.length() > 0) {
                builder.append("=");
            }
            builder.append(value);
        }
        if (!grade.isEmpty()) {
            if (builder.length() > 0) {
                builder.append("，");
            }
            builder.append("等级 ").append(grade);
        }
        return builder.toString();
    }

    private String toolHitText(Map<String, Object> variables, String toolName, String label) {
        Map<String, Object> evidence = toMap(variables.get("businessEvidence"));
        Object raw = evidence.get("toolSummary");
        if (!(raw instanceof List)) {
            return "";
        }
        for (Object item : (List<?>) raw) {
            Map<String, Object> tool = toMap(item);
            if (!toolName.equals(safe(tool.get("toolName")))) {
                continue;
            }
            String summary = safe(tool.get("summary"));
            if (!summary.isEmpty()) {
                return summary;
            }
            return label + " " + firstNonBlank(safe(tool.get("hitCount")), "0") + " 条";
        }
        return "";
    }

    private String quantityText(Map<String, Object> variables) {
        String quantity = safe(variables.get("quantity"));
        String unit = safe(variables.get("measureUnit"));
        if (quantity.isEmpty() && unit.isEmpty()) {
            return "";
        }
        return quantity + unit;
    }

    private String appendUnit(String value, String unit) {
        return safe(value).isEmpty() ? "" : safe(value) + " " + unit;
    }

    private void appendTextLine(StringBuilder builder, String label, String value) {
        if (builder == null || safe(value).isEmpty()) {
            return;
        }
        builder.append("- ").append(label).append("：").append(safe(value)).append("\n");
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

    private String inferSingleRouteCode(Map<String, Object> regionSummary) {
        if (regionSummary == null || regionSummary.isEmpty()) {
            return "";
        }
        Set<String> routes = new LinkedHashSet<String>();
        collectRouteCode(routes, first(regionSummary, "routeCodes", "route_codes", "routes"));
        for (Object raw : firstList(regionSummary.get("hotspots"), regionSummary.get("hot_spots"))) {
            Map<String, Object> hotspot = toMap(raw);
            collectRouteCode(routes, first(hotspot, "route_code", "routeCode", "route"));
        }
        return routes.size() == 1 ? routes.iterator().next() : "";
    }

    private void collectRouteCode(Set<String> routes, Object value) {
        if (routes == null || value == null) {
            return;
        }
        if (value instanceof Map) {
            Map<String, Object> map = toMap(value);
            collectRouteCode(routes, first(map, "routeCode", "route_code", "code", "route"));
            return;
        }
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                collectRouteCode(routes, item);
            }
            return;
        }
        String text = safe(value);
        if (!text.isEmpty()) {
            routes.add(text);
        }
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
