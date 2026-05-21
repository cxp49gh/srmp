package com.smartroad.srmp.roadasset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.common.exception.BizException;
import com.smartroad.srmp.common.util.IdUtils;
import com.smartroad.srmp.roadasset.dto.DataMgmtAuditLogQueryDTO;
import com.smartroad.srmp.roadasset.entity.DataMgmtAuditLog;
import com.smartroad.srmp.roadasset.entity.DataMgmtProject;
import com.smartroad.srmp.roadasset.mapper.DataMgmtAuditLogMapper;
import com.smartroad.srmp.roadasset.mapper.DataMgmtProjectMapper;
import com.smartroad.srmp.roadasset.service.DataMgmtAuditLogService;
import com.smartroad.srmp.roadasset.vo.DataMgmtAuditLogVO;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DataMgmtAuditLogServiceImpl implements DataMgmtAuditLogService {

    @Resource
    private DataMgmtAuditLogMapper dataMgmtAuditLogMapper;
    @Resource
    private DataMgmtProjectMapper dataMgmtProjectMapper;

    @Override
    public void log(String projectId, String projectName, String operationType, String result,
                    String reason, String snapshotBefore, String snapshotAfter) {
        DataMgmtAuditLog e = new DataMgmtAuditLog();
        e.setId(IdUtils.uuid());
        e.setTenantId(TenantContextHolder.getTenantId());
        e.setProjectId(projectId);
        e.setProjectName(resolveProjectName(projectId, projectName));
        e.setOperationType(operationType);
        e.setOperator(resolveOperator());
        e.setOperatedAt(LocalDateTime.now());
        e.setResult(result);
        e.setReason(reason);
        e.setSnapshotBefore(snapshotBefore);
        e.setSnapshotAfter(snapshotAfter);
        e.setCreatedAt(LocalDateTime.now());
        e.setDeleted(false);
        dataMgmtAuditLogMapper.insert(e);
    }

    @Override
    public PageResult<DataMgmtAuditLogVO> page(DataMgmtAuditLogQueryDTO query) {
        String tenantId = TenantContextHolder.getTenantId();
        LambdaQueryWrapper<DataMgmtAuditLog> w = new LambdaQueryWrapper<>();
        w.eq(DataMgmtAuditLog::getTenantId, tenantId).eq(DataMgmtAuditLog::getDeleted, false);
        if (StringUtils.hasText(query.getProjectId())) {
            w.eq(DataMgmtAuditLog::getProjectId, query.getProjectId().trim());
        }
        if (StringUtils.hasText(query.getOperationType())) {
            w.eq(DataMgmtAuditLog::getOperationType, query.getOperationType().trim());
        }
        if (StringUtils.hasText(query.getOperator())) {
            w.like(DataMgmtAuditLog::getOperator, query.getOperator().trim());
        }
        if (query.getOperatedFrom() != null) {
            w.ge(DataMgmtAuditLog::getOperatedAt, query.getOperatedFrom());
        }
        if (query.getOperatedTo() != null) {
            w.le(DataMgmtAuditLog::getOperatedAt, query.getOperatedTo());
        }
        w.orderByDesc(DataMgmtAuditLog::getOperatedAt);
        Page<DataMgmtAuditLog> mp = new Page<>(query.getPageNo(), query.getPageSize());
        Page<DataMgmtAuditLog> out = dataMgmtAuditLogMapper.selectPage(mp, w);
        List<DataMgmtAuditLogVO> records = out.getRecords().stream().map(this::toVo).collect(Collectors.toList());
        enrichProjectNames(records);
        PageResult<DataMgmtAuditLogVO> r = new PageResult<>();
        r.setPageNo(query.getPageNo());
        r.setPageSize(query.getPageSize());
        r.setTotal(out.getTotal());
        r.setRecords(records);
        return r;
    }

    @Override
    public DataMgmtAuditLogVO getById(String id) {
        DataMgmtAuditLog e = dataMgmtAuditLogMapper.selectOne(new LambdaQueryWrapper<DataMgmtAuditLog>()
                .eq(DataMgmtAuditLog::getId, id)
                .eq(DataMgmtAuditLog::getTenantId, TenantContextHolder.getTenantId())
                .eq(DataMgmtAuditLog::getDeleted, false)
                .last("LIMIT 1"));
        if (e == null) {
            throw new BizException("审计记录不存在");
        }
        DataMgmtAuditLogVO vo = toVo(e);
        vo.setProjectName(resolveProjectName(vo.getProjectId(), vo.getProjectName()));
        return vo;
    }

    private String resolveOperator() {
        String tenant = TenantContextHolder.getTenantId();
        return StringUtils.hasText(tenant) ? tenant : "system";
    }

    private DataMgmtAuditLogVO toVo(DataMgmtAuditLog e) {
        DataMgmtAuditLogVO v = new DataMgmtAuditLogVO();
        v.setId(e.getId());
        v.setProjectId(e.getProjectId());
        v.setProjectName(e.getProjectName());
        v.setOperationType(e.getOperationType());
        v.setOperator(e.getOperator());
        v.setOperatedAt(e.getOperatedAt());
        v.setResult(e.getResult());
        v.setReason(e.getReason());
        v.setSnapshotBefore(e.getSnapshotBefore());
        v.setSnapshotAfter(e.getSnapshotAfter());
        return v;
    }

    private String resolveProjectName(String projectId, String projectName) {
        if (StringUtils.hasText(projectName) || !StringUtils.hasText(projectId)) {
            return projectName;
        }
        DataMgmtProject project = dataMgmtProjectMapper.selectOne(new LambdaQueryWrapper<DataMgmtProject>()
                .eq(DataMgmtProject::getId, projectId)
                .eq(DataMgmtProject::getTenantId, TenantContextHolder.getTenantId())
                .last("LIMIT 1"));
        return project != null ? project.getName() : projectName;
    }

    private void enrichProjectNames(List<DataMgmtAuditLogVO> records) {
        Set<String> projectIds = records.stream()
                .filter(v -> !StringUtils.hasText(v.getProjectName()) && StringUtils.hasText(v.getProjectId()))
                .map(DataMgmtAuditLogVO::getProjectId)
                .collect(Collectors.toSet());
        if (projectIds.isEmpty()) {
            return;
        }
        Map<String, String> projectNames = loadProjectNames(projectIds);
        for (DataMgmtAuditLogVO record : records) {
            if (!StringUtils.hasText(record.getProjectName())) {
                record.setProjectName(projectNames.get(record.getProjectId()));
            }
        }
    }

    private Map<String, String> loadProjectNames(Set<String> projectIds) {
        if (projectIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DataMgmtProject> projects = dataMgmtProjectMapper.selectList(new LambdaQueryWrapper<DataMgmtProject>()
                .eq(DataMgmtProject::getTenantId, TenantContextHolder.getTenantId())
                .in(DataMgmtProject::getId, projectIds));
        return projects.stream().collect(Collectors.toMap(DataMgmtProject::getId, DataMgmtProject::getName, (a, b) -> a));
    }
}
