package com.smartroad.srmp.agent.orchestrator;

import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunResponse;

public interface AgentOrchestrator {
    MapAgentRunResponse run(MapAgentRunRequest request);
}
