package com.smartroad.srmp.agent.solution.dto;

import lombok.Data;

@Data
public class AiSolutionDraftUpdateRequest {
    private String title;
    private String markdown;
    private String changeNote;
}
