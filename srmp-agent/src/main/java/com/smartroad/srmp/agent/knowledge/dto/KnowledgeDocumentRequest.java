package com.smartroad.srmp.agent.knowledge.dto;

import lombok.Data;

@Data
public class KnowledgeDocumentRequest {
    private String title;
    private String content;
    private String sourceType;
    private String docType;
    private String category;
    private String url;
}
