package com.smartroad.srmp.roadasset.dto;
import lombok.Data; import javax.validation.constraints.*; import java.math.BigDecimal;
@Data
public class RoadSectionSaveDTO {
    private String routeId;
    @NotBlank(message="路线编号不能为空") private String routeCode;
    @NotBlank(message="路段编码不能为空") private String sectionCode;
    private String sectionName; private String direction = "BOTH";
    @NotNull(message="起点桩号不能为空") private BigDecimal startStake;
    @NotNull(message="终点桩号不能为空") private BigDecimal endStake;
    private BigDecimal lengthKm; private String pavementType; private String technicalGrade; private Integer laneCount; private BigDecimal roadWidth;
    private String trafficVolumeLevel; private String adcode; private String manageOrgId; private String projectId; private String geomWkt; private String remark;
}
