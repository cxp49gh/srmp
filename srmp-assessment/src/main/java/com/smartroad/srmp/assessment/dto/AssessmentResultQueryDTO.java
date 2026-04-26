package com.smartroad.srmp.assessment.dto;

import com.smartroad.srmp.common.core.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class AssessmentResultQueryDTO extends PageQuery {
    private String taskId;
    private String objectType;
    private String objectId;
    private String routeCode;
    private String direction;
    private String unitId;
    private Integer year;
    private String grade;
    private String indexCode;
    private BigDecimal startStake;
    private BigDecimal endStake;
}
