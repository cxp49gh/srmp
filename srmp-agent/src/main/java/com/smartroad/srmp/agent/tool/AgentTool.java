package com.smartroad.srmp.agent.tool;

import java.util.Map;

public interface AgentTool {
    String name();
    String description();
    Object execute(Map<String, Object> params);
}
