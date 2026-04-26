package com.smartroad.srmp.disease.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DiseaseTypeVO {
    private String id;
    private String diseaseCode;
    private String diseaseName;
    private String diseaseCategory;
    private String measureUnit;
    private String relatedIndex;
    private Boolean severityEnabled;
    private Boolean enabled;
    private Integer sortNo;
    private String remark;
    private LocalDateTime createdAt;
}
