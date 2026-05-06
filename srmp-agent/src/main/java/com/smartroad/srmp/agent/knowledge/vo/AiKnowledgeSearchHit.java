package com.smartroad.srmp.agent.knowledge.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
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
