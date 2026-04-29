package com.smartroad.srmp.agent.solution.dto;

import lombok.Data;

@Data
public class AiSolutionTemplateVersionRequest {
    private String version;
    private String content;
    private String changeNote;
}
