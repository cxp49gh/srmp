package com.smartroad.srmp.agent.solution.dto;

import lombok.Data;

@Data
public class AiSolutionTemplateMatchPreviewRequest {
    private String originType;
    private String objectType;
    private String solutionType;
    private String templateId;
    private String templateCode;
}
