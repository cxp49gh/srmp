package com.smartroad.srmp.agent.solution.dto;

import lombok.Data;

@Data
public class AiSolutionTemplateQuery {
    private String keyword;
    private String solutionType;
    private String status;
    private String originType;
    private String objectType;
    private Boolean isDefault;
    private Integer limit;
}
