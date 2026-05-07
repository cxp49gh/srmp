package com.smartroad.srmp.agent.mapagent.service.impl;

import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunResponse;
import com.smartroad.srmp.agent.mapagent.service.MapAiAgentService;
import com.smartroad.srmp.agent.orchestrator.AgentOrchestratorRouter;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class MapAiAgentServiceImpl implements MapAiAgentService {

    @Resource
    private AgentOrchestratorRouter agentOrchestratorRouter;

    @Override
    public MapAgentRunResponse run(MapAgentRunRequest request) {
        return agentOrchestratorRouter.run(request);
    }
}
