package com.smartroad.srmp.disease.dto;

import lombok.Data;

@Data
public class DiseaseReviewDTO {
    private String id;
    private Boolean verified;
    private String status;
    private String remark;
}
