package com.smartroad.srmp.roadasset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartroad.srmp.common.exception.BizException;
import com.smartroad.srmp.disease.entity.DiseaseRecord;
import com.smartroad.srmp.disease.mapper.DiseaseRecordMapper;
import com.smartroad.srmp.roadasset.datamgmt.DataImportType;
import com.smartroad.srmp.roadasset.datamgmt.DataMgmtClearScope;
import com.smartroad.srmp.roadasset.entity.DataImportRecord;
import com.smartroad.srmp.roadasset.entity.RoadEvaluationUnit;
import com.smartroad.srmp.roadasset.entity.RoadRoute;
import com.smartroad.srmp.roadasset.entity.RoadSection;
import com.smartroad.srmp.roadasset.entity.RoadSectionHm;
import com.smartroad.srmp.roadasset.entity.RoadSectionKm;
import com.smartroad.srmp.roadasset.mapper.DataImportRecordMapper;
import com.smartroad.srmp.roadasset.mapper.RoadEvaluationUnitMapper;
import com.smartroad.srmp.roadasset.mapper.RoadRouteMapper;
import com.smartroad.srmp.roadasset.mapper.RoadSectionHmMapper;
import com.smartroad.srmp.roadasset.mapper.RoadSectionKmMapper;
import com.smartroad.srmp.roadasset.mapper.RoadSectionMapper;
import com.smartroad.srmp.roadasset.service.DataMgmtClearService;
import com.smartroad.srmp.roadasset.service.DataMgmtProjectService;
import com.smartroad.srmp.roadasset.vo.DataMgmtClearPreviewVO;
import com.smartroad.srmp.roadasset.vo.DataMgmtClearResultVO;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

@Service
public class DataMgmtClearServiceImpl implements DataMgmtClearService {

    @Resource
    private DataMgmtProjectService dataMgmtProjectService;
    @Resource
    private RoadRouteMapper roadRouteMapper;
    @Resource
    private RoadSectionMapper roadSectionMapper;
    @Resource
    private RoadEvaluationUnitMapper roadEvaluationUnitMapper;
    @Resource
    private RoadSectionKmMapper roadSectionKmMapper;
    @Resource
    private RoadSectionHmMapper roadSectionHmMapper;
    @Resource
    private DiseaseRecordMapper diseaseRecordMapper;
    @Resource
    private DataImportRecordMapper dataImportRecordMapper;

