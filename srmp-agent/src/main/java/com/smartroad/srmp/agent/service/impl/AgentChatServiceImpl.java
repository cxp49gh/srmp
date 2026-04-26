package com.smartroad.srmp.agent.service.impl;

import com.smartroad.srmp.agent.dto.AgentAnalysisRequest;
import com.smartroad.srmp.agent.dto.AgentChatRequest;
import com.smartroad.srmp.agent.llm.LlmClient;
import com.smartroad.srmp.agent.rag.RagAnswerService;
import com.smartroad.srmp.agent.rag.RagOptions;
import com.smartroad.srmp.agent.service.AgentAnalysisService;
import com.smartroad.srmp.agent.vo.AgentAnalysisResponse;
import com.smartroad.srmp.agent.vo.AgentChatResponse;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AgentChatServiceImpl implements com.smartroad.srmp.agent.service.AgentChatService {

    @Resource
    private AgentAnalysisService agentAnalysisService;

    @Resource
    private LlmClient llmClient;

    @Resource
    private RagAnswerService ragAnswerService;

    @Override
    public AgentChatResponse chat(AgentChatRequest request) {
        String message = request == null ? "" : request.getMessage();
        RagOptions options = RagOptions.autoByQuestion(message, request == null ? null : request.getOptions());

        AgentAnalysisResponse analysis = null;
        String businessMarkdown = "";

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
        }

        Map rag = ragAnswerService.answer(message, businessMarkdown, options);

        String answer = String.valueOf(rag.get("answer"));
        String llmMode = (answer == null || answer.trim().isEmpty()) ? null : "HYBRID_RAG";

        if ((answer == null || answer.trim().isEmpty()) && analysis != null) {
            String systemPrompt = "你是智路养护平台的智能分析助手，请结合已有分析结果回答用户问题。";
            String llm = llmClient.chat(systemPrompt, "用户问题：" + message + "\n\n已有分析：\n" + analysis.getMarkdown());
            answer = llm == null || llm.trim().isEmpty() ? analysis.getMarkdown() : llm;
            llmMode = llm == null || llm.trim().isEmpty() ? analysis.getMode() : "LLM";
        }

        AgentChatResponse response = new AgentChatResponse();
        response.setAnswer(answer);
        response.setMode(llmMode == null ? "HYBRID_RAG" : llmMode);

        Map data = new LinkedHashMap<>();
        data.put("analysis", analysis == null ? null : analysis.getData());
        data.put("context", request == null ? null : request.getContext());
        data.put("knowledgeSources", rag.get("knowledgeSources"));
        data.put("outlineSources", rag.get("outlineSources"));
        data.put("usedKnowledge", rag.get("usedKnowledge"));
        data.put("usedOutline", rag.get("usedOutline"));
        response.setData(data);
        return response;
    }

    private boolean looksLikeBusinessQuestion(String message) {
        return containsAny(message,
                "G210", "路线", "路况", "病害", "评定", "MQI", "PQI", "PCI", "优良", "次差", "报告", "桩号");
    }

    private AgentAnalysisRequest toAnalysisRequest(AgentChatRequest request) {
        AgentAnalysisRequest analysisRequest = new AgentAnalysisRequest();
        if (request == null || request.getContext() == null) {
            return analysisRequest;
        }
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
        for (String word : words) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
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
