package com.smartroad.srmp.roadasset.vo;

import lombok.Data;

/** 本次清除操作物理删除的行数（按租户 + 项目汇总）。 */
@Data
public class DataMgmtClearResultVO {
    private int routes;
    private int sections;
    private int diseaseRecords;
    private int importRecords;
}
