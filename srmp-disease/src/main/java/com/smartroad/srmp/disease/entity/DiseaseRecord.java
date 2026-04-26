package com.smartroad.srmp.disease.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartroad.srmp.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("disease_record")
public class DiseaseRecord extends BaseEntity {
    @TableId(type = IdType.ASSIGN_UUID)
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
    private String remark;

    @TableField(exist = false)
    private String geomWkt;

    @TableField(exist = false)
    private String geomGeoJson;
}
