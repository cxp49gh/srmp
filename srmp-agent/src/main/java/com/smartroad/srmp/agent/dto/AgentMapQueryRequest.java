package com.smartroad.srmp.agent.dto;

import lombok.Data;

@Data
public class AgentMapQueryRequest {
    private String message;
    private String routeCode;
    private Integer year;
    private String indexCode;
    private String grade;
    private String diseaseType;
    private String severity;
}
