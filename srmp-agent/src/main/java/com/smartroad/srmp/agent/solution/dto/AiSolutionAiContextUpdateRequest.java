package com.smartroad.srmp.agent.solution.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class AiSolutionAiContextUpdateRequest {
    private String aiTraceId;
    private String aiAnswer;
    private List<Map<String, Object>> aiSources;
    private List<Map<String, Object>> aiToolResults;
    private Map<String, Object> aiEvidence;
    private Map<String, Object> aiContext;
    private String generationMode;
}
