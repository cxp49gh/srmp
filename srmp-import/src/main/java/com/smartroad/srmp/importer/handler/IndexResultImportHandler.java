package com.smartroad.srmp.importer.handler;

import com.smartroad.srmp.assessment.dto.IndexResultSaveDTO;
import com.smartroad.srmp.assessment.service.IndexResultService;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.util.Map;

@Component
public class IndexResultImportHandler extends AbstractImportHandler {
    @Resource private IndexResultService indexResultService;
    public String dataType() { return "INDEX_RESULT"; }
    public String handle(Map<String, String> row) {
        IndexResultSaveDTO dto = new IndexResultSaveDTO();
        dto.setTaskId(first(row, "task_id", "taskId", "巡检任务ID"));
        dto.setAssessmentId(first(row, "assessment_id", "assessmentId", "评定结果ID"));
        dto.setUnitId(first(row, "unit_id", "unitId", "评定单元ID"));
        dto.setRouteId(first(row, "route_id", "routeId", "路线ID"));
        dto.setSectionId(first(row, "section_id", "sectionId", "路段ID"));
        dto.setRouteCode(first(row, "route_code", "routeCode", "路线编号"));
        dto.setDirection(first(row, "direction", "方向"));
        dto.setStartStake(decimal(row, firstKey(row, "start_stake", "startStake", "起点桩号")));
        dto.setEndStake(decimal(row, firstKey(row, "end_stake", "endStake", "终点桩号")));
        dto.setYear(integer(row, firstKey(row, "year", "年度")));
        dto.setIndexCode(first(row, "index_code", "indexCode", "指标编码"));
        required(dto.getRouteCode(), "路线编号"); required(dto.getIndexCode(), "指标编码");
        if (dto.getYear() == null) throw new com.smartroad.srmp.common.exception.BizException("年度不能为空");
        dto.setIndexName(first(row, "index_name", "indexName", "指标名称"));
        dto.setIndexValue(decimal(row, firstKey(row, "index_value", "indexValue", "指标值")));
        dto.setGrade(first(row, "grade", "等级"));
        dto.setRawMetrics(first(row, "raw_metrics", "rawMetrics", "原始指标"));
        dto.setCalculationVersion(first(row, "calculation_version", "calculationVersion", "计算版本"));
        return indexResultService.create(dto);
    }
    private String firstKey(Map<String,String> row, String... keys) { for(String k: keys) if(row.containsKey(k)) return k; return keys[0]; }
}