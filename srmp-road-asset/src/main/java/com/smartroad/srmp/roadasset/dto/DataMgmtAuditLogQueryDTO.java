package com.smartroad.srmp.roadasset.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DataMgmtAuditLogQueryDTO {
    private Integer pageNo = 1;
    private Integer pageSize = 20;
    private String projectId;
    private String operationType;
    private String operator;
    private LocalDateTime operatedFrom;
    private LocalDateTime operatedTo;
}
