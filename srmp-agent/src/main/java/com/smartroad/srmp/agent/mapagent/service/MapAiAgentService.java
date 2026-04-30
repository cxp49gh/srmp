package com.smartroad.srmp.agent.mapagent.service;

import com.smartroad.srmp.agent.mapagent.dto.MapAiAgentRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAiAgentResponse;

public interface MapAiAgentService {
    MapAiAgentResponse chat(MapAiAgentRequest request);
}
