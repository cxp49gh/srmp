package com.smartroad.srmp.disease.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.common.exception.BizException;
import com.smartroad.srmp.common.util.IdUtils;
import com.smartroad.srmp.disease.dto.DiseaseQueryDTO;
import com.smartroad.srmp.disease.dto.DiseaseReviewDTO;
import com.smartroad.srmp.disease.dto.DiseaseSaveDTO;
import com.smartroad.srmp.disease.entity.DiseaseRecord;
import com.smartroad.srmp.disease.mapper.DiseaseRecordMapper;
import com.smartroad.srmp.disease.service.DiseaseRecordService;
import com.smartroad.srmp.disease.vo.DiseaseRecordVO;
import com.smartroad.srmp.disease.vo.DiseaseStatisticsVO;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DiseaseRecordServiceImpl implements DiseaseRecordService {
    /** 单次 multi-row INSERT 行数上限（PostgreSQL 占位符约 65535，每行约 30 个参数） */
    private static final int INSERT_SQL_BATCH_SIZE = 1000;

    @Resource private DiseaseRecordMapper mapper;

    public PageResult<DiseaseRecordVO> page(DiseaseQueryDTO query) {
        Page<DiseaseRecordVO> page = mapper.selectPageVO(new Page<>(query.getPageNo(), query.getPageSize()), TenantContextHolder.getTenantId(), query);
        PageResult<DiseaseRecordVO> r = new PageResult<>(); r.setPageNo(query.getPageNo()); r.setPageSize(query.getPageSize()); r.setTotal(page.getTotal()); r.setRecords(page.getRecords()); return r;
    }
    public DiseaseRecordVO getById(String id) {
        DiseaseRecordVO vo = mapper.selectDetail(TenantContextHolder.getTenantId(), id);
        if (vo == null) throw new BizException("病害记录不存在");
        return vo;
    }
    public String create(DiseaseSaveDTO dto) {
        DiseaseRecord e = new DiseaseRecord(); BeanUtils.copyProperties(dto, e);
        e.setId(IdUtils.uuid()); e.setTenantId(TenantContextHolder.getTenantId()); e.setCreatedAt(LocalDateTime.now()); e.setUpdatedAt(LocalDateTime.now()); e.setDeleted(false);
        if (e.getStatus() == null) e.setStatus("UNPROCESSED");
        if (e.getVerified() == null) e.setVerified(false);
        mapper.insertWithGeom(e); return e.getId();
    }

    @Override
    public void createBatch(List<DiseaseSaveDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        String tenantId = TenantContextHolder.getTenantId();
        LocalDateTime now = LocalDateTime.now();
        List<DiseaseRecord> records = new ArrayList<>(dtos.size());
        for (DiseaseSaveDTO dto : dtos) {
            DiseaseRecord e = new DiseaseRecord();
            BeanUtils.copyProperties(dto, e);
            e.setId(IdUtils.uuid());
            e.setTenantId(tenantId);
            e.setCreatedAt(now);
            e.setUpdatedAt(now);
            e.setDeleted(false);
            if (e.getStatus() == null) {
                e.setStatus("UNPROCESSED");
            }
            if (e.getVerified() == null) {
                e.setVerified(false);
            }
            records.add(e);
        }
        for (int i = 0; i < records.size(); i += INSERT_SQL_BATCH_SIZE) {
            int end = Math.min(i + INSERT_SQL_BATCH_SIZE, records.size());
            mapper.insertBatchWithGeom(records.subList(i, end));
        }
    }
    public void update(String id, DiseaseSaveDTO dto) {
        getById(id); DiseaseRecord e = new DiseaseRecord(); BeanUtils.copyProperties(dto, e); e.setId(id); e.setTenantId(TenantContextHolder.getTenantId()); mapper.updateWithGeom(e);
    }
    public void delete(String id) {
        LambdaUpdateWrapper<DiseaseRecord> w = new LambdaUpdateWrapper<>(); w.eq(DiseaseRecord::getTenantId, TenantContextHolder.getTenantId()).eq(DiseaseRecord::getId, id).set(DiseaseRecord::getDeleted, true);
        mapper.update(null, w);
    }
    public void review(DiseaseReviewDTO dto) {
        if (dto == null || dto.getId() == null) throw new BizException("病害ID不能为空");
        getById(dto.getId());
        LambdaUpdateWrapper<DiseaseRecord> w = new LambdaUpdateWrapper<>();
        w.eq(DiseaseRecord::getTenantId, TenantContextHolder.getTenantId()).eq(DiseaseRecord::getId, dto.getId())
         .set(DiseaseRecord::getVerified, dto.getVerified() == null ? true : dto.getVerified())
         .set(DiseaseRecord::getStatus, dto.getStatus() == null ? "VERIFIED" : dto.getStatus())
         .set(DiseaseRecord::getRemark, dto.getRemark())
         .set(DiseaseRecord::getVerifiedAt, LocalDateTime.now());
        mapper.update(null, w);
    }
    public List<DiseaseRecordVO> listForMap(DiseaseQueryDTO query) { return mapper.selectForMap(TenantContextHolder.getTenantId(), query); }
    public DiseaseStatisticsVO statistics(DiseaseQueryDTO query) { return mapper.selectStatistics(TenantContextHolder.getTenantId(), query); }
}
