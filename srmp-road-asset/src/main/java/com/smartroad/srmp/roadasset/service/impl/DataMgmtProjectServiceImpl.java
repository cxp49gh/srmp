package com.smartroad.srmp.roadasset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.common.exception.BizException;
import com.smartroad.srmp.common.util.IdUtils;
import com.smartroad.srmp.roadasset.dto.DataMgmtProjectQueryDTO;
import com.smartroad.srmp.roadasset.dto.DataMgmtProjectSaveDTO;
import com.smartroad.srmp.roadasset.entity.DataMgmtProject;
import com.smartroad.srmp.roadasset.datamgmt.DataMgmtClearScope;
import com.smartroad.srmp.roadasset.mapper.DataMgmtProjectMapper;
import com.smartroad.srmp.roadasset.datamgmt.DataMgmtAuditOperationType;
import com.smartroad.srmp.roadasset.service.DataMgmtAuditLogService;
import com.smartroad.srmp.roadasset.service.DataMgmtClearService;
import com.smartroad.srmp.roadasset.service.DataMgmtProjectService;
import com.smartroad.srmp.roadasset.service.DataMgmtStatsService;
import com.smartroad.srmp.roadasset.vo.DataMgmtProjectVO;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DataMgmtProjectServiceImpl implements DataMgmtProjectService {

    @Resource
    private DataMgmtProjectMapper dataMgmtProjectMapper;
    @Resource
    @Lazy
    private DataMgmtClearService dataMgmtClearService;
    @Resource
    private DataMgmtStatsService dataMgmtStatsService;
    @Resource
    private DataMgmtAuditLogService dataMgmtAuditLogService;

    @Override
    public PageResult<DataMgmtProjectVO> page(DataMgmtProjectQueryDTO query) {
        String tenantId = TenantContextHolder.getTenantId();
        LambdaQueryWrapper<DataMgmtProject> w = new LambdaQueryWrapper<>();
        w.eq(DataMgmtProject::getTenantId, tenantId).eq(DataMgmtProject::getDeleted, false);
        if (!Boolean.TRUE.equals(query.getIncludeArchived())) {
            w.and(q -> q.isNull(DataMgmtProject::getArchived).or().eq(DataMgmtProject::getArchived, false));
        }
        if (StringUtils.hasText(query.getNameKeyword())) {
            w.like(DataMgmtProject::getName, query.getNameKeyword().trim());
        }
        w.orderByDesc(DataMgmtProject::getUpdatedAt);
        Page<DataMgmtProject> mp = new Page<>(query.getPageNo(), query.getPageSize());
        Page<DataMgmtProject> out = dataMgmtProjectMapper.selectPage(mp, w);
        List<DataMgmtProjectVO> records = out.getRecords().stream().map(this::toVo).collect(Collectors.toList());
        dataMgmtStatsService.enrichSummaries(records);
        PageResult<DataMgmtProjectVO> r = new PageResult<>();
        r.setPageNo(query.getPageNo());
        r.setPageSize(query.getPageSize());
        r.setTotal(out.getTotal());
        r.setRecords(records);
        return r;
    }

    @Override
    public String create(DataMgmtProjectSaveDTO dto) {
        DataMgmtProject e = new DataMgmtProject();
        e.setId(IdUtils.uuid());
        e.setTenantId(TenantContextHolder.getTenantId());
        e.setName(dto.getName().trim());
        e.setRemark(dto.getRemark());
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        e.setDeleted(false);
        e.setArchived(false);
        dataMgmtProjectMapper.insert(e);
        dataMgmtAuditLogService.log(e.getId(), e.getName(), DataMgmtAuditOperationType.PROJECT_CREATE,
                "SUCCESS", null, null, "{\"name\":\"" + e.getName() + "\"}");
        return e.getId();
    }

    @Override
    public void requireExists(String projectId) {
        if (!StringUtils.hasText(projectId)) {
            throw new BizException("项目不存在");
        }
        DataMgmtProject p = dataMgmtProjectMapper.selectOne(new LambdaQueryWrapper<DataMgmtProject>()
                .eq(DataMgmtProject::getId, projectId)
                .eq(DataMgmtProject::getTenantId, TenantContextHolder.getTenantId())
                .eq(DataMgmtProject::getDeleted, false)
                .last("LIMIT 1"));
        if (p == null) {
            throw new BizException("项目不存在或已删除");
        }
    }

    @Override
    public DataMgmtProjectVO getById(String id) {
        DataMgmtProject p = dataMgmtProjectMapper.selectOne(new LambdaQueryWrapper<DataMgmtProject>()
                .eq(DataMgmtProject::getId, id)
                .eq(DataMgmtProject::getTenantId, TenantContextHolder.getTenantId())
                .eq(DataMgmtProject::getDeleted, false)
                .last("LIMIT 1"));
        if (p == null) {
            throw new BizException("项目不存在");
        }
        DataMgmtProjectVO vo = toVo(p);
        vo.setSummary(dataMgmtStatsService.getSummary(id));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(String id) {
        DataMgmtProject p = loadActive(id);
        if (Boolean.TRUE.equals(p.getArchived())) {
            throw new BizException("项目已归档");
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<DataMgmtProject> w = new LambdaUpdateWrapper<>();
        w.eq(DataMgmtProject::getId, id)
                .set(DataMgmtProject::getArchived, true)
                .set(DataMgmtProject::getArchivedAt, now)
                .set(DataMgmtProject::getArchivedBy, TenantContextHolder.getTenantId())
                .set(DataMgmtProject::getUpdatedAt, now);
        dataMgmtProjectMapper.update(null, w);
        dataMgmtAuditLogService.log(id, p.getName(), DataMgmtAuditOperationType.PROJECT_ARCHIVE,
                "SUCCESS", null, null, "{\"archived\":true}");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(String id) {
        DataMgmtProject p = loadActive(id);
        if (!Boolean.TRUE.equals(p.getArchived())) {
            throw new BizException("项目未归档");
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<DataMgmtProject> w = new LambdaUpdateWrapper<>();
        w.eq(DataMgmtProject::getId, id)
                .set(DataMgmtProject::getArchived, false)
                .set(DataMgmtProject::getArchivedAt, null)
                .set(DataMgmtProject::getArchivedBy, null)
                .set(DataMgmtProject::getUpdatedAt, now);
        dataMgmtProjectMapper.update(null, w);
        dataMgmtAuditLogService.log(id, p.getName(), DataMgmtAuditOperationType.PROJECT_RESTORE,
                "SUCCESS", null, null, "{\"archived\":false}");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        DataMgmtProject p = loadActive(id);
        String before = dataMgmtStatsService.getSummary(id).toString();
        dataMgmtClearService.clearByProject(id, DataMgmtClearScope.ALL);
        String tenantId = TenantContextHolder.getTenantId();
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<DataMgmtProject> w = new LambdaUpdateWrapper<>();
        w.eq(DataMgmtProject::getTenantId, tenantId)
                .eq(DataMgmtProject::getId, id)
                .eq(DataMgmtProject::getDeleted, false)
                .set(DataMgmtProject::getDeleted, true)
                .set(DataMgmtProject::getUpdatedAt, now);
        int n = dataMgmtProjectMapper.update(null, w);
        if (n == 0) {
            throw new BizException("项目不存在或已删除");
        }
        dataMgmtAuditLogService.log(id, p.getName(), DataMgmtAuditOperationType.PROJECT_DELETE,
                "SUCCESS", null, before, null);
    }

    private DataMgmtProject loadActive(String id) {
        requireExists(id);
        return dataMgmtProjectMapper.selectOne(new LambdaQueryWrapper<DataMgmtProject>()
                .eq(DataMgmtProject::getId, id)
                .eq(DataMgmtProject::getTenantId, TenantContextHolder.getTenantId())
                .eq(DataMgmtProject::getDeleted, false)
                .last("LIMIT 1"));
    }

    private DataMgmtProjectVO toVo(DataMgmtProject e) {
        DataMgmtProjectVO v = new DataMgmtProjectVO();
        v.setId(e.getId());
        v.setName(e.getName());
        v.setRemark(e.getRemark());
        v.setArchived(e.getArchived());
        v.setArchivedAt(e.getArchivedAt());
        v.setCreatedAt(e.getCreatedAt());
        v.setUpdatedAt(e.getUpdatedAt());
        return v;
    }
}
