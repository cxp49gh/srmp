package com.smartroad.srmp.roadasset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.roadasset.dto.DataImportRecordQueryDTO;
import com.smartroad.srmp.roadasset.entity.DataImportRecord;
import com.smartroad.srmp.roadasset.mapper.DataImportRecordMapper;
import com.smartroad.srmp.roadasset.service.DataImportRecordService;
import com.smartroad.srmp.roadasset.vo.DataImportRecordVO;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DataImportRecordServiceImpl implements DataImportRecordService {

    @Resource
    private DataImportRecordMapper dataImportRecordMapper;

    @Override
    public PageResult<DataImportRecordVO> pageByProject(String projectId, DataImportRecordQueryDTO query) {
        String tenantId = TenantContextHolder.getTenantId();
        LambdaQueryWrapper<DataImportRecord> w = new LambdaQueryWrapper<>();
        w.eq(DataImportRecord::getTenantId, tenantId)
                .eq(DataImportRecord::getProjectId, projectId)
                .eq(DataImportRecord::getDeleted, false)
                .orderByDesc(DataImportRecord::getStartedAt);
        Page<DataImportRecord> mp = new Page<>(query.getPageNo(), query.getPageSize());
        Page<DataImportRecord> out = dataImportRecordMapper.selectPage(mp, w);
        List<DataImportRecordVO> records = out.getRecords().stream().map(this::toVo).collect(Collectors.toList());
        PageResult<DataImportRecordVO> r = new PageResult<>();
        r.setPageNo(query.getPageNo());
        r.setPageSize(query.getPageSize());
        r.setTotal(out.getTotal());
        r.setRecords(records);
        return r;
    }

    private DataImportRecordVO toVo(DataImportRecord e) {
        DataImportRecordVO v = new DataImportRecordVO();
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
        return v;
    }
}
