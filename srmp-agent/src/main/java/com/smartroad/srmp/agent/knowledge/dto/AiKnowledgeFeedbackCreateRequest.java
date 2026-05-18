package com.smartroad.srmp.agent.knowledge.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AiKnowledgeFeedbackCreateRequest {
    /** MISSING_KNOWLEDGE | SOURCE_INACCURATE */
    private String feedbackType;
    private String question;
    private String remark;
    private String userId;
    private Map<String, Object> businessContext;
    private List<Map<String, Object>> citedSources;
}