    @Override
    public DataMgmtClearPreviewVO previewClear(String projectId, String scope) {
        dataMgmtProjectService.requireExists(projectId);
        String tenantId = TenantContextHolder.getTenantId();
        String effectiveScope = StringUtils.hasText(scope) ? scope : DataMgmtClearScope.ALL;
        if (!DataMgmtClearScope.isKnown(effectiveScope)) {
            throw new BizException("不支持的清除范围：" + effectiveScope);
        }
        DataMgmtClearPreviewVO vo = new DataMgmtClearPreviewVO();
        if (DataMgmtClearScope.ALL.equals(effectiveScope)) {
            vo.setDiseaseRecords(countDisease(tenantId, projectId));
            vo.setSections(countSections(tenantId, projectId));
            vo.setRoutes(countRoutes(tenantId, projectId));
            vo.setImportRecords(countImports(tenantId, projectId));
            return vo;
        }
        if (DataImportType.DISEASE_EXCEL.equals(effectiveScope)) {
            vo.setDiseaseRecords(countDisease(tenantId, projectId));
            return vo;
        }
        if (DataImportType.SECTION_PACKAGE.equals(effectiveScope)) {
            vo.setSections(countSections(tenantId, projectId));
            return vo;
        }
        if (DataImportType.ROAD_NETWORK.equals(effectiveScope)) {
            vo.setRoutes(countRoutes(tenantId, projectId));
            return vo;
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataMgmtClearResultVO clearByProject(String projectId, String scope) {
        if (!DataMgmtClearScope.isKnown(scope)) {
            throw new BizException("不支持的清除范围：" + scope);
        }
        dataMgmtProjectService.requireExists(projectId);
        String tenantId = TenantContextHolder.getTenantId();
        DataMgmtClearResultVO vo = new DataMgmtClearResultVO();
        if (DataMgmtClearScope.ALL.equals(scope)) {
            vo.setDiseaseRecords(deleteDiseaseRecords(tenantId, projectId));
            vo.setSections(deleteSectionsByProject(tenantId, projectId));
            vo.setRoutes(deleteRoutesByProject(tenantId, projectId));
            vo.setImportRecords(deleteImportRecords(tenantId, projectId));
            return vo;
        }
        if (DataImportType.DISEASE_EXCEL.equals(scope)) {
            vo.setDiseaseRecords(deleteDiseaseRecords(tenantId, projectId));
            return vo;
        }
        if (DataImportType.SECTION_PACKAGE.equals(scope)) {
            vo.setSections(deleteSectionsByProject(tenantId, projectId));
            return vo;
        }
        if (DataImportType.ROAD_NETWORK.equals(scope)) {
            vo.setRoutes(deleteRoutesByProject(tenantId, projectId));
            return vo;
        }
        return vo;
    }

    private int countRoutes(String tenantId, String projectId) {
        return Math.toIntExact(roadRouteMapper.selectCount(new LambdaQueryWrapper<RoadRoute>()
                .eq(RoadRoute::getTenantId, tenantId)
                .eq(RoadRoute::getProjectId, projectId)));
    }

    private int countSections(String tenantId, String projectId) {
        int n = 0;
        n += Math.toIntExact(roadSectionMapper.selectCount(new LambdaQueryWrapper<RoadSection>()
                .eq(RoadSection::getTenantId, tenantId).eq(RoadSection::getProjectId, projectId)));
        n += Math.toIntExact(roadEvaluationUnitMapper.selectCount(new LambdaQueryWrapper<RoadEvaluationUnit>()
                .eq(RoadEvaluationUnit::getTenantId, tenantId).eq(RoadEvaluationUnit::getProjectId, projectId)));
        n += Math.toIntExact(roadSectionKmMapper.selectCount(new LambdaQueryWrapper<RoadSectionKm>()
                .eq(RoadSectionKm::getTenantId, tenantId).eq(RoadSectionKm::getProjectId, projectId)));
        n += Math.toIntExact(roadSectionHmMapper.selectCount(new LambdaQueryWrapper<RoadSectionHm>()
                .eq(RoadSectionHm::getTenantId, tenantId).eq(RoadSectionHm::getProjectId, projectId)));
        return n;
    }

    private int countDisease(String tenantId, String projectId) {
        return Math.toIntExact(diseaseRecordMapper.selectCount(new LambdaQueryWrapper<DiseaseRecord>()
                .eq(DiseaseRecord::getTenantId, tenantId).eq(DiseaseRecord::getProjectId, projectId)));
    }

    private int countImports(String tenantId, String projectId) {
        return Math.toIntExact(dataImportRecordMapper.selectCount(new LambdaQueryWrapper<DataImportRecord>()
                .eq(DataImportRecord::getTenantId, tenantId).eq(DataImportRecord::getProjectId, projectId)));
    }

    private int deleteRoutesByProject(String tenantId, String projectId) {
        return roadRouteMapper.delete(new LambdaQueryWrapper<RoadRoute>()
                .eq(RoadRoute::getTenantId, tenantId)
                .eq(RoadRoute::getProjectId, projectId));
    }

    private int deleteSectionsByProject(String tenantId, String projectId) {
        int n = 0;
        n += roadSectionMapper.delete(new LambdaQueryWrapper<RoadSection>()
                .eq(RoadSection::getTenantId, tenantId)
                .eq(RoadSection::getProjectId, projectId));
        n += roadEvaluationUnitMapper.delete(new LambdaQueryWrapper<RoadEvaluationUnit>()
                .eq(RoadEvaluationUnit::getTenantId, tenantId)
                .eq(RoadEvaluationUnit::getProjectId, projectId));
        n += roadSectionKmMapper.delete(new LambdaQueryWrapper<RoadSectionKm>()
                .eq(RoadSectionKm::getTenantId, tenantId)
                .eq(RoadSectionKm::getProjectId, projectId));
        n += roadSectionHmMapper.delete(new LambdaQueryWrapper<RoadSectionHm>()
                .eq(RoadSectionHm::getTenantId, tenantId)
                .eq(RoadSectionHm::getProjectId, projectId));
        return n;
    }

    private int deleteDiseaseRecords(String tenantId, String projectId) {
        return diseaseRecordMapper.delete(new LambdaQueryWrapper<DiseaseRecord>()
                .eq(DiseaseRecord::getTenantId, tenantId)
                .eq(DiseaseRecord::getProjectId, projectId));
    }

    private int deleteImportRecords(String tenantId, String projectId) {
        return dataImportRecordMapper.delete(new LambdaQueryWrapper<DataImportRecord>()
                .eq(DataImportRecord::getTenantId, tenantId)
                .eq(DataImportRecord::getProjectId, projectId));
    }
}
