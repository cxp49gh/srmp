package com.smartroad.srmp.roadasset.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ImportSectionPackageResultVO {
    /** 线路级写入条数 */
    private int lineCount;
    /** 台账级写入条数 */
    private int ledgerCount;
    /** 公里级写入条数 */
    private int kmCount;
    /** 百米级写入条数 */
    private int hmCount;
    /** 与 lineCount 一致，兼容旧前端 */
    private int routeSectionCount;
    /** 与 ledgerCount 一致，兼容旧前端 */
    private int evaluationUnitCount;
    private int assessmentRowCount;
    private List<String> ignoredShapefileGroups = new ArrayList<>();
    private long durationMs;
    private List<String> warnings = new ArrayList<>();
}
