package com.smartroad.srmp.agent.mapagent.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class MapAgentSuggestedAction {
    private String action;
    private String label;
    private Boolean requiresConfirmation = false;
    private Boolean disabled = false;
    private String reason;
    private Map<String, Object> payload = new LinkedHashMap<>();
}
