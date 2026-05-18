package com.smartroad.srmp.agent.outline.dto;

import lombok.Data;

import java.util.List;

@Data
public class OutlineAutoSyncConfigRequest {
    private String id;
    private String name;
    private Boolean enabled;
    private String collectionId;
    /** COLLECTION | SINGLE_DOCUMENT | MULTIPLE_DOCUMENTS */
    private String syncScope;
    private List<String> documentIds;
    private Integer intervalMinutes;
    private Boolean force;
    private Boolean cleanupMissing;
    private Boolean vectorizeAfterSync;
    private Boolean vectorForce;
    private Integer vectorLimit;
    private Boolean webhookEnabled;
    private String webhookSecret;
}
