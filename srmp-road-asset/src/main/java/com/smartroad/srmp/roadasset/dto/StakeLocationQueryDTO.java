package com.smartroad.srmp.roadasset.dto;
import lombok.Data; import javax.validation.constraints.*; import java.math.BigDecimal;
@Data
public class StakeLocationQueryDTO {
    @NotBlank(message="路线编号不能为空") private String routeCode;
    private String direction = "BOTH";
    @NotNull(message="桩号不能为空") private BigDecimal stake;
}
