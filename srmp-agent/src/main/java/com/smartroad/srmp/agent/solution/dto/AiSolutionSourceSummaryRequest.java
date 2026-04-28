package com.smartroad.srmp.agent.solution.dto;

import lombok.Data;

@Data
public class AiSolutionSourceSummaryRequest {
    private String sourceType;
    private String sourceTitle;
    private String sourceId;
    private String sourceUrl;
    private String contentExcerpt;
}
