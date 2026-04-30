package com.smartroad.srmp.agent.tool;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class AiToolRegistry {
    private final Map<String, AiTool> tools = new LinkedHashMap<>();

    public AiToolRegistry(List<AiTool> toolList) {
        if (toolList != null) {
            for (AiTool tool : toolList) {
                register(tool);
            }
        }
    }

    public void register(AiTool tool) {
        if (tool != null) {
            tools.put(tool.name(), tool);
        }
    }

    public AiTool get(String name) {
        return tools.get(name);
    }

    public List<AiTool> list() {
        return new ArrayList<>(tools.values());
    }
}
