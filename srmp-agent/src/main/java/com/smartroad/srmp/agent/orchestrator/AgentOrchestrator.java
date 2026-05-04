package com.smartroad.srmp.agent.orchestrator;

import com.smartroad.srmp.agent.mapagent.dto.MapAiAgentRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAiAgentResponse;

/**
 * Phase50：AI Agent 编排器统一入口。
 *
 * 该接口有意保持窄接口，避免当前 Java 8 / Spring Boot 2.7 主工程
 * 被 LangChain/LangGraph 运行时依赖直接牵引升级。
 */
public interface AgentOrchestrator {

    /**
     * 编排器标识，例如 native、langgraph。
     */
    String provider();

    /**
     * 执行一张图 AI Agent 对话。
     */
    MapAiAgentResponse chat(MapAiAgentRequest request);
}
