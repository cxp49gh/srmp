package com.smartroad.srmp.assessment.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class IndexResultVO {
    private String id;
    private String taskId;
    private String assessmentId;
    private String unitId;
    private String routeCode;
    private Integer year;
    private String indexCode;
    private String indexName;
    private BigDecimal indexValue;
    private String grade;
    private String rawMetrics;
    private String calculationVersion;
    private LocalDateTime calculatedAt;
}
