package com.smartroad.srmp.disease.dto;

import com.smartroad.srmp.common.core.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class DiseaseQueryDTO extends PageQuery {
    private String taskId;
    private String routeCode;
    private String direction;
    private String unitId;
    private String diseaseCategory;
    private String diseaseType;
    private String severity;
    private String status;
    private BigDecimal startStake;
    private BigDecimal endStake;
}
