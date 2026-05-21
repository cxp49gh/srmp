package com.smartroad.srmp.importer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartroad.srmp.common.exception.BizException;
import com.smartroad.srmp.common.util.IdUtils;
import com.smartroad.srmp.importer.entity.DataImportErrorLog;
import com.smartroad.srmp.importer.entity.DataImportTask;
import com.smartroad.srmp.importer.handler.ImportHandler;
import com.smartroad.srmp.importer.mapper.DataImportErrorLogMapper;
import com.smartroad.srmp.importer.mapper.DataImportTaskMapper;
import com.smartroad.srmp.importer.parser.ImportTableParser;
import com.smartroad.srmp.importer.service.DataImportService;
import com.smartroad.srmp.importer.vo.ImportResultVO;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DataImportServiceImpl implements DataImportService {
    @Resource private ImportTableParser parser;
    @Resource private DataImportTaskMapper taskMapper;
    @Resource private DataImportErrorLogMapper errorLogMapper;
    private final Map<String, ImportHandler> handlerMap;

    public DataImportServiceImpl(List<ImportHandler> handlers) {
        this.handlerMap = handlers.stream().collect(Collectors.toMap(h -> h.dataType().toUpperCase(Locale.ROOT), h -> h));
    }

    @Override
    public ImportResultVO importFile(String dataType, String importName, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new BizException("导入文件不能为空");
        String type = dataType == null ? null : dataType.trim().toUpperCase(Locale.ROOT);
        ImportHandler handler = handlerMap.get(type);
        if (handler == null) throw new BizException("不支持的导入类型：" + dataType);

        String tenantId = TenantContextHolder.getTenantId();
        String taskId = IdUtils.uuid();
        String importCode = "IMP" + System.currentTimeMillis();
        DataImportTask task = new DataImportTask();
        task.setId(taskId);
        task.setTenantId(tenantId);
        task.setImportCode(importCode);
        task.setImportName(importName == null || importName.trim().isEmpty() ? file.getOriginalFilename() : importName);
        task.setDataType(type);
        task.setStatus("IMPORTING");
        task.setTotalCount(0);
        task.setSuccessCount(0);
        task.setFailedCount(0);
        task.setStartedAt(LocalDateTime.now());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setDeleted(false);
        taskMapper.insert(task);

        ImportResultVO result = new ImportResultVO();
        result.setImportTaskId(taskId);
        result.setImportCode(importCode);
        result.setDataType(type);

        List<Map<String, String>> rows;
        try {
            rows = parser.parse(file);
        } catch (Exception e) {
            finishTask(task, "FAILED", 0, 0, 0, "文件解析失败：" + e.getMessage());
            throw e;
        }

        int success = 0;
        int failed = 0;
        int rowNo = 1;
        for (Map<String, String> row : rows) {
            rowNo++;
            try {
                handler.handle(row);
                success++;
            } catch (Exception e) {
                failed++;
                String errorMessage = enrichRowErrorMessage(type, row, e.getMessage());
                String msg = "第" + rowNo + "行：" + errorMessage;
                result.getErrors().add(msg);
                DataImportErrorLog log = new DataImportErrorLog();
                log.setId(IdUtils.uuid());
                log.setTenantId(tenantId);
                log.setImportTaskId(taskId);
                log.setRowNo(rowNo);
                log.setErrorType("BIZ_CHECK");
                log.setErrorMessage(errorMessage);
                log.setCreatedAt(LocalDateTime.now());
                errorLogMapper.insert(log);
            }
        }
        String status = failed == 0 ? "SUCCESS" : (success > 0 ? "PART_SUCCESS" : "FAILED");
        finishTask(task, status, rows.size(), success, failed, failed > 0 ? "存在导入失败记录" : null);
        result.setStatus(status);
        result.setTotalCount(rows.size());
        result.setSuccessCount(success);
        result.setFailedCount(failed);
        return result;
    }

    private String enrichRowErrorMessage(String type, Map<String, String> row, String message) {
        if (!"ROAD_SECTION".equals(type)) {
            return message;
        }
        String routeCode = firstPresent(row, "route_code", "routeCode", "路线编号", "线路编码");
        if (routeCode == null || routeCode.trim().isEmpty()) {
            return message;
        }
        return "线路编码：" + routeCode.trim() + "；" + message;
    }

    private String firstPresent(Map<String, String> row, String... keys) {
        if (row == null) {
            return null;
        }
        for (String key : keys) {
            String value = row.get(key);
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private void finishTask(DataImportTask task, String status, int total, int success, int failed, String errorMessage) {
        task.setStatus(status);
        task.setTotalCount(total);
        task.setSuccessCount(success);
        task.setFailedCount(failed);
        task.setErrorMessage(errorMessage);
        task.setFinishedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    @Override
    public List<DataImportErrorLog> listErrors(String importTaskId) {
        return errorLogMapper.selectList(new LambdaQueryWrapper<DataImportErrorLog>()
                .eq(DataImportErrorLog::getTenantId, TenantContextHolder.getTenantId())
                .eq(DataImportErrorLog::getImportTaskId, importTaskId)
                .orderByAsc(DataImportErrorLog::getRowNo));
    }

    @Override
    public void downloadTemplate(String dataType, HttpServletResponse response) throws IOException {
        String type = dataType == null ? "" : dataType.trim().toUpperCase(Locale.ROOT);
        String csv = template(type);
        if (csv == null) throw new BizException("不支持的模板类型：" + dataType);
        String fileName = type.toLowerCase(Locale.ROOT) + "_template.csv";
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, "UTF-8"));
        response.getOutputStream().write(("﻿" + csv).getBytes(StandardCharsets.UTF_8));
    }

    private String template(String type) {
        switch (type) {
            case "ROAD_ROUTE":
                return "route_code,route_name,route_type,admin_grade,technical_grade,start_stake,end_stake,length_km,adcode,manage_org_id,geom_wkt,remark\nG210,G210测试路线,NATIONAL_HIGHWAY,NATIONAL,FIRST_CLASS,0,10,10,,,\"LINESTRING(106.630 26.650,106.720 26.710)\",示例\n";
            case "ROAD_SECTION":
                return "route_id,route_code,section_code,section_name,direction,start_stake,end_stake,length_km,pavement_type,technical_grade,lane_count,road_width,traffic_volume_level,adcode,manage_org_id,geom_wkt,remark\n";
            case "EVALUATION_UNIT":
                return "route_id,section_id,route_code,unit_code,direction,lane_no,start_stake,end_stake,length_m,pavement_type,technical_grade,road_width,adcode,manage_org_id,geom_wkt,center_point_wkt\n";
            case "DISEASE":
                return "task_id,route_id,section_id,unit_id,route_code,direction,lane_no,start_stake,end_stake,disease_category,disease_type,disease_name,severity,quantity,measure_unit,damage_area,damage_length,damage_width,damage_depth,source,confidence,longitude,latitude,geom_wkt,remark\n";
            case "ASSESSMENT":
                return "task_id,object_type,object_id,route_id,section_id,unit_id,route_code,direction,start_stake,end_stake,year,standard_code,mqi,sci,pqi,bci,tci,pci,rqi,rdi,pbi,pwi,sri,pssi,grade,zero_reason\n";
            case "INDEX_RESULT":
                return "task_id,assessment_id,unit_id,route_id,section_id,route_code,direction,start_stake,end_stake,year,index_code,index_name,index_value,grade,raw_metrics,calculation_version\n";
            default:
                return null;
        }
    }
}
