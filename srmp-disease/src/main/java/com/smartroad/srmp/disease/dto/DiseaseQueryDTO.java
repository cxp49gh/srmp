package com.smartroad.srmp.disease.dto;

import com.smartroad.srmp.common.core.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class DiseaseQueryDTO extends PageQuery {
    private String taskId;
    private String routeCode;
    private String direction;
    private String unitId;
    private String diseaseCategory;
    private String diseaseType;
    private String severity;
    private String status;
    private BigDecimal startStake;
    private BigDecimal endStake;
    /** 可选：按数据管理项目过滤 */
    private String projectId;

    /** 视窗范围（WGS84）：与 GIS 前端 bbox [west, south, east, north] 一致，用于地图图层查询、避免全表拉取 */
    private Double minLng;
    private Double minLat;
    private Double maxLng;
    private Double maxLat;
}
