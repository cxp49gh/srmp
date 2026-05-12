package com.smartroad.srmp.assessment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentImportNaturalKey {
    private String objectType;
    private String objectId;
    private Integer year;
    private String standardCode;
}
