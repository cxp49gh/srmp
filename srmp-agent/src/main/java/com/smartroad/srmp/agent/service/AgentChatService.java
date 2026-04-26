package com.smartroad.srmp.agent.service;

import com.smartroad.srmp.agent.dto.AgentChatRequest;
import com.smartroad.srmp.agent.vo.AgentChatResponse;

public interface AgentChatService {
    AgentChatResponse chat(AgentChatRequest request);
}
