package com.smartroad.srmp.agent.outline.dto;

import lombok.Data;

@Data
public class OutlineSyncRequest {
    private String collectionId;
    private Integer limit;
    private Boolean force;
}