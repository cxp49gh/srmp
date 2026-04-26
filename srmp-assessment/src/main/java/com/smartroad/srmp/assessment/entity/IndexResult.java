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
@TableName("index_result")
public class IndexResult extends BaseEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
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
    private LocalDateTime calculatedAt;
}
