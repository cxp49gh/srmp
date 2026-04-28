package com.smartroad.srmp.gis.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class MapRegionSolutionRequest {
    private String solutionType;
    private Map<String, Object> geometry;
    private Map<String, Object> query;
    private List<String> layers;
    private Map<String, Object> options;
}
