package com.smartroad.srmp.agent.execution.service;

import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunResponse;

public interface AiExecutionRecorder {
    void record(MapAgentRunRequest request, MapAgentRunResponse response, String tenantId, String traceId, long costMs);
}
