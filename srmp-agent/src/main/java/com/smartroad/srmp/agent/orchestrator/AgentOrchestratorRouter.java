package com.smartroad.srmp.agent.orchestrator;

import com.smartroad.srmp.agent.mapagent.dto.MapAgentActionResult;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class AgentOrchestratorRouter {

    @Resource
    private AgentOrchestrator remoteLangGraphOrchestrator;

    public MapAgentRunResponse run(MapAgentRunRequest request) {
        try {
            MapAgentRunResponse response = remoteLangGraphOrchestrator.run(request);
            markLangGraph(response);
            return response;
        } catch (Exception e) {
            log.warn("[AI-ORCHESTRATOR] langgraph failed without native fallback: {}", e.getMessage(), e);
            return errorResponse(request, e.getMessage());
        }
    }

    private void markLangGraph(MapAgentRunResponse response) {
        if (response == null) {
            return;
        }
        if (response.getData() == null) {
            response.setData(new LinkedHashMap<String, Object>());
        }
        response.getData().put("orchestratorProvider", "langgraph");
        response.getData().put("orchestratorFallback", false);
        if (response.getTrace() == null) {
            response.setTrace(new LinkedHashMap<String, Object>());
        }
        response.getTrace().put("orchestratorProvider", "langgraph");
        response.getTrace().put("orchestratorFallback", false);
    }

    private MapAgentRunResponse errorResponse(MapAgentRunRequest request, String message) {
        MapAgentRunResponse response = new MapAgentRunResponse();
        response.setAction(request == null ? "CHAT" : request.getAction());
        response.setIntent("ERROR");
        response.setAnswer("LangGraph Runtime 不可用：" + safe(message));
        MapAgentActionResult result = new MapAgentActionResult();
        result.setType("ERROR");
        result.setStatus("FAILED");
        result.setErrorMessage(safe(message));
        response.setActionResult(result);
        Map<String, Object> answerMeta = new LinkedHashMap<>();
        answerMeta.put("answerSource", "ERROR");
        answerMeta.put("llmSuccess", false);
        answerMeta.put("llmStatus", "FAILED");
        answerMeta.put("fallbackReason", safe(message));
        response.setAnswerMeta(answerMeta);
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("orchestratorProvider", "langgraph");
        trace.put("orchestratorFallback", false);
        trace.put("steps", java.util.Collections.singletonList(step(safe(message))));
        response.setTrace(trace);
        response.getData().put("orchestratorProvider", "langgraph");
        response.getData().put("orchestratorFallback", false);
        return response;
    }

    private Map<String, Object> step(String message) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("name", "remote_langgraph_call");
        step.put("label", "远程 LangGraph 调用");
        step.put("status", "FAILED");
        step.put("error", message);
        return step;
    }

    private String safe(String value) {
        return value == null || value.trim().length() == 0 ? "未知错误" : value.trim();
    }
}
