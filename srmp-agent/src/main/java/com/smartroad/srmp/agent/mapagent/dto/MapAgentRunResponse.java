package com.smartroad.srmp.agent.mapagent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class MapAgentRunResponse {
    private String answer;
    private String mode = "LANGGRAPH_MAP_AGENT";
    private String action;
    private String intent;
    private Map<String, Object> mapContext;
    private MapAgentActionResult actionResult = new MapAgentActionResult();
    private List<MapAgentSuggestedAction> suggestedActions = new ArrayList<>();
    private List<Map<String, Object>> toolResults = new ArrayList<>();
    private List<Map<String, Object>> knowledgeSources = new ArrayList<>();
    private List<Map<String, Object>> sources = new ArrayList<>();
    private Map<String, Object> answerMeta = new LinkedHashMap<>();
    private Map<String, Object> planExecution = new LinkedHashMap<>();
    private Map<String, Object> trace = new LinkedHashMap<>();
    private Map<String, Object> data = new LinkedHashMap<>();
}
