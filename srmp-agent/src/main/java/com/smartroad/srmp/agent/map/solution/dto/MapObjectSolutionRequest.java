package com.smartroad.srmp.agent.map.solution.dto;

import lombok.Data;

import java.util.Map;

@Data
public class MapObjectSolutionRequest {
    private String tenantId;
    private String objectType;
    private String objectId;
    private String routeCode;
    private Integer year;
    private String solutionType;
    private Map<String, Object> mapObject;
    private Map<String, Object> businessEvidence;
    private Map<String, Object> options;
}
