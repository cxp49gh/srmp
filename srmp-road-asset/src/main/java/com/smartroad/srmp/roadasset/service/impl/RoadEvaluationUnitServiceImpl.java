package com.smartroad.srmp.roadasset.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.common.exception.BizException;
import com.smartroad.srmp.common.util.IdUtils;
import com.smartroad.srmp.roadasset.dto.*;
import com.smartroad.srmp.roadasset.entity.RoadEvaluationUnit;
import com.smartroad.srmp.roadasset.mapper.RoadEvaluationUnitMapper;
import com.smartroad.srmp.roadasset.service.RoadEvaluationUnitService;
import com.smartroad.srmp.roadasset.vo.*;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RoadEvaluationUnitServiceImpl implements RoadEvaluationUnitService {
    @Resource private RoadEvaluationUnitMapper mapper;
    public PageResult<RoadEvaluationUnitVO> page(EvaluationUnitQueryDTO query) {
        Page<RoadEvaluationUnitVO> page = mapper.selectPageVO(new Page<>(query.getPageNo(), query.getPageSize()), TenantContextHolder.getTenantId(), query);
        PageResult<RoadEvaluationUnitVO> r = new PageResult<>(); r.setPageNo(query.getPageNo()); r.setPageSize(query.getPageSize()); r.setTotal(page.getTotal()); r.setRecords(page.getRecords()); return r;
    }
    public RoadEvaluationUnitVO getById(String id) {
        RoadEvaluationUnitVO vo = mapper.selectDetail(TenantContextHolder.getTenantId(), id);
        if (vo == null) throw new BizException("评定单元不存在"); return vo;
    }
    public String create(EvaluationUnitSaveDTO dto) {
        RoadEvaluationUnit e = new RoadEvaluationUnit(); BeanUtils.copyProperties(dto, e);
        e.setId(IdUtils.uuid()); e.setTenantId(TenantContextHolder.getTenantId()); e.setCreatedAt(LocalDateTime.now()); e.setUpdatedAt(LocalDateTime.now()); e.setDeleted(false);
        mapper.insertWithGeom(e); return e.getId();
    }
    public void update(String id, EvaluationUnitSaveDTO dto) {
        getById(id); RoadEvaluationUnit e = new RoadEvaluationUnit(); BeanUtils.copyProperties(dto, e); e.setId(id); e.setTenantId(TenantContextHolder.getTenantId()); mapper.updateWithGeom(e);
    }
    public void delete(String id) {
        LambdaUpdateWrapper<RoadEvaluationUnit> w = new LambdaUpdateWrapper<>(); w.eq(RoadEvaluationUnit::getTenantId, TenantContextHolder.getTenantId()).eq(RoadEvaluationUnit::getId, id).set(RoadEvaluationUnit::getDeleted, true);
        mapper.update(null, w);
    }
    public List<RoadEvaluationUnitVO> listForMap(EvaluationUnitQueryDTO query) { return mapper.selectForMap(TenantContextHolder.getTenantId(), query); }
    public StakeLocationVO locateByStake(StakeLocationQueryDTO query) {
        StakeLocationVO vo = mapper.locateByStake(TenantContextHolder.getTenantId(), query);
        if (vo == null) throw new BizException("未找到对应评定单元"); return vo;
    }
}
