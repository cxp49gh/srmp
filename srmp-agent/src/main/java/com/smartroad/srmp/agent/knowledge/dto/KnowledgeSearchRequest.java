package com.smartroad.srmp.agent.knowledge.dto;

import lombok.Data;

@Data
public class KnowledgeSearchRequest {
    private String query;
    private String category;
    private String sourceType;
    private Integer topK;
}
