package com.smartroad.srmp.agent.mapagent.dto;

import lombok.Data;

import java.util.Map;

@Data
public class MapAiAgentRequest {
    private String message;
    private MapAiContext mapContext;
    private Map<String, Object> context;
    private Map<String, Object> mapObject;
    private Map<String, Object> options;
}
