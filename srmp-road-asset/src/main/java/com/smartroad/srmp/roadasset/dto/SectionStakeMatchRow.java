package com.smartroad.srmp.roadasset.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 路线编号 + 方向 + 起终点桩号 与库表 id 的对应（用于批量 IN 查询或结果映射）。
 */
@Data
public class SectionStakeMatchRow {
    private String id;
    private String routeCode;
    private String direction;
    private BigDecimal startStake;
    private BigDecimal endStake;
}
