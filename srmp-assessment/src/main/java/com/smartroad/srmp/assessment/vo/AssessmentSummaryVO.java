package com.smartroad.srmp.assessment.vo;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class AssessmentSummaryVO {
    private Long totalCount;
    private Long excellentCount;
    private Long goodCount;
    private Long mediumCount;
    private Long poorCount;
    private Long badCount;
    private BigDecimal avgMqi;
    private BigDecimal avgPqi;
    private BigDecimal avgPci;
}
