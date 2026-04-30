package com.smartroad.srmp.agent.knowledge.vo;

import lombok.Data;

import java.util.Map;

@Data
public class AiKnowledgeSearchHit {
    private String chunkId;
    private String documentId;
    private String title;
    private String sectionTitle;
    private String sourceType;
    private String sourceId;
    private String content;
    private Double score;
    private Map<String, Object> metadata;
}
