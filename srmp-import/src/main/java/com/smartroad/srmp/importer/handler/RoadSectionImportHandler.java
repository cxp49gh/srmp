package com.smartroad.srmp.importer.handler;

import com.smartroad.srmp.roadasset.dto.RoadSectionSaveDTO;
import com.smartroad.srmp.roadasset.service.RoadSectionService;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.util.Map;

@Component
public class RoadSectionImportHandler extends AbstractImportHandler {
    @Resource private RoadSectionService roadSectionService;
    public String dataType() { return "ROAD_SECTION"; }
    public String handle(Map<String, String> row) {
        RoadSectionSaveDTO dto = new RoadSectionSaveDTO();
        dto.setRouteId(first(row, "route_id", "routeId", "路线ID"));
        dto.setRouteCode(first(row, "route_code", "routeCode", "路线编号"));
        dto.setSectionCode(first(row, "section_code", "sectionCode", "路段编码"));
        required(dto.getRouteCode(), "路线编号"); required(dto.getSectionCode(), "路段编码");
        dto.setSectionName(first(row, "section_name", "sectionName", "路段名称"));
        dto.setDirection(first(row, "direction", "方向"));
        dto.setStartStake(decimal(row, firstKey(row, "start_stake", "startStake", "起点桩号")));
        dto.setEndStake(decimal(row, firstKey(row, "end_stake", "endStake", "终点桩号")));
        dto.setLengthKm(decimal(row, firstKey(row, "length_km", "lengthKm", "长度")));
        dto.setPavementType(first(row, "pavement_type", "pavementType", "路面类型"));
        dto.setTechnicalGrade(first(row, "technical_grade", "technicalGrade", "技术等级"));
        dto.setLaneCount(integer(row, firstKey(row, "lane_count", "laneCount", "车道数")));
        dto.setRoadWidth(decimal(row, firstKey(row, "road_width", "roadWidth", "路面宽度")));
        dto.setTrafficVolumeLevel(first(row, "traffic_volume_level", "trafficVolumeLevel", "交通量等级"));
        dto.setAdcode(first(row, "adcode", "行政区划"));
        dto.setManageOrgId(first(row, "manage_org_id", "manageOrgId", "管养单位ID"));
        dto.setGeomWkt(first(row, "geom_wkt", "geomWkt", "wkt", "geom", "空间WKT"));
        dto.setRemark(first(row, "remark", "备注"));
        return roadSectionService.create(dto);
    }
    private String firstKey(Map<String,String> row, String... keys) { for(String k: keys) if(row.containsKey(k)) return k; return keys[0]; }
}