package com.smartroad.srmp.agent.map.solution.dto;

import lombok.Data;

import java.util.Map;

@Data
public class MapObjectSolutionResponse {
    private String solutionType;
    private String title;
    private String markdown;
    private Map<String, Object> objectSummary;
    private MapObjectSolutionQualityCheck qualityCheck;
    private Map<String, Object> trace;
}
