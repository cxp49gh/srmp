package com.smartroad.srmp.roadasset.dto;

import lombok.Data;

@Data
public class DataMgmtProjectQueryDTO {
    private Integer pageNo = 1;
    private Integer pageSize = 20;
    private String nameKeyword;
}
