package com.smartroad.srmp.disease.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DiseaseRecordVO {
    private String id;
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
    private Boolean verified;
    private String verifiedBy;
    private LocalDateTime verifiedAt;
    private String geomGeoJson;
    private String remark;
    private LocalDateTime createdAt;
}
