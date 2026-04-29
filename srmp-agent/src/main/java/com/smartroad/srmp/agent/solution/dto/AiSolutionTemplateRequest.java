package com.smartroad.srmp.agent.solution.dto;

import lombok.Data;

@Data
public class AiSolutionTemplateRequest {
    private String templateCode;
    private String templateName;
    private String solutionType;
    private String sourceType;
    private String sourceId;
    private String category;
    private String version;
    private String content;
    private String sourceUrl;
    private String originType;
    private String objectType;
    private Boolean isDefault;
    private Integer priority;
    private String changeNote;
}
