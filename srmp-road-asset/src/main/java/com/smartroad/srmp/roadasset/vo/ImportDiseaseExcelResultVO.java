package com.smartroad.srmp.roadasset.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ImportDiseaseExcelResultVO {
    /** 成功写入的条数 */
    private int insertedCount;
    /**
     * 路线编码在路网中未找到、但仍写入病害的行数（route_id 为空，route_code 已按规则去上下行后缀）。
     * 旧版语义为「跳过不写库」；现已改为入库，详见 warnings 与日志。
     */
    private int skippedMissingRouteCount;
    /** 汇总提示（如未匹配路网的路线编码列表） */
    private List<String> warnings = new ArrayList<>();
}
