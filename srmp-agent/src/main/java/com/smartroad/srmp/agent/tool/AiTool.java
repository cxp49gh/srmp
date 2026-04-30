package com.smartroad.srmp.agent.tool;

import java.util.Map;

public interface AiTool {
    String name();
    String description();
    boolean supports(AiToolContext context);
    AiToolResult execute(AiToolContext context, Map<String, Object> args);
}
