package com.smartroad.srmp.roadasset.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.smartroad.srmp.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("road_route")
public class RoadRoute extends BaseEntity {
    @TableId(type = IdType.ASSIGN_UUID)
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
    private String remark;
    @TableField(exist = false)
    private String geomWkt;
    @TableField(exist = false)
    private String geomGeoJson;
}
