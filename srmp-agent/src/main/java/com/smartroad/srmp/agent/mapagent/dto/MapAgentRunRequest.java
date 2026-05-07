package com.smartroad.srmp.agent.mapagent.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class MapAgentRunRequest {
    private String message;
    private String action = "CHAT";
    private MapAiContext mapContext;
    private Map<String, Object> actionInput = new LinkedHashMap<>();
    private Map<String, Object> options = new LinkedHashMap<>();
}
