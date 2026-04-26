package com.smartroad.srmp.roadasset.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.smartroad.srmp.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("road_section")
public class RoadSection extends BaseEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String routeId;
    private String routeCode;
    private String sectionCode;
    private String sectionName;
    private String direction;
    private BigDecimal startStake;
    private BigDecimal endStake;
    private BigDecimal lengthKm;
    private String pavementType;
    private String technicalGrade;
    private Integer laneCount;
    private BigDecimal roadWidth;
    private String trafficVolumeLevel;
    private String adcode;
    private String manageOrgId;
    private String remark;
    @TableField(exist = false)
    private String geomWkt;
    @TableField(exist = false)
    private String geomGeoJson;
}
