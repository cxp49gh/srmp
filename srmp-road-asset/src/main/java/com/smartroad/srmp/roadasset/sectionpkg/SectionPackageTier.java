package com.smartroad.srmp.roadasset.sectionpkg;

/**
 * 路段包 Shapefile 四级：线路 / 台账 / 公里 / 百米。
 */
public enum SectionPackageTier {
    LINE,
    LEDGER,
    KM,
    HM,
    /** 无法从路径/文件名识别的级别 */
    UNKNOWN
}
