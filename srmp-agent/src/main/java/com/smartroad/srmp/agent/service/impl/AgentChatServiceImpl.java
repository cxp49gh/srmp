package com.smartroad.srmp.agent.service.impl;

import com.smartroad.srmp.agent.dto.AgentAnalysisRequest;
import com.smartroad.srmp.agent.dto.AgentChatRequest;
import com.smartroad.srmp.agent.llm.LlmClient;
import com.smartroad.srmp.agent.map.MapObjectContext;
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

    @Resource
    private AgentAnalysisService agentAnalysisService;

    @Resource
    private LlmClient llmClient;

    @Resource
    private RagAnswerService ragAnswerService;

    @Resource
    private AiTraceService aiTraceService;

    @Resource
    private MapObjectContextService mapObjectContextService;

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
        MapObjectContext mapObjCtx = MapObjectContext.empty();
        Map enrichedMapObject = new LinkedHashMap();
        String mapObjectContext = null;
        boolean mapObjectUsed = false;

        try {
            RagOptions options = RagOptions.autoByQuestion(message, request == null ? null : request.getOptions());
            AgentAnalysisResponse analysis = null;
            String businessMarkdown = "";

            AiTraceContext.StepTimer mapTimer = trace.step("map_object_context", "地图对象上下文");
            try {
                mapObjCtx = mapObjectContextService.resolve(buildContextMap(request));
                mapObjectUsed = mapObjCtx != null && mapObjCtx.isPresent();
                if (mapObjectUsed) {
                    enrichedMapObject = mapObjCtx.getDetail() == null ? new LinkedHashMap() : mapObjCtx.getDetail();
                    mapObjectContext = mapObjCtx.getMarkdown();
                    mapTimer.success(1);
                } else {
                    mapTimer.skipped();
                }
            } catch (Exception e) {
                mapTimer.failed(e);
                log.warn("[AI-CHAT] map object context failed traceId={} error={}", trace.getTraceId(), e.getMessage());
            }

            if (isSingleDiseaseObject(mapObjCtx)) {
                businessMarkdown = buildSingleDiseaseObjectAnswer(mapObjCtx);
                answerSource = "MAP_OBJECT_LOCAL";
                llmSuccess = false;
                fallback = true;
                fallbackReason = "当前选择的是单个病害对象，优先返回单对象处置建议";
                mode = "MAP_OBJECT_LOCAL";
            } else {
                AiTraceContext.StepTimer businessTimer = trace.step("business_analysis", "业务数据分析");
                try {
                    if (options.isUseBusinessData() && (looksLikeBusinessQuestion(message) || mapObjectUsed)) {
                        AgentAnalysisRequest analysisRequest = toAnalysisRequest(request, enrichedMapObject);
                        String objType = firstString(enrichedMapObject, "object_type", "objectType");
                        if (containsAny(message, "病害", "裂缝", "坑槽", "车辙", "沉陷")) {
                            analysis = agentAnalysisService.analyzeDisease(analysisRequest);
                        } else if (containsAny(message, "评定", "MQI", "PQI", "PCI", "优良", "次差")
                                || "ASSESSMENT_RESULT".equals(objType)
                                || "EVALUATION_UNIT".equals(objType)) {
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
            }

            if (mapObjectContext != null && !mapObjectContext.trim().isEmpty()
                    && !businessMarkdown.contains("【当前地图选中对象】")) {
                businessMarkdown = mapObjectContext + "\n\n" + businessMarkdown;
            }

            Map rag = new LinkedHashMap();
            if (!"MAP_OBJECT_LOCAL".equals(answerSource)) {
                AiTraceContext.StepTimer ragTimer = trace.step("rag_answer", "RAG 问答生成");
                try {
                    rag = ragAnswerService.answer(message, businessMarkdown, options);
                    ragTimer.success(hitCount(rag));
                } catch (Exception e) {
                    ragTimer.failed(e);
                    rag.put("answer", businessMarkdown == null || businessMarkdown.trim().isEmpty()
                            ? "AI 问答处理失败：" + e.getMessage()
                            : businessMarkdown);
                    rag.put("answerSource", "BUSINESS_ANALYSIS_FALLBACK");
                    rag.put("llmSuccess", false);
                    rag.put("fallback", true);
                    rag.put("fallbackReason", e.getMessage());
                }
            } else {
                rag.put("answer", businessMarkdown);
                rag.put("answerSource", answerSource);
                rag.put("llmSuccess", false);
                rag.put("fallback", true);
                rag.put("fallbackReason", fallbackReason);
            }

            String answer = sanitizeAssistantAnswer(asString(rag.get("answer")));
            answerSource = answerSource == null ? asString(rag.get("answerSource")) : answerSource;
            llmSuccess = "MAP_OBJECT_LOCAL".equals(answerSource) ? false : asBoolean(rag.get("llmSuccess"));
            fallback = "MAP_OBJECT_LOCAL".equals(answerSource) ? true : asBoolean(rag.get("fallback"));
            fallbackReason = fallbackReason == null ? asString(rag.get("fallbackReason")) : fallbackReason;
            mode = "MAP_OBJECT_LOCAL".equals(answerSource)
                    ? "MAP_OBJECT_LOCAL"
                    : Boolean.TRUE.equals(llmSuccess) ? "HYBRID_RAG_LLM" : "HYBRID_RAG_FALLBACK";

            if (mapObjectUsed && answer != null && !answer.startsWith("【基于当前地图对象】")) {
                answer = "【基于当前地图对象】\n" + answer;
            }

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
            trace.setMode(mode);
            trace.setStatus("FAILED");
            trace.setFallback(true);
            trace.setError(e.getMessage());
            response.setAnswer(sanitizeAssistantAnswer("AI 问答处理失败：" + e.getMessage()));
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
            if (response.getData() == null) {
                response.setData(new LinkedHashMap());
            }
            response.getData().put("trace", trace.toMap());
            try {
                aiTraceService.save(trace);
            } catch (Exception saveError) {
                log.warn("[AI-CHAT] save trace failed traceId={} error={}", trace.getTraceId(), saveError.getMessage(), saveError);
            }
        }
    }

    private Map buildContextMap(AgentChatRequest request) {
        Map context = new LinkedHashMap();
        if (request == null) {
            return context;
        }
        if (request.getContext() != null) {
            context.putAll(request.getContext());
        }
        if (request.getMapObject() != null && !request.getMapObject().isEmpty()) {
            context.put("mapObject", request.getMapObject());
        }
        return context;
    }

    private AgentAnalysisRequest toAnalysisRequest(AgentChatRequest request, Map mapObject) {
        AgentAnalysisRequest r = new AgentAnalysisRequest();
        Map ctx = request == null ? null : request.getContext();
        if (ctx != null) {
            r.setRouteCode(asString(ctx.get("routeCode")));
            r.setIndexCode(asString(ctx.get("indexCode")));
            r.setGrade(asString(ctx.get("grade")));
            r.setDiseaseType(asString(ctx.get("diseaseType")));
            r.setSeverity(asString(ctx.get("severity")));
            r.setTaskId(asString(ctx.get("taskId")));
            r.setYear(asInteger(ctx.get("year")));
        }
        if (mapObject != null && !mapObject.isEmpty()) {
            String routeCode = firstString(mapObject, "routeCode", "route_code");
            String grade = firstString(mapObject, "grade");
            String diseaseType = firstString(mapObject, "diseaseType", "disease_type");
            String severity = firstString(mapObject, "severity");
            Integer year = firstInteger(mapObject, "year");
            if (routeCode != null) {
                r.setRouteCode(routeCode);
            }
            if (grade != null) {
                r.setGrade(grade);
            }
            if (diseaseType != null) {
                r.setDiseaseType(diseaseType);
            }
            if (severity != null) {
                r.setSeverity(severity);
            }
            if (year != null) {
                r.setYear(year);
            }
        }
        return r;
    }

    private boolean isSingleDiseaseObject(MapObjectContext ctx) {
        if (ctx == null || !ctx.isPresent()) {
            return false;
        }
        String type = ctx.getObjectType();
        if (type == null && ctx.getDetail() != null) {
            type = firstString(ctx.getDetail(), "objectType", "object_type", "type", "layerType");
        }
        if (type == null) {
            return false;
        }
        String value = type.trim().toUpperCase();
        return "DISEASE".equals(value) || "DISEASE_RECORD".equals(value);
    }

    private String buildSingleDiseaseObjectAnswer(MapObjectContext ctx) {
        Map d = ctx == null ? null : ctx.getDetail();
        String route = firstString(d, "routeCode", "route_code");
        String start = firstString(d, "startStake", "start_stake");
        String end = firstString(d, "endStake", "end_stake");
        String disease = firstString(d, "diseaseName", "disease_name", "diseaseType", "disease_type");
        String severity = firstString(d, "severity");
        String quantity = firstString(d, "quantity");
        String unit = firstString(d, "measureUnit", "measure_unit");
        String stake = start == null ? "" : (end == null ? "K" + start : "K" + start + "—K" + end);

        StringBuilder sb = new StringBuilder();
        sb.append("【当前地图选中对象】\n");
        sb.append("- 对象类型：DISEASE\n");
        appendLine(sb, "路线", route);
        appendLine(sb, "桩号", stake);
        appendLine(sb, "病害", disease);
        appendLine(sb, "严重程度", severity);
        if (quantity != null || unit != null) {
            sb.append("- 数量：").append(quantity == null ? "" : quantity).append(unit == null ? "" : unit).append("\n");
        }

        sb.append("\n## 当前对象判断\n");
        sb.append("当前选中对象为")
                .append(route == null ? "" : route + " ")
                .append(stake)
                .append(" 的")
                .append(severity == null ? "" : severity + " ")
                .append(disease == null ? "病害" : disease)
                .append("。该回答仅针对当前地图选中的单个病害对象，不展开为整条路线病害统计。\n");

        sb.append("\n## 主要问题\n");
        sb.append("该位置存在").append(disease == null ? "道路病害" : disease)
                .append("，严重程度为").append(severity == null ? "未标明" : severity)
                .append("。若继续发展，可能造成局部路面平整度下降、行车舒适性下降，并增加后续修复成本。\n");

        sb.append("\n## 成因判断\n");
        sb.append("建议现场重点复核既有修补层与原路面的结合情况、基层是否松散或含水、排水是否不畅，以及车辆荷载反复作用导致的二次损坏风险。\n");

        sb.append("\n## 养护处置建议\n");
        sb.append("1. 先进行现场复核，确认损坏边界、深度和基层状态。\n");
        sb.append("2. 若仅为表层损坏，建议采用局部铣刨、清理、重新摊铺或热补修复。\n");
        sb.append("3. 若基层松散、脱空或含水，应先处理基层和排水，再恢复面层。\n");
        sb.append("4. 该对象可列为近期处置对象；若周边存在连续同类病害，可合并为小段集中处置。\n");
        return sb.toString();
    }

    private String sanitizeAssistantAnswer(String answer) {
        if (answer == null) {
            return null;
        }
        String cleaned = answer.replaceAll("(?is)<think>.*?</think>", "");
        cleaned = cleaned.replaceAll("(?is)<thinking>.*?</thinking>", "");
        return cleaned.trim();
    }

    private void fillAnswerMeta(Map data, String answerSource, Boolean llmSuccess, Boolean fallback, String fallbackReason, String mode) {
        data.put("answerSource", answerSource);
        data.put("answerSourceLabel", labelOfAnswerSource(answerSource));
        data.put("llmSuccess", Boolean.TRUE.equals(llmSuccess));
        data.put("fallback", Boolean.TRUE.equals(fallback));
        data.put("fallbackReason", fallbackReason);
        data.put("answerNotice", noticeOfAnswerSource(answerSource, fallbackReason));
        Map answerMeta = new LinkedHashMap();
        answerMeta.put("answerSource", answerSource);
        answerMeta.put("answerSourceLabel", labelOfAnswerSource(answerSource));
        answerMeta.put("llmSuccess", Boolean.TRUE.equals(llmSuccess));
        answerMeta.put("fallback", Boolean.TRUE.equals(fallback));
        answerMeta.put("fallbackReason", fallbackReason);
        answerMeta.put("mode", mode);
        answerMeta.put("notice", noticeOfAnswerSource(answerSource, fallbackReason));
        data.put("answerMeta", answerMeta);
    }

    private String labelOfAnswerSource(String answerSource) {
        if ("LLM".equals(answerSource)) {
            return "大模型返回";
        }
        if ("MAP_OBJECT_LOCAL".equals(answerSource)) {
            return "地图对象本地分析";
        }
        if ("LOCAL_FALLBACK".equals(answerSource)) {
            return "本地知识库/业务数据降级返回";
        }
        if ("BUSINESS_ANALYSIS_FALLBACK".equals(answerSource)) {
            return "业务分析结果降级返回";
        }
        if ("FAILED".equals(answerSource)) {
            return "AI 问答失败";
        }
        return "未知来源";
    }

    private String noticeOfAnswerSource(String answerSource, String fallbackReason) {
        if ("LLM".equals(answerSource)) {
            return "本次 answer 由大模型成功生成。";
        }
        if ("MAP_OBJECT_LOCAL".equals(answerSource)) {
            return "当前 answer 基于地图选中对象生成，优先使用单对象业务规则。";
        }
        String reason = fallbackReason == null || fallbackReason.trim().isEmpty() ? "大模型未返回有效内容" : fallbackReason;
        return "本次 answer 不是大模型成功返回，而是系统降级生成。原因：" + reason;
    }

    private int hitCount(Map rag) {
        int count = 0;
        Object knowledgeSources = rag == null ? null : rag.get("knowledgeSources");
        Object outlineSources = rag == null ? null : rag.get("outlineSources");
        if (knowledgeSources instanceof java.util.Collection) {
            count += ((java.util.Collection) knowledgeSources).size();
        }
        if (outlineSources instanceof java.util.Collection) {
            count += ((java.util.Collection) outlineSources).size();
        }
        return count;
    }

    private boolean looksLikeBusinessQuestion(String message) {
        return containsAny(message, "G210", "路线", "路况", "病害", "评定", "MQI", "PQI", "PCI", "优良", "次差", "报告", "桩号", "当前", "这个", "选中");
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

    private void appendLine(StringBuilder sb, String label, String value) {
        if (value != null && value.trim().length() > 0) {
            sb.append("- ").append(label).append("：").append(value).append("\n");
        }
    }

    private String firstString(Map map, String... keys) {
        Object value = valueOfFirst(map, keys);
        return value == null ? null : String.valueOf(value);
    }

    private Integer firstInteger(Map map, String... keys) {
        return asInteger(valueOfFirst(map, keys));
    }

    private Object valueOfFirst(Map map, String... keys) {
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

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
