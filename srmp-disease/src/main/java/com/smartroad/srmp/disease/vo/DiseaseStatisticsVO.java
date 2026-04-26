package com.smartroad.srmp.disease.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class DiseaseStatisticsVO {
    private Long totalCount;
    private Long heavyCount;
    private Long mediumCount;
    private Long lightCount;
    private BigDecimal totalArea;
    private BigDecimal totalLength;
}
