package com.smartroad.srmp.roadasset.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DataImportRecordVO {
    private String id;
    private String projectId;
    private String importType;
    private String fileName;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private String status;
    private String message;
    private String resultSummary;
}
