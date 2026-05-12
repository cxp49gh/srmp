package com.smartroad.srmp.roadasset.dto;
import com.smartroad.srmp.common.core.PageQuery;
import lombok.Data; import lombok.EqualsAndHashCode; import java.math.BigDecimal;
@Data @EqualsAndHashCode(callSuper = true)
public class EvaluationUnitQueryDTO extends PageQuery {
    private String routeCode; private String unitCode; private String direction; private Integer laneNo; private BigDecimal stake; private String adcode;
    /** 数据管理项目：按所属路线的 project_id 过滤 */
    private String projectId;
}
