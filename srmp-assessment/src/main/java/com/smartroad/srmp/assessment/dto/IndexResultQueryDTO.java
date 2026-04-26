package com.smartroad.srmp.assessment.dto;

import com.smartroad.srmp.common.core.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class IndexResultQueryDTO extends PageQuery {
    private String taskId;
    private String assessmentId;
    private String unitId;
    private String routeCode;
    private Integer year;
    private String indexCode;
    private String grade;
}
