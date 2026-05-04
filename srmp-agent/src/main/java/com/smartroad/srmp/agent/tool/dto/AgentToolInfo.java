package com.smartroad.srmp.agent.tool.dto;

import lombok.Data;

/**
 * Phase50.3：暴露给外部 LangGraph Runtime 的工具元信息。
 */
@Data
public class AgentToolInfo {
    private String name;
    private String description;
    private Boolean writeTool;

    public static AgentToolInfo of(String name, String description, boolean writeTool) {
        AgentToolInfo info = new AgentToolInfo();
        info.setName(name);
        info.setDescription(description);
        info.setWriteTool(writeTool);
        return info;
    }

    public static AgentToolInfo of(String name, String description) {
        return of(name, description, false);
    }
}
