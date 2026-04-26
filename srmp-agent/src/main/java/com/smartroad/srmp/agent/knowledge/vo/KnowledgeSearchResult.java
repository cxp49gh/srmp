package com.smartroad.srmp.agent.knowledge.vo;

import lombok.Data;

@Data
public class KnowledgeSearchResult {
    private String documentId;
    private String chunkId;
    private String title;
    private String heading;
    private String content;
    private String sourceType;
    private String sourceUrl;
    private Double score;
}
