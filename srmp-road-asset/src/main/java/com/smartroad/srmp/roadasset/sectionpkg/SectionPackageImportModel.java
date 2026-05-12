package com.smartroad.srmp.roadasset.sectionpkg;

import com.smartroad.srmp.roadasset.dto.RouteNetworkMatch;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 四级路段导入共用的传输模型：同结构写入线路/台账/公里/百米，与路网仅通过 {@link #routeId}、{@link #routeCode} 关联。
 */
@Data
@AllArgsConstructor
public class SectionPackageImportModel {
    private final SectionPackageTier tier;
    private final int featureIndex;
    /** 与路网对齐后的路线编号（route_code） */
    private final String routeCodeDb;
    /** 本级业务编码：路段 line_code / 台账 ledger_code / 公里 km_code / 百米 hm_code */
    private final String segmentCode;
    private final String direction;
    private final BigDecimal startStake;
    private final BigDecimal endStake;
    private final String geomWkt;
    private final String projectId;
    private final String remark;
    private final RouteNetworkMatch networkMatch;
}
