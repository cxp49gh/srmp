package com.smartroad.srmp.roadasset.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class DataMgmtClearDTO {

    /**
     * {@link com.smartroad.srmp.roadasset.datamgmt.DataMgmtClearScope#ALL}、
     * {@link com.smartroad.srmp.roadasset.datamgmt.DataImportType} 中三类之一。
     */
    @NotBlank(message = "清除范围不能为空")
    private String scope;
}
