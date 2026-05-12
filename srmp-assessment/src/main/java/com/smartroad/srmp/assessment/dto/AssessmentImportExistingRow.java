package com.smartroad.srmp.assessment.dto;

import lombok.Data;

@Data
public class AssessmentImportExistingRow {
    private String id;
    private String objectType;
    private String objectId;
    private Integer year;
    private String standardCode;
}
