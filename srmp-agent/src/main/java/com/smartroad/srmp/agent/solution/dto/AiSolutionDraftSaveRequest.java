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
    private String originType;
    private String objectType;
    private String objectId;
    private Map<String, Object> mapObject;
    private Map<String, Object> objectSummary;
    private Map<String, Object> regionSummary;
    private Map<String, Object> qualityCheck;
    private Map<String, Object> trace;
    private List<AiSolutionSourceSummaryRequest> sourceSummaries;
    private String templateId;
    private String templateVersion;
    private String templateName;
    private Map<String, Object> options;
    private Map<String, Object> requestContext;
}
