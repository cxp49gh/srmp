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
    /** 数据管理项目：按 assessment_result.route_id 对应路线的 project_id 过滤 */
    private String projectId;
    /** 路段包级别：GIS 专题与统计按评定对象粒度过滤 {@code LINE|LEDGER|KM|HM} → object_type */
    private String sectionTier;
}
