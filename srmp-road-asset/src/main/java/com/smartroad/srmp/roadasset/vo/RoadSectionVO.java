package com.smartroad.srmp.roadasset.vo;
import lombok.Data; import java.math.BigDecimal; import java.time.LocalDateTime;
@Data
public class RoadSectionVO {
    private String id; private String routeId; private String routeCode; private String sectionCode; private String sectionName; private String direction;
    private BigDecimal startStake; private BigDecimal endStake; private BigDecimal lengthKm; private String pavementType; private String technicalGrade;
    private Integer laneCount; private BigDecimal roadWidth; private String trafficVolumeLevel; private String adcode; private String manageOrgId; private String projectId;
    private String geomGeoJson; private String remark; private LocalDateTime createdAt;
}
