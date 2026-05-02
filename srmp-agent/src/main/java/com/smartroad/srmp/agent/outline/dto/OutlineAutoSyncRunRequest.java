package com.smartroad.srmp.agent.outline.dto;

import lombok.Data;

@Data
public class OutlineAutoSyncRunRequest {
    private String triggerType;
    private String outlineEvent;
    private String outlineDocumentId;
    private String outlineCollectionId;
    private Boolean force;
    private Boolean vectorizeAfterSync;
}
