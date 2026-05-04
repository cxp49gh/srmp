package com.smartroad.srmp.agent.mapagent.service.impl;

import com.smartroad.srmp.agent.mapagent.dto.MapAiAgentRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAiAgentResponse;
import com.smartroad.srmp.agent.mapagent.service.MapAiAgentService;
import com.smartroad.srmp.agent.orchestrator.AgentOrchestratorRouter;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Phase50：一张图 AI 服务门面。
 *
 * 具体执行链路由 AgentOrchestratorRouter 按配置选择：
 * - native：沿用当前 Java 原生工具/RAG/LLM 链路，默认值，保持兼容；
 * - langgraph：调用外部 LangGraph 编排服务，失败后可自动回退 native。
 */
@Service
public class MapAiAgentServiceImpl implements MapAiAgentService {

    @Resource
    private AgentOrchestratorRouter agentOrchestratorRouter;

    @Override
    public MapAiAgentResponse chat(MapAiAgentRequest request) {
        return agentOrchestratorRouter.chat(request);
    }
}
