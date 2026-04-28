package com.smartroad.srmp.agent.solution.dto;

import lombok.Data;

@Data
public class AiSolutionTaskQuery {
    private String solutionType;
    private String routeCode;
    private Integer year;
    private String status;
    private String draftStatus;
    private Integer limit;
}
