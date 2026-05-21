package com.smartroad.srmp.roadasset.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DataMgmtAuditLogVO {
    private String id;
    private String projectId;
    private String projectName;
    private String operationType;
    private String operator;
    private LocalDateTime operatedAt;
    private String result;
    private String reason;
    private String snapshotBefore;
    private String snapshotAfter;
}
