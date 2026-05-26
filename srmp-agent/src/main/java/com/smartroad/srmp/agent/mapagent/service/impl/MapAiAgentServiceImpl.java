package com.smartroad.srmp.agent.mapagent.service.impl;

import com.smartroad.srmp.agent.execution.service.AiExecutionRecorder;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunResponse;
import com.smartroad.srmp.agent.mapagent.service.MapAiAgentService;
import com.smartroad.srmp.agent.orchestrator.AgentOrchestratorRouter;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class MapAiAgentServiceImpl implements MapAiAgentService {

    @Resource
    private AgentOrchestratorRouter agentOrchestratorRouter;
    @Resource
    private AiExecutionRecorder aiExecutionRecorder;

    @Override
    public MapAgentRunResponse run(MapAgentRunRequest request) {
        long start = System.currentTimeMillis();
        MapAgentRunResponse response = agentOrchestratorRouter.run(request);
        try {
            aiExecutionRecorder.record(request, response, TenantContextHolder.getTenantId(), traceId(response), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("AI execution observability record failed: {}", e.getMessage());
        }
        return response;
    }

    private String traceId(MapAgentRunResponse response) {
        if (response == null || response.getTrace() == null) return null;
        Object value = response.getTrace().get("traceId");
        if (value == null) value = response.getTrace().get("trace_id");
        return value == null ? null : String.valueOf(value);
    }
}
