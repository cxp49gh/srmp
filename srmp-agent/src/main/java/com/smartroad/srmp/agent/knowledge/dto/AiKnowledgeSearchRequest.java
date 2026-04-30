package com.smartroad.srmp.agent.knowledge.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AiKnowledgeSearchRequest {
    private String tenantId;
    private String query;
    private Integer topK;
    private Map<String, Object> filters;
    private List<String> sourceTypes;
}
