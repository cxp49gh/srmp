package com.smartroad.srmp.disease.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.common.exception.BizException;
import com.smartroad.srmp.common.util.IdUtils;
import com.smartroad.srmp.disease.dto.DiseaseTypeQueryDTO;
import com.smartroad.srmp.disease.dto.DiseaseTypeSaveDTO;
import com.smartroad.srmp.disease.entity.DiseaseTypeDict;
import com.smartroad.srmp.disease.mapper.DiseaseTypeDictMapper;
import com.smartroad.srmp.disease.service.DiseaseTypeService;
import com.smartroad.srmp.disease.vo.DiseaseTypeVO;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class DiseaseTypeServiceImpl implements DiseaseTypeService {
    @Resource private DiseaseTypeDictMapper mapper;

    public PageResult<DiseaseTypeVO> page(DiseaseTypeQueryDTO query) {
        Page<DiseaseTypeVO> page = mapper.selectPageVO(new Page<>(query.getPageNo(), query.getPageSize()), TenantContextHolder.getTenantId(), query);
        PageResult<DiseaseTypeVO> r = new PageResult<>(); r.setPageNo(query.getPageNo()); r.setPageSize(query.getPageSize()); r.setTotal(page.getTotal()); r.setRecords(page.getRecords()); return r;
    }
    public DiseaseTypeVO getById(String id) {
        DiseaseTypeVO vo = mapper.selectDetail(TenantContextHolder.getTenantId(), id);
        if (vo == null) throw new BizException("病害类型不存在");
        return vo;
    }
    public String create(DiseaseTypeSaveDTO dto) {
        DiseaseTypeDict e = new DiseaseTypeDict(); BeanUtils.copyProperties(dto, e);
        e.setId(IdUtils.uuid()); e.setTenantId(TenantContextHolder.getTenantId()); e.setCreatedAt(LocalDateTime.now()); e.setUpdatedAt(LocalDateTime.now()); e.setDeleted(false);
        if (e.getEnabled() == null) e.setEnabled(true);
        if (e.getSeverityEnabled() == null) e.setSeverityEnabled(true);
        mapper.insert(e); return e.getId();
    }
    public void update(String id, DiseaseTypeSaveDTO dto) {
        getById(id); DiseaseTypeDict e = new DiseaseTypeDict(); BeanUtils.copyProperties(dto, e); e.setId(id); e.setTenantId(TenantContextHolder.getTenantId()); e.setUpdatedAt(LocalDateTime.now()); mapper.updateById(e);
    }
    public void delete(String id) {
        LambdaUpdateWrapper<DiseaseTypeDict> w = new LambdaUpdateWrapper<>(); w.eq(DiseaseTypeDict::getTenantId, TenantContextHolder.getTenantId()).eq(DiseaseTypeDict::getId, id).set(DiseaseTypeDict::getDeleted, true);
        mapper.update(null, w);
    }
}
