package com.smartroad.srmp.roadasset.vo;
import lombok.Data; import java.math.BigDecimal;
@Data
public class StakeLocationVO {
    private String unitId; private String unitCode; private String routeCode; private String direction; private BigDecimal startStake; private BigDecimal endStake;
    private String centerPointGeoJson; private String geomGeoJson;
}
