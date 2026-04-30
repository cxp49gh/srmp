package com.smartroad.srmp.agent.mapagent.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class MapAiContext {
    private String tenantId;
    private String mode;
    private String routeCode;
    private Integer year;
    private Map<String, Object> mapObject;
    private Map<String, Object> regionSummary;
    private Map<String, Object> viewport;
    private List<String> selectedLayers;
    private List<Map<String, Object>> nearbyObjects;
    private String userQuestion;
    private Map<String, Object> extra;
}
