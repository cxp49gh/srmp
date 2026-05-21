package com.smartroad.srmp.roadasset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.smartroad.srmp.disease.entity.DiseaseRecord;
import com.smartroad.srmp.disease.mapper.DiseaseRecordMapper;
import com.smartroad.srmp.roadasset.entity.RoadEvaluationUnit;
import com.smartroad.srmp.roadasset.entity.RoadRoute;
import com.smartroad.srmp.roadasset.entity.RoadSection;
import com.smartroad.srmp.roadasset.entity.RoadSectionHm;
import com.smartroad.srmp.roadasset.entity.RoadSectionKm;
import com.smartroad.srmp.roadasset.mapper.RoadEvaluationUnitMapper;
import com.smartroad.srmp.roadasset.mapper.RoadRouteMapper;
import com.smartroad.srmp.roadasset.mapper.RoadSectionHmMapper;
import com.smartroad.srmp.roadasset.mapper.RoadSectionKmMapper;
import com.smartroad.srmp.roadasset.mapper.RoadSectionMapper;
import com.smartroad.srmp.roadasset.service.DataMgmtProjectService;
import com.smartroad.srmp.roadasset.service.DataMgmtQualityService;
import com.smartroad.srmp.roadasset.vo.DataMgmtProjectVO;
import com.smartroad.srmp.roadasset.vo.DataMgmtQualityReportVO;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DataMgmtQualityServiceImpl implements DataMgmtQualityService {

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

    @Override
    public DataMgmtQualityReportVO getQualityReport(String projectId) {
        dataMgmtProjectService.requireExists(projectId);
        DataMgmtProjectVO project = dataMgmtProjectService.getById(projectId);
        String tenantId = TenantContextHolder.getTenantId();

        long routeCount = roadRouteMapper.selectCount(routeWrapper(tenantId, projectId));
        long lineSectionCount = roadSectionMapper.selectCount(sectionWrapper(tenantId, projectId));
        long ledgerSectionCount = roadEvaluationUnitMapper.selectCount(ledgerWrapper(tenantId, projectId));
        long kmSectionCount = roadSectionKmMapper.selectCount(kmWrapper(tenantId, projectId));
        long hmSectionCount = roadSectionHmMapper.selectCount(hmWrapper(tenantId, projectId));
        long sectionCount = lineSectionCount + ledgerSectionCount + kmSectionCount + hmSectionCount;
        long diseaseCount = diseaseRecordMapper.selectCount(new LambdaQueryWrapper<DiseaseRecord>()
                .eq(DiseaseRecord::getTenantId, tenantId)
                .eq(DiseaseRecord::getProjectId, projectId)
                .eq(DiseaseRecord::getDeleted, false));

        long unmatchedLineSections = roadSectionMapper.selectCount(sectionWrapper(tenantId, projectId)
                .and(w -> w.isNull(RoadSection::getRouteId).or().eq(RoadSection::getRouteId, "")));
        long unmatchedLedgers = roadEvaluationUnitMapper.selectCount(ledgerWrapper(tenantId, projectId)
                .and(w -> w.isNull(RoadEvaluationUnit::getRouteId).or().eq(RoadEvaluationUnit::getRouteId, "")));
        long unmatchedKm = roadSectionKmMapper.selectCount(kmWrapper(tenantId, projectId)
                .and(w -> w.isNull(RoadSectionKm::getRouteId).or().eq(RoadSectionKm::getRouteId, "")));
        long unmatchedHm = roadSectionHmMapper.selectCount(hmWrapper(tenantId, projectId)
                .and(w -> w.isNull(RoadSectionHm::getRouteId).or().eq(RoadSectionHm::getRouteId, "")));
        long unmatchedSections = unmatchedLineSections + unmatchedLedgers + unmatchedKm + unmatchedHm;

        long unmatchedDiseases = diseaseRecordMapper.selectCount(new LambdaQueryWrapper<DiseaseRecord>()
                .eq(DiseaseRecord::getTenantId, tenantId)
                .eq(DiseaseRecord::getProjectId, projectId)
                .eq(DiseaseRecord::getDeleted, false)
                .and(w -> w.isNull(DiseaseRecord::getRouteId).or().eq(DiseaseRecord::getRouteId, "")));
        long unmatched = unmatchedSections + unmatchedDiseases;

        long unclassified = diseaseRecordMapper.selectCount(new LambdaQueryWrapper<DiseaseRecord>()
                .eq(DiseaseRecord::getTenantId, tenantId)
                .eq(DiseaseRecord::getProjectId, projectId)
                .eq(DiseaseRecord::getDeleted, false)
                .and(w -> w.isNull(DiseaseRecord::getDiseaseCategory).or().eq(DiseaseRecord::getDiseaseCategory, "")));

        long emptyRouteGeom = roadRouteMapper.selectCount(routeWrapper(tenantId, projectId).apply("geom IS NULL"));

        long emptyLineSectionGeom = roadSectionMapper.selectCount(sectionWrapper(tenantId, projectId).apply("geom IS NULL"));
        long emptyLedgerGeom = roadEvaluationUnitMapper.selectCount(ledgerWrapper(tenantId, projectId).apply("geom IS NULL"));
        long emptyKmGeom = roadSectionKmMapper.selectCount(kmWrapper(tenantId, projectId).apply("geom IS NULL"));
        long emptyHmGeom = roadSectionHmMapper.selectCount(hmWrapper(tenantId, projectId).apply("geom IS NULL"));
        long emptySectionGeom = emptyLineSectionGeom + emptyLedgerGeom + emptyKmGeom + emptyHmGeom;

        long emptyDiseaseGeom = diseaseRecordMapper.selectCount(new LambdaQueryWrapper<DiseaseRecord>()
                .eq(DiseaseRecord::getTenantId, tenantId)
                .eq(DiseaseRecord::getProjectId, projectId)
                .eq(DiseaseRecord::getDeleted, false)
                .apply("geom IS NULL"));

        long emptyGeometry = emptyRouteGeom + emptySectionGeom + emptyDiseaseGeom;

        DataMgmtQualityReportVO vo = new DataMgmtQualityReportVO();
        vo.setProjectId(projectId);
        vo.setProjectName(project.getName());
        vo.setRouteCount(routeCount);
        vo.setSectionCount(sectionCount);
        vo.setDiseaseCount(diseaseCount);
        vo.setUnmatchedRouteCount(unmatched);
        vo.setUnmatchedSectionRouteCount(unmatchedSections);
        vo.setUnmatchedDiseaseRouteCount(unmatchedDiseases);
        vo.setUnclassifiedDiseaseCount(unclassified);
        vo.setEmptyGeometryCount(emptyGeometry);
        vo.setEmptyRouteGeometryCount(emptyRouteGeom);
        vo.setEmptySectionGeometryCount(emptySectionGeom);
        vo.setEmptyDiseaseGeometryCount(emptyDiseaseGeom);
        vo.setAbnormalCoordinateCount(0);
        vo.setSectionBreakdown(metricItems(
                metric("线路级路段", lineSectionCount),
                metric("台账级单元", ledgerSectionCount),
                metric("公里级", kmSectionCount),
                metric("百米级", hmSectionCount)));
        vo.setRouteMatchBreakdown(metricItems(
                metric("已关联路线", Math.max(sectionCount + diseaseCount - unmatched, 0)),
                metric("路段未关联", unmatchedSections),
                metric("病害未关联", unmatchedDiseases)));
        vo.setGeometryBreakdown(metricItems(
                metric("路线空几何", emptyRouteGeom),
                metric("路段空几何", emptySectionGeom),
                metric("病害空几何", emptyDiseaseGeom)));
        vo.setDiseaseCategoryBreakdown(loadDiseaseGroupCounts(tenantId, projectId, "disease_category", "未分类"));
        vo.setDiseaseSeverityBreakdown(loadDiseaseGroupCounts(tenantId, projectId, "severity", "未标注"));

        long matchBase = sectionCount + diseaseCount;
        if (matchBase > 0) {
            long matched = matchBase - unmatched;
            vo.setRouteMatchRate(Math.round((matched * 10000.0 / matchBase)) / 100.0);
        } else {
            vo.setRouteMatchRate(null);
        }

        List<DataMgmtQualityReportVO.QualityIssueVO> issues = new ArrayList<>();
        if (routeCount == 0) {
            addIssue(issues, "NO_ROAD_NETWORK", 1, "请先导入路网数据");
        }
        if (unmatched > 0) {
            addIssue(issues, "UNMATCHED_ROUTE", unmatched, "检查路段/病害路线编码是否与路网一致");
        }
        if (unclassified > 0) {
            addIssue(issues, "UNCLASSIFIED_DISEASE", unclassified, "补全病害分类字段");
        }
        if (emptyGeometry > 0) {
            addIssue(issues, "EMPTY_GEOMETRY", emptyGeometry, "检查 Shapefile / Excel 中的坐标或几何字段");
        }
        vo.setIssues(issues);
        return vo;
    }

    private void addIssue(List<DataMgmtQualityReportVO.QualityIssueVO> issues, String type, long count, String suggestion) {
        DataMgmtQualityReportVO.QualityIssueVO q = new DataMgmtQualityReportVO.QualityIssueVO();
        q.setIssueType(type);
        q.setCount(count);
        q.setSuggestion(suggestion);
        issues.add(q);
    }

    private LambdaQueryWrapper<RoadRoute> routeWrapper(String tenantId, String projectId) {
        return new LambdaQueryWrapper<RoadRoute>()
                .eq(RoadRoute::getTenantId, tenantId)
                .eq(RoadRoute::getProjectId, projectId)
                .eq(RoadRoute::getDeleted, false);
    }

    private LambdaQueryWrapper<RoadSection> sectionWrapper(String tenantId, String projectId) {
        return new LambdaQueryWrapper<RoadSection>()
                .eq(RoadSection::getTenantId, tenantId)
                .eq(RoadSection::getProjectId, projectId)
                .eq(RoadSection::getDeleted, false);
    }

    private LambdaQueryWrapper<RoadEvaluationUnit> ledgerWrapper(String tenantId, String projectId) {
        return new LambdaQueryWrapper<RoadEvaluationUnit>()
                .eq(RoadEvaluationUnit::getTenantId, tenantId)
                .eq(RoadEvaluationUnit::getProjectId, projectId)
                .eq(RoadEvaluationUnit::getDeleted, false);
    }

    private LambdaQueryWrapper<RoadSectionKm> kmWrapper(String tenantId, String projectId) {
        return new LambdaQueryWrapper<RoadSectionKm>()
                .eq(RoadSectionKm::getTenantId, tenantId)
                .eq(RoadSectionKm::getProjectId, projectId)
                .eq(RoadSectionKm::getDeleted, false);
    }

    private LambdaQueryWrapper<RoadSectionHm> hmWrapper(String tenantId, String projectId) {
        return new LambdaQueryWrapper<RoadSectionHm>()
                .eq(RoadSectionHm::getTenantId, tenantId)
                .eq(RoadSectionHm::getProjectId, projectId)
                .eq(RoadSectionHm::getDeleted, false);
    }

    private List<DataMgmtQualityReportVO.MetricItemVO> loadDiseaseGroupCounts(String tenantId, String projectId,
                                                                               String column, String emptyLabel) {
        QueryWrapper<DiseaseRecord> wrapper = new QueryWrapper<>();
        wrapper.select("COALESCE(NULLIF(" + column + ", ''), '" + emptyLabel + "') AS label", "COUNT(*) AS value")
                .eq("tenant_id", tenantId)
                .eq("project_id", projectId)
                .eq("deleted", false)
                .groupBy("COALESCE(NULLIF(" + column + ", ''), '" + emptyLabel + "')")
                .orderByDesc("COUNT(*)");
        List<Map<String, Object>> rows = diseaseRecordMapper.selectMaps(wrapper);
        List<DataMgmtQualityReportVO.MetricItemVO> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            out.add(metric(String.valueOf(row.get("label")), numberValue(row.get("value"))));
        }
        return out;
    }

    private List<DataMgmtQualityReportVO.MetricItemVO> metricItems(DataMgmtQualityReportVO.MetricItemVO... items) {
        List<DataMgmtQualityReportVO.MetricItemVO> out = new ArrayList<>();
        for (DataMgmtQualityReportVO.MetricItemVO item : items) {
            out.add(item);
        }
        return out;
    }

    private DataMgmtQualityReportVO.MetricItemVO metric(String label, long value) {
        DataMgmtQualityReportVO.MetricItemVO item = new DataMgmtQualityReportVO.MetricItemVO();
        item.setLabel(label);
        item.setValue(value);
        return item;
    }

    private long numberValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }
}
