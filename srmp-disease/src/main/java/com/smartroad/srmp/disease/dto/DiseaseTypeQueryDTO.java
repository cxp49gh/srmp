package com.smartroad.srmp.disease.dto;

import com.smartroad.srmp.common.core.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DiseaseTypeQueryDTO extends PageQuery {
    private String diseaseCode;
    private String diseaseName;
    private String diseaseCategory;
    private String relatedIndex;
    private Boolean enabled;
}
