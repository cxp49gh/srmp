package com.smartroad.srmp.roadasset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartroad.srmp.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("road_section_hm")
public class RoadSectionHm extends BaseEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String routeId;
    private String routeCode;
    private String lineId;
    private String kmId;
    private String hmCode;
    private String direction;
    private BigDecimal startStake;
    private BigDecimal endStake;
    private Integer lengthM;
    private String label;
    private String projectId;
    private String remark;
    @TableField(exist = false)
    private String geomWkt;
}
