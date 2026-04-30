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

        String template = "# {{routeCode}} {{year}} 框选区域养护建议\n\n" +
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
