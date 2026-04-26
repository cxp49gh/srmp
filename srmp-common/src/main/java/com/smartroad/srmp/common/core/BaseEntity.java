package com.smartroad.srmp.common.core;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BaseEntity {
    private String id;
    private String tenantId;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean deleted;
}
