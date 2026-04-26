package com.smartroad.srmp.agent.dto;

import lombok.Data;

@Data
public class AgentAnalysisRequest {
    private String routeCode;
    private Integer year;
    private String indexCode;
    private String grade;
    private String diseaseType;
    private String severity;
    private String taskId;
}
