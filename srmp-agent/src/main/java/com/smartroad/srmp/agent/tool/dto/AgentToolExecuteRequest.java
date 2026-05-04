package com.smartroad.srmp.agent.tool.dto;

import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import lombok.Data;

import java.util.Map;

/**
 * Phase50.3：外部编排服务调用 Java AiTool 的统一请求体。
 */
@Data
public class AgentToolExecuteRequest {
    private String toolName;
    private String tenantId;
    private String traceId;
    private String userQuestion;
    private MapAiContext mapContext;
    private Map<String, Object> options;
    private Map<String, Object> args;
}
