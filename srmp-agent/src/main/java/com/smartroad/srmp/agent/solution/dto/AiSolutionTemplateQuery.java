package com.smartroad.srmp.agent.solution.dto;

import lombok.Data;

@Data
public class AiSolutionTemplateQuery {
    private String keyword;
    private String solutionType;
    private String status;
    private Integer limit;
}
