package com.smartroad.srmp.importer.handler;

import com.smartroad.srmp.disease.dto.DiseaseSaveDTO;
import com.smartroad.srmp.disease.service.DiseaseRecordService;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Map;

@Component
public class DiseaseImportHandler extends AbstractImportHandler {
    @Resource private DiseaseRecordService diseaseRecordService;
    public String dataType() { return "DISEASE"; }
    public String handle(Map<String, String> row) {
        DiseaseSaveDTO dto = new DiseaseSaveDTO();
        dto.setTaskId(first(row, "task_id", "taskId", "巡检任务ID"));
        dto.setRouteId(first(row, "route_id", "routeId", "路线ID"));
        dto.setSectionId(first(row, "section_id", "sectionId", "路段ID"));
        dto.setUnitId(first(row, "unit_id", "unitId", "评定单元ID"));
        dto.setRouteCode(first(row, "route_code", "routeCode", "路线编号"));
        dto.setDirection(first(row, "direction", "方向"));
        dto.setLaneNo(integer(row, firstKey(row, "lane_no", "laneNo", "车道编号")));
        dto.setStartStake(decimal(row, firstKey(row, "start_stake", "startStake", "起点桩号")));
        dto.setEndStake(decimal(row, firstKey(row, "end_stake", "endStake", "终点桩号")));
        dto.setDiseaseCategory(first(row, "disease_category", "diseaseCategory", "病害大类"));
        dto.setDiseaseType(first(row, "disease_type", "diseaseType", "病害类型"));
        required(dto.getRouteCode(), "路线编号"); required(dto.getDiseaseCategory(), "病害大类"); required(dto.getDiseaseType(), "病害类型");
        dto.setDiseaseName(first(row, "disease_name", "diseaseName", "病害名称"));
        dto.setSeverity(first(row, "severity", "严重程度"));
        dto.setQuantity(decimal(row, firstKey(row, "quantity", "数量")));
        dto.setMeasureUnit(first(row, "measure_unit", "measureUnit", "计量单位"));
        dto.setDamageArea(decimal(row, firstKey(row, "damage_area", "damageArea", "病害面积")));
        dto.setDamageLength(decimal(row, firstKey(row, "damage_length", "damageLength", "病害长度")));
        dto.setDamageWidth(decimal(row, firstKey(row, "damage_width", "damageWidth", "病害宽度")));
        dto.setDamageDepth(decimal(row, firstKey(row, "damage_depth", "damageDepth", "病害深度")));
        dto.setSource(first(row, "source", "来源"));
        dto.setConfidence(decimal(row, firstKey(row, "confidence", "置信度")));
        dto.setStatus(first(row, "status", "状态"));
        String geomWkt = first(row, "geom_wkt", "geomWkt", "wkt", "geom", "空间WKT");
        String lng = first(row, "longitude", "lng", "经度");
        String lat = first(row, "latitude", "lat", "纬度");
        if ((geomWkt == null || geomWkt.isEmpty()) && lng != null && lat != null) geomWkt = "POINT(" + lng + " " + lat + ")";
        dto.setGeomWkt(geomWkt);
        dto.setRemark(first(row, "remark", "备注"));
        return diseaseRecordService.create(dto);
    }
    private String firstKey(Map<String,String> row, String... keys) { for(String k: keys) if(row.containsKey(k)) return k; return keys[0]; }
}