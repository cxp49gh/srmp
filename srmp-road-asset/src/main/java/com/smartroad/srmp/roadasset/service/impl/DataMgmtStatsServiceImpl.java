package com.smartroad.srmp.roadasset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartroad.srmp.common.exception.BizException;
import com.smartroad.srmp.disease.entity.DiseaseRecord;
import com.smartroad.srmp.disease.mapper.DiseaseRecordMapper;
import com.smartroad.srmp.roadasset.datamgmt.DataMgmtProjectStatus;
import com.smartroad.srmp.roadasset.entity.DataImportRecord;
import com.smartroad.srmp.roadasset.entity.DataMgmtProject;
import com.smartroad.srmp.roadasset.entity.RoadEvaluationUnit;
import com.smartroad.srmp.roadasset.entity.RoadRoute;
import com.smartroad.srmp.roadasset.entity.RoadSection;
import com.smartroad.srmp.roadasset.entity.RoadSectionHm;
import com.smartroad.srmp.roadasset.entity.RoadSectionKm;
import com.smartroad.srmp.roadasset.mapper.DataImportRecordMapper;
import com.smartroad.srmp.roadasset.mapper.DataMgmtProjectMapper;
import com.smartroad.srmp.roadasset.mapper.RoadEvaluationUnitMapper;
import com.smartroad.srmp.roadasset.mapper.RoadRouteMapper;
import com.smartroad.srmp.roadasset.mapper.RoadSectionHmMapper;
import com.smartroad.srmp.roadasset.mapper.RoadSectionKmMapper;
import com.smartroad.srmp.roadasset.mapper.RoadSectionMapper;
import com.smartroad.srmp.roadasset.service.DataMgmtStatsService;
import com.smartroad.srmp.roadasset.vo.DataMgmtProjectSummaryVO;
import com.smartroad.srmp.roadasset.vo.DataMgmtProjectVO;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DataMgmtStatsServiceImpl implements DataMgmtStatsService {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    @Resource
    private DataMgmtProjectMapper dataMgmtProjectMapper;
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
    public DataMgmtProjectSummaryVO getSummary(String projectId) {
        requireProjectExists(projectId);
        return buildSummary(projectId, countRoutes(projectId), countSections(projectId), countDiseases(projectId),
                loadLatestImport(projectId), loadLastSuccessImport(projectId), countImportRecords(projectId));
    }

    @Override
    public void enrichSummaries(List<DataMgmtProjectVO> projects) {
        if (CollectionUtils.isEmpty(projects)) {
            return;
        }
        List<String> ids = projects.stream().map(DataMgmtProjectVO::getId).collect(Collectors.toList());
        Map<String, DataMgmtProjectSummaryVO> map = batchSummaries(ids);
        for (DataMgmtProjectVO p : projects) {
            p.setSummary(map.getOrDefault(p.getId(), emptySummary()));
        }
    }

    @Override
    public long countRoutes(String projectId) {
        return roadRouteMapper.selectCount(routeWrapper(projectId));
    }

    @Override
    public boolean isRoadNetworkReady(String projectId) {
        return countRoutes(projectId) > 0;
    }

    @Override
    public Map<String, DataMgmtProjectSummaryVO> batchSummaries(List<String> projectIds) {
        if (CollectionUtils.isEmpty(projectIds)) {
            return Collections.emptyMap();
        }
        String tenantId = TenantContextHolder.getTenantId();
        Map<String, Long> routes = countByProject(roadRouteMapper.selectList(
                new LambdaQueryWrapper<RoadRoute>()
                        .eq(RoadRoute::getTenantId, tenantId)
                        .eq(RoadRoute::getDeleted, false)
                        .in(RoadRoute::getProjectId, projectIds)
                        .select(RoadRoute::getProjectId)));
        Map<String, Long> sections = new HashMap<>();
        for (String pid : projectIds) {
            sections.put(pid, countSections(pid));
        }
        Map<String, Long> diseases = countByProject(diseaseRecordMapper.selectList(
                new LambdaQueryWrapper<DiseaseRecord>()
                        .eq(DiseaseRecord::getTenantId, tenantId)
                        .eq(DiseaseRecord::getDeleted, false)
                        .in(DiseaseRecord::getProjectId, projectIds)
                        .select(DiseaseRecord::getProjectId)));
        Map<String, DataImportRecord> latest = loadLatestImports(projectIds);
        Map<String, DataImportRecord> lastSuccess = loadLastSuccessImports(projectIds);
        Map<String, Long> importCounts = new HashMap<>();
        for (String pid : projectIds) {
            importCounts.put(pid, countImportRecords(pid));
        }

        Map<String, DataMgmtProjectSummaryVO> out = new HashMap<>();
        for (String pid : projectIds) {
            long r = routes.getOrDefault(pid, 0L);
            long s = sections.getOrDefault(pid, 0L);
            long d = diseases.getOrDefault(pid, 0L);
            out.put(pid, buildSummary(pid, r, s, d, latest.get(pid), lastSuccess.get(pid), importCounts.getOrDefault(pid, 0L)));
        }
        return out;
    }

    private Map<String, Long> countByProject(List<?> rows) {
        Map<String, Long> m = new HashMap<>();
        if (rows == null) {
            return m;
        }
        for (Object o : rows) {
            String pid = null;
            if (o instanceof RoadRoute) {
                pid = ((RoadRoute) o).getProjectId();
            } else if (o instanceof DiseaseRecord) {
                pid = ((DiseaseRecord) o).getProjectId();
            }
            if (StringUtils.hasText(pid)) {
                m.merge(pid, 1L, Long::sum);
            }
        }
        return m;
    }

    private long countSections(String projectId) {
        String tenantId = TenantContextHolder.getTenantId();
        long n = 0;
        n += roadSectionMapper.selectCount(new LambdaQueryWrapper<RoadSection>()
                .eq(RoadSection::getTenantId, tenantId)
                .eq(RoadSection::getProjectId, projectId)
                .eq(RoadSection::getDeleted, false));
        n += roadEvaluationUnitMapper.selectCount(new LambdaQueryWrapper<RoadEvaluationUnit>()
                .eq(RoadEvaluationUnit::getTenantId, tenantId)
                .eq(RoadEvaluationUnit::getProjectId, projectId)
                .eq(RoadEvaluationUnit::getDeleted, false));
        n += roadSectionKmMapper.selectCount(new LambdaQueryWrapper<RoadSectionKm>()
                .eq(RoadSectionKm::getTenantId, tenantId)
                .eq(RoadSectionKm::getProjectId, projectId)
                .eq(RoadSectionKm::getDeleted, false));
        n += roadSectionHmMapper.selectCount(new LambdaQueryWrapper<RoadSectionHm>()
                .eq(RoadSectionHm::getTenantId, tenantId)
                .eq(RoadSectionHm::getProjectId, projectId)
                .eq(RoadSectionHm::getDeleted, false));
        return n;
    }

    private long countDiseases(String projectId) {
        return diseaseRecordMapper.selectCount(new LambdaQueryWrapper<DiseaseRecord>()
                .eq(DiseaseRecord::getTenantId, TenantContextHolder.getTenantId())
                .eq(DiseaseRecord::getProjectId, projectId)
                .eq(DiseaseRecord::getDeleted, false));
    }

    private LambdaQueryWrapper<RoadRoute> routeWrapper(String projectId) {
        return new LambdaQueryWrapper<RoadRoute>()
                .eq(RoadRoute::getTenantId, TenantContextHolder.getTenantId())
                .eq(RoadRoute::getProjectId, projectId)
                .eq(RoadRoute::getDeleted, false);
    }

    private DataImportRecord loadLatestImport(String projectId) {
        return dataImportRecordMapper.selectOne(new LambdaQueryWrapper<DataImportRecord>()
                .eq(DataImportRecord::getTenantId, TenantContextHolder.getTenantId())
                .eq(DataImportRecord::getProjectId, projectId)
                .eq(DataImportRecord::getDeleted, false)
                .orderByDesc(DataImportRecord::getStartedAt)
                .last("LIMIT 1"));
    }

    private DataImportRecord loadLastSuccessImport(String projectId) {
        return dataImportRecordMapper.selectOne(new LambdaQueryWrapper<DataImportRecord>()
                .eq(DataImportRecord::getTenantId, TenantContextHolder.getTenantId())
                .eq(DataImportRecord::getProjectId, projectId)
                .eq(DataImportRecord::getDeleted, false)
                .eq(DataImportRecord::getStatus, STATUS_SUCCESS)
                .orderByDesc(DataImportRecord::getStartedAt)
                .last("LIMIT 1"));
    }

    private long countImportRecords(String projectId) {
        return dataImportRecordMapper.selectCount(new LambdaQueryWrapper<DataImportRecord>()
                .eq(DataImportRecord::getTenantId, TenantContextHolder.getTenantId())
                .eq(DataImportRecord::getProjectId, projectId)
                .eq(DataImportRecord::getDeleted, false));
    }

    private Map<String, DataImportRecord> loadLatestImports(List<String> projectIds) {
        List<DataImportRecord> all = dataImportRecordMapper.selectList(new LambdaQueryWrapper<DataImportRecord>()
                .eq(DataImportRecord::getTenantId, TenantContextHolder.getTenantId())
                .in(DataImportRecord::getProjectId, projectIds)
                .eq(DataImportRecord::getDeleted, false)
                .orderByDesc(DataImportRecord::getStartedAt));
        Map<String, DataImportRecord> map = new HashMap<>();
        for (DataImportRecord r : all) {
            map.putIfAbsent(r.getProjectId(), r);
        }
        return map;
    }

    private Map<String, DataImportRecord> loadLastSuccessImports(List<String> projectIds) {
        List<DataImportRecord> all = dataImportRecordMapper.selectList(new LambdaQueryWrapper<DataImportRecord>()
                .eq(DataImportRecord::getTenantId, TenantContextHolder.getTenantId())
                .in(DataImportRecord::getProjectId, projectIds)
                .eq(DataImportRecord::getDeleted, false)
                .eq(DataImportRecord::getStatus, STATUS_SUCCESS)
                .orderByDesc(DataImportRecord::getStartedAt));
        Map<String, DataImportRecord> map = new HashMap<>();
        for (DataImportRecord r : all) {
            map.putIfAbsent(r.getProjectId(), r);
        }
        return map;
    }

    private DataMgmtProjectSummaryVO buildSummary(String projectId, long routes, long sections, long diseases,
                                                  DataImportRecord latest, DataImportRecord lastSuccess,
                                                  long importRecordCount) {
        DataMgmtProjectSummaryVO s = new DataMgmtProjectSummaryVO();
        s.setRouteCount(routes);
        s.setSectionCount(sections);
        s.setDiseaseCount(diseases);
        s.setRoadNetworkReady(routes > 0);
        if (latest != null) {
            s.setLastImportType(latest.getImportType());
            s.setLastImportStatus(latest.getStatus());
            s.setLastImportTime(latest.getStartedAt());
        }
        if (lastSuccess != null) {
            s.setLastSuccessImportTime(lastSuccess.getFinishedAt() != null ? lastSuccess.getFinishedAt() : lastSuccess.getStartedAt());
        }
        s.setProjectStatus(computeStatus(routes, sections, diseases, latest, importRecordCount));
        return s;
    }

    private String computeStatus(long routes, long sections, long diseases, DataImportRecord latest, long importCount) {
        if (routes > 0) {
            if (sections > 0 || diseases > 0) {
                return DataMgmtProjectStatus.AVAILABLE;
            }
            return DataMgmtProjectStatus.PARTIAL;
        }
        if (sections > 0 || diseases > 0) {
            return DataMgmtProjectStatus.PARTIAL;
        }
        if (latest != null && STATUS_FAILED.equals(latest.getStatus())) {
            return DataMgmtProjectStatus.IMPORT_FAILED;
        }
        if (importCount > 0) {
            return DataMgmtProjectStatus.CLEARED;
        }
        return DataMgmtProjectStatus.NOT_IMPORTED;
    }

    private DataMgmtProjectSummaryVO emptySummary() {
        DataMgmtProjectSummaryVO s = new DataMgmtProjectSummaryVO();
        s.setProjectStatus(DataMgmtProjectStatus.NOT_IMPORTED);
        return s;
    }

    private void requireProjectExists(String projectId) {
        if (!StringUtils.hasText(projectId)) {
            throw new BizException("项目不存在");
        }
        Long count = dataMgmtProjectMapper.selectCount(new LambdaQueryWrapper<DataMgmtProject>()
                .eq(DataMgmtProject::getId, projectId)
                .eq(DataMgmtProject::getTenantId, TenantContextHolder.getTenantId())
                .eq(DataMgmtProject::getDeleted, false));
        if (count == null || count == 0) {
            throw new BizException("项目不存在或已删除");
        }
    }
}
