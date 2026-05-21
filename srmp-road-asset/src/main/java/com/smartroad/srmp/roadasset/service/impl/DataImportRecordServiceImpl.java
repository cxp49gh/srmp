package com.smartroad.srmp.roadasset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.common.exception.BizException;
import com.smartroad.srmp.roadasset.dto.DataImportRecordGlobalQueryDTO;
import com.smartroad.srmp.roadasset.dto.DataImportRecordQueryDTO;
import com.smartroad.srmp.roadasset.entity.DataImportRecord;
import com.smartroad.srmp.roadasset.entity.DataMgmtProject;
import com.smartroad.srmp.roadasset.mapper.DataImportRecordMapper;
import com.smartroad.srmp.roadasset.mapper.DataMgmtProjectMapper;
import com.smartroad.srmp.roadasset.service.DataImportRecordService;
import com.smartroad.srmp.roadasset.service.DataMgmtProjectService;
import com.smartroad.srmp.roadasset.vo.DataImportRecordDetailVO;
import com.smartroad.srmp.roadasset.vo.DataImportRecordVO;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DataImportRecordServiceImpl implements DataImportRecordService {

    @Resource
    private DataImportRecordMapper dataImportRecordMapper;
    @Resource
    private DataMgmtProjectMapper dataMgmtProjectMapper;
    @Resource
    private DataMgmtProjectService dataMgmtProjectService;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public PageResult<DataImportRecordVO> pageByProject(String projectId, DataImportRecordQueryDTO query) {
        dataMgmtProjectService.requireExists(projectId);
        return doPage(buildWrapper(projectId, null, null, null, null, null), query.getPageNo(), query.getPageSize(), false);
    }

    @Override
    public PageResult<DataImportRecordVO> pageGlobal(DataImportRecordGlobalQueryDTO query) {
        return doPage(buildWrapper(
                query.getProjectId(),
                query.getImportType(),
                query.getStatus(),
                query.getUploadedBy(),
                query.getStartedFrom(),
                query.getStartedTo()), query.getPageNo(), query.getPageSize(), true);
    }

    @Override
    public DataImportRecordDetailVO getDetail(String projectId, String recordId) {
        dataMgmtProjectService.requireExists(projectId);
        DataImportRecord e = dataImportRecordMapper.selectOne(new LambdaQueryWrapper<DataImportRecord>()
                .eq(DataImportRecord::getTenantId, TenantContextHolder.getTenantId())
                .eq(DataImportRecord::getProjectId, projectId)
                .eq(DataImportRecord::getId, recordId)
                .eq(DataImportRecord::getDeleted, false)
                .last("LIMIT 1"));
        if (e == null) {
            throw new BizException("导入记录不存在");
        }
        DataImportRecordDetailVO d = new DataImportRecordDetailVO();
        copyBase(e, d);
        d.setUploadedBy(e.getCreatedBy());
        DataMgmtProject p = dataMgmtProjectMapper.selectById(projectId);
        if (p != null) {
            d.setProjectName(p.getName());
        }
        d.setTechnicalInfo(e.getResultSummary());
        parseResultSummary(e, d);
        if (StringUtils.hasText(e.getMessage()) && "FAILED".equals(e.getStatus())) {
            DataImportRecordDetailVO.FailureDetailVO f = new DataImportRecordDetailVO.FailureDetailVO();
            f.setFileName(e.getFileName());
            f.setReason(e.getMessage());
            d.getFailureDetails().add(f);
        }
        return d;
    }

    private LambdaQueryWrapper<DataImportRecord> buildWrapper(String projectId, String importType, String status,
                                                              String uploadedBy,
                                                              java.time.LocalDateTime from,
                                                              java.time.LocalDateTime to) {
        LambdaQueryWrapper<DataImportRecord> w = new LambdaQueryWrapper<>();
        w.eq(DataImportRecord::getTenantId, TenantContextHolder.getTenantId())
                .eq(DataImportRecord::getDeleted, false);
        if (StringUtils.hasText(projectId)) {
            w.eq(DataImportRecord::getProjectId, projectId.trim());
        }
        if (StringUtils.hasText(importType)) {
            w.eq(DataImportRecord::getImportType, importType.trim());
        }
        if (StringUtils.hasText(status)) {
            w.eq(DataImportRecord::getStatus, status.trim());
        }
        if (StringUtils.hasText(uploadedBy)) {
            w.like(DataImportRecord::getCreatedBy, uploadedBy.trim());
        }
        if (from != null) {
            w.ge(DataImportRecord::getStartedAt, from);
        }
        if (to != null) {
            w.le(DataImportRecord::getStartedAt, to);
        }
        w.orderByDesc(DataImportRecord::getStartedAt);
        return w;
    }

    private PageResult<DataImportRecordVO> doPage(LambdaQueryWrapper<DataImportRecord> w, int pageNo, int pageSize,
                                                  boolean withProjectName) {
        Page<DataImportRecord> mp = new Page<>(pageNo, pageSize);
        Page<DataImportRecord> out = dataImportRecordMapper.selectPage(mp, w);
        Map<String, String> projectNames = withProjectName ? loadProjectNames(out.getRecords()) : Collections.emptyMap();
        List<DataImportRecordVO> records = out.getRecords().stream().map(e -> {
            DataImportRecordVO v = toVo(e);
            if (withProjectName) {
                v.setProjectName(projectNames.get(e.getProjectId()));
            }
            v.setUploadedBy(e.getCreatedBy());
            return v;
        }).collect(Collectors.toList());
        PageResult<DataImportRecordVO> r = new PageResult<>();
        r.setPageNo(pageNo);
        r.setPageSize(pageSize);
        r.setTotal(out.getTotal());
        r.setRecords(records);
        return r;
    }

    private Map<String, String> loadProjectNames(List<DataImportRecord> records) {
        List<String> ids = records.stream().map(DataImportRecord::getProjectId).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DataMgmtProject> projects = dataMgmtProjectMapper.selectList(new LambdaQueryWrapper<DataMgmtProject>()
                .eq(DataMgmtProject::getTenantId, TenantContextHolder.getTenantId())
                .in(DataMgmtProject::getId, ids));
        return projects.stream().collect(Collectors.toMap(DataMgmtProject::getId, DataMgmtProject::getName, (a, b) -> a));
    }

    private void parseResultSummary(DataImportRecord e, DataImportRecordDetailVO d) {
        if (!StringUtils.hasText(e.getResultSummary())) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(e.getResultSummary());
            d.setResultStats(node);
            if (node.has("warnings") && node.get("warnings").isArray()) {
                List<String> warnings = new ArrayList<>();
                node.get("warnings").forEach(w -> warnings.add(w.asText()));
                d.setWarnings(warnings);
            }
            if (node.has("skippedMissingRouteCount") && node.get("skippedMissingRouteCount").asInt() > 0) {
                d.getWarnings().add("未匹配路网路线编码行数：" + node.get("skippedMissingRouteCount").asInt());
            }
        } catch (Exception ex) {
            d.setResultStats(e.getResultSummary());
        }
    }

    private DataImportRecordVO toVo(DataImportRecord e) {
        DataImportRecordVO v = new DataImportRecordVO();
        copyBase(e, v);
        return v;
    }

    private void copyBase(DataImportRecord e, DataImportRecordVO v) {
        v.setId(e.getId());
        v.setProjectId(e.getProjectId());
        v.setImportType(e.getImportType());
        v.setFileName(e.getFileName());
        v.setStartedAt(e.getStartedAt());
        v.setFinishedAt(e.getFinishedAt());
        v.setDurationMs(e.getDurationMs());
        v.setStatus(e.getStatus());
        v.setMessage(e.getMessage());
        v.setResultSummary(e.getResultSummary());
    }
}
