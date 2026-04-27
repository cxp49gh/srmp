package com.smartroad.srmp.agent.service.impl;

import com.smartroad.srmp.agent.dto.AgentAnalysisRequest;
import com.smartroad.srmp.agent.dto.AgentChatRequest;
import com.smartroad.srmp.agent.llm.LlmClient;
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
    private com.smartroad.srmp.agent.map.MapObjectContextService mapObjectContextService;

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

        try {
            log.info("[AI-CHAT] start traceId={} message={}", trace.getTraceId(), shortText(message, 200));

            RagOptions options = RagOptions.autoByQuestion(message, request == null ? null : request.getOptions());

            // Resolve map object context
            com.smartroad.srmp.agent.map.MapObjectContext mapObjCtx = null;
            String mapObjectContextMarkdown = "";
            boolean mapObjectUsed = false;
            try {
                if (mapObjectContextService != null) {
                    mapObjCtx = mapObjectContextService.resolve(buildContextMap(request));
                    mapObjectUsed = mapObjCtx != null && mapObjCtx.isPresent();
                    if (mapObjectUsed) {
                        mapObjectContextMarkdown = mapObjCtx.getMarkdown();
                    }
                }
            } catch (Exception e) {
                log.warn("[AI-CHAT] map object context resolve failed: {}", e.getMessage());
            }

            AgentAnalysisResponse analysis = null;
            String businessMarkdown = mapObjectContextMarkdown;

            AiTraceContext.StepTimer businessTimer = trace.step("business_analysis", "业务数据分析");
            try {
                if (options.isUseBusinessData() && looksLikeBusinessQuestion(message)) {
                    AgentAnalysisRequest analysisRequest = toAnalysisRequest(request);
                    if (containsAny(message, "病害", "裂缝", "坑槽", "车辙", "沉陷")) {
                        analysis = agentAnalysisService.analyzeDisease(analysisRequest);
                    } else if (containsAny(message, "评定", "MQI", "PQI", "PCI", "优良", "次差")) {
                        analysis = agentAnalysisService.analyzeAssessment(analysisRequest);
                    } else {
                        analysis = agentAnalysisService.analyzeRoute(analysisRequest);
                    }
                    businessMarkdown = analysis == null ? "" : analysis.getMarkdown();
                    if (mapObjectUsed && mapObjCtx != null) {
                        businessMarkdown = mapObjCtx.getMarkdown() + "\n" + businessMarkdown;
                    }
                    businessTimer.success();
                } else {
                    businessTimer.skipped();
                }
            } catch (Exception e) {
                businessTimer.failed(e);
                log.warn("[AI-CHAT] business analysis failed traceId={} error={}", trace.getTraceId(), e.getMessage());
            }

            AiTraceContext.StepTimer ragTimer = trace.step("rag_answer", "RAG 问答生成");
            Map rag;
            try {
                rag = ragAnswerService.answer(message, businessMarkdown, options);
                Integer hitCount = hitCount(rag);
                ragTimer.success(hitCount);
            } catch (Exception e) {
                ragTimer.failed(e);
                rag = new LinkedHashMap();
                rag.put("answer", businessMarkdown == null || businessMarkdown.trim().isEmpty()
                        ? "AI 问答处理失败：" + e.getMessage()
                        : businessMarkdown);
                rag.put("answerSource", "BUSINESS_ANALYSIS_FALLBACK");
                rag.put("answerSourceLabel", "业务分析结果降级返回");
                rag.put("llmSuccess", false);
                rag.put("fallback", true);
                rag.put("fallbackReason", e.getMessage());
                log.warn("[AI-CHAT] rag answer failed traceId={} error={}", trace.getTraceId(), e.getMessage());
            }

            String answer = asString(rag.get("answer"));
            answerSource = asString(rag.get("answerSource"));
            llmSuccess = asBoolean(rag.get("llmSuccess"));
            fallback = asBoolean(rag.get("fallback"));
            fallbackReason = asString(rag.get("fallbackReason"));

            mode = Boolean.TRUE.equals(llmSuccess) ? "HYBRID_RAG_LLM" : "HYBRID_RAG_FALLBACK";

            if ((answer == null || answer.trim().isEmpty()) && analysis != null) {
                AiTraceContext.StepTimer directLlmTimer = trace.step("direct_llm_call", "直接大模型兜底");
                try {
                    String systemPrompt = "你是智路养护平台的智能分析助手，请结合已有分析结果回答用户问题。";
                    String llm = llmClient.chat(systemPrompt, "用户问题：" + message + "\n\n已有分析：\n" + analysis.getMarkdown());

                    if (llm == null || llm.trim().isEmpty()) {
                        answer = analysis.getMarkdown();
                        answerSource = "BUSINESS_ANALYSIS_FALLBACK";
                        fallback = true;
                        llmSuccess = false;
                        fallbackReason = fallbackReason == null ? "LLM 返回为空，使用业务分析结果兜底" : fallbackReason;
                        mode = analysis.getMode();
                        directLlmTimer.failed(new RuntimeException(fallbackReason));
                    } else {
                        answer = llm;
                        answerSource = "LLM";
                        fallback = false;
                        llmSuccess = true;
                        fallbackReason = null;
                        mode = "LLM";
                        directLlmTimer.success();
                    }
                } catch (Exception e) {
                    answer = analysis.getMarkdown();
                    answerSource = "BUSINESS_ANALYSIS_FALLBACK";
                    fallback = true;
                    llmSuccess = false;
                    fallbackReason = e.getMessage();
                    mode = analysis.getMode();
                    directLlmTimer.failed(e);
                }
            }

            response.setAnswer(answer);
            response.setMode(mode);

            Map data = new LinkedHashMap<>();
            data.put("analysis", analysis == null ? null : analysis.getData());
            data.put("context", request == null ? null : request.getContext());
            data.put("knowledgeSources", rag.get("knowledgeSources"));
            data.put("outlineSources", rag.get("outlineSources"));
            data.put("usedKnowledge", rag.get("usedKnowledge"));
            data.put("usedOutline", rag.get("usedOutline"));

            // Handle map object context
            Map<String, Object> mapObjectForResponse = (mapObjCtx != null && mapObjCtx.isPresent()) ? mapObjCtx.getDetail() : null;
            response.setMapObjectUsed(mapObjectUsed);
            response.setMapObject(mapObjectForResponse);
            response.setMapObjectContext(mapObjectUsed && mapObjCtx != null ? mapObjCtx.getMarkdown() : null);
            data.put("mapObjectUsed", mapObjectUsed);
            data.put("mapObject", mapObjectForResponse);
            data.put("mapObjectContext", mapObjectUsed && mapObjCtx != null ? mapObjCtx.getMarkdown() : null);

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

            response.setAnswer("AI 问答处理失败：" + e.getMessage());
            response.setMode("FAILED");

            Map data = new LinkedHashMap<>();
            data.put("context", request == null ? null : request.getContext());
            fillAnswerMeta(data, "FAILED", false, true, e.getMessage(), "FAILED");
            response.setData(data);

            return response;
        } finally {
            trace.finish();

            if (response.getData() == null) {
                response.setData(new LinkedHashMap<>());
            }
            response.getData().put("trace", trace.toMap());

            try {
                aiTraceService.save(trace);
            } catch (Exception saveError) {
                log.warn("[AI-CHAT] save trace failed traceId={} error={}", trace.getTraceId(), saveError.getMessage(), saveError);
            }

            log.info("[AI-CHAT] finish traceId={} status={} mode={} fallback={} totalCostMs={}",
                    trace.getTraceId(), trace.getStatus(), trace.getMode(), trace.getFallback(), trace.getTotalCostMs());
        }
    }

    private void fillAnswerMeta(Map data,
                                String answerSource,
                                Boolean llmSuccess,
                                Boolean fallback,
                                String fallbackReason,
                                String mode) {
        data.put("answerSource", answerSource);
        data.put("answerSourceLabel", labelOfAnswerSource(answerSource));
        data.put("llmSuccess", Boolean.TRUE.equals(llmSuccess));
        data.put("fallback", Boolean.TRUE.equals(fallback));
        data.put("fallbackReason", fallbackReason);
        data.put("answerNotice", noticeOfAnswerSource(answerSource, fallbackReason));

        Map answerMeta = new LinkedHashMap<>();
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
        if ("LLM".equals(answerSource)) return "大模型返回";
        if ("LOCAL_FALLBACK".equals(answerSource)) return "本地知识库/业务数据降级返回";
        if ("BUSINESS_ANALYSIS_FALLBACK".equals(answerSource)) return "业务分析结果降级返回";
        if ("FAILED".equals(answerSource)) return "AI 问答失败";
        return "未知来源";
    }

    private String noticeOfAnswerSource(String answerSource, String fallbackReason) {
        if ("LLM".equals(answerSource)) return "本次 answer 由大模型成功生成。";
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
        return containsAny(message, "G210", "路线", "路况", "病害", "评定", "MQI", "PQI", "PCI", "优良", "次差", "报告", "桩号");
    }

    private AgentAnalysisRequest toAnalysisRequest(AgentChatRequest request) {
        AgentAnalysisRequest analysisRequest = new AgentAnalysisRequest();
        if (request == null || request.getContext() == null) return analysisRequest;
        Map ctx = request.getContext();
        analysisRequest.setRouteCode(asString(ctx.get("routeCode")));
        analysisRequest.setIndexCode(asString(ctx.get("indexCode")));
        analysisRequest.setGrade(asString(ctx.get("grade")));
        analysisRequest.setDiseaseType(asString(ctx.get("diseaseType")));
        analysisRequest.setSeverity(asString(ctx.get("severity")));
        analysisRequest.setTaskId(asString(ctx.get("taskId")));
        analysisRequest.setYear(asInteger(ctx.get("year")));
        return analysisRequest;
    }

    private boolean containsAny(String text, String... words) {
        String value = text == null ? "" : text;
        for (String word : words) if (value.contains(word)) return true;
        return false;
    }

    private String asString(Object value) { return value == null ? null : String.valueOf(value); }

    private Boolean asBoolean(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private Integer asInteger(Object value) {
        if (value == null) return null;
        try { return Integer.valueOf(String.valueOf(value)); } catch (Exception e) { return null; }
    }

    private String shortText(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private String buildMapObjectContext(Map<String, Object> mapObject) {
        if (mapObject == null || mapObject.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【当前地图选中对象】\n");
        String objectType = asString(mapObject.get("objectType"));
        sb.append("- 类型：").append(objectType).append("\n");
        String routeCode = asString(mapObject.get("routeCode"));
        if (routeCode != null) {
            sb.append("- 路线：").append(routeCode).append("\n");
        }
        Object startStake = mapObject.get("startStake");
        Object endStake = mapObject.get("endStake");
        if (startStake != null && endStake != null) {
            sb.append("- 桩号：").append(startStake).append(" ~ ").append(endStake).append("\n");
        }
        Object mqi = mapObject.get("mqi");
        if (mqi != null) {
            sb.append("- MQI：").append(mqi).append("\n");
        }
        Object pqi = mapObject.get("pqi");
        if (pqi != null) {
            sb.append("- PQI：").append(pqi).append("\n");
        }
        Object pci = mapObject.get("pci");
        if (pci != null) {
            sb.append("- PCI：").append(pci).append("\n");
        }
        Object grade = mapObject.get("grade");
        if (grade != null) {
            sb.append("- 等级：").append(grade).append("\n");
        }
        Object diseaseType = mapObject.get("diseaseType");
        Object diseaseName = mapObject.get("diseaseName");
        if (diseaseType != null || diseaseName != null) {
            sb.append("- 病害：").append(diseaseName != null ? diseaseName : diseaseType).append("\n");
        }
        Object severity = mapObject.get("severity");
        if (severity != null) {
            sb.append("- 程度：").append(severity).append("\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildContextMap(AgentChatRequest request) {
        if (request == null) return null;
        Map<String, Object> ctx = new LinkedHashMap<>();
        if (request.getContext() != null) {
            ctx.putAll(request.getContext());
        }
        if (request.getMapObject() != null) {
            ctx.put("mapObject", request.getMapObject());
        }
        return ctx;
    }
}
