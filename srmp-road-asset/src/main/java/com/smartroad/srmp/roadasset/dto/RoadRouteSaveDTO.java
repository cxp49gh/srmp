package com.smartroad.srmp.roadasset.dto;
import lombok.Data; import javax.validation.constraints.NotBlank; import java.math.BigDecimal;
@Data
public class RoadRouteSaveDTO {
    @NotBlank(message="路线编号不能为空") private String routeCode;
    @NotBlank(message="路线名称不能为空") private String routeName;
    @NotBlank(message="路线类型不能为空") private String routeType;
    private String adminGrade; private String technicalGrade; private BigDecimal startStake; private BigDecimal endStake; private BigDecimal lengthKm;
    private String adcode; private String manageOrgId; private String projectId; private String geomWkt; private String remark;
}
