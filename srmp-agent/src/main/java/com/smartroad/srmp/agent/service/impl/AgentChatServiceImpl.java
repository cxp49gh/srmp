package com.smartroad.srmp.agent.service.impl;

import com.smartroad.srmp.agent.dto.AgentAnalysisRequest;
import com.smartroad.srmp.agent.dto.AgentChatRequest;
import com.smartroad.srmp.agent.llm.LlmClient;
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

    @Override
    public AgentChatResponse chat(AgentChatRequest request) {
        String message = request == null ? "" : request.getMessage();
        AgentAnalysisRequest analysisRequest = toAnalysisRequest(request);

        AgentAnalysisResponse analysis;
        if (containsAny(message, "病害", "裂缝", "坑槽", "车辙", "沉陷")) {
            analysis = agentAnalysisService.analyzeDisease(analysisRequest);
        } else if (containsAny(message, "评定", "MQI", "PQI", "PCI", "优良", "次差")) {
            analysis = agentAnalysisService.analyzeAssessment(analysisRequest);
        } else {
            analysis = agentAnalysisService.analyzeRoute(analysisRequest);
        }

        String systemPrompt = "你是智路养护平台的智能分析助手，请结合已有分析结果回答用户问题。";
        String llm = llmClient.chat(systemPrompt, "用户问题：" + message + "\n\n已有分析：\n" + analysis.getMarkdown());

        AgentChatResponse response = new AgentChatResponse();
        response.setAnswer(llm == null || llm.trim().isEmpty() ? analysis.getMarkdown() : llm);
        response.setMode(llm == null || llm.trim().isEmpty() ? analysis.getMode() : "LLM");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("analysis", analysis.getData());
        data.put("context", request == null ? null : request.getContext());
        response.setData(data);
        return response;
    }

    private AgentAnalysisRequest toAnalysisRequest(AgentChatRequest request) {
        AgentAnalysisRequest analysisRequest = new AgentAnalysisRequest();
        if (request == null || request.getContext() == null) {
            return analysisRequest;
        }
        Map<String, Object> ctx = request.getContext();
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
