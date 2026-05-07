package com.smartroad.srmp.agent.mapagent.service;

import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunResponse;

public interface MapAiAgentService {
    MapAgentRunResponse run(MapAgentRunRequest request);
}
