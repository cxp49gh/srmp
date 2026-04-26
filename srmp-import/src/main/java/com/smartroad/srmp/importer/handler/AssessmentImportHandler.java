package com.smartroad.srmp.importer.handler;

import com.smartroad.srmp.assessment.dto.AssessmentResultSaveDTO;
import com.smartroad.srmp.assessment.service.AssessmentResultService;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.util.Map;

@Component
public class AssessmentImportHandler extends AbstractImportHandler {
    @Resource private AssessmentResultService assessmentResultService;
    public String dataType() { return "ASSESSMENT"; }
    public String handle(Map<String, String> row) {
        AssessmentResultSaveDTO dto = new AssessmentResultSaveDTO();
        dto.setTaskId(first(row, "task_id", "taskId", "巡检任务ID"));
        dto.setObjectType(first(row, "object_type", "objectType", "评定对象类型"));
        dto.setObjectId(first(row, "object_id", "objectId", "评定对象ID"));
        dto.setRouteId(first(row, "route_id", "routeId", "路线ID"));
        dto.setSectionId(first(row, "section_id", "sectionId", "路段ID"));
        dto.setUnitId(first(row, "unit_id", "unitId", "评定单元ID"));
        dto.setRouteCode(first(row, "route_code", "routeCode", "路线编号"));
        dto.setDirection(first(row, "direction", "方向"));
        dto.setStartStake(decimal(row, firstKey(row, "start_stake", "startStake", "起点桩号")));
        dto.setEndStake(decimal(row, firstKey(row, "end_stake", "endStake", "终点桩号")));
        dto.setYear(integer(row, firstKey(row, "year", "年度")));
        required(dto.getRouteCode(), "路线编号");
        if (dto.getYear() == null) throw new com.smartroad.srmp.common.exception.BizException("年度不能为空");
        dto.setStandardCode(first(row, "standard_code", "standardCode", "标准编码"));
        dto.setMqi(decimal(row, firstKey(row, "mqi", "MQI")));
        dto.setSci(decimal(row, firstKey(row, "sci", "SCI")));
        dto.setPqi(decimal(row, firstKey(row, "pqi", "PQI")));
        dto.setBci(decimal(row, firstKey(row, "bci", "BCI")));
        dto.setTci(decimal(row, firstKey(row, "tci", "TCI")));
        dto.setPci(decimal(row, firstKey(row, "pci", "PCI")));
        dto.setRqi(decimal(row, firstKey(row, "rqi", "RQI")));
        dto.setRdi(decimal(row, firstKey(row, "rdi", "RDI")));
        dto.setPbi(decimal(row, firstKey(row, "pbi", "PBI")));
        dto.setPwi(decimal(row, firstKey(row, "pwi", "PWI")));
        dto.setSri(decimal(row, firstKey(row, "sri", "SRI")));
        dto.setPssi(decimal(row, firstKey(row, "pssi", "PSSI")));
        dto.setGrade(first(row, "grade", "评定等级"));
        dto.setZeroReason(first(row, "zero_reason", "zeroReason", "评0原因"));
        return assessmentResultService.create(dto);
    }
    private String firstKey(Map<String,String> row, String... keys) { for(String k: keys) if(row.containsKey(k)) return k; return keys[0]; }
}