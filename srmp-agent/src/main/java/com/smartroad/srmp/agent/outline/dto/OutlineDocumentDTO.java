package com.smartroad.srmp.agent.outline.dto;

import lombok.Data;

@Data
public class OutlineDocumentDTO {
    private String id;
    private String collectionId;
    private String title;
    private String text;
    private String url;
    private String updatedAt;
}