package com.smartroad.srmp.disease.dto;

import lombok.Data;

@Data
public class DiseaseTypeSaveDTO {
    private String diseaseCode;
    private String diseaseName;
    private String diseaseCategory;
    private String measureUnit;
    private String relatedIndex;
    private Boolean severityEnabled;
    private Boolean enabled;
    private Integer sortNo;
    private String remark;
}
