package com.smartroad.srmp.agent.solution.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AiSolutionDraftSaveRequest {
    private String solutionType;
    private String title;
    private String markdown;
    private String routeCode;
    private Integer year;
    private Map<String, Object> mapObject;
    private Map<String, Object> objectSummary;
    private Map<String, Object> qualityCheck;
    private List<AiSolutionSourceSummaryRequest> sourceSummaries;
    private String templateId;
    private String templateVersion;
    private String templateName;
    private Map<String, Object> options;
    private Map<String, Object> requestContext;
}
