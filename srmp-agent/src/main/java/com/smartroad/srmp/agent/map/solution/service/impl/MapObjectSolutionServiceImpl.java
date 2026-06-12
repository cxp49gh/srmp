package com.smartroad.srmp.agent.map.solution.service.impl;

import com.smartroad.srmp.agent.map.MapObjectContext;
import com.smartroad.srmp.agent.map.MapObjectContextService;
import com.smartroad.srmp.agent.llm.LlmClient;
import com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionRequest;
import com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionResponse;
import com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionType;
import com.smartroad.srmp.agent.map.solution.service.MapObjectSolutionQualityChecker;
import com.smartroad.srmp.agent.map.solution.service.MapObjectSolutionService;
import com.smartroad.srmp.agent.solution.service.AiSolutionTemplatePipelineService;
import com.smartroad.srmp.agent.solution.template.SolutionTemplateContext;
import com.smartroad.srmp.agent.solution.template.TemplatePipelineResult;
import com.smartroad.srmp.agent.trace.AiTraceContext;
import com.smartroad.srmp.agent.trace.service.AiTraceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class MapObjectSolutionServiceImpl implements MapObjectSolutionService {

    @Resource
    private MapObjectContextService mapObjectContextService;

    @Resource
    private MapObjectSolutionQualityChecker qualityChecker;

    @Resource
    private AiTraceService aiTraceService;

    @Resource
    private AiSolutionTemplatePipelineService templatePipelineService;

    @Resource
    private LlmClient llmClient;

    @Override
    public MapObjectSolutionResponse generate(MapObjectSolutionRequest request) {
        AiTraceContext trace = AiTraceContext.start("MAP_OBJECT_SOLUTION", stringValue(request == null ? null : request.getSolutionType(), ""));
        try {
            if (request == null) {
                throw new IllegalArgumentException("请求不能为空");
            }

            AiTraceContext.StepTimer contextTimer = trace.step("map_object_context", "地图对象上下文");
            MapObjectContext ctx = resolveContext(request);
            contextTimer.success(ctx != null && ctx.isPresent() ? 1 : 0);
            if (ctx == null || !ctx.isPresent()) {
                throw new IllegalArgumentException("未识别到可生成方案的地图对象");
            }

            AiTraceContext.StepTimer solutionTimer = trace.step("map_object_solution_generate", "生成对象方案");
            MapObjectSolutionResponse response = buildResponse(request, ctx, trace);
            solutionTimer.success(1);

            AiTraceContext.StepTimer qualityTimer = trace.step("map_object_quality_check", "质量检查");
            qualityTimer.success(response.getQualityCheck() == null || response.getQualityCheck().getItems() == null ? 0 : response.getQualityCheck().getItems().size());

            boolean llmSuccess = response.getAnswerMeta() != null && Boolean.TRUE.equals(response.getAnswerMeta().get("llmSuccess"));
            boolean fallback = !llmSuccess && (
                    (response.getAnswerMeta() != null && Boolean.TRUE.equals(response.getAnswerMeta().get("fallback")))
                            || templateFallback(response)
            );
            trace.setMode(llmSuccess ? "MAP_OBJECT_LLM" : fallback ? "MAP_OBJECT_FALLBACK" : "MAP_OBJECT_TEMPLATE");
            trace.setStatus("SUCCESS");
            trace.setFallback(fallback);
            trace.finish();
            response.setTrace(trace.toMap());
            saveTrace(trace);
            return response;
        } catch (RuntimeException e) {
            trace.setMode("FAILED");
            trace.setStatus("FAILED");
            trace.setFallback(true);
            trace.setError(e.getMessage());
            trace.finish();
            saveTrace(trace);
            throw e;
        }
    }

    private MapObjectSolutionResponse buildResponse(MapObjectSolutionRequest request, MapObjectContext ctx, AiTraceContext trace) {
        Map detail = ctx.getDetail() == null ? new LinkedHashMap() : ctx.getDetail();
        Map<String, Object> effectiveDetail = mapObjectVariables(request, detail);
        String objectType = normalizeType(firstString(effectiveDetail, "objectType", "object_type", "type", "layerType", "assessment_object_type"));
        if (objectType == null || objectType.length() == 0) {
            objectType = normalizeType(request.getObjectType());
        }
        Map<String, Object> summary = buildObjectSummary(ctx, effectiveDetail, objectType);
        MapObjectSolutionType solutionType = resolveSolutionType(request, objectType, summary);
        Map<String, Object> businessEvidence = request.getBusinessEvidence() == null ? new LinkedHashMap<String, Object>() : request.getBusinessEvidence();
        String businessEvidenceSummary = businessEvidenceText(businessEvidence);
        if (businessEvidenceSummary.length() > 0) {
            summary.put("businessEvidenceSummary", businessEvidenceSummary);
        }
        String title = buildTitle(solutionType, summary);
        String fallbackMarkdown = appendBusinessEvidenceSection(buildMarkdown(solutionType, objectType, summary), businessEvidence);
        TemplatePipelineResult pipelineResult = templatePipelineService.generate(buildTemplateContext(request, ctx, detail, objectType, solutionType, summary, title, fallbackMarkdown, trace));
        LlmDraftResult draftResult = generateWithLlmIfRequested(request, solutionType, objectType, summary, pipelineResult, businessEvidence, trace);
        String markdown = draftResult.markdown;

        MapObjectSolutionResponse response = new MapObjectSolutionResponse();
        response.setSolutionType(solutionType.name());
        response.setTitle(title);
        response.setMarkdown(markdown);
        response.setObjectSummary(summary);
        response.setQualityCheck(qualityChecker.check(solutionType, objectType, summary, markdown));
        response.setTemplateMeta(pipelineResult.getTemplateMeta());
        response.setAnswerMeta(draftResult.answerMeta);
        response.setSourceSummaries(buildSourceSummaries(pipelineResult, businessEvidence));
        return response;
    }

    private MapObjectSolutionType resolveSolutionType(MapObjectSolutionRequest request,
                                                      String objectType,
                                                      Map<String, Object> summary) {
        MapObjectSolutionType requested = MapObjectSolutionType.of(request == null ? null : request.getSolutionType(), objectType);
        if (hasExplicitSolutionType(request) || !isAssessmentType(objectType)) {
            return requested;
        }
        return isLowAssessment(summary) ? MapObjectSolutionType.LOW_SCORE_TREATMENT : MapObjectSolutionType.EVALUATION_UNIT_ADVICE;
    }

    private boolean hasExplicitSolutionType(MapObjectSolutionRequest request) {
        return request != null
                && request.getSolutionType() != null
                && request.getSolutionType().trim().length() > 0;
    }

    private boolean isAssessmentType(String objectType) {
        String type = objectType == null ? "" : objectType.trim().toUpperCase();
        return "ASSESSMENT".equals(type) || "ASSESSMENT_RESULT".equals(type);
    }

    private LlmDraftResult generateWithLlmIfRequested(MapObjectSolutionRequest request,
                                                      MapObjectSolutionType solutionType,
                                                      String objectType,
                                                      Map<String, Object> summary,
                                                      TemplatePipelineResult pipelineResult,
                                                      Map<String, Object> businessEvidence,
                                                      AiTraceContext trace) {
        String draft = appendBusinessEvidenceSection(stringValue(pipelineResult == null ? null : pipelineResult.getMarkdown(), ""), businessEvidence);
        Map<String, Object> templateMeta = pipelineResult == null ? new LinkedHashMap<String, Object>() : pipelineResult.getTemplateMeta();
        if (!readBoolean(request == null ? null : request.getOptions(), "requireAi", false)) {
            return LlmDraftResult.of(draft, templateAnswerMeta(templateMeta));
        }

        AiTraceContext.StepTimer timer = trace.step("map_object_llm_generate", "大模型生成对象方案");
        if (llmClient == null || !llmClient.enabled()) {
            Map<String, Object> data = llmStepData("SKIPPED", "大模型未启用");
            timer.skipped(data);
            return LlmDraftResult.of(draft, fallbackAnswerMeta("大模型未启用", "SKIPPED", templateMeta));
        }

        try {
            String answer = llmClient.chat(
                    "你是智路养护平台 AI 方案生成助手。请基于业务事实生成结构化养护建议，不能编造未给出的检测或工程量数据。",
                    buildLlmPrompt(solutionType, objectType, summary, draft, pipelineResult, businessEvidence)
            );
            if (answer != null && answer.trim().length() > 0) {
                Map<String, Object> data = llmStepData("SUCCESS", "");
                data.put("answerChars", answer.trim().length());
                timer.success(1, data);
                return LlmDraftResult.of(answer.trim(), llmAnswerMeta());
            }
            Map<String, Object> data = llmStepData("EMPTY", "大模型返回为空");
            timer.skipped(data);
            return LlmDraftResult.of(draft, fallbackAnswerMeta("大模型返回为空", "EMPTY", templateMeta));
        } catch (Exception e) {
            Map<String, Object> data = llmStepData("FAILED", e.getMessage());
            timer.failed(e, data);
            return LlmDraftResult.of(draft, fallbackAnswerMeta(e.getMessage() == null ? "大模型调用异常" : e.getMessage(), "FAILED", templateMeta));
        }
    }

    private String buildLlmPrompt(MapObjectSolutionType solutionType,
                                  String objectType,
                                  Map<String, Object> summary,
                                  String draft,
                                  TemplatePipelineResult pipelineResult,
                                  Map<String, Object> businessEvidence) {
        StringBuilder sb = new StringBuilder();
        sb.append("请生成一份面向公路养护业务人员的结构化方案草稿。\n\n");
        sb.append("【对象类型】\n").append(objectType).append("\n\n");
        sb.append("【成果类型】\n").append(solutionType == null ? "" : solutionType.name()).append(" / ")
                .append(solutionType == null ? "" : solutionType.getLabel()).append("\n\n");
        sb.append("【业务对象摘要】\n");
        appendPromptLine(sb, "对象编号", summary.get("objectId"));
        appendPromptLine(sb, "路线", summary.get("routeCode"));
        appendPromptLine(sb, "桩号", summary.get("stakeRange"));
        appendPromptLine(sb, "病害", summary.get("diseaseName"));
        appendPromptLine(sb, "严重程度", summary.get("severity"));
        appendPromptLine(sb, "数量", quantityText(summary));
        appendPromptLine(sb, "MQI", summary.get("mqi"));
        appendPromptLine(sb, "PQI", summary.get("pqi"));
        appendPromptLine(sb, "PCI", summary.get("pci"));
        appendPromptLine(sb, "等级", summary.get("grade"));
        sb.append("\n【参考草稿】\n").append(draft).append("\n\n");
        sb.append("【模板/依据摘要】\n").append(sourceSummaryText(pipelineResult)).append("\n\n");
        String businessEvidenceSummary = businessEvidenceText(businessEvidence);
        if (businessEvidenceSummary.length() > 0) {
            sb.append("【业务查询证据】\n").append(businessEvidenceSummary).append("\n\n");
        }
        sb.append("【输出要求】\n");
        sb.append("1. 保留 Markdown 章节，标题清晰；\n");
        sb.append("2. 必须围绕当前对象，不要扩写成全县或全路线泛泛报告；\n");
        sb.append("3. 涉及病害、指标和桩号时只能使用上面给出的事实；\n");
        sb.append("4. 给出处置优先级、复核重点、建议措施和风险提示；\n");
        sb.append("5. 不要输出寒暄、过程说明或 JSON。\n");
        return sb.toString();
    }

    private void appendPromptLine(StringBuilder sb, String label, Object value) {
        String text = stringValue(value, "");
        if (text.length() > 0) {
            sb.append("- ").append(label).append("：").append(text).append("\n");
        }
    }

    private String sourceSummaryText(TemplatePipelineResult pipelineResult) {
        if (pipelineResult == null || pipelineResult.getSourceSummaries() == null || pipelineResult.getSourceSummaries().isEmpty()) {
            return "无额外模板依据。";
        }
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (Map<String, Object> source : pipelineResult.getSourceSummaries()) {
            if (index > 5) {
                break;
            }
            sb.append(index++).append(". ")
                    .append(stringValue(source.get("sourceTitle"), stringValue(source.get("title"), "依据")))
                    .append("：")
                    .append(shortText(stringValue(source.get("contentExcerpt"), stringValue(source.get("content"), "")), 220))
                    .append("\n");
        }
        return sb.toString();
    }

    private List<Map<String, Object>> buildSourceSummaries(TemplatePipelineResult pipelineResult, Map<String, Object> businessEvidence) {
        List<Map<String, Object>> sources = new ArrayList<>();
        if (pipelineResult != null && pipelineResult.getSourceSummaries() != null) {
            sources.addAll(pipelineResult.getSourceSummaries());
        }
        String summary = businessEvidenceText(businessEvidence);
        if (summary.length() > 0) {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("sourceType", "BUSINESS_DATA");
            source.put("sourceTitle", "业务查询证据");
            source.put("contentExcerpt", shortText(summary, 600));
            source.put("toolSuccessCount", firstObject(businessEvidence, "toolSuccessCount"));
            source.put("businessHitCount", firstObject(businessEvidence, "businessHitCount"));
            source.put("knowledgeHitCount", firstObject(businessEvidence, "knowledgeHitCount"));
            sources.add(source);
        }
        return sources;
    }

    private String appendBusinessEvidenceSection(String markdown, Map<String, Object> businessEvidence) {
        String base = stringValue(markdown, "");
        String summary = businessEvidenceText(businessEvidence);
        if (summary.length() == 0 || base.contains("业务查询证据")) {
            return base;
        }
        return base + "\n\n## 业务查询证据\n\n" + summary + "\n";
    }

    private String businessEvidenceText(Map<String, Object> businessEvidence) {
        if (businessEvidence == null || businessEvidence.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Object successCount = businessEvidence.get("toolSuccessCount");
        Object businessHitCount = businessEvidence.get("businessHitCount");
        Object knowledgeHitCount = businessEvidence.get("knowledgeHitCount");
        if (successCount != null || businessHitCount != null || knowledgeHitCount != null) {
            sb.append("- 取证概况：")
                    .append(successCount == null ? "已执行业务查询" : "成功工具 " + successCount + " 个");
            if (businessHitCount != null) {
                sb.append("，业务命中 ").append(businessHitCount);
            }
            if (knowledgeHitCount != null) {
                sb.append("，知识命中 ").append(knowledgeHitCount);
            }
            sb.append("\n");
        }
        Object rawSummary = businessEvidence.get("toolSummary");
        if (rawSummary instanceof List) {
            int count = 0;
            for (Object item : (List) rawSummary) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map tool = (Map) item;
                String toolName = stringValue(tool.get("toolName"), "");
                if (toolName.length() == 0) {
                    continue;
                }
                String summary = stringValue(tool.get("summary"), "");
                String error = stringValue(firstObject(tool, "error", "errorMessage"), "");
                Object hitCount = firstObject(tool, "hitCount", "count", "totalCount");
                boolean success = !"false".equalsIgnoreCase(stringValue(tool.get("success"), "true"));
                sb.append("- ").append(toolName).append("：");
                if (summary.length() > 0) {
                    sb.append(summary);
                } else {
                    sb.append(success ? "查询成功" : "查询失败");
                }
                if (hitCount != null && summary.indexOf(String.valueOf(hitCount)) < 0) {
                    sb.append("（命中 ").append(hitCount).append("）");
                }
                if (!success && error.length() > 0) {
                    sb.append("，错误：").append(error);
                }
                sb.append("\n");
                count++;
                if (count >= 8) {
                    break;
                }
            }
        }
        return sb.toString().trim();
    }

    private Map<String, Object> llmAnswerMeta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("answerSource", "LLM");
        meta.put("answerSourceLabel", "大模型返回");
        meta.put("llmSuccess", true);
        meta.put("llmStatus", "SUCCESS");
        meta.put("fallback", false);
        return meta;
    }

    private Map<String, Object> templateAnswerMeta(Map<String, Object> templateMeta) {
        boolean fallback = templateMetaFallback(templateMeta);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("answerSource", fallback ? "TEMPLATE_FALLBACK" : "TEMPLATE");
        meta.put("answerSourceLabel", fallback ? "业务统计模板兜底" : "业务模板");
        meta.put("llmSuccess", false);
        meta.put("llmStatus", "SKIPPED");
        meta.put("fallback", fallback);
        if (fallback) {
            meta.put("fallbackReason", stringValue(templateMeta == null ? null : templateMeta.get("fallbackReason"), "未调用大模型，当前为业务统计模板兜底结果"));
        }
        return meta;
    }

    private Map<String, Object> fallbackAnswerMeta(String reason, String llmStatus, Map<String, Object> templateMeta) {
        Map<String, Object> meta = templateAnswerMeta(templateMeta);
        meta.put("answerSource", "TEMPLATE_FALLBACK");
        meta.put("answerSourceLabel", "业务统计模板兜底");
        meta.put("llmStatus", stringValue(llmStatus, "FAILED"));
        meta.put("fallback", true);
        meta.put("fallbackReason", stringValue(reason, "大模型未返回有效内容，当前为业务统计模板兜底结果。"));
        return meta;
    }

    private Map<String, Object> llmStepData(String status, String reason) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("llmStatus", status);
        if (reason != null && reason.trim().length() > 0) {
            data.put("reason", reason);
        }
        return data;
    }

    private boolean templateMetaFallback(Map<String, Object> templateMeta) {
        Object fallback = templateMeta == null ? null : templateMeta.get("fallback");
        if (fallback instanceof Boolean) {
            return (Boolean) fallback;
        }
        return fallback != null && Boolean.parseBoolean(String.valueOf(fallback));
    }

    private boolean readBoolean(Map<String, Object> options, String key, boolean defaultValue) {
        if (options == null || !options.containsKey(key)) {
            return defaultValue;
        }
        Object value = options.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value == null ? defaultValue : Boolean.parseBoolean(String.valueOf(value));
    }

    private String shortText(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private static class LlmDraftResult {
        private final String markdown;
        private final Map<String, Object> answerMeta;

        private LlmDraftResult(String markdown, Map<String, Object> answerMeta) {
            this.markdown = markdown;
            this.answerMeta = answerMeta;
        }

        private static LlmDraftResult of(String markdown, Map<String, Object> answerMeta) {
            return new LlmDraftResult(markdown, answerMeta);
        }
    }

    private boolean templateFallback(MapObjectSolutionResponse response) {
        Map<String, Object> templateMeta = response == null ? null : response.getTemplateMeta();
        Object fallback = templateMeta == null ? null : templateMeta.get("fallback");
        if (fallback instanceof Boolean) {
            return (Boolean) fallback;
        }
        return fallback == null || Boolean.parseBoolean(String.valueOf(fallback));
    }

    private SolutionTemplateContext buildTemplateContext(MapObjectSolutionRequest request,
                                                         MapObjectContext ctx,
                                                         Map detail,
                                                         String objectType,
                                                         MapObjectSolutionType solutionType,
                                                         Map<String, Object> summary,
                                                         String title,
                                                         String fallbackMarkdown,
                                                         AiTraceContext trace) {
        SolutionTemplateContext context = new SolutionTemplateContext();
        context.setTenantId(request.getTenantId());
        context.setOriginType("MAP_OBJECT");
        context.setObjectType(objectType);
        context.setSolutionType(solutionType.name());
        context.setRouteCode(firstNonEmpty(request.getRouteCode(), stringValue(summary.get("routeCode"), "")));
        context.setYear(request.getYear() == null ? intObject(summary.get("year")) : request.getYear());
        context.setTitle(title);
        context.setMapObject(mapObjectVariables(request, detail));
        context.setObjectSummary(summary);
        context.setFallbackMarkdown(fallbackMarkdown);
        context.setTrace(trace);
        if (request.getOptions() != null) {
            context.setOptions(request.getOptions());
        }
        Map<String, Object> businessData = new LinkedHashMap<>();
        businessData.put("fallbackMarkdown", fallbackMarkdown);
        businessData.put("contextPresent", ctx != null && ctx.isPresent());
        businessData.put("objectSummary", summary);
        businessData.put("businessEvidence", request.getBusinessEvidence() == null ? new LinkedHashMap<String, Object>() : request.getBusinessEvidence());
        businessData.put("businessEvidenceSummary", businessEvidenceText(request.getBusinessEvidence()));
        context.setBusinessData(businessData);
        return context;
    }

    private Map<String, Object> mapObjectVariables(MapObjectSolutionRequest request, Map detail) {
        Map<String, Object> mapObject = new LinkedHashMap<>();
        if (detail != null) {
            mapObject.putAll(detail);
        }
        if (request.getMapObject() != null) {
            mapObject.putAll(request.getMapObject());
        }
        putIfPresent(mapObject, "objectType", request.getObjectType());
        putIfPresent(mapObject, "objectId", request.getObjectId());
        putIfMissing(mapObject, "routeCode", request.getRouteCode(), "route_code", "route");
        if (request.getYear() != null) {
            mapObject.put("year", request.getYear());
        }
        return mapObject;
    }

    private void saveTrace(AiTraceContext trace) {
        try {
            aiTraceService.save(trace);
        } catch (Exception e) {
            log.warn("[MAP-OBJECT] save trace failed traceId={} error={}", trace.getTraceId(), e.getMessage(), e);
        }
    }

    private MapObjectContext resolveContext(MapObjectSolutionRequest request) {
        Map<String, Object> mapObject = new LinkedHashMap<>();
        if (request.getMapObject() != null) {
            mapObject.putAll(request.getMapObject());
        }
        putIfPresent(mapObject, "objectType", request.getObjectType());
        putIfPresent(mapObject, "objectId", request.getObjectId());
        putIfPresent(mapObject, "id", request.getObjectId());
        putIfMissing(mapObject, "routeCode", request.getRouteCode(), "route_code", "route");
        if (request.getYear() != null) {
            mapObject.put("year", request.getYear());
        }

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("mapObject", mapObject);
        MapObjectContext resolved;
        try {
            resolved = mapObjectContextService.resolve(context);
        } catch (RuntimeException e) {
            if (!mapObject.isEmpty()) {
                log.warn("[MAP-OBJECT] context lookup failed, fallback to request mapObject objectType={} objectId={} routeCode={} error={}",
                        request.getObjectType(), request.getObjectId(), request.getRouteCode(), e.getMessage());
                return MapObjectContext.of(mapObject);
            }
            throw e;
        }
        if ((resolved == null || !resolved.isPresent()) && !mapObject.isEmpty()) {
            return MapObjectContext.of(mapObject);
        }
        return resolved;
    }

    private Map<String, Object> buildObjectSummary(MapObjectContext ctx, Map detail, String objectType) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("objectType", objectType);
        summary.put("objectId", firstString(detail, "objectId", "object_id", "id"));
        summary.put("routeCode", firstString(detail, "routeCode", "route_code", "route"));
        summary.put("year", firstObject(detail, "year"));
        summary.put("stakeRange", formatStake(firstObject(detail, "startStake", "start_stake"), firstObject(detail, "endStake", "end_stake")));
        summary.put("routeName", firstString(detail, "routeName", "route_name"));
        summary.put("sectionName", firstString(detail, "sectionName", "section_name"));
        summary.put("sectionCode", firstString(detail, "sectionCode", "section_code"));
        summary.put("lengthKm", summaryLengthKm(detail, objectType));
        summary.put("unitCode", firstString(detail, "unitCode", "unit_code"));
        summary.put("diseaseName", firstString(detail, "diseaseName", "disease_name", "diseaseType", "disease_type"));
        summary.put("severity", firstObject(detail, "severity"));
        summary.put("quantity", firstObject(detail, "quantity"));
        summary.put("measureUnit", firstString(detail, "measureUnit", "measure_unit"));
        summary.put("mqi", firstObject(detail, "mqi"));
        summary.put("pqi", firstObject(detail, "pqi"));
        summary.put("pci", firstObject(detail, "pci"));
        summary.put("grade", firstObject(detail, "grade"));
        summary.put("activeMetricCode", firstObject(detail, "activeMetricCode", "active_metric_code", "activeIndexCode", "active_index_code", "indexCode", "index_code"));
        summary.put("activeMetricValue", firstObject(detail, "activeMetricValue", "active_metric_value", "activeIndexValue", "active_index_value", "metricValue", "metric_value"));
        summary.put("activeMetricGrade", firstObject(detail, "activeMetricGrade", "active_metric_grade", "activeIndexGrade", "active_index_grade", "metricGrade", "metric_grade"));
        summary.put("contextSummary", buildContextSummary(detail, objectType, summary));
        summary.put("raw", detail);
        if (summary.get("year") == null && ctx != null) {
            summary.put("year", ctx.getYear());
        }
        return summary;
    }

    private String buildContextSummary(Map detail, String objectType, Map<String, Object> summary) {
        String explicit = firstString(detail, "contextSummary");
        if (explicit != null && explicit.trim().length() > 0) {
            return explicit;
        }
        String type = normalizeType(objectType);
        if ("ROAD_ROUTE".equals(type) || "ROUTE".equals(type)) {
            String route = stringValue(summary.get("routeCode"), "");
            String routeName = stringValue(summary.get("routeName"), "");
            String length = routeLengthText(detail);
            if (route.length() > 0 || routeName.length() > 0 || length.length() > 0) {
                StringBuilder sb = new StringBuilder("路线");
                if (route.length() > 0) {
                    sb.append(" ").append(route);
                }
                if (routeName.length() > 0) {
                    sb.append(" ").append(routeName);
                }
                if (length.length() > 0) {
                    sb.append("，里程 ").append(length).append(" km");
                }
                return sb.toString();
            }
        }
        return stringValue(firstString(detail, "context_summary"), "");
    }

    private String routeLengthText(Map detail) {
        Object explicit = firstObject(detail, "lengthKm");
        if (explicit != null) {
            return stringValue(explicit, "");
        }
        String derivedFromRequestStyleStakes = stakeLengthText(firstObject(detail, "startStake"), firstObject(detail, "endStake"));
        if (derivedFromRequestStyleStakes.length() > 0) {
            return derivedFromRequestStyleStakes;
        }
        String derivedFromDbStyleStakes = stakeLengthText(firstObject(detail, "start_stake"), firstObject(detail, "end_stake"));
        if (derivedFromDbStyleStakes.length() > 0) {
            return derivedFromDbStyleStakes;
        }
        return stringValue(firstObject(detail, "length_km", "length"), "");
    }

    private Object summaryLengthKm(Map detail, String objectType) {
        String type = normalizeType(objectType);
        if ("ROAD_ROUTE".equals(type) || "ROUTE".equals(type)) {
            String length = routeLengthText(detail);
            return length.length() == 0 ? null : length;
        }
        return firstObject(detail, "lengthKm", "length_km");
    }

    private String stakeLengthText(Object start, Object end) {
        Double startValue = firstNumber(start);
        Double endValue = firstNumber(end);
        if (startValue == null || endValue == null || endValue < startValue) {
            return "";
        }
        return decimalText(endValue - startValue);
    }

    private boolean isLowAssessment(Map<String, Object> summary) {
        String grade = normalizeGrade(stringValue(firstPresent(
                summary == null ? null : summary.get("activeMetricGrade"),
                summary == null ? null : summary.get("grade")
        ), ""));
        if ("POOR".equals(grade) || "BAD".equals(grade)) {
            return true;
        }
        if ("EXCELLENT".equals(grade) || "GOOD".equals(grade) || "MEDIUM".equals(grade)) {
            return false;
        }
        Double score = firstNumber(
                summary == null ? null : summary.get("activeMetricValue"),
                summary == null ? null : summary.get("mqi"),
                summary == null ? null : summary.get("pqi"),
                summary == null ? null : summary.get("pci")
        );
        return score != null && score < 70;
    }

    private String normalizeGrade(String value) {
        String raw = value == null ? "" : value.trim();
        String text = raw.toUpperCase();
        if ("EXCELLENT".equals(text) || "优".equals(raw) || "优秀".equals(raw)) {
            return "EXCELLENT";
        }
        if ("GOOD".equals(text) || "良".equals(raw) || "良好".equals(raw)) {
            return "GOOD";
        }
        if ("MEDIUM".equals(text) || "中".equals(raw) || "中等".equals(raw)) {
            return "MEDIUM";
        }
        if ("POOR".equals(text) || "次".equals(raw) || "较差".equals(raw)) {
            return "POOR";
        }
        if ("BAD".equals(text) || "差".equals(raw) || "很差".equals(raw)) {
            return "BAD";
        }
        return text;
    }

    private String buildTitle(MapObjectSolutionType solutionType, Map<String, Object> summary) {
        String route = stringValue(summary.get("routeCode"), solutionType == MapObjectSolutionType.ROUTE_REPORT ? "当前路线范围" : "当前对象");
        String stake = stringValue(summary.get("stakeRange"), "");
        String disease = stringValue(summary.get("diseaseName"), "");
        StringBuilder title = new StringBuilder();
        title.append(route);
        if (stake.length() > 0) {
            title.append(" ").append(stake);
        }
        if (disease.length() > 0) {
            title.append(" ").append(disease);
        }
        title.append(solutionType.getLabel());
        return title.toString();
    }

    private String buildMarkdown(MapObjectSolutionType solutionType, String objectType, Map<String, Object> summary) {
        if (solutionType == MapObjectSolutionType.DISEASE_REVIEW) {
            return diseaseReview(summary);
        }
        if (solutionType == MapObjectSolutionType.DISEASE_TREATMENT) {
            return diseaseTreatment(summary);
        }
        if (solutionType == MapObjectSolutionType.LOW_SCORE_TREATMENT) {
            return lowScoreTreatment(summary);
        }
        if (solutionType == MapObjectSolutionType.EVALUATION_UNIT_ADVICE) {
            return evaluationUnitAdvice(summary);
        }
        if (solutionType == MapObjectSolutionType.SECTION_PLAN) {
            return sectionPlan(summary);
        }
        if (solutionType == MapObjectSolutionType.ROUTE_REPORT) {
            return routeReport(summary);
        }
        return generalAdvice(objectType, summary);
    }

    private String diseaseReview(Map<String, Object> s) {
        StringBuilder sb = header("病害复核意见", s);
        sb.append("## 1. 当前病害对象\n\n");
        appendObjectLines(sb, s);
        sb.append("\n## 2. 问题判断\n\n");
        sb.append("当前对象为").append(stringValue(s.get("diseaseName"), "道路病害"))
                .append("，严重程度为").append(stringValue(s.get("severity"), "未标明"))
                .append("。建议重点判断病害边界、深度、是否存在二次损坏，以及对行车安全和路面使用性能的影响。\n\n");
        sb.append("## 3. 现场复核重点\n\n");
        sb.append("1. 复核病害边界和实际工程量；\n");
        sb.append("2. 检查面层与基层结合状态；\n");
        sb.append("3. 复核排水、荷载和既有修补质量；\n");
        sb.append("4. 检查周边是否存在连续同类病害。\n\n");
        appendCauseTreatmentPriorityRisk(sb, "复核后按实际损坏深度选择局部修补、铣刨重铺或基层处理。");
        return sb.toString();
    }

    private String diseaseTreatment(Map<String, Object> s) {
        StringBuilder sb = header("病害处置建议", s);
        sb.append("## 1. 对象概况\n\n");
        appendObjectLines(sb, s);
        sb.append("\n## 2. 成因判断\n\n");
        sb.append("建议结合现场排水、交通荷载、既有修补质量、基层状态和材料老化情况判断主要成因，避免只处理表面损坏。\n\n");
        sb.append("## 3. 病害影响分析\n\n");
        sb.append("该病害可能影响局部平整度、结构耐久性和行车舒适性。若病害继续扩展，后续处置范围和成本可能增加。\n\n");
        sb.append("## 4. 推荐处置工艺\n\n");
        sb.append("建议先现场复核损坏深度和基层状态。表层病害可采用局部铣刨、清理、重新摊铺或热补；若基层松散或含水，应先处理基层和排水后恢复面层。\n\n");
        sb.append("## 5. 工程量估算\n\n");
        sb.append("当前记录工程量为 ").append(stringValue(s.get("quantity"), "待复核"))
                .append(stringValue(s.get("measureUnit"), ""))
                .append("，最终工程量以现场复核和设计计量为准。\n\n");
        sb.append("## 6. 实施优先级\n\n");
        sb.append(priorityText(s)).append("\n\n");
        sb.append("## 7. 后续跟踪建议\n\n");
        sb.append("处置后建议在下一轮巡检中复核修补边界、平整度和周边扩展情况，必要时纳入小段集中养护。\n\n");
        sb.append("## 8. 风险提示\n\n");
        sb.append("本建议为 AI 生成草稿，需由养护技术人员结合现场复核、预算和交通组织条件审核确认。\n");
        return sb.toString();
    }

    private String lowScoreTreatment(Map<String, Object> s) {
        StringBuilder sb = header("低分单元处置建议", s);
        sb.append("## 1. 评定对象概况\n\n");
        appendObjectLines(sb, s);
        sb.append("\n## 2. 低分原因判断\n\n");
        sb.append("当前对象指标为 MQI=").append(stringValue(s.get("mqi"), "-"))
                .append("，PQI=").append(stringValue(s.get("pqi"), "-"))
                .append("，PCI=").append(stringValue(s.get("pci"), "-"))
                .append("，等级=").append(stringValue(s.get("grade"), "-"))
                .append("。应优先核查 PCI/PQI 偏低对应的裂缝、坑槽、沉陷、车辙等病害贡献。\n\n");
        sb.append("## 3. 周边病害分析\n\n");
        sb.append("建议结合相邻桩号病害记录，判断是否存在连续分布、集中损坏或排水不良诱发的成片劣化。\n\n");
        sb.append("## 4. 处置策略\n\n");
        sb.append("可按病害类型选择局部修补、裂缝处治、罩面、铣刨重铺或排水修复，并与相邻低分单元合并评估。\n\n");
        sb.append("## 5. 优先级\n\n");
        sb.append(priorityText(s)).append("\n\n");
        sb.append("## 6. 风险提示\n\n");
        sb.append("若低分单元继续劣化，可能扩大处置范围并影响路线整体技术状况评价。本草稿需人工审核。\n");
        return sb.toString();
    }

    private String evaluationUnitAdvice(Map<String, Object> s) {
        StringBuilder sb = header("评定单元养护建议", s);
        sb.append("## 1. 评定单元概况\n\n");
        appendObjectLines(sb, s);
        sb.append("\n## 2. 技术状况判断\n\n");
        sb.append("建议结合该单元历年评定结果、当前病害和交通荷载，判断主要性能短板。\n\n");
        sb.append("## 3. 成因判断\n\n");
        sb.append("重点排查材料老化、结构承载不足、排水不畅、施工接缝或重载交通导致的局部劣化。\n\n");
        sb.append("## 4. 养护建议\n\n");
        sb.append("根据病害类型和指标短板选择预防性养护、局部修补或结构性修复，并与相邻单元统筹安排。\n\n");
        sb.append("## 5. 优先级\n\n");
        sb.append(priorityText(s)).append("\n\n");
        sb.append("## 6. 风险提示\n\n");
        sb.append("本建议为草稿，需结合现场复核、检测资料和年度养护计划确认。\n");
        return sb.toString();
    }

    private String sectionPlan(Map<String, Object> s) {
        StringBuilder sb = header("路段养护计划草稿", s);
        sb.append("## 1. 路段概况\n\n");
        appendObjectLines(sb, s);
        sb.append("\n## 2. 技术状况\n\n建议汇总该路段评定指标、病害记录和交通条件，识别主要短板。\n\n");
        sb.append("## 3. 主要问题与成因判断\n\n重点关注低 PCI/PQI 单元、重度病害和连续损坏区段，并从交通荷载、排水条件、材料老化和结构承载等方面分析成因。\n\n");
        sb.append("## 4. 病害分布\n\n建议按桩号统计病害集中区，形成分段处置清单。\n\n");
        sb.append("## 5. 养护目标\n\n控制病害扩展，恢复路面功能，提升路段技术状况和通行舒适性。\n\n");
        sb.append("## 6. 建议工程措施\n\n按局部修补、预防性养护、罩面或结构性修复分级安排。\n\n");
        sb.append("## 7. 优先级与实施计划\n\n优先处置安全风险高和病害集中区段，其他区段纳入中期计划。\n\n");
        sb.append("## 8. 资源需求\n\n需结合现场复核工程量、交通组织和年度预算进一步细化。\n\n");
        sb.append("## 9. 风险与保障措施\n\n需做好施工质量控制、交通安全组织和处置后跟踪评价。本草稿需人工审核。\n");
        return sb.toString();
    }

    private String routeReport(Map<String, Object> s) {
        StringBuilder sb = header("路线技术状况分析报告草稿", s);
        sb.append("## 1. 路线概况\n\n");
        appendObjectLines(sb, s);
        sb.append("\n## 2. 技术状况总体评价\n\n建议结合年度评定结果判断路线总体技术状况和主要短板。\n\n");
        sb.append("## 3. 指标短板与成因判断\n\n重点分析 MQI、PQI、PCI 等指标的低值区段和变化趋势，并结合交通荷载、排水、结构和病害记录判断成因。\n\n");
        sb.append("## 4. 病害分布分析\n\n按病害类型、严重程度和桩号区间识别集中分布区域。\n\n");
        sb.append("## 5. 低分区段分析\n\n筛选次差等级或关键指标低于阈值的区段，作为优先复核对象。\n\n");
        sb.append("## 6. 养护建议\n\n按轻重缓急安排预防性养护、功能性修复和结构性修复。\n\n");
        sb.append("## 7. 优先级与后续工作建议\n\n优先复核低分区段和病害集中区，完善现场复核、工程量核定、预算测算和实施计划。本报告为 AI 草稿，需人工审核。\n\n");
        sb.append("## 8. 风险提示\n\n若低分区段持续劣化，可能影响路线整体技术状况评价和养护资金使用效率。\n");
        return sb.toString();
    }

    private String generalAdvice(String objectType, Map<String, Object> s) {
        StringBuilder sb = header("通用养护建议", s);
        sb.append("## 1. 对象概况\n\n");
        appendObjectLines(sb, s);
        sb.append("\n## 2. 成因判断\n\n建议结合对象类型 ").append(objectType == null ? "未知" : objectType)
                .append("、现场病害和历史评定资料分析原因。\n\n");
        sb.append("## 3. 处置建议\n\n先开展现场复核，再根据损坏程度选择局部修补、预防性养护或专项处治。\n\n");
        sb.append("## 4. 优先级\n\n建议列为 P2 近期复核对象，若存在安全风险则提升至 P1。\n\n");
        sb.append("## 5. 风险提示\n\n本建议为 AI 草稿，需人工审核确认。\n");
        return sb.toString();
    }

    private StringBuilder header(String title, Map<String, Object> s) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");
        sb.append("> 本文为智路养护平台基于当前地图对象生成的 AI 草稿，正式方案需经人工审核确认。\n\n");
        String summary = stringValue(s.get("contextSummary"), "");
        if (summary.length() > 0) {
            sb.append("对象摘要：").append(summary).append("\n\n");
        }
        return sb;
    }

    private void appendObjectLines(StringBuilder sb, Map<String, Object> s) {
        appendLine(sb, "对象类型", s.get("objectType"));
        appendLine(sb, "路线", s.get("routeCode"));
        appendLine(sb, "桩号", s.get("stakeRange"));
        appendLine(sb, "路段", firstPresent(s.get("sectionName"), s.get("sectionCode")));
        appendLine(sb, "评定单元", s.get("unitCode"));
        appendLine(sb, "病害类型", s.get("diseaseName"));
        appendLine(sb, "严重程度", s.get("severity"));
        appendLine(sb, "数量", quantityText(s));
        appendLine(sb, "MQI", s.get("mqi"));
        appendLine(sb, "PQI", s.get("pqi"));
        appendLine(sb, "PCI", s.get("pci"));
        appendLine(sb, "等级", s.get("grade"));
    }

    private void appendCauseTreatmentPriorityRisk(StringBuilder sb, String treatment) {
        sb.append("## 4. 成因分析\n\n");
        sb.append("可从材料老化、结构薄弱、施工质量、交通荷载和排水条件等方面开展复核。若为既有修补区域，还应检查修补层与原路面结合质量。\n\n");
        sb.append("## 5. 处置建议\n\n");
        sb.append(treatment).append("\n\n");
        sb.append("## 6. 优先级判断\n\n");
        sb.append("建议列为 P2 近期处置对象；如现场复核发现快速扩展、深层结构损坏或明显安全风险，应提升至 P1。\n\n");
        sb.append("## 7. 风险提示\n\n");
        sb.append("若不及时处置，可能导致损坏范围扩大、路面服务性能下降和后续修复成本增加。本草稿需人工审核。\n");
    }

    private String priorityText(Map<String, Object> s) {
        String severity = stringValue(s.get("severity"), "").toUpperCase();
        String grade = stringValue(s.get("grade"), "").toUpperCase();
        if ("HEAVY".equals(severity) || "BAD".equals(grade)) {
            return "建议优先级为 P1，应尽快现场复核并安排处置。";
        }
        if ("MEDIUM".equals(severity) || "POOR".equals(grade)) {
            return "建议优先级为 P2，纳入近期养护计划。";
        }
        return "建议优先级为 P3，可结合巡检复核和年度计划统筹安排。";
    }

    private String quantityText(Map<String, Object> s) {
        Object quantity = s.get("quantity");
        Object unit = s.get("measureUnit");
        if (quantity == null && unit == null) {
            return "";
        }
        return stringValue(quantity, "") + stringValue(unit, "");
    }

    private void appendLine(StringBuilder sb, String label, Object value) {
        String text = stringValue(value, "");
        if (text.length() > 0) {
            sb.append("- ").append(label).append("：").append(text).append("\n");
        }
    }

    private Object firstPresent(Object... values) {
        for (Object value : values) {
            if (value != null && String.valueOf(value).trim().length() > 0) {
                return value;
            }
        }
        return null;
    }

    private String formatStake(Object start, Object end) {
        String s = stringValue(start, "");
        String e = stringValue(end, "");
        if (s.length() == 0) {
            return "";
        }
        return e.length() == 0 ? "K" + s : "K" + s + "-K" + e;
    }

    private void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null && String.valueOf(value).trim().length() > 0) {
            map.put(key, value);
        }
    }

    private void putIfMissing(Map<String, Object> map, String key, Object value, String... existingKeys) {
        if (map == null || value == null || String.valueOf(value).trim().length() == 0) {
            return;
        }
        if (firstObject(map, key) != null || firstObject(map, existingKeys) != null) {
            return;
        }
        map.put(key, value);
    }

    private String normalizeType(String value) {
        if (value == null) {
            return "";
        }
        String type = value.trim().toUpperCase().replace("-", "_");
        if ("ASSESSMENT".equals(type)) {
            return "ASSESSMENT_RESULT";
        }
        if ("DISEASE_RECORD".equals(type)) {
            return "DISEASE";
        }
        return type;
    }

    private String firstString(Map map, String... keys) {
        Object value = firstObject(map, keys);
        return value == null ? null : String.valueOf(value);
    }

    private Object firstObject(Map map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && String.valueOf(value).trim().length() > 0) {
                return value;
            }
        }
        return null;
    }

    private String stringValue(Object value, String fallback) {
        if (value == null || String.valueOf(value).trim().length() == 0) {
            return fallback;
        }
        return String.valueOf(value).trim();
    }

    private String firstNonEmpty(String first, String second) {
        return stringValue(first, "").length() > 0 ? stringValue(first, "") : stringValue(second, "");
    }

    private Integer intObject(Object value) {
        if (value == null || String.valueOf(value).trim().length() == 0) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Double firstNumber(Object... values) {
        for (Object value : values) {
            if (value == null || String.valueOf(value).trim().length() == 0) {
                continue;
            }
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            try {
                return Double.valueOf(String.valueOf(value).trim());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String decimalText(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
