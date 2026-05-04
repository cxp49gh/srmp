package com.smartroad.srmp.agent.orchestrator;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Phase50：AI 编排配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "srmp.ai.orchestrator")
public class AgentOrchestratorProperties {

    /**
     * native：默认原生 Java 链路；langgraph：远程 LangGraph 编排服务。
     */
    private String provider = "native";

    /**
     * 远程 LangGraph 服务地址，例如 http://127.0.0.1:18080。
     */
    private String langgraphUrl = "http://127.0.0.1:18080";

    /**
     * 远程 LangGraph 一张图 Agent 入口路径。
     */
    private String langgraphEndpointPath = "/api/srmp/langgraph/map-agent/chat";

    /**
     * 远程 LangGraph 健康检查路径。
     */
    private String langgraphHealthPath = "/health";

    /**
     * 远程 LangGraph 就绪检查路径。该接口会检查 Java Tool Gateway 是否可达。
     */
    private String langgraphReadyPath = "/ready";

    /**
     * 连接超时，毫秒。
     */
    private Integer connectTimeoutMs = 10000;

    /**
     * 读取超时，毫秒。复杂方案生成建议 180~300 秒。
     */
    private Integer readTimeoutMs = 300000;

    /**
     * 远程编排失败时是否回退 native。
     */
    private Boolean fallbackToNative = true;

    /**
     * Tool Gateway 是否允许执行写工具。第一版建议关闭，写操作走人工确认。
     */
    private Boolean allowWriteTools = false;
}
