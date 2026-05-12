package com.smartroad.srmp.disease.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class DiseaseSaveDTO {
    private String taskId;
    private String routeId;
    private String sectionId;
    private String unitId;
    private String routeCode;
    private String direction;
    private Integer laneNo;
    private BigDecimal startStake;
    private BigDecimal endStake;
    private String diseaseCategory;
    private String diseaseType;
    private String diseaseName;
    private String severity;
    private BigDecimal quantity;
    private String measureUnit;
    private BigDecimal damageArea;
    private BigDecimal damageLength;
    private BigDecimal damageWidth;
    private BigDecimal damageDepth;
    private String source;
    private BigDecimal confidence;
    private String status;
    private String geomWkt;
    private String remark;
    private String projectId;
}
