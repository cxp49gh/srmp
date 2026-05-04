package com.smartroad.srmp.agent.orchestrator;

import com.smartroad.srmp.agent.mapagent.dto.MapAiAgentRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAiAgentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase50：编排器路由。
 *
 * provider=native 时保持原有行为；provider=langgraph 时调用远程编排，
 * 失败后默认回退 native，确保前端入口稳定。
 */
@Slf4j
@Service
public class AgentOrchestratorRouter {

    private final Map<String, AgentOrchestrator> orchestratorMap = new LinkedHashMap<>();
    private final AgentOrchestratorProperties properties;

    public AgentOrchestratorRouter(List<AgentOrchestrator> orchestrators,
                                   AgentOrchestratorProperties properties) {
        this.properties = properties;
        if (orchestrators != null) {
            for (AgentOrchestrator orchestrator : orchestrators) {
                if (orchestrator != null && orchestrator.provider() != null) {
                    orchestratorMap.put(normalize(orchestrator.provider()), orchestrator);
                }
            }
        }
    }

    public MapAiAgentResponse chat(MapAiAgentRequest request) {
        String provider = normalize(properties.getProvider());
        if (provider.length() == 0) {
            provider = "native";
        }

        AgentOrchestrator selected = orchestratorMap.get(provider);
        if (selected == null) {
            log.warn("[AI-ORCHESTRATOR] provider={} not found, fallback to native", provider);
            selected = nativeOrchestrator();
        }

        if (selected == null) {
            MapAiAgentResponse response = new MapAiAgentResponse();
            response.setMode("MAP_AI_AGENT");
            response.setAnswer("AI 编排器未初始化：未找到 native 编排器");
            return response;
        }

        try {
            MapAiAgentResponse response = selected.chat(request);
            markProvider(response, selected.provider(), false, null);
            return response;
        } catch (Exception e) {
            if (!Boolean.TRUE.equals(properties.getFallbackToNative()) || "native".equals(provider)) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            log.warn("[AI-ORCHESTRATOR] provider={} failed, fallback to native: {}", provider, e.getMessage(), e);
            AgentOrchestrator fallback = nativeOrchestrator();
            MapAiAgentResponse response = fallback.chat(request);
            markProvider(response, fallback.provider(), true, e.getMessage());
            return response;
        }
    }

    private AgentOrchestrator nativeOrchestrator() {
        return orchestratorMap.get("native");
    }

    private void markProvider(MapAiAgentResponse response, String provider, boolean fallback, String reason) {
        if (response == null) {
            return;
        }
        Map<String, Object> data = response.getData();
        if (data == null) {
            data = new LinkedHashMap<>();
            response.setData(data);
        }
        data.put("orchestratorProvider", provider);
        data.put("orchestratorFallback", fallback);
        if (reason != null && reason.trim().length() > 0) {
            data.put("orchestratorFallbackReason", reason);
        }
        Map<String, Object> trace = response.getTrace();
        if (trace != null) {
            trace.put("orchestratorProvider", provider);
            trace.put("orchestratorFallback", fallback);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
