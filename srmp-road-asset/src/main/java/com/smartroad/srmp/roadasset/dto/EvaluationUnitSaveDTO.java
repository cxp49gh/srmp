package com.smartroad.srmp.roadasset.dto;
import lombok.Data; import javax.validation.constraints.*; import java.math.BigDecimal;
@Data
public class EvaluationUnitSaveDTO {
    private String routeId; private String sectionId;
    @NotBlank(message="路线编号不能为空") private String routeCode;
    @NotBlank(message="评定单元编码不能为空") private String unitCode;
    private String direction = "BOTH"; private Integer laneNo;
    @NotNull(message="起点桩号不能为空") private BigDecimal startStake;
    @NotNull(message="终点桩号不能为空") private BigDecimal endStake;
    private Integer lengthM = 1000; private String pavementType; private String technicalGrade; private BigDecimal roadWidth;
    private String adcode; private String manageOrgId; private String geomWkt; private String centerPointWkt;
    /** 数据管理项目冗余 */
    private String projectId;
}
