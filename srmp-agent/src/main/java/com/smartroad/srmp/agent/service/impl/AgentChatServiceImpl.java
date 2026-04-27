package com.smartroad.srmp.agent.service.impl;

import com.smartroad.srmp.agent.dto.AgentAnalysisRequest;
import com.smartroad.srmp.agent.dto.AgentChatRequest;
import com.smartroad.srmp.agent.llm.LlmClient;
import com.smartroad.srmp.agent.map.MapObjectContextService;
import com.smartroad.srmp.agent.rag.RagAnswerService;
import com.smartroad.srmp.agent.rag.RagOptions;
import com.smartroad.srmp.agent.service.AgentAnalysisService;
import com.smartroad.srmp.agent.trace.AiTraceContext;
import com.smartroad.srmp.agent.trace.service.AiTraceService;
import com.smartroad.srmp.agent.vo.AgentAnalysisResponse;
import com.smartroad.srmp.agent.vo.AgentChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class AgentChatServiceImpl implements com.smartroad.srmp.agent.service.AgentChatService {
    @Resource private AgentAnalysisService agentAnalysisService;
    @Resource private LlmClient llmClient;
    @Resource private RagAnswerService ragAnswerService;
    @Resource private AiTraceService aiTraceService;
    @Resource private MapObjectContextService mapObjectContextService;

    @Override
    public AgentChatResponse chat(AgentChatRequest request) {
        String message = request == null ? "" : request.getMessage();
        AiTraceContext trace = AiTraceContext.start("AGENT_CHAT", message);
        AgentChatResponse response = new AgentChatResponse();
        String answerSource = null;
        Boolean llmSuccess = false;
        Boolean fallback = true;
        String fallbackReason = null;
        String mode = "HYBRID_RAG_FALLBACK";
        Map rawMapObject = resolveRawMapObject(request);
        Map enrichedMapObject = new LinkedHashMap();
        String mapObjectContext = null;
        boolean mapObjectUsed = false;
        try {
            RagOptions options = RagOptions.autoByQuestion(message, request == null ? null : request.getOptions());
            AgentAnalysisResponse analysis = null;
            String businessMarkdown = "";
            AiTraceContext.StepTimer mapTimer = trace.step("map_object_context", "地图对象上下文");
            try {
                enrichedMapObject = enrichMapObject(rawMapObject, request);
                mapObjectUsed = enrichedMapObject != null && !enrichedMapObject.isEmpty();
                mapObjectContext = buildMapObjectContext(enrichedMapObject);
                if (mapObjectUsed) mapTimer.success(1); else mapTimer.skipped();
            } catch (Exception e) {
                mapTimer.failed(e);
                log.warn("[AI-CHAT] map object context failed traceId={} error={}", trace.getTraceId(), e.getMessage());
            }
            AiTraceContext.StepTimer businessTimer = trace.step("business_analysis", "业务数据分析");
            try {
                if (options.isUseBusinessData() && (looksLikeBusinessQuestion(message) || mapObjectUsed)) {
                    AgentAnalysisRequest analysisRequest = toAnalysisRequest(request, enrichedMapObject);
                    String objType = firstString(enrichedMapObject, "object_type", "objectType");
                    if (containsAny(message, "病害", "裂缝", "坑槽", "车辙", "沉陷") || "DISEASE".equals(objType)) {
                        analysis = agentAnalysisService.analyzeDisease(analysisRequest);
                    } else if (containsAny(message, "评定", "MQI", "PQI", "PCI", "优良", "次差") || "ASSESSMENT_RESULT".equals(objType) || "EVALUATION_UNIT".equals(objType)) {
                        analysis = agentAnalysisService.analyzeAssessment(analysisRequest);
                    } else {
                        analysis = agentAnalysisService.analyzeRoute(analysisRequest);
                    }
                    businessMarkdown = analysis == null ? "" : analysis.getMarkdown();
                    businessTimer.success();
                } else {
                    businessTimer.skipped();
                }
            } catch (Exception e) {
                businessTimer.failed(e);
                log.warn("[AI-CHAT] business analysis failed traceId={} error={}", trace.getTraceId(), e.getMessage());
            }
            if (mapObjectContext != null && !mapObjectContext.trim().isEmpty()) businessMarkdown = mapObjectContext + "\n\n" + businessMarkdown;
            AiTraceContext.StepTimer ragTimer = trace.step("rag_answer", "RAG 问答生成");
            Map rag;
            try {
                rag = ragAnswerService.answer(message, businessMarkdown, options);
                ragTimer.success(hitCount(rag));
            } catch (Exception e) {
                ragTimer.failed(e);
                rag = new LinkedHashMap();
                rag.put("answer", businessMarkdown == null || businessMarkdown.trim().isEmpty() ? "AI 问答处理失败：" + e.getMessage() : businessMarkdown);
                rag.put("answerSource", "BUSINESS_ANALYSIS_FALLBACK");
                rag.put("llmSuccess", false);
                rag.put("fallback", true);
                rag.put("fallbackReason", e.getMessage());
            }
            String answer = asString(rag.get("answer"));
            answerSource = asString(rag.get("answerSource"));
            llmSuccess = asBoolean(rag.get("llmSuccess"));
            fallback = asBoolean(rag.get("fallback"));
            fallbackReason = asString(rag.get("fallbackReason"));
            mode = Boolean.TRUE.equals(llmSuccess) ? "HYBRID_RAG_LLM" : "HYBRID_RAG_FALLBACK";
            if (mapObjectUsed && answer != null && !answer.startsWith("【基于当前地图对象】")) answer = "【基于当前地图对象】\n" + answer;
            response.setAnswer(answer);
            response.setMode(mode);
            response.setMapObjectUsed(mapObjectUsed);
            response.setMapObject(enrichedMapObject);
            response.setMapObjectContext(mapObjectContext);
            Map data = new LinkedHashMap();
            data.put("analysis", analysis == null ? null : analysis.getData());
            data.put("context", request == null ? null : request.getContext());
            data.put("knowledgeSources", rag.get("knowledgeSources"));
            data.put("outlineSources", rag.get("outlineSources"));
            data.put("usedKnowledge", rag.get("usedKnowledge"));
            data.put("usedOutline", rag.get("usedOutline"));
            data.put("mapObjectUsed", mapObjectUsed);
            data.put("mapObject", enrichedMapObject);
            data.put("mapObjectContext", mapObjectContext);
            fillAnswerMeta(data, answerSource, llmSuccess, fallback, fallbackReason, mode);
            response.setData(data);
            trace.setMode(mode);
            trace.setStatus("SUCCESS");
            trace.setFallback(Boolean.TRUE.equals(fallback));
            trace.setError(fallbackReason);
            return response;
        } catch (Exception e) {
            trace.setMode(mode); trace.setStatus("FAILED"); trace.setFallback(true); trace.setError(e.getMessage());
            response.setAnswer("AI 问答处理失败：" + e.getMessage());
            response.setMode("FAILED");
            Map data = new LinkedHashMap();
            data.put("context", request == null ? null : request.getContext());
            data.put("mapObjectUsed", mapObjectUsed);
            data.put("mapObject", enrichedMapObject);
            data.put("mapObjectContext", mapObjectContext);
            fillAnswerMeta(data, "FAILED", false, true, e.getMessage(), "FAILED");
            response.setData(data);
            return response;
        } finally {
            trace.finish();
            if (response.getData() == null) response.setData(new LinkedHashMap());
            response.getData().put("trace", trace.toMap());
            try { aiTraceService.save(trace); } catch (Exception saveError) { log.warn("[AI-CHAT] save trace failed traceId={} error={}", trace.getTraceId(), saveError.getMessage(), saveError); }
        }
    }
    private Map resolveRawMapObject(AgentChatRequest request) { if (request == null) return null; Map mapObject = request.getMapObject(); if (mapObject != null && !mapObject.isEmpty()) return mapObject; Map context = request.getContext(); if (context == null || context.isEmpty()) return null; Object direct = context.get("mapObject"); if (direct instanceof Map) return (Map) direct; Object selectedMapObject = context.get("selectedMapObject"); if (selectedMapObject instanceof Map) return (Map) selectedMapObject; Object selected = context.get("selected"); if (selected instanceof Map) return (Map) selected; return null; }
    private Map enrichMapObject(Map rawMapObject, AgentChatRequest request) { if (rawMapObject == null || rawMapObject.isEmpty()) return new LinkedHashMap(); String objectType = firstString(rawMapObject, "objectType", "object_type", "type", "layerType"); String objectId = firstString(rawMapObject, "objectId", "object_id", "id"); String routeCode = firstString(rawMapObject, "routeCode", "route_code"); Integer year = firstInteger(rawMapObject, "year"); if (year == null && request != null && request.getContext() != null) year = asInteger(request.getContext().get("year")); Map detail = mapObjectContextService.getObjectDetail(objectType, objectId, routeCode, year); Map merged = new LinkedHashMap(); merged.putAll(rawMapObject); if (detail != null) merged.putAll(detail); if (objectType != null) merged.put("objectType", objectType); if (objectId != null) merged.put("objectId", objectId); if (routeCode != null) merged.put("routeCode", routeCode); if (year != null) merged.put("year", year); return merged; }
    private AgentAnalysisRequest toAnalysisRequest(AgentChatRequest request, Map mapObject) { AgentAnalysisRequest r = new AgentAnalysisRequest(); Map ctx = request == null ? null : request.getContext(); if (ctx != null) { r.setRouteCode(asString(ctx.get("routeCode"))); r.setIndexCode(asString(ctx.get("indexCode"))); r.setGrade(asString(ctx.get("grade"))); r.setDiseaseType(asString(ctx.get("diseaseType"))); r.setSeverity(asString(ctx.get("severity"))); r.setTaskId(asString(ctx.get("taskId"))); r.setYear(asInteger(ctx.get("year"))); } if (mapObject != null && !mapObject.isEmpty()) { String routeCode = firstString(mapObject, "routeCode", "route_code"); String grade = firstString(mapObject, "grade"); String diseaseType = firstString(mapObject, "diseaseType", "disease_type"); String severity = firstString(mapObject, "severity"); Integer year = firstInteger(mapObject, "year"); if (routeCode != null) r.setRouteCode(routeCode); if (grade != null) r.setGrade(grade); if (diseaseType != null) r.setDiseaseType(diseaseType); if (severity != null) r.setSeverity(severity); if (year != null) r.setYear(year); } return r; }
    private String buildMapObjectContext(Map mapObject) { if (mapObject == null || mapObject.isEmpty()) return null; StringBuilder sb = new StringBuilder(); sb.append("【当前地图选中对象】\n"); append(sb, "对象类型", firstString(mapObject, "objectType", "object_type")); append(sb, "对象ID", firstString(mapObject, "objectId", "object_id", "id")); append(sb, "路线", firstString(mapObject, "routeCode", "route_code")); append(sb, "名称", firstString(mapObject, "routeName", "route_name", "sectionName", "section_name", "unitCode", "unit_code")); append(sb, "起点桩号", valueOfFirst(mapObject, "startStake", "start_stake")); append(sb, "终点桩号", valueOfFirst(mapObject, "endStake", "end_stake")); append(sb, "年度", valueOfFirst(mapObject, "year")); append(sb, "MQI", valueOfFirst(mapObject, "mqi")); append(sb, "PQI", valueOfFirst(mapObject, "pqi")); append(sb, "PCI", valueOfFirst(mapObject, "pci")); append(sb, "等级", valueOfFirst(mapObject, "grade")); append(sb, "病害", firstString(mapObject, "diseaseName", "disease_name", "diseaseType", "disease_type")); append(sb, "严重程度", valueOfFirst(mapObject, "severity")); append(sb, "数量", valueOfFirst(mapObject, "quantity")); append(sb, "单位", valueOfFirst(mapObject, "measureUnit", "measure_unit")); append(sb, "摘要", firstString(mapObject, "context_summary", "contextSummary")); return sb.toString(); }
    private void append(StringBuilder sb, String label, Object value) { if (value != null && String.valueOf(value).trim().length() > 0) sb.append("- ").append(label).append("：").append(value).append("\n"); }
    private String firstString(Map map, String... keys) { Object value = valueOfFirst(map, keys); return value == null ? null : String.valueOf(value); }
    private Integer firstInteger(Map map, String... keys) { return asInteger(valueOfFirst(map, keys)); }
    private Object valueOfFirst(Map map, String... keys) { if (map == null) return null; for (String key : keys) { Object value = map.get(key); if (value != null && String.valueOf(value).trim().length() > 0) return value; } return null; }
    private void fillAnswerMeta(Map data, String answerSource, Boolean llmSuccess, Boolean fallback, String fallbackReason, String mode) { data.put("answerSource", answerSource); data.put("answerSourceLabel", labelOfAnswerSource(answerSource)); data.put("llmSuccess", Boolean.TRUE.equals(llmSuccess)); data.put("fallback", Boolean.TRUE.equals(fallback)); data.put("fallbackReason", fallbackReason); data.put("answerNotice", noticeOfAnswerSource(answerSource, fallbackReason)); Map answerMeta = new LinkedHashMap(); answerMeta.put("answerSource", answerSource); answerMeta.put("answerSourceLabel", labelOfAnswerSource(answerSource)); answerMeta.put("llmSuccess", Boolean.TRUE.equals(llmSuccess)); answerMeta.put("fallback", Boolean.TRUE.equals(fallback)); answerMeta.put("fallbackReason", fallbackReason); answerMeta.put("mode", mode); answerMeta.put("notice", noticeOfAnswerSource(answerSource, fallbackReason)); data.put("answerMeta", answerMeta); }
    private String labelOfAnswerSource(String answerSource) { if ("LLM".equals(answerSource)) return "大模型返回"; if ("LOCAL_FALLBACK".equals(answerSource)) return "本地知识库/业务数据降级返回"; if ("BUSINESS_ANALYSIS_FALLBACK".equals(answerSource)) return "业务分析结果降级返回"; if ("FAILED".equals(answerSource)) return "AI 问答失败"; return "未知来源"; }
    private String noticeOfAnswerSource(String answerSource, String fallbackReason) { if ("LLM".equals(answerSource)) return "本次 answer 由大模型成功生成。"; String reason = fallbackReason == null || fallbackReason.trim().isEmpty() ? "大模型未返回有效内容" : fallbackReason; return "本次 answer 不是大模型成功返回，而是系统降级生成。原因：" + reason; }
    private int hitCount(Map rag) { int count = 0; Object knowledgeSources = rag == null ? null : rag.get("knowledgeSources"); Object outlineSources = rag == null ? null : rag.get("outlineSources"); if (knowledgeSources instanceof java.util.Collection) count += ((java.util.Collection) knowledgeSources).size(); if (outlineSources instanceof java.util.Collection) count += ((java.util.Collection) outlineSources).size(); return count; }
    private boolean looksLikeBusinessQuestion(String message) { return containsAny(message, "G210", "路线", "路况", "病害", "评定", "MQI", "PQI", "PCI", "优良", "次差", "报告", "桩号", "当前", "这个", "选中"); }
    private boolean containsAny(String text, String... words) { String value = text == null ? "" : text; for (String word : words) if (value.contains(word)) return true; return false; }
    private String asString(Object value) { return value == null ? null : String.valueOf(value); }
    private Boolean asBoolean(Object value) { if (value == null) return null; if (value instanceof Boolean) return (Boolean) value; return Boolean.parseBoolean(String.valueOf(value)); }
    private Integer asInteger(Object value) { if (value == null) return null; try { return Integer.valueOf(String.valueOf(value)); } catch (Exception e) { return null; } }
    private String shortText(String text, int max) { if (text == null) return ""; return text.length() <= max ? text : text.substring(0, max) + "..."; }
}
