package com.smartroad.srmp.roadasset.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.common.exception.BizException;
import com.smartroad.srmp.common.util.IdUtils;
import com.smartroad.srmp.roadasset.dto.*;
import com.smartroad.srmp.roadasset.entity.RoadSection;
import com.smartroad.srmp.roadasset.mapper.RoadSectionMapper;
import com.smartroad.srmp.roadasset.service.RoadSectionService;
import com.smartroad.srmp.roadasset.vo.RoadSectionVO;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RoadSectionServiceImpl implements RoadSectionService {
    @Resource private RoadSectionMapper mapper;
    public PageResult<RoadSectionVO> page(RoadSectionQueryDTO query) {
        Page<RoadSectionVO> page = mapper.selectPageVO(new Page<>(query.getPageNo(), query.getPageSize()), TenantContextHolder.getTenantId(), query);
        PageResult<RoadSectionVO> r = new PageResult<>(); r.setPageNo(query.getPageNo()); r.setPageSize(query.getPageSize()); r.setTotal(page.getTotal()); r.setRecords(page.getRecords()); return r;
    }
    public RoadSectionVO getById(String id) {
        RoadSectionVO vo = mapper.selectDetail(TenantContextHolder.getTenantId(), id);
        if (vo == null) throw new BizException("路段不存在"); return vo;
    }
    public String create(RoadSectionSaveDTO dto) {
        RoadSection e = new RoadSection(); BeanUtils.copyProperties(dto, e);
        e.setId(IdUtils.uuid()); e.setTenantId(TenantContextHolder.getTenantId()); e.setCreatedAt(LocalDateTime.now()); e.setUpdatedAt(LocalDateTime.now()); e.setDeleted(false);
        mapper.insertWithGeom(e); return e.getId();
    }
    public void update(String id, RoadSectionSaveDTO dto) {
        getById(id); RoadSection e = new RoadSection(); BeanUtils.copyProperties(dto, e); e.setId(id); e.setTenantId(TenantContextHolder.getTenantId()); mapper.updateWithGeom(e);
    }
    public void delete(String id) {
        LambdaUpdateWrapper<RoadSection> w = new LambdaUpdateWrapper<>(); w.eq(RoadSection::getTenantId, TenantContextHolder.getTenantId()).eq(RoadSection::getId, id).set(RoadSection::getDeleted, true);
        mapper.update(null, w);
    }
    public List<RoadSectionVO> listForMap(RoadSectionQueryDTO query) { return mapper.selectForMap(TenantContextHolder.getTenantId(), query); }
}
