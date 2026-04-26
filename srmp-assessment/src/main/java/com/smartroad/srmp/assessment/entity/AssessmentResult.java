package com.smartroad.srmp.assessment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartroad.srmp.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("assessment_result")
public class AssessmentResult extends BaseEntity {
    @TableId(type = IdType.ASSIGN_UUID)
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
}
