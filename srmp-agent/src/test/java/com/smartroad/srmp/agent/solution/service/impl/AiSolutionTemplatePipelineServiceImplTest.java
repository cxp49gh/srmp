package com.smartroad.srmp.agent.solution.service.impl;

import com.smartroad.srmp.agent.solution.template.MarkdownTemplateRenderer;
import com.smartroad.srmp.agent.solution.template.SolutionTemplateContext;
import org.junit.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AiSolutionTemplatePipelineServiceImplTest {

    @Test
    public void buildVariablesFillsRegionDefaultTemplateSections() throws Exception {
        AiSolutionTemplatePipelineServiceImpl service = new AiSolutionTemplatePipelineServiceImpl();
        SolutionTemplateContext context = regionContext();

        Method method = AiSolutionTemplatePipelineServiceImpl.class
                .getDeclaredMethod("buildVariables", com.smartroad.srmp.agent.trace.AiTraceContext.class, SolutionTemplateContext.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) method.invoke(service, null, context);

        String template = "# {{routeCode}} 框选区域养护建议\n\n" +
                "## 二、热点识别\n{{hotspotSummary}}\n\n" +
                "## 三、区域综合判断\n{{regionSummary}}\n\n" +
                "## 四、养护建议\n{{maintenanceSuggestion}}\n\n" +
                "## 五、风险提示\n{{riskNotice}}\n";
        MarkdownTemplateRenderer.RenderResult rendered = new MarkdownTemplateRenderer().renderWithCheck(template, variables);

        assertTrue(rendered.getMissingVariables().isEmpty());
        assertFalse(rendered.getRenderedMarkdown().contains("{{"));
        assertTrue(rendered.getRenderedMarkdown().contains("G210"));
        assertTrue(rendered.getRenderedMarkdown().contains("K97.474-K112.386"));
        assertTrue(rendered.getRenderedMarkdown().contains("LLM 养护建议"));
        assertTrue(rendered.getRenderedMarkdown().contains("人工复核"));
    }

    @Test
    public void buildVariablesFillsRegionRouteCodeWhenQueryRouteIsMissing() throws Exception {
        AiSolutionTemplatePipelineServiceImpl service = new AiSolutionTemplatePipelineServiceImpl();
        SolutionTemplateContext context = regionContext();
        context.setRouteCode("");

        Method method = AiSolutionTemplatePipelineServiceImpl.class
                .getDeclaredMethod("buildVariables", com.smartroad.srmp.agent.trace.AiTraceContext.class, SolutionTemplateContext.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) method.invoke(service, null, context);

        String template = "# {{routeCode}} 框选区域养护建议\n\n{{regionSummary}}\n";
        MarkdownTemplateRenderer.RenderResult rendered = new MarkdownTemplateRenderer().renderWithCheck(template, variables);

        assertTrue(rendered.getMissingVariables().isEmpty());
        assertFalse(rendered.getRenderedMarkdown().contains("{{routeCode}}"));
        assertTrue(rendered.getRenderedMarkdown().contains("G210 框选区域养护建议"));
    }

    @Test
    public void buildVariablesInfersRegionRouteCodeFromRouteObjects() throws Exception {
        AiSolutionTemplatePipelineServiceImpl service = new AiSolutionTemplatePipelineServiceImpl();
        SolutionTemplateContext context = regionContext();
        context.setRouteCode("");
        Map<String, Object> summary = regionSummary();
        summary.remove("hotspots");
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("routeCode", "G310");
        List<Map<String, Object>> routes = new ArrayList<>();
        routes.add(route);
        summary.put("routes", routes);
        context.setRegionSummary(summary);

        Method method = AiSolutionTemplatePipelineServiceImpl.class
                .getDeclaredMethod("buildVariables", com.smartroad.srmp.agent.trace.AiTraceContext.class, SolutionTemplateContext.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) method.invoke(service, null, context);

        String template = "# {{routeCode}} 框选区域养护建议\n";
        MarkdownTemplateRenderer.RenderResult rendered = new MarkdownTemplateRenderer().renderWithCheck(template, variables);

        assertTrue(rendered.getMissingVariables().isEmpty());
        assertTrue(rendered.getRenderedMarkdown().contains("G310 框选区域养护建议"));
    }

    @Test
    public void buildVariablesFillsMapObjectSectionPlanTemplateSections() throws Exception {
        AiSolutionTemplatePipelineServiceImpl service = new AiSolutionTemplatePipelineServiceImpl();
        SolutionTemplateContext context = mapObjectContext("ROAD_SECTION", "SECTION_PLAN");
        context.setObjectSummary(sectionSummary());
        context.setBusinessData(businessEvidenceData());

        Method method = AiSolutionTemplatePipelineServiceImpl.class
                .getDeclaredMethod("buildVariables", com.smartroad.srmp.agent.trace.AiTraceContext.class, SolutionTemplateContext.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) method.invoke(service, null, context);

        String template = "# {{routeCode}} 路段养护计划\n\n" +
                "## 一、路段概况\n{{routeSummary}}\n\n" +
                "## 二、技术状况\n{{assessmentSummary}}\n\n" +
                "## 三、主要病害\n{{diseaseSummary}}\n\n" +
                "## 四、问题分析\n{{problemAnalysis}}\n\n" +
                "## 五、养护建议\n{{maintenanceSuggestion}}\n\n" +
                "## 六、风险提示\n{{riskNotice}}\n\n" +
                "## 七、业务证据\n{{businessEvidenceSummary}}\n";
        MarkdownTemplateRenderer.RenderResult rendered = new MarkdownTemplateRenderer().renderWithCheck(template, variables);

        assertTrue(rendered.getMissingVariables().isEmpty());
        assertFalse(rendered.getRenderedMarkdown().contains("{{"));
        assertTrue(rendered.getRenderedMarkdown().contains("Y016140727"));
        assertTrue(rendered.getRenderedMarkdown().contains("K10-K12.5"));
        assertTrue(rendered.getRenderedMarkdown().contains("评定结果 2"));
        assertTrue(rendered.getRenderedMarkdown().contains("病害 5"));
        assertTrue(rendered.getRenderedMarkdown().contains("gis.queryDiseases"));
    }

    @Test
    public void buildVariablesFillsAssessmentAdviceAndLowScoreTemplateSections() throws Exception {
        AiSolutionTemplatePipelineServiceImpl service = new AiSolutionTemplatePipelineServiceImpl();
        Method method = AiSolutionTemplatePipelineServiceImpl.class
                .getDeclaredMethod("buildVariables", com.smartroad.srmp.agent.trace.AiTraceContext.class, SolutionTemplateContext.class);
        method.setAccessible(true);

        for (String solutionType : new String[]{"EVALUATION_UNIT_ADVICE", "LOW_SCORE_TREATMENT"}) {
            SolutionTemplateContext context = mapObjectContext("ASSESSMENT_RESULT", solutionType);
            context.setObjectSummary(assessmentSummary(solutionType));
            context.setBusinessData(businessEvidenceData());

            @SuppressWarnings("unchecked")
            Map<String, Object> variables = (Map<String, Object>) method.invoke(service, null, context);

            String template = "# {{routeCode}} 评定结果处置建议\n\n" +
                    "- 桩号：{{stakeRange}}\n" +
                    "- MQI：{{mqi}}\n" +
                    "- PQI：{{pqi}}\n" +
                    "- PCI：{{pci}}\n" +
                    "- 等级：{{grade}}\n\n" +
                    "## 一、评定判断\n{{assessmentSummary}}\n\n" +
                    "## 二、关联病害\n{{diseaseSummary}}\n\n" +
                    "## 三、问题分析\n{{problemAnalysis}}\n\n" +
                    "## 四、养护建议\n{{maintenanceSuggestion}}\n\n" +
                    "## 五、风险提示\n{{riskNotice}}\n";
            MarkdownTemplateRenderer.RenderResult rendered = new MarkdownTemplateRenderer().renderWithCheck(template, variables);

            assertEquals(solutionType, variables.get("solutionType"));
            assertTrue(rendered.getMissingVariables().isEmpty());
            assertFalse(rendered.getRenderedMarkdown().contains("{{"));
            assertTrue(rendered.getRenderedMarkdown().contains("Y016140727"));
            assertTrue(rendered.getRenderedMarkdown().contains("PCI"));
            assertTrue(rendered.getRenderedMarkdown().contains("病害 5"));
        }
    }

    @Test
    public void buildVariablesFillsAssessmentIdentityFallbacks() throws Exception {
        AiSolutionTemplatePipelineServiceImpl service = new AiSolutionTemplatePipelineServiceImpl();
        SolutionTemplateContext context = mapObjectContext("ASSESSMENT_RESULT", "LOW_SCORE_TREATMENT");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("objectType", "ASSESSMENT_RESULT");
        summary.put("objectId", "assessment-1");
        summary.put("routeCode", "Y016140727");
        summary.put("unit_id", "U-001");
        summary.put("stakeRange", "K10-K12.5");
        summary.put("mqi", 82.4);
        summary.put("pqi", 76.1);
        summary.put("pci", 72.8);
        summary.put("activeMetricGrade", "GOOD");
        context.setObjectSummary(summary);

        Method method = AiSolutionTemplatePipelineServiceImpl.class
                .getDeclaredMethod("buildVariables", com.smartroad.srmp.agent.trace.AiTraceContext.class, SolutionTemplateContext.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) method.invoke(service, null, context);

        String template = "- 单元编号：{{unitCode}}\n- 等级：{{grade}}\n";
        MarkdownTemplateRenderer.RenderResult rendered = new MarkdownTemplateRenderer().renderWithCheck(template, variables);

        assertTrue(rendered.getMissingVariables().isEmpty());
        assertEquals("U-001", variables.get("unitCode"));
        assertEquals("良", variables.get("grade"));
        assertTrue(rendered.getRenderedMarkdown().contains("U-001"));
        assertTrue(rendered.getRenderedMarkdown().contains("良"));
    }

    @Test
    public void buildVariablesDoesNotInventAssessmentIdentityForRouteReport() throws Exception {
        AiSolutionTemplatePipelineServiceImpl service = new AiSolutionTemplatePipelineServiceImpl();
        SolutionTemplateContext context = mapObjectContext("ROAD_ROUTE", "ROUTE_REPORT");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("objectType", "ROAD_ROUTE");
        summary.put("objectId", "route-1");
        summary.put("routeCode", "Y016140727");
        summary.put("stakeRange", "K0-K14.072");
        context.setObjectSummary(summary);

        Method method = AiSolutionTemplatePipelineServiceImpl.class
                .getDeclaredMethod("buildVariables", com.smartroad.srmp.agent.trace.AiTraceContext.class, SolutionTemplateContext.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) method.invoke(service, null, context);

        String template = "## 评定结果\n{{assessmentSummary}}\n";
        MarkdownTemplateRenderer.RenderResult rendered = new MarkdownTemplateRenderer().renderWithCheck(template, variables);

        assertTrue(rendered.getMissingVariables().isEmpty());
        assertFalse(rendered.getRenderedMarkdown().contains("评定单元：route-1"));
        assertFalse(rendered.getRenderedMarkdown().contains("等级：未分级"));
        assertTrue(rendered.getRenderedMarkdown().contains("未取得完整评定指标"));
    }

    @Test
    public void buildVariablesFillsRouteReportRouteCodeWhenRouteScopeHasNoSingleRoute() throws Exception {
        AiSolutionTemplatePipelineServiceImpl service = new AiSolutionTemplatePipelineServiceImpl();
        SolutionTemplateContext context = mapObjectContext("ROAD_ROUTE", "ROUTE_REPORT");
        context.setRouteCode("");
        context.setTitle("当前路线范围路线技术状况报告草稿");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("objectType", "ROAD_ROUTE");
        context.setObjectSummary(summary);

        Method method = AiSolutionTemplatePipelineServiceImpl.class
                .getDeclaredMethod("buildVariables", com.smartroad.srmp.agent.trace.AiTraceContext.class, SolutionTemplateContext.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) method.invoke(service, null, context);

        String template = "# {{routeCode}} 路线技术状况报告草稿\n\n{{routeSummary}}\n";
        MarkdownTemplateRenderer.RenderResult rendered = new MarkdownTemplateRenderer().renderWithCheck(template, variables);

        assertTrue(rendered.getMissingVariables().isEmpty());
        assertFalse(rendered.getRenderedMarkdown().contains("{{routeCode}}"));
        assertTrue(rendered.getRenderedMarkdown().contains("当前路线范围 路线技术状况报告草稿"));
    }

    @Test
    public void buildVariablesMarksMissingDiseaseSeverityWithoutLeakingTemplateVariable() throws Exception {
        AiSolutionTemplatePipelineServiceImpl service = new AiSolutionTemplatePipelineServiceImpl();
        SolutionTemplateContext context = mapObjectContext("DISEASE", "DISEASE_TREATMENT");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("objectType", "DISEASE");
        summary.put("objectId", "disease-1");
        summary.put("routeCode", "Y016140727");
        summary.put("stakeRange", "K1.1-K1.2");
        summary.put("diseaseName", "裂缝");
        summary.put("quantity", 12.5);
        summary.put("measureUnit", "m");
        context.setObjectSummary(summary);

        Method method = AiSolutionTemplatePipelineServiceImpl.class
                .getDeclaredMethod("buildVariables", com.smartroad.srmp.agent.trace.AiTraceContext.class, SolutionTemplateContext.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) method.invoke(service, null, context);

        String template = "- 严重程度：{{severity}}\n";
        MarkdownTemplateRenderer.RenderResult rendered = new MarkdownTemplateRenderer().renderWithCheck(template, variables);

        assertTrue(rendered.getMissingVariables().isEmpty());
        assertEquals("未标注", variables.get("severity"));
        assertTrue(rendered.getRenderedMarkdown().contains("严重程度：未标注"));
    }

    @Test
    public void buildVariablesFillsDiseaseReviewTemplateSections() throws Exception {
        AiSolutionTemplatePipelineServiceImpl service = new AiSolutionTemplatePipelineServiceImpl();
        SolutionTemplateContext context = mapObjectContext("DISEASE", "DISEASE_REVIEW");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("objectType", "DISEASE");
        summary.put("objectId", "disease-1");
        summary.put("routeCode", "Y016140727");
        summary.put("stakeRange", "K1.1-K1.2");
        summary.put("diseaseName", "裂缝");
        summary.put("quantity", 12.5);
        summary.put("measureUnit", "m");
        context.setObjectSummary(summary);
        context.setBusinessData(businessEvidenceData());

        Method method = AiSolutionTemplatePipelineServiceImpl.class
                .getDeclaredMethod("buildVariables", com.smartroad.srmp.agent.trace.AiTraceContext.class, SolutionTemplateContext.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) method.invoke(service, null, context);

        String template = "# {{routeCode}} 病害复核意见\n\n" +
                "## 一、病害对象\n" +
                "- 对象编号：{{objectId}}\n" +
                "- 病害类型：{{diseaseName}}\n" +
                "- 严重程度：{{severity}}\n" +
                "- 位置范围：{{stakeRange}}\n" +
                "- 工程量：{{quantity}}{{measureUnit}}\n\n" +
                "## 二、复核判断\n{{problemAnalysis}}\n\n" +
                "## 三、现场复核重点\n{{maintenanceSuggestion}}\n\n" +
                "## 四、业务证据\n{{businessEvidenceSummary}}\n\n" +
                "## 五、风险提示\n{{riskNotice}}\n";
        MarkdownTemplateRenderer.RenderResult rendered = new MarkdownTemplateRenderer().renderWithCheck(template, variables);

        assertTrue(rendered.getMissingVariables().isEmpty());
        assertFalse(rendered.getRenderedMarkdown().contains("{{"));
        assertTrue(rendered.getRenderedMarkdown().contains("病害复核意见"));
        assertTrue(rendered.getRenderedMarkdown().contains("严重程度：未标注"));
        assertTrue(rendered.getRenderedMarkdown().contains("gis.queryDiseases"));
    }

    @Test
    public void buildVariablesKeepsMapObjectTemplateCompleteWithoutBusinessEvidence() throws Exception {
        AiSolutionTemplatePipelineServiceImpl service = new AiSolutionTemplatePipelineServiceImpl();
        SolutionTemplateContext context = mapObjectContext("ROAD_SECTION", "SECTION_PLAN");
        context.setObjectSummary(sectionSummary());

        Method method = AiSolutionTemplatePipelineServiceImpl.class
                .getDeclaredMethod("buildVariables", com.smartroad.srmp.agent.trace.AiTraceContext.class, SolutionTemplateContext.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) method.invoke(service, null, context);

        String template = "## 业务证据\n{{businessEvidenceSummary}}\n";
        MarkdownTemplateRenderer.RenderResult rendered = new MarkdownTemplateRenderer().renderWithCheck(template, variables);

        assertTrue(rendered.getMissingVariables().isEmpty());
        assertFalse(rendered.getRenderedMarkdown().contains("{{"));
        assertTrue(rendered.getRenderedMarkdown().contains("未取得结构化工具取证"));
    }

    private SolutionTemplateContext regionContext() {
        SolutionTemplateContext context = new SolutionTemplateContext();
        context.setOriginType("MAP_REGION");
        context.setObjectType("MAP_REGION");
        context.setSolutionType("REGION_MAINTENANCE_SUGGESTION");
        context.setRouteCode("G210");
        context.setYear(2026);
        context.setRegionSummary(regionSummary());

        Map<String, Object> businessData = new LinkedHashMap<>();
        businessData.put("businessMarkdown", "区域业务分析");
        businessData.put("llmMarkdown", "## 区域综合判断\nLLM 区域判断\n\n## 养护建议\nLLM 养护建议\n\n## 风险提示\n人工复核后实施。");
        businessData.put("hotspots", regionSummary().get("hotspots"));
        context.setBusinessData(businessData);
        return context;
    }

    private SolutionTemplateContext mapObjectContext(String objectType, String solutionType) {
        SolutionTemplateContext context = new SolutionTemplateContext();
        context.setOriginType("MAP_OBJECT");
        context.setObjectType(objectType);
        context.setSolutionType(solutionType);
        context.setRouteCode("Y016140727");
        context.setYear(2026);
        context.setTitle("Y016140727 对象方案");
        return context;
    }

    private Map<String, Object> sectionSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("objectType", "ROAD_SECTION");
        summary.put("objectId", "section-1");
        summary.put("routeCode", "Y016140727");
        summary.put("year", 2026);
        summary.put("stakeRange", "K10-K12.5");
        summary.put("sectionName", "北堡-北六门线");
        summary.put("lengthKm", "2.5");
        summary.put("mqi", 82.4);
        summary.put("pqi", 76.1);
        summary.put("pci", 72.8);
        return summary;
    }

    private Map<String, Object> assessmentSummary(String solutionType) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("objectType", "ASSESSMENT_RESULT");
        summary.put("objectId", "assessment-1");
        summary.put("routeCode", "Y016140727");
        summary.put("year", 2026);
        summary.put("stakeRange", "K10-K12.5");
        summary.put("unitCode", "Y016-2026-U010");
        summary.put("mqi", "LOW_SCORE_TREATMENT".equals(solutionType) ? 58.2 : 85.1);
        summary.put("pqi", "LOW_SCORE_TREATMENT".equals(solutionType) ? 63.0 : 76.2);
        summary.put("pci", "LOW_SCORE_TREATMENT".equals(solutionType) ? 59.8 : 77.6);
        summary.put("grade", "LOW_SCORE_TREATMENT".equals(solutionType) ? "差" : "良");
        return summary;
    }

    private Map<String, Object> businessEvidenceData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("businessEvidenceSummary", "- 取证概况：成功工具 3 个，业务命中 7\n" +
                "- gis.queryAssessmentResults：查询到评定结果 2 条（命中 2）\n" +
                "- gis.queryDiseases：查询到病害 5 条（命中 5）");

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("toolSuccessCount", 3);
        evidence.put("businessHitCount", 7);
        List<Map<String, Object>> toolSummary = new ArrayList<>();
        Map<String, Object> assessmentTool = new LinkedHashMap<>();
        assessmentTool.put("toolName", "gis.queryAssessmentResults");
        assessmentTool.put("hitCount", 2);
        assessmentTool.put("summary", "查询到评定结果 2 条");
        toolSummary.add(assessmentTool);
        Map<String, Object> diseaseTool = new LinkedHashMap<>();
        diseaseTool.put("toolName", "gis.queryDiseases");
        diseaseTool.put("hitCount", 5);
        diseaseTool.put("summary", "查询到病害 5 条");
        toolSummary.add(diseaseTool);
        evidence.put("toolSummary", toolSummary);
        data.put("businessEvidence", evidence);
        return data;
    }

    private Map<String, Object> regionSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("areaKm2", new BigDecimal("262.5469"));
        summary.put("routeCount", 1);
        summary.put("sectionCount", 4);
        summary.put("unitCount", 23);

        Map<String, Object> disease = new LinkedHashMap<>();
        disease.put("disease_count", 59);
        disease.put("heavy_count", 12);
        disease.put("medium_count", 26);
        summary.put("diseaseSummary", disease);

        Map<String, Object> assessment = new LinkedHashMap<>();
        assessment.put("avg_mqi", new BigDecimal("79.655"));
        assessment.put("avg_pqi", new BigDecimal("78.600"));
        assessment.put("avg_pci", new BigDecimal("77.046"));
        summary.put("assessmentSummary", assessment);

        List<Map<String, Object>> hotspots = new ArrayList<>();
        Map<String, Object> hotspot = new LinkedHashMap<>();
        hotspot.put("route_code", "G210");
        hotspot.put("start_stake", new BigDecimal("97.474"));
        hotspot.put("end_stake", new BigDecimal("112.386"));
        hotspot.put("disease_count", 59);
        hotspot.put("heavy_count", 12);
        hotspots.add(hotspot);
        summary.put("hotspots", hotspots);
        return summary;
    }
}
