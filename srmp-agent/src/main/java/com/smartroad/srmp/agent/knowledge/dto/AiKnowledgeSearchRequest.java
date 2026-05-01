package com.smartroad.srmp.agent.knowledge.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AiKnowledgeSearchRequest {
    private String tenantId;

    /**
     * 原始 query 或有效 query。
     */
    private String query;

    /**
     * Phase37.4：原始用户问题。
     */
    private String originalQuery;

    /**
     * Phase37.4：结合地图上下文改写后的检索 query。
     */
    private String rewrittenQuery;

    private Integer topK;
    private Map<String, Object> filters;
    private List<String> sourceTypes;
}
