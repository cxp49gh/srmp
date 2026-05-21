package com.smartroad.srmp.roadasset.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DataImportRecordGlobalQueryDTO {
    private Integer pageNo = 1;
    private Integer pageSize = 20;
    private String projectId;
    private String importType;
    private String status;
    private LocalDateTime startedFrom;
    private LocalDateTime startedTo;
    /** 对应导入记录 created_by */
    private String uploadedBy;
}
