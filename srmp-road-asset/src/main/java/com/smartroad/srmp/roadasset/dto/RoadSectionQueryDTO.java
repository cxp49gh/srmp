package com.smartroad.srmp.roadasset.dto;
import com.smartroad.srmp.common.core.PageQuery;
import lombok.Data; import lombok.EqualsAndHashCode; import java.math.BigDecimal;
@Data @EqualsAndHashCode(callSuper = true)
public class RoadSectionQueryDTO extends PageQuery {
    private String routeCode; private String sectionCode; private String sectionName; private String direction; private BigDecimal stake; private String adcode;
    private String projectId;
}
