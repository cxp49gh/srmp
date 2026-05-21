package com.smartroad.srmp.roadasset.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.common.util.IdUtils;
import com.smartroad.srmp.roadasset.datamgmt.DataImportType;
import com.smartroad.srmp.roadasset.entity.DataImportRecord;
import com.smartroad.srmp.roadasset.mapper.DataImportRecordMapper;
import com.smartroad.srmp.roadasset.datamgmt.DataMgmtAuditOperationType;
import com.smartroad.srmp.roadasset.service.DataMgmtAuditLogService;
import com.smartroad.srmp.roadasset.service.DataMgmtImportService;
import com.smartroad.srmp.roadasset.service.DataMgmtProjectService;
import com.smartroad.srmp.roadasset.service.DataMgmtStatsService;
import com.smartroad.srmp.common.exception.BizException;
import com.smartroad.srmp.roadasset.service.DiseaseExcelImportService;
import com.smartroad.srmp.roadasset.service.RoadNetworkImportService;
import com.smartroad.srmp.roadasset.service.RoadSectionPackageImportService;
import com.smartroad.srmp.roadasset.vo.ImportDiseaseExcelResultVO;
import com.smartroad.srmp.roadasset.vo.ImportNetworkResultVO;
import com.smartroad.srmp.roadasset.vo.ImportSectionPackageResultVO;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class DataMgmtImportServiceImpl implements DataMgmtImportService {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String ROAD_NETWORK_REQUIRED = "请先导入路网数据，再导入路段或病害数据";

    @Resource
    private DataMgmtProjectService dataMgmtProjectService;
    @Resource
    private DataMgmtStatsService dataMgmtStatsService;
    @Resource
    private DataMgmtAuditLogService dataMgmtAuditLogService;
    @Resource
    private RoadNetworkImportService roadNetworkImportService;
    @Resource
    private RoadSectionPackageImportService roadSectionPackageImportService;
    @Resource
    private DiseaseExcelImportService diseaseExcelImportService;
    @Resource
    private DataImportRecordMapper dataImportRecordMapper;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public ImportNetworkResultVO importRoadNetwork(String projectId, MultipartFile file) {
        dataMgmtProjectService.requireExists(projectId);
        LocalDateTime started = LocalDateTime.now();
        long t0 = System.currentTimeMillis();
        String fileName = file != null ? file.getOriginalFilename() : null;
        try {
            ImportNetworkResultVO vo = roadNetworkImportService.importNetwork(file, projectId);
            insertRecord(projectId, DataImportType.ROAD_NETWORK, fileName, started, t0, STATUS_SUCCESS, null, vo);
            auditImport(projectId, DataMgmtAuditOperationType.IMPORT_ROAD_NETWORK, STATUS_SUCCESS, fileName);
            return vo;
        } catch (RuntimeException ex) {
            insertRecord(projectId, DataImportType.ROAD_NETWORK, fileName, started, t0, STATUS_FAILED, ex.getMessage(), null);
            auditImport(projectId, DataMgmtAuditOperationType.IMPORT_ROAD_NETWORK, STATUS_FAILED, fileName);
            throw ex;
        }
    }

    @Override
    public ImportSectionPackageResultVO importSectionPackage(String projectId, MultipartFile file) {
        dataMgmtProjectService.requireExists(projectId);
        requireRoadNetwork(projectId);
        LocalDateTime started = LocalDateTime.now();
        long t0 = System.currentTimeMillis();
        String fileName = file != null ? file.getOriginalFilename() : null;
        try {
            ImportSectionPackageResultVO vo = roadSectionPackageImportService.importPackage(file, projectId);
            insertRecord(projectId, DataImportType.SECTION_PACKAGE, fileName, started, t0, STATUS_SUCCESS, null, vo);
            auditImport(projectId, DataMgmtAuditOperationType.IMPORT_SECTION, STATUS_SUCCESS, fileName);
            return vo;
        } catch (RuntimeException ex) {
            insertRecord(projectId, DataImportType.SECTION_PACKAGE, fileName, started, t0, STATUS_FAILED, ex.getMessage(), null);
            auditImport(projectId, DataMgmtAuditOperationType.IMPORT_SECTION, STATUS_FAILED, fileName);
            throw ex;
        }
    }

    @Override
    public ImportDiseaseExcelResultVO importDiseaseExcel(String projectId, MultipartFile file) {
        dataMgmtProjectService.requireExists(projectId);
        requireRoadNetwork(projectId);
        LocalDateTime started = LocalDateTime.now();
        long t0 = System.currentTimeMillis();
        String fileName = file != null ? file.getOriginalFilename() : null;
        try {
            ImportDiseaseExcelResultVO vo = diseaseExcelImportService.importExcel(file, projectId);
            insertRecord(projectId, DataImportType.DISEASE_EXCEL, fileName, started, t0, STATUS_SUCCESS, null, vo);
            auditImport(projectId, DataMgmtAuditOperationType.IMPORT_DISEASE, STATUS_SUCCESS, fileName);
            return vo;
        } catch (RuntimeException ex) {
            insertRecord(projectId, DataImportType.DISEASE_EXCEL, fileName, started, t0, STATUS_FAILED, ex.getMessage(), null);
            auditImport(projectId, DataMgmtAuditOperationType.IMPORT_DISEASE, STATUS_FAILED, fileName);
            throw ex;
        }
    }

    private void requireRoadNetwork(String projectId) {
        if (!dataMgmtStatsService.isRoadNetworkReady(projectId)) {
            throw new BizException(ROAD_NETWORK_REQUIRED);
        }
    }

    private void auditImport(String projectId, String op, String result, String fileName) {
        try {
            dataMgmtAuditLogService.log(projectId, null, op, result, fileName, null, null);
        } catch (Exception ignored) {
            // 审计失败不影响主流程
        }
    }

    private void insertRecord(String projectId, String importType, String fileName, LocalDateTime started,
                              long t0, String status, String message, Object resultBody) {
        long durationMs = System.currentTimeMillis() - t0;
        LocalDateTime finished = LocalDateTime.now();
        DataImportRecord r = new DataImportRecord();
        r.setId(IdUtils.uuid());
        r.setTenantId(TenantContextHolder.getTenantId());
        r.setProjectId(projectId);
        r.setImportType(importType);
        r.setFileName(fileName);
        r.setStartedAt(started);
        r.setFinishedAt(finished);
        r.setDurationMs(durationMs);
        r.setStatus(status);
        r.setMessage(message);
        r.setResultSummary(toJson(resultBody));
        r.setCreatedAt(finished);
        r.setUpdatedAt(finished);
        r.setDeleted(false);
        dataImportRecordMapper.insert(r);
    }

    private String toJson(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return String.valueOf(o);
        }
    }
}
