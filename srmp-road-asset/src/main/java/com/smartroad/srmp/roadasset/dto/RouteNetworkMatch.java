package com.smartroad.srmp.roadasset.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 路网命中：{@code road_route.id} 与库内规范 {@code road_route.route_code} */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteNetworkMatch {
    private String routeId;
    /** 与库表 {@code road_route.route_code} 一致，用于写入路段/病害的线路编码 */
    private String canonicalRouteCode;
}
