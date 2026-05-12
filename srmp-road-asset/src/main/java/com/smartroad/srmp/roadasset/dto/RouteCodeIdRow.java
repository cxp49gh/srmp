package com.smartroad.srmp.roadasset.dto;

import lombok.Data;

/** 路网 route_code → id，供批量查询结果映射 */
@Data
public class RouteCodeIdRow {
    private String routeCode;
    private String id;
}
