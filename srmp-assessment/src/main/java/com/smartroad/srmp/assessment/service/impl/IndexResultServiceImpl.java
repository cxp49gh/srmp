package com.smartroad.srmp.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartroad.srmp.assessment.dto.IndexResultQueryDTO;
import com.smartroad.srmp.assessment.dto.IndexResultSaveDTO;
import com.smartroad.srmp.assessment.entity.IndexResult;
import com.smartroad.srmp.assessment.mapper.IndexResultMapper;
import com.smartroad.srmp.assessment.service.IndexResultService;
import com.smartroad.srmp.assessment.vo.IndexResultVO;
import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.common.exception.BizException;
import com.smartroad.srmp.common.util.IdUtils;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class IndexResultServiceImpl implements IndexResultService {
    @Resource private IndexResultMapper mapper;

    public PageResult<IndexResultVO> page(IndexResultQueryDTO query) {
        Page<IndexResultVO> page = mapper.selectPageVO(new Page<>(query.getPageNo(), query.getPageSize()), TenantContextHolder.getTenantId(), query);
        PageResult<IndexResultVO> r = new PageResult<>(); r.setPageNo(query.getPageNo()); r.setPageSize(query.getPageSize()); r.setTotal(page.getTotal()); r.setRecords(page.getRecords()); return r;
    }
    public IndexResultVO getById(String id) {
        IndexResultVO vo = mapper.selectDetail(TenantContextHolder.getTenantId(), id);
        if (vo == null) throw new BizException("指标结果不存在");
        return vo;
    }
    public String create(IndexResultSaveDTO dto) {
        IndexResult e = new IndexResult(); BeanUtils.copyProperties(dto, e);
        e.setId(IdUtils.uuid()); e.setTenantId(TenantContextHolder.getTenantId()); e.setCreatedAt(LocalDateTime.now()); e.setUpdatedAt(LocalDateTime.now()); e.setDeleted(false); e.setCalculatedAt(LocalDateTime.now());
        mapper.insertWithJson(e); return e.getId();
    }
    public void update(String id, IndexResultSaveDTO dto) {
        getById(id); IndexResult e = new IndexResult(); BeanUtils.copyProperties(dto, e); e.setId(id); e.setTenantId(TenantContextHolder.getTenantId()); e.setUpdatedAt(LocalDateTime.now()); mapper.updateWithJson(e);
    }
    public void delete(String id) {
        LambdaUpdateWrapper<IndexResult> w = new LambdaUpdateWrapper<>(); w.eq(IndexResult::getTenantId, TenantContextHolder.getTenantId()).eq(IndexResult::getId, id).set(IndexResult::getDeleted, true);
        mapper.update(null, w);
    }
}
