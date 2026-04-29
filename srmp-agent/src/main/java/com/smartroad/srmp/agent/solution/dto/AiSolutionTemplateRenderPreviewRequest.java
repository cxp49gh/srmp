package com.smartroad.srmp.agent.solution.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AiSolutionTemplateRenderPreviewRequest {
    private String originType;
    private String objectType;
    private String solutionType;
    private Map<String, Object> variables;
}
