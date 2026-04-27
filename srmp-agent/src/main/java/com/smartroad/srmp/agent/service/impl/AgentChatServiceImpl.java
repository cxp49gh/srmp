package com.smartroad.srmp.agent.service.impl;

import com.smartroad.srmp.agent.dto.AgentAnalysisRequest;
import com.smartroad.srmp.agent.dto.AgentChatRequest;
import com.smartroad.srmp.agent.llm.LlmClient;
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
    @Resource private AiTraceService aiTraceService;

    @Override
    public AgentChatResponse chat(AgentChatRequest request) {
        String message = request == null ? "" : request.getMessage();
        AiTraceContext trace = AiTraceContext.start("AGENT_CHAT", message);
        AgentChatResponse response = new AgentChatResponse();
        AgentAnalysisResponse analysis = null;
        String llm = null;
        try {
            log.info("[AI-CHAT] start traceId={} message={}", trace.getTraceId(), shortText(message, 200));
            AgentAnalysisRequest analysisRequest = toAnalysisRequest(request);
            AiTraceContext.StepTimer businessTimer = trace.step("business_analysis", "业务数据分析");
            try {
                if (containsAny(message, "病害", "裂缝", "坑槽", "车辙", "沉陷")) {
                    analysis = agentAnalysisService.analyzeDisease(analysisRequest);
                } else if (containsAny(message, "评定", "MQI", "PQI", "PCI", "优良", "次差")) {
                    analysis = agentAnalysisService.analyzeAssessment(analysisRequest);
                } else {
                    analysis = agentAnalysisService.analyzeRoute(analysisRequest);
                }
                businessTimer.success();
            } catch (Exception e) {
                businessTimer.failed(e); throw e;
            }
            String systemPrompt = "你是智路养护平台的智能分析助手，请结合已有分析结果回答用户问题。";
            String userPrompt = "用户问题：" + message + "\n\n已有分析：\n" + (analysis == null ? "" : analysis.getMarkdown());
            AiTraceContext.StepTimer llmTimer = trace.step("llm_call", "大模型调用");
            try {
                llm = llmClient.chat(systemPrompt, userPrompt);
                if (llm == null || llm.trim().isEmpty()) {
                    trace.setFallback(true);
                    llmTimer.failed(new RuntimeException("LLM 返回为空，已降级为本地分析结果"));
                } else {
                    llmTimer.success();
                }
            } catch (Exception e) {
                trace.setFallback(true); trace.setError(e.getMessage()); llmTimer.failed(e);
                log.warn("[AI-CHAT] llm failed traceId={} error={}", trace.getTraceId(), e.getMessage());
            }
            boolean fallback = llm == null || llm.trim().isEmpty();
            response.setAnswer(fallback ? (analysis == null ? "AI 分析失败，且没有可用的业务分析结果。" : analysis.getMarkdown()) : llm);
            response.setMode(fallback ? (analysis == null ? "FALLBACK" : analysis.getMode()) : "LLM");
            trace.setMode(response.getMode()); trace.setFallback(fallback); trace.setStatus("SUCCESS");
            Map data = new LinkedHashMap<>();
            data.put("analysis", analysis == null ? null : analysis.getData());
            data.put("context", request == null ? null : request.getContext());
            response.setData(data);
            return response;
        } catch (Exception e) {
            trace.setStatus("FAILED"); trace.setError(e.getMessage()); trace.setFallback(true);
            response.setAnswer("AI 问答处理失败：" + e.getMessage()); response.setMode("FAILED");
            Map data = new LinkedHashMap<>(); data.put("context", request == null ? null : request.getContext()); response.setData(data);
            return response;
        } finally {
            trace.finish();
            if (response.getData() == null) response.setData(new LinkedHashMap<>());
            response.getData().put("trace", trace.toMap());
            try { aiTraceService.save(trace); } catch (Exception saveError) { log.warn("[AI-CHAT] save trace failed traceId={} error={}", trace.getTraceId(), saveError.getMessage()); }
            log.info("[AI-CHAT] finish traceId={} status={} mode={} fallback={} totalCostMs={}", trace.getTraceId(), trace.getStatus(), trace.getMode(), trace.getFallback(), trace.getTotalCostMs());
        }
    }
    private AgentAnalysisRequest toAnalysisRequest(AgentChatRequest request) {
        AgentAnalysisRequest analysisRequest = new AgentAnalysisRequest();
        if (request == null || request.getContext() == null) return analysisRequest;
        Map ctx = request.getContext();
        analysisRequest.setRouteCode(asString(ctx.get("routeCode"))); analysisRequest.setIndexCode(asString(ctx.get("indexCode")));
        analysisRequest.setGrade(asString(ctx.get("grade"))); analysisRequest.setDiseaseType(asString(ctx.get("diseaseType")));
        analysisRequest.setSeverity(asString(ctx.get("severity"))); analysisRequest.setTaskId(asString(ctx.get("taskId")));
        analysisRequest.setYear(asInteger(ctx.get("year"))); return analysisRequest;
    }
    private boolean containsAny(String text, String... words) { String value = text == null ? "" : text; for (String word : words) if (value.contains(word)) return true; return false; }
    private String asString(Object value) { return value == null ? null : String.valueOf(value); }
    private Integer asInteger(Object value) { if (value == null) return null; try { return Integer.valueOf(String.valueOf(value)); } catch (Exception e) { return null; } }
    private String shortText(String text, int max) { if (text == null) return ""; return text.length() <= max ? text : text.substring(0, max) + "..."; }
}
