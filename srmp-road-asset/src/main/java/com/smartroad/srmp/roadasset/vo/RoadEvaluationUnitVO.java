package com.smartroad.srmp.roadasset.vo;
import lombok.Data; import java.math.BigDecimal; import java.time.LocalDateTime;
@Data
public class RoadEvaluationUnitVO {
    private String id; private String routeId; private String sectionId; private String routeCode; private String unitCode; private String direction;
    private Integer laneNo; private BigDecimal startStake; private BigDecimal endStake; private Integer lengthM; private String pavementType; private String technicalGrade;
    private BigDecimal roadWidth; private String adcode; private String manageOrgId; private String geomGeoJson; private String centerPointGeoJson; private LocalDateTime createdAt;
}
