package com.smartroad.srmp.gis.service.impl;

import com.smartroad.srmp.agent.knowledge.dto.KnowledgeSearchRequest;
import com.smartroad.srmp.agent.knowledge.service.KnowledgeService;
import com.smartroad.srmp.agent.knowledge.vo.KnowledgeSearchResult;
import com.smartroad.srmp.agent.llm.LlmClient;
import com.smartroad.srmp.agent.outline.service.OutlineService;
import com.smartroad.srmp.agent.outline.vo.OutlineSearchResult;
import com.smartroad.srmp.agent.rag.RagOptions;
import com.smartroad.srmp.agent.solution.service.AiSolutionTemplatePipelineService;
import com.smartroad.srmp.agent.solution.support.AiSolutionReferenceSourceGuard;
import com.smartroad.srmp.agent.solution.template.SolutionTemplateContext;
import com.smartroad.srmp.agent.solution.template.TemplatePipelineResult;
import com.smartroad.srmp.agent.trace.AiTraceContext;
import com.smartroad.srmp.agent.trace.service.AiTraceService;
import com.smartroad.srmp.gis.dto.MapRegionAnalysisRequest;
import com.smartroad.srmp.gis.dto.MapRegionSolutionRequest;
import com.smartroad.srmp.gis.dto.MapRegionSolutionResponse;
import com.smartroad.srmp.gis.service.MapRegionAnalysisService;
import com.smartroad.srmp.gis.service.MapRegionSolutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class MapRegionSolutionServiceImpl implements MapRegionSolutionService {

    @Resource
    private MapRegionAnalysisService mapRegionAnalysisService;

    @Resource
    private KnowledgeService knowledgeService;

    @Resource
    private OutlineService outlineService;

    @Resource
    private LlmClient llmClient;

    @Resource
    private AiTraceService aiTraceService;

    @Resource
    private AiSolutionTemplatePipelineService templatePipelineService;

    @Override
    public MapRegionSolutionResponse generate(MapRegionSolutionRequest request) {
        AiTraceContext trace = AiTraceContext.start("MAP_REGION_SOLUTION", buildTraceMessage(request));
        MapRegionSolutionResponse response = new MapRegionSolutionResponse();
        response.setSolutionType(safe(request == null ? null : request.getSolutionType()).isEmpty()
                ? "REGION_MAINTENANCE_SUGGESTION"
                : safe(request.getSolutionType()));
        try {
            MapRegionAnalysisRequest analysisRequest = toAnalysisRequest(request);
            Map<String, Object> regionSummary = runRegionAnalysis(trace, analysisRequest);
            List<Map<String, Object>> hotspots = runHotspotStep(trace, regionSummary);
            String businessMarkdown = runBusinessAnalysis(trace, request, regionSummary, hotspots);
            Map<String, Object> sources = runKnowledgeRetrieve(trace, request, businessMarkdown);
            String llmMarkdown = runSolutionGenerate(trace, request, regionSummary, businessMarkdown, sources);
            TemplatePipelineResult pipelineResult = runTemplatePipeline(trace, request, regionSummary, hotspots, businessMarkdown, llmMarkdown, sources);
            String markdown = pipelineResult.getMarkdown();
            Map<String, Object> quality = runQualityCheck(trace, regionSummary, markdown);

            response.setTitle(buildTitle(request, regionSummary));
            response.setMarkdown(markdown);
            response.setRegionSummary(regionSummary);
            response.setQualityCheck(quality);
            response.setTemplateMeta(pipelineResult.getTemplateMeta());
            response.setAnswerMeta(buildAnswerMeta(sources));
            response.setSourceSummaries(mergeSources(buildSourceSummaries(regionSummary, sources, trace), pipelineResult.getSourceSummaries()));
            trace.setMode(Boolean.TRUE.equals(sources.get("llmSuccess")) ? "MAP_REGION_LLM" : "MAP_REGION_FALLBACK");
            trace.setStatus("SUCCESS");
            trace.setFallback(!Boolean.TRUE.equals(sources.get("llmSuccess")));
            return response;
        } catch (Exception e) {
            trace.setMode("FAILED");
            trace.setStatus("FAILED");
            trace.setFallback(true);
            trace.setError(e.getMessage());
            throw e;
        } finally {
            trace.finish();
            response.setTrace(trace.toMap());
            try {
                aiTraceService.save(trace);
            } catch (Exception saveError) {
                log.warn("[MAP-REGION] save trace failed traceId={} error={}", trace.getTraceId(), saveError.getMessage(), saveError);
            }
        }
    }

    private Map<String, Object> runRegionAnalysis(AiTraceContext trace, MapRegionAnalysisRequest request) {
        AiTraceContext.StepTimer geometryTimer = trace.step("region_geometry_parse", "解析框选范围");
        try {
            ensurePolygonRequest(request);
            geometryTimer.success(1);
        } catch (RuntimeException e) {
            geometryTimer.failed(e);
            throw e;
        }

        AiTraceContext.StepTimer spatialTimer = trace.step("region_spatial_query", "查询区域内对象");
        try {
            Map<String, Object> summary = mapRegionAnalysisService.analyze(request);
            int objectCount = intValue(summary.get("routeCount")) + intValue(summary.get("sectionCount")) + intValue(summary.get("unitCount"));
            Map<String, Object> disease = mapValue(summary.get("diseaseSummary"));
            objectCount += intValue(disease.get("disease_count"));
            spatialTimer.success(objectCount);

            AiTraceContext.StepTimer statisticsTimer = trace.step("region_statistics", "区域统计");
            statisticsTimer.success(objectCount);
            return summary;
        } catch (RuntimeException e) {
            spatialTimer.failed(e);
            throw e;
        }
    }

    private List<Map<String, Object>> runHotspotStep(AiTraceContext trace, Map<String, Object> regionSummary) {
        AiTraceContext.StepTimer timer = trace.step("region_hotspot_detect", "热点识别");
        Object raw = regionSummary.get("hotspots");
        List<Map<String, Object>> hotspots = raw instanceof List ? (List<Map<String, Object>>) raw : new ArrayList<>();
        timer.success(hotspots.size());
        return hotspots;
    }

    private String runBusinessAnalysis(AiTraceContext trace, MapRegionSolutionRequest request, Map<String, Object> summary, List<Map<String, Object>> hotspots) {
        AiTraceContext.StepTimer timer = trace.step("region_business_analysis", "业务分析");
        String markdown = buildBusinessMarkdown(request, summary, hotspots);
        timer.success(1);
        return markdown;
    }

    private Map<String, Object> runKnowledgeRetrieve(AiTraceContext trace, MapRegionSolutionRequest request, String businessMarkdown) {
        AiTraceContext.StepTimer timer = trace.step("region_knowledge_retrieve", "知识库检索");
        Map<String, Object> result = new LinkedHashMap<>();
        List<KnowledgeSearchResult> knowledgeSources = new ArrayList<>();
        List<OutlineSearchResult> outlineSources = new ArrayList<>();
        result.put("knowledgeSources", knowledgeSources);
        result.put("outlineSources", outlineSources);
        result.put("llmSuccess", false);

        RagOptions options = RagOptions.autoByQuestion("区域养护建议", request == null ? null : request.getOptions());
        if (!options.isUseKnowledge() && !options.isUseOutline()) {
            timer.skipped();
            return result;
        }
        try {
            String query = buildKnowledgeQuery(request, businessMarkdown);
            if (options.isUseKnowledge()) {
                KnowledgeSearchRequest searchRequest = new KnowledgeSearchRequest();
                searchRequest.setQuery(query);
                // 多取一些再做业务相关性过滤，避免 Outline 默认引导文档挤占真正的养护资料。
                searchRequest.setTopK(Math.max(options.getTopK(), Math.min(options.getTopK() * 3, 20)));
                knowledgeSources = filterKnowledgeSources(knowledgeService.search(searchRequest), options.isUseOutline(), options.getTopK());
            }
            if (options.isUseOutline()) {
                outlineSources = filterOutlineSources(outlineService.search(query, Math.max(options.getTopK(), Math.min(options.getTopK() * 3, 20))), options.getTopK());
            }
            result.put("knowledgeSources", knowledgeSources);
            result.put("outlineSources", outlineSources);
            timer.success(knowledgeSources.size() + outlineSources.size(), sourceFilterTraceData(knowledgeSources, outlineSources, options));
        } catch (RuntimeException e) {
            result.put("knowledgeError", e.getMessage());
            timer.failed(e);
        }
        return result;
    }

    private String runSolutionGenerate(AiTraceContext trace, MapRegionSolutionRequest request, Map<String, Object> summary, String businessMarkdown, Map<String, Object> sources) {
        String prompt = buildPrompt(summary, businessMarkdown, sources);
        boolean requireAi = optionBoolean(request, "requireAi", "aiRequired", "forceAi");
        sources.put("requireAi", requireAi);

        AiTraceContext.StepTimer promptTimer = trace.step("region_prompt_build", "区域方案 Prompt 构建");
        Map<String, Object> promptData = new LinkedHashMap<>();
        promptData.put("systemPrompt", "智路养护平台区域养护方案生成助手");
        promptData.put("promptChars", prompt.length());
        promptData.put("businessMarkdownChars", safe(businessMarkdown).length());
        promptData.put("knowledgeCount", listSize(sources == null ? null : sources.get("knowledgeSources")));
        promptData.put("outlineCount", listSize(sources == null ? null : sources.get("outlineSources")));
        promptData.put("requireAi", requireAi);
        promptTimer.success(1, promptData);

        AiTraceContext.StepTimer llmTimer = trace.step("llm_answer", "大模型生成区域养护建议");
        Map<String, Object> beforeDiag = new LinkedHashMap<>(llmClient.diagnostics());
        beforeDiag.put("enabled", llmClient.enabled());
        sources.put("llmDiagnosticsBeforeCall", beforeDiag);

        if (!llmClient.enabled()) {
            String reason = firstNonBlank(safe(beforeDiag.get("errorMessage")), "srmp.llm 未启用，区域养护建议使用业务统计模板兜底");
            sources.put("llmSuccess", false);
            sources.put("llmSkipped", true);
            sources.put("llmError", reason);
            sources.put("answerSource", "BUSINESS_FALLBACK");
            sources.put("fallbackReason", reason);
            beforeDiag.put("requireAi", requireAi);
            llmTimer.skipped(beforeDiag);
            if (requireAi) {
                throw new IllegalStateException("区域养护建议要求调用 AI，但 " + reason);
            }
            return businessMarkdown;
        }

        try {
            String answer = llmClient.chat("你是智路养护平台区域养护方案生成助手。资料不足时必须说明，输出 Markdown 草稿。", prompt);
            Map<String, Object> llmData = new LinkedHashMap<>(llmClient.diagnostics());
            boolean success = answer != null && answer.trim().length() > 0;
            sources.put("llmSuccess", success);
            sources.put("llmDiagnostics", llmData);

            if (success) {
                sources.put("llmSkipped", false);
                sources.put("answerSource", "LLM");
                llmData.put("answerChars", answer.length());
                llmData.put("requireAi", requireAi);
                llmTimer.success(1, llmData);
                return sanitize(answer);
            }

            String reason = firstNonBlank(safe(llmData.get("errorMessage")), "LLM 未返回内容，区域养护建议使用业务统计模板兜底");
            sources.put("llmSkipped", false);
            sources.put("llmError", reason);
            sources.put("answerSource", "BUSINESS_FALLBACK");
            sources.put("fallbackReason", reason);
            llmData.put("reason", reason);
            llmData.put("requireAi", requireAi);
            llmTimer.failed(new RuntimeException(reason), llmData);
            if (requireAi) {
                throw new IllegalStateException("区域养护建议要求调用 AI，但 " + reason);
            }
            return businessMarkdown;
        } catch (RuntimeException e) {
            if (requireAi && safe(e.getMessage()).startsWith("区域养护建议要求调用 AI")) {
                throw e;
            }
            Map<String, Object> llmData = new LinkedHashMap<>(llmClient.diagnostics());
            llmData.put("exception", e.getClass().getSimpleName());
            llmData.put("exceptionMessage", e.getMessage());
            sources.put("llmSuccess", false);
            sources.put("llmSkipped", false);
            sources.put("llmError", e.getMessage());
            sources.put("answerSource", "BUSINESS_FALLBACK");
            sources.put("fallbackReason", e.getMessage());
            sources.put("llmDiagnostics", llmData);
            llmData.put("requireAi", requireAi);
            llmTimer.failed(e, llmData);
            if (requireAi) {
                throw new IllegalStateException("区域养护建议要求调用 AI，但 LLM 调用失败：" + e.getMessage(), e);
            }
            return businessMarkdown;
        }
    }

    private Map<String, Object> runQualityCheck(AiTraceContext trace, Map<String, Object> summary, String markdown) {
        AiTraceContext.StepTimer timer = trace.step("region_quality_check", "质量检查");
        List<Map<String, Object>> items = new ArrayList<>();
        int score = 100;
        score -= qualityItem(items, summary.get("geometry") != null, "REGION_GEOMETRY", "已包含区域范围", 20);
        score -= qualityItem(items, !mapValue(summary.get("diseaseSummary")).isEmpty(), "REGION_STATISTICS", "已包含区域统计", 20);
        score -= qualityItem(items, containsAny(markdown, "热点", "重点"), "REGION_HOTSPOT", "已包含热点识别", 15);
        score -= qualityItem(items, containsAny(markdown, "养护", "处置", "策略"), "REGION_STRATEGY", "已包含养护策略", 20);
        score -= qualityItem(items, containsAny(markdown, "优先", "近期", "P1", "P2"), "REGION_PRIORITY", "已包含优先级建议", 15);
        score -= qualityItem(items, containsAny(markdown, "人工审核", "复核", "草稿"), "REGION_REVIEW_NOTICE", "已包含人工审核提示", 10);
        if (score < 0) {
            score = 0;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", score >= 80 && items.stream().noneMatch(i -> "ERROR".equals(i.get("level"))));
        result.put("score", score);
        result.put("level", score >= 90 ? "A" : score >= 80 ? "B" : score >= 60 ? "C" : "D");
        result.put("summary", "区域方案质量校验" + (Boolean.TRUE.equals(result.get("passed")) ? "通过" : "需复核") + "，评分 " + score + "。");
        result.put("items", items);
        result.put("originType", "MAP_REGION");
        result.put("checkedAt", new Date());
        timer.success(items.size());
        return result;
    }

    private TemplatePipelineResult runTemplatePipeline(AiTraceContext trace,
                                                       MapRegionSolutionRequest request,
                                                       Map<String, Object> regionSummary,
                                                       List<Map<String, Object>> hotspots,
                                                       String businessMarkdown,
                                                       String llmMarkdown,
                                                       Map<String, Object> sources) {
        SolutionTemplateContext context = new SolutionTemplateContext();
        context.setOriginType("MAP_REGION");
        context.setObjectType("MAP_REGION");
        context.setSolutionType(safe(request == null ? null : request.getSolutionType()).isEmpty()
                ? "REGION_MAINTENANCE_SUGGESTION"
                : safe(request.getSolutionType()));
        context.setRouteCode(queryValue(request, "routeCode"));
        context.setYear(intObject(queryValue(request, "year")));
        context.setTitle(buildTitle(request, regionSummary));
        context.setRegionSummary(regionSummary);
        context.setBusinessData(regionBusinessData(businessMarkdown, llmMarkdown, hotspots, sources));
        context.setKnowledgeSources(knowledgeSourceMaps(sources));
        context.setOutlineSources(outlineSourceMaps(sources));
        context.setFallbackMarkdown(safe(llmMarkdown).isEmpty() ? businessMarkdown : llmMarkdown);
        context.setTrace(trace);
        return templatePipelineService.generate(context);
    }

    private Map<String, Object> regionBusinessData(String businessMarkdown,
                                                   String llmMarkdown,
                                                   List<Map<String, Object>> hotspots,
                                                   Map<String, Object> sources) {
        Map<String, Object> businessData = new LinkedHashMap<>();
        businessData.put("businessMarkdown", businessMarkdown);
        businessData.put("llmMarkdown", llmMarkdown);
        businessData.put("hotspots", hotspots == null ? new ArrayList<>() : hotspots);
        businessData.put("sourcePrecision", sourcePrecision(sources));
        return businessData;
    }

    private Map<String, Object> sourcePrecision(Map<String, Object> sources) {
        Map<String, Object> precision = new LinkedHashMap<>();
        Object knowledgeRaw = sources == null ? null : sources.get("knowledgeSources");
        Object outlineRaw = sources == null ? null : sources.get("outlineSources");
        precision.put("knowledgeCount", knowledgeRaw instanceof List ? ((List<?>) knowledgeRaw).size() : 0);
        precision.put("outlineCount", outlineRaw instanceof List ? ((List<?>) outlineRaw).size() : 0);
        precision.put("llmSuccess", sources != null && Boolean.TRUE.equals(sources.get("llmSuccess")));
        precision.put("knowledgeError", sources == null ? "" : safe(sources.get("knowledgeError")));
        precision.put("llmError", sources == null ? "" : safe(sources.get("llmError")));
        return precision;
    }


    private Map<String, Object> buildAnswerMeta(Map<String, Object> sources) {
        Map<String, Object> meta = new LinkedHashMap<>();
        boolean llmSuccess = sources != null && Boolean.TRUE.equals(sources.get("llmSuccess"));
        meta.put("answerSource", sources == null ? "BUSINESS_FALLBACK" : firstNonBlank(safe(sources.get("answerSource")), llmSuccess ? "LLM" : "BUSINESS_FALLBACK"));
        meta.put("llmSuccess", llmSuccess);
        meta.put("llmSkipped", sources != null && Boolean.TRUE.equals(sources.get("llmSkipped")));
        meta.put("requireAi", sources != null && Boolean.TRUE.equals(sources.get("requireAi")));
        meta.put("fallback", !llmSuccess);
        meta.put("fallbackReason", sources == null ? "" : firstNonBlank(safe(sources.get("fallbackReason")), safe(sources.get("llmError"))));
        meta.put("llmDiagnostics", sources == null ? new LinkedHashMap<>() : sources.get("llmDiagnostics"));
        meta.put("llmDiagnosticsBeforeCall", sources == null ? new LinkedHashMap<>() : sources.get("llmDiagnosticsBeforeCall"));
        return meta;
    }

    private int qualityItem(List<Map<String, Object>> items, boolean passed, String code, String message, int penalty) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("level", passed ? "OK" : (penalty >= 20 ? "ERROR" : "WARN"));
        item.put("code", code);
        item.put("message", passed ? message : "缺少或需补充：" + message);
        item.put("penalty", passed ? 0 : penalty);
        items.add(item);
        return passed ? 0 : penalty;
    }

    private MapRegionAnalysisRequest toAnalysisRequest(MapRegionSolutionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        MapRegionAnalysisRequest analysis = new MapRegionAnalysisRequest();
        analysis.setGeometry(request.getGeometry());
        analysis.setQuery(request.getQuery());
        analysis.setLayers(request.getLayers());
        analysis.setOptions(request.getOptions());
        return analysis;
    }

    private void ensurePolygonRequest(MapRegionAnalysisRequest request) {
        Map<String, Object> geometry = request == null ? null : request.getGeometry();
        if (geometry == null || !"Polygon".equals(String.valueOf(geometry.get("type")))) {
            throw new IllegalArgumentException("geometry 只支持 GeoJSON Polygon");
        }
    }

    private String buildTraceMessage(MapRegionSolutionRequest request) {
        Map<String, Object> query = request == null || request.getQuery() == null ? new LinkedHashMap<>() : request.getQuery();
        return "区域养护建议 " + safe(query.get("routeCode")) + " " + safe(query.get("year"));
    }

    private String buildTitle(MapRegionSolutionRequest request, Map<String, Object> summary) {
        Map<String, Object> query = request == null || request.getQuery() == null ? new LinkedHashMap<>() : request.getQuery();
        String route = safe(query.get("routeCode")).isEmpty() ? "选区" : safe(query.get("routeCode"));
        return route + " 区域养护建议草稿";
    }

    private String buildBusinessMarkdown(MapRegionSolutionRequest request, Map<String, Object> summary, List<Map<String, Object>> hotspots) {
        Map<String, Object> disease = mapValue(summary.get("diseaseSummary"));
        Map<String, Object> assessment = mapValue(summary.get("assessmentSummary"));
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(buildTitle(request, summary)).append("\n\n");
        sb.append("## 一、区域概况\n\n");
        sb.append("- 选区面积：").append(safe(summary.get("areaKm2"))).append(" km2\n");
        sb.append("- 涉及路线：").append(safe(summary.get("routeCount"))).append(" 条\n");
        sb.append("- 涉及路段：").append(safe(summary.get("sectionCount"))).append(" 个\n");
        sb.append("- 评定单元：").append(safe(summary.get("unitCount"))).append(" 个\n\n");
        sb.append("## 二、区域统计\n\n");
        sb.append("- 病害数量：").append(safe(disease.get("disease_count"))).append("\n");
        sb.append("- 重度病害：").append(safe(disease.get("heavy_count"))).append("\n");
        sb.append("- 平均 MQI：").append(safe(assessment.get("avg_mqi"))).append("\n");
        sb.append("- 平均 PQI：").append(safe(assessment.get("avg_pqi"))).append("\n");
        sb.append("- 平均 PCI：").append(safe(assessment.get("avg_pci"))).append("\n\n");
        sb.append("## 三、热点识别\n\n");
        if (hotspots.isEmpty()) {
            sb.append("当前选区未识别到明显病害热点。\n\n");
        } else {
            for (int i = 0; i < hotspots.size(); i++) {
                Map<String, Object> item = hotspots.get(i);
                sb.append(i + 1).append(". ").append(safe(item.get("route_code")))
                        .append("，病害 ").append(safe(item.get("disease_count")))
                        .append("，重度 ").append(safe(item.get("heavy_count"))).append("\n");
            }
            sb.append("\n");
        }
        sb.append("## 四、养护策略\n\n");
        sb.append("建议优先复核重度病害集中、PCI 或 MQI 偏低的局部区域，按病害集中程度安排近期处置和预防性养护。\n\n");
        sb.append("## 五、优先级建议\n\n");
        sb.append("P1：重度病害集中或低 PCI 区域；P2：中度病害集中区域；P3：轻度病害和常规巡查区域。\n\n");
        sb.append("## 六、风险提示\n\n");
        sb.append("本建议为 AI 生成草稿，需由养护技术人员结合现场复核、预算和交通组织条件人工审核。\n");
        return sb.toString();
    }

    private String buildKnowledgeQuery(MapRegionSolutionRequest request, String businessMarkdown) {
        return "区域养护建议 " + buildTraceMessage(request) + " " + shortText(businessMarkdown, 300);
    }

    private String buildPrompt(Map<String, Object> summary, String businessMarkdown, Map<String, Object> sources) {
        return "请基于区域统计、热点和公路养护知识来源生成区域养护建议草稿。只允许引用与道路养护、病害处置、技术状况评定、低分单元或区域养护直接相关的资料；忽略 Outline 产品说明、编辑器说明、入门说明、API 集成说明等无关资料。\n\n【区域统计】\n" + summary +
                "\n\n【业务分析】\n" + businessMarkdown +
                "\n\n【已过滤的可用来源】\n" + buildSourceContext(sources);
    }

    private List<Map<String, Object>> buildSourceSummaries(Map<String, Object> summary, Map<String, Object> sources, AiTraceContext trace) {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(source("MAP_REGION", "地图框选区域", trace.getTraceId(), "", "区域面积 " + safe(summary.get("areaKm2")) + " km2"));
        result.add(source("BUSINESS_DATA", "区域业务统计", trace.getTraceId(), "", "病害、评定和热点聚合统计"));

        Object knowledgeRaw = sources.get("knowledgeSources");
        if (knowledgeRaw instanceof List) {
            for (Object raw : (List<?>) knowledgeRaw) {
                if (raw instanceof KnowledgeSearchResult) {
                    KnowledgeSearchResult item = (KnowledgeSearchResult) raw;
                    result.add(source("KNOWLEDGE", item.getTitle(), item.getDocumentId(), item.getSourceUrl(), shortText(item.getContent(), 500)));
                }
            }
        }
        Object outlineRaw = sources.get("outlineSources");
        if (outlineRaw instanceof List) {
            for (Object raw : (List<?>) outlineRaw) {
                if (raw instanceof OutlineSearchResult) {
                    OutlineSearchResult item = (OutlineSearchResult) raw;
                    result.add(source("OUTLINE", item.getTitle(), item.getId(), item.getUrl(), shortText(item.getText(), 500)));
                }
            }
        }
        result.add(source("TRACE", "AI Trace", trace.getTraceId(), "", trace.getTraceId()));
        return result;
    }

    private Map<String, Object> source(String type, String title, String id, String url, String excerpt) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("sourceType", type);
        item.put("sourceTitle", safe(title).isEmpty() ? type : title);
        item.put("sourceId", safe(id));
        item.put("sourceUrl", safe(url));
        item.put("contentExcerpt", safe(excerpt));
        return item;
    }

    private List<Map<String, Object>> mergeSources(List<Map<String, Object>> original, List<Map<String, Object>> pipelineSources) {
        List<Map<String, Object>> merged = new ArrayList<>();
        if (original != null) {
            merged.addAll(original);
        }
        if (pipelineSources != null) {
            merged.addAll(pipelineSources);
        }
        return merged;
    }

    private List<Map<String, Object>> knowledgeSourceMaps(Map<String, Object> sources) {
        List<Map<String, Object>> result = new ArrayList<>();
        Object raw = sources == null ? null : sources.get("knowledgeSources");
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (item instanceof KnowledgeSearchResult) {
                    KnowledgeSearchResult source = (KnowledgeSearchResult) item;
                    result.add(source("KNOWLEDGE", source.getTitle(), source.getDocumentId(), source.getSourceUrl(), shortText(source.getContent(), 500)));
                }
            }
        }
        return result;
    }

    private List<Map<String, Object>> outlineSourceMaps(Map<String, Object> sources) {
        List<Map<String, Object>> result = new ArrayList<>();
        Object raw = sources == null ? null : sources.get("outlineSources");
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                if (item instanceof OutlineSearchResult) {
                    OutlineSearchResult source = (OutlineSearchResult) item;
                    result.add(source("OUTLINE", source.getTitle(), source.getId(), source.getUrl(), shortText(source.getText(), 500)));
                }
            }
        }
        return result;
    }

    private List<KnowledgeSearchResult> filterKnowledgeSources(List<KnowledgeSearchResult> sources, boolean allowOutline, int limit) {
        List<KnowledgeSearchResult> result = new ArrayList<>();
        if (sources == null || sources.isEmpty()) {
            return result;
        }
        for (KnowledgeSearchResult source : sources) {
            if (source == null) {
                continue;
            }
            String sourceType = safe(source.getSourceType()).toUpperCase(Locale.ROOT);
            boolean outlineSource = "OUTLINE".equals(sourceType);
            if (outlineSource && !allowOutline) {
                continue;
            }
            if (AiSolutionReferenceSourceGuard.isIrrelevantOutlineDoc(source.getTitle(), source.getContent())) {
                continue;
            }
            if (!AiSolutionReferenceSourceGuard.isRoadMaintenanceText(source.getTitle(), source.getHeading(), source.getContent())) {
                continue;
            }
            result.add(source);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private List<OutlineSearchResult> filterOutlineSources(List<OutlineSearchResult> sources, int limit) {
        List<OutlineSearchResult> result = new ArrayList<>();
        if (sources == null || sources.isEmpty()) {
            return result;
        }
        for (OutlineSearchResult source : sources) {
            if (source == null) {
                continue;
            }
            if (AiSolutionReferenceSourceGuard.isIrrelevantOutlineDoc(source.getTitle(), source.getText())) {
                continue;
            }
            if (!AiSolutionReferenceSourceGuard.isRoadMaintenanceText(source.getTitle(), source.getText())) {
                continue;
            }
            result.add(source);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private Map<String, Object> sourceFilterTraceData(List<KnowledgeSearchResult> knowledgeSources,
                                                      List<OutlineSearchResult> outlineSources,
                                                      RagOptions options) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("knowledgeCount", knowledgeSources == null ? 0 : knowledgeSources.size());
        data.put("outlineCount", outlineSources == null ? 0 : outlineSources.size());
        data.put("useOutline", options != null && options.isUseOutline());
        data.put("filter", "ROAD_MAINTENANCE_RELEVANCE_ONLY");
        return data;
    }

    private String buildSourceContext(Map<String, Object> sources) {
        StringBuilder sb = new StringBuilder();
        Object knowledgeRaw = sources == null ? null : sources.get("knowledgeSources");
        int index = 1;
        if (knowledgeRaw instanceof List) {
            for (Object raw : (List<?>) knowledgeRaw) {
                if (raw instanceof KnowledgeSearchResult) {
                    KnowledgeSearchResult item = (KnowledgeSearchResult) raw;
                    sb.append(index++).append(". [KNOWLEDGE] ").append(safe(item.getTitle()));
                    if (!safe(item.getHeading()).isEmpty()) {
                        sb.append(" / ").append(safe(item.getHeading()));
                    }
                    sb.append("：").append(shortText(item.getContent(), 260)).append("\n");
                }
            }
        }
        Object outlineRaw = sources == null ? null : sources.get("outlineSources");
        if (outlineRaw instanceof List) {
            for (Object raw : (List<?>) outlineRaw) {
                if (raw instanceof OutlineSearchResult) {
                    OutlineSearchResult item = (OutlineSearchResult) raw;
                    sb.append(index++).append(". [OUTLINE] ").append(safe(item.getTitle()))
                            .append("：").append(shortText(item.getText(), 260)).append("\n");
                }
            }
        }
        return sb.length() == 0 ? "无可用专业知识来源，本次仅使用区域业务统计和热点识别结果。" : sb.toString();
    }

    private String queryValue(MapRegionSolutionRequest request, String key) {
        Map<String, Object> query = request == null || request.getQuery() == null ? new LinkedHashMap<>() : request.getQuery();
        return safe(query.get(key));
    }

    private Integer intObject(String value) {
        if (safe(value).isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean optionBoolean(MapRegionSolutionRequest request, String... keys) {
        Map<String, Object> options = request == null ? null : request.getOptions();
        if (options == null || keys == null) {
            return false;
        }
        for (String key : keys) {
            Object value = options.get(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            String text = safe(value);
            if ("true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text) || "Y".equalsIgnoreCase(text)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<>();
    }

    private int intValue(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean containsAny(String text, String... words) {
        String value = text == null ? "" : text;
        for (String word : words) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String shortText(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private int listSize(Object value) {
        return value instanceof List ? ((List<?>) value).size() : 0;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!safe(value).isEmpty()) {
                return safe(value);
            }
        }
        return "";
    }

    private String sanitize(String text) {
        return text == null ? "" : text.replaceAll("(?is)<think>.*?</think>", "").trim();
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
