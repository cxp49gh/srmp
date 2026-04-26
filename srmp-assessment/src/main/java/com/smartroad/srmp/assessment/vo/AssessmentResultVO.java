package com.smartroad.srmp.assessment.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AssessmentResultVO {
    private String id;
    private String taskId;
    private String objectType;
    private String objectId;
    private String routeId;
    private String sectionId;
    private String unitId;
    private String routeCode;
    private String direction;
    private BigDecimal startStake;
    private BigDecimal endStake;
    private Integer year;
    private String standardCode;
    private BigDecimal mqi;
    private BigDecimal sci;
    private BigDecimal pqi;
    private BigDecimal bci;
    private BigDecimal tci;
    private BigDecimal pci;
    private BigDecimal rqi;
    private BigDecimal rdi;
    private BigDecimal pbi;
    private BigDecimal pwi;
    private BigDecimal sri;
    private BigDecimal pssi;
    private String grade;
    private String zeroReason;
    private LocalDateTime assessedAt;
    private String geomGeoJson;
    private LocalDateTime createdAt;
}
