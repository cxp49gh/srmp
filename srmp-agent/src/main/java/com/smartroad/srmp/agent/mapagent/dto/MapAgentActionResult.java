package com.smartroad.srmp.agent.mapagent.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class MapAgentActionResult {
    private String type = "ANSWER";
    private String status = "SUCCESS";
    private String title;
    private String markdown;
    private Map<String, Object> objectSummary = new LinkedHashMap<>();
    private Map<String, Object> regionSummary = new LinkedHashMap<>();
    private Map<String, Object> routeSummary = new LinkedHashMap<>();
    private Map<String, Object> templateMeta = new LinkedHashMap<>();
    private Map<String, Object> qualityCheck = new LinkedHashMap<>();
    private Map<String, Object> draftTask;
    private String errorMessage;
}
