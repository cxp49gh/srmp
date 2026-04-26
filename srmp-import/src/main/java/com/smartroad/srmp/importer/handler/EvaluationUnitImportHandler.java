package com.smartroad.srmp.importer.handler;

import com.smartroad.srmp.roadasset.dto.EvaluationUnitSaveDTO;
import com.smartroad.srmp.roadasset.service.RoadEvaluationUnitService;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.util.Map;

@Component
public class EvaluationUnitImportHandler extends AbstractImportHandler {
    @Resource private RoadEvaluationUnitService evaluationUnitService;
    public String dataType() { return "EVALUATION_UNIT"; }
    public String handle(Map<String, String> row) {
        EvaluationUnitSaveDTO dto = new EvaluationUnitSaveDTO();
        dto.setRouteId(first(row, "route_id", "routeId", "路线ID"));
        dto.setSectionId(first(row, "section_id", "sectionId", "路段ID"));
        dto.setRouteCode(first(row, "route_code", "routeCode", "路线编号"));
        dto.setUnitCode(first(row, "unit_code", "unitCode", "评定单元编码"));
        required(dto.getRouteCode(), "路线编号"); required(dto.getUnitCode(), "评定单元编码");
        dto.setDirection(first(row, "direction", "方向"));
        dto.setLaneNo(integer(row, firstKey(row, "lane_no", "laneNo", "车道编号")));
        dto.setStartStake(decimal(row, firstKey(row, "start_stake", "startStake", "起点桩号")));
        dto.setEndStake(decimal(row, firstKey(row, "end_stake", "endStake", "终点桩号")));
        dto.setLengthM(integer(row, firstKey(row, "length_m", "lengthM", "单元长度")));
        dto.setPavementType(first(row, "pavement_type", "pavementType", "路面类型"));
        dto.setTechnicalGrade(first(row, "technical_grade", "technicalGrade", "技术等级"));
        dto.setRoadWidth(decimal(row, firstKey(row, "road_width", "roadWidth", "路面宽度")));
        dto.setAdcode(first(row, "adcode", "行政区划"));
        dto.setManageOrgId(first(row, "manage_org_id", "manageOrgId", "管养单位ID"));
        dto.setGeomWkt(first(row, "geom_wkt", "geomWkt", "wkt", "geom", "空间WKT"));
        dto.setCenterPointWkt(first(row, "center_point_wkt", "centerPointWkt", "中心点WKT"));
        return evaluationUnitService.create(dto);
    }
    private String firstKey(Map<String,String> row, String... keys) { for(String k: keys) if(row.containsKey(k)) return k; return keys[0]; }
}