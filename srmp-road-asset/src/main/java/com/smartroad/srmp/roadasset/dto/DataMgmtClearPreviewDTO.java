package com.smartroad.srmp.roadasset.dto;

import lombok.Data;

@Data
public class DataMgmtClearPreviewDTO {
    /** 可选：预览指定 scope 将清除的数据量；为空则预览 ALL */
    private String scope;
}
