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
    /** GeoJSON 几何/框选区域。用于 LangGraph Runtime 识别 POLYGON/BOX 等区域上下文。 */
    private Map<String, Object> geometry;
    private List<String> selectedLayers;
    private List<Map<String, Object>> nearbyObjects;
    private String userQuestion;
    private Map<String, Object> extra;
}
