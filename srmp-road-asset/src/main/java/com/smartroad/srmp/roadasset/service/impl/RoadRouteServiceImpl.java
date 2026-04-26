package com.smartroad.srmp.roadasset.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.common.exception.BizException;
import com.smartroad.srmp.common.util.IdUtils;
import com.smartroad.srmp.roadasset.dto.*;
import com.smartroad.srmp.roadasset.entity.RoadRoute;
import com.smartroad.srmp.roadasset.mapper.RoadRouteMapper;
import com.smartroad.srmp.roadasset.service.RoadRouteService;
import com.smartroad.srmp.roadasset.vo.RoadRouteVO;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RoadRouteServiceImpl implements RoadRouteService {
    @Resource private RoadRouteMapper mapper;

    public PageResult<RoadRouteVO> page(RoadRouteQueryDTO query) {
        Page<RoadRouteVO> page = mapper.selectPageVO(new Page<>(query.getPageNo(), query.getPageSize()), TenantContextHolder.getTenantId(), query);
        PageResult<RoadRouteVO> r = new PageResult<>(); r.setPageNo(query.getPageNo()); r.setPageSize(query.getPageSize()); r.setTotal(page.getTotal()); r.setRecords(page.getRecords()); return r;
    }
    public RoadRouteVO getById(String id) {
        RoadRouteVO vo = mapper.selectDetail(TenantContextHolder.getTenantId(), id);
        if (vo == null) throw new BizException("路线不存在");
        return vo;
    }
    public String create(RoadRouteSaveDTO dto) {
        RoadRoute e = new RoadRoute(); BeanUtils.copyProperties(dto, e);
        e.setId(IdUtils.uuid()); e.setTenantId(TenantContextHolder.getTenantId()); e.setCreatedAt(LocalDateTime.now()); e.setUpdatedAt(LocalDateTime.now()); e.setDeleted(false);
        mapper.insertWithGeom(e); return e.getId();
    }
    public void update(String id, RoadRouteSaveDTO dto) {
        getById(id); RoadRoute e = new RoadRoute(); BeanUtils.copyProperties(dto, e); e.setId(id); e.setTenantId(TenantContextHolder.getTenantId()); mapper.updateWithGeom(e);
    }
    public void delete(String id) {
        LambdaUpdateWrapper<RoadRoute> w = new LambdaUpdateWrapper<>(); w.eq(RoadRoute::getTenantId, TenantContextHolder.getTenantId()).eq(RoadRoute::getId, id).set(RoadRoute::getDeleted, true);
        mapper.update(null, w);
    }
    public List<RoadRouteVO> listForMap(RoadRouteQueryDTO query) { return mapper.selectForMap(TenantContextHolder.getTenantId(), query); }
}
