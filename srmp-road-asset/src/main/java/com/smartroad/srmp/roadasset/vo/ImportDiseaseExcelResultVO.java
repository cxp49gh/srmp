package com.smartroad.srmp.roadasset.vo;

import lombok.Data;

@Data
public class ImportDiseaseExcelResultVO {
    /** 成功写入的条数 */
    private int insertedCount;
    /** 因路线编码在路网中不存在而跳过的行数 */
    private int skippedMissingRouteCount;
}
