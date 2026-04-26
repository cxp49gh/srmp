package com.smartroad.srmp.importer.handler;

import com.smartroad.srmp.roadasset.dto.RoadRouteSaveDTO;
import com.smartroad.srmp.roadasset.service.RoadRouteService;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.util.Map;

@Component
public class RoadRouteImportHandler extends AbstractImportHandler {
    @Resource private RoadRouteService roadRouteService;
    public String dataType() { return "ROAD_ROUTE"; }
    public String handle(Map<String, String> row) {
        RoadRouteSaveDTO dto = new RoadRouteSaveDTO();
        dto.setRouteCode(first(row, "route_code", "routeCode", "路线编号"));
        dto.setRouteName(first(row, "route_name", "routeName", "路线名称"));
        dto.setRouteType(first(row, "route_type", "routeType", "路线类型"));
        required(dto.getRouteCode(), "路线编号"); required(dto.getRouteName(), "路线名称"); required(dto.getRouteType(), "路线类型");
        dto.setAdminGrade(first(row, "admin_grade", "adminGrade", "行政等级"));
        dto.setTechnicalGrade(first(row, "technical_grade", "technicalGrade", "技术等级"));
        dto.setStartStake(decimal(row, firstKey(row, "start_stake", "startStake", "起点桩号")));
        dto.setEndStake(decimal(row, firstKey(row, "end_stake", "endStake", "终点桩号")));
        dto.setLengthKm(decimal(row, firstKey(row, "length_km", "lengthKm", "长度")));
        dto.setAdcode(first(row, "adcode", "行政区划"));
        dto.setManageOrgId(first(row, "manage_org_id", "manageOrgId", "管养单位ID"));
        dto.setGeomWkt(first(row, "geom_wkt", "geomWkt", "wkt", "geom", "空间WKT"));
        dto.setRemark(first(row, "remark", "备注"));
        return roadRouteService.create(dto);
    }
    private String firstKey(Map<String,String> row, String... keys) { for(String k: keys) if(row.containsKey(k)) return k; return keys[0]; }
}