package com.smartroad.srmp.assessment.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class IndexResultSaveDTO {
    private String taskId;
    private String assessmentId;
    private String unitId;
    private String routeId;
    private String sectionId;
    private String routeCode;
    private String direction;
    private BigDecimal startStake;
    private BigDecimal endStake;
    private Integer year;
    private String indexCode;
    private String indexName;
    private BigDecimal indexValue;
    private String grade;
    private String rawMetrics;
    private String calculationVersion;
}
