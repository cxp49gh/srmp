package com.smartroad.srmp.roadasset.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class DataMgmtProjectSaveDTO {
    @NotBlank(message = "项目名称不能为空")
    private String name;
    private String remark;
}
