package com.smartroad.srmp.gis.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class MapRegionSolutionResponse {
    private String solutionType;
    private String title;
    private String markdown;
    private Map<String, Object> regionSummary;
    private Map<String, Object> qualityCheck;
    private Map<String, Object> templateMeta;
    private List<Map<String, Object>> sourceSummaries;
    private Map<String, Object> trace;
}
