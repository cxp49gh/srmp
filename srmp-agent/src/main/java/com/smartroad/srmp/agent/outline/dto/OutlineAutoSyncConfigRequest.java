package com.smartroad.srmp.agent.outline.dto;

import lombok.Data;

@Data
public class OutlineAutoSyncConfigRequest {
    private String id;
    private String name;
    private Boolean enabled;
    private String collectionId;
    private Integer intervalMinutes;
    private Boolean force;
    private Boolean cleanupMissing;
    private Boolean vectorizeAfterSync;
    private Boolean vectorForce;
    private Integer vectorLimit;
    private Boolean webhookEnabled;
    private String webhookSecret;
}
