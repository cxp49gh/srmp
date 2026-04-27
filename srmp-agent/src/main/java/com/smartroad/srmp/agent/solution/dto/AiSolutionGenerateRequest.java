package com.smartroad.srmp.agent.solution.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AiSolutionGenerateRequest {
    private String solutionType;
    private String routeCode;
    private Integer year;
    private String templateCode;
    private String templateId;
    private Map<String, Object> options;
}
