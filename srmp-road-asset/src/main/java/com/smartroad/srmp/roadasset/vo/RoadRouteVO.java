package com.smartroad.srmp.roadasset.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class RoadRouteVO {
    private String id;
    private String routeCode;
    private String routeName;
    private String routeType;
    private String adminGrade;
    private String technicalGrade;
    private BigDecimal startStake;
    private BigDecimal endStake;
    private BigDecimal lengthKm;
    private String adcode;
    private String manageOrgId;
    private String projectId;
    private String geomGeoJson;
    private String remark;
    private LocalDateTime createdAt;
}
