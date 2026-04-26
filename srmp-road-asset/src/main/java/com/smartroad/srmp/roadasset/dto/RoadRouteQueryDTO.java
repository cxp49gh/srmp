package com.smartroad.srmp.roadasset.dto;
import com.smartroad.srmp.common.core.PageQuery;
import lombok.Data; import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true)
public class RoadRouteQueryDTO extends PageQuery {
    private String routeCode; private String routeName; private String routeType; private String technicalGrade; private String adcode;
}
