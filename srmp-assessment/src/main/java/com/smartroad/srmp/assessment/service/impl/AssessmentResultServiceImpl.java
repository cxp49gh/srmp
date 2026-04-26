package com.smartroad.srmp.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartroad.srmp.assessment.dto.AssessmentResultQueryDTO;
import com.smartroad.srmp.assessment.dto.AssessmentResultSaveDTO;
import com.smartroad.srmp.assessment.entity.AssessmentResult;
import com.smartroad.srmp.assessment.mapper.AssessmentResultMapper;
import com.smartroad.srmp.assessment.service.AssessmentResultService;
import com.smartroad.srmp.assessment.vo.AssessmentResultVO;
import com.smartroad.srmp.assessment.vo.AssessmentSummaryVO;
import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.common.exception.BizException;
import com.smartroad.srmp.common.util.IdUtils;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AssessmentResultServiceImpl implements AssessmentResultService {
    @Resource private AssessmentResultMapper mapper;

    public PageResult<AssessmentResultVO> page(AssessmentResultQueryDTO query) {
        Page<AssessmentResultVO> page = mapper.selectPageVO(new Page<>(query.getPageNo(), query.getPageSize()), TenantContextHolder.getTenantId(), query);
        PageResult<AssessmentResultVO> r = new PageResult<>(); r.setPageNo(query.getPageNo()); r.setPageSize(query.getPageSize()); r.setTotal(page.getTotal()); r.setRecords(page.getRecords()); return r;
    }
    public AssessmentResultVO getById(String id) {
        AssessmentResultVO vo = mapper.selectDetail(TenantContextHolder.getTenantId(), id);
        if (vo == null) throw new BizException("评定结果不存在");
        return vo;
    }
    public String create(AssessmentResultSaveDTO dto) {
        AssessmentResult e = new AssessmentResult(); BeanUtils.copyProperties(dto, e);
        e.setId(IdUtils.uuid()); e.setTenantId(TenantContextHolder.getTenantId()); e.setCreatedAt(LocalDateTime.now()); e.setUpdatedAt(LocalDateTime.now()); e.setDeleted(false);
        if (e.getObjectType() == null) e.setObjectType("EVALUATION_UNIT");
        if (e.getObjectId() == null) e.setObjectId(e.getUnitId());
        if (e.getStandardCode() == null) e.setStandardCode("JTG_5210_2018");
        if (e.getAssessedAt() == null) e.setAssessedAt(LocalDateTime.now());
        mapper.insert(e); return e.getId();
    }
    public void update(String id, AssessmentResultSaveDTO dto) {
        getById(id); AssessmentResult e = new AssessmentResult(); BeanUtils.copyProperties(dto, e); e.setId(id); e.setTenantId(TenantContextHolder.getTenantId()); e.setUpdatedAt(LocalDateTime.now()); mapper.updateById(e);
    }
    public void delete(String id) {
        LambdaUpdateWrapper<AssessmentResult> w = new LambdaUpdateWrapper<>(); w.eq(AssessmentResult::getTenantId, TenantContextHolder.getTenantId()).eq(AssessmentResult::getId, id).set(AssessmentResult::getDeleted, true);
        mapper.update(null, w);
    }
    public List<AssessmentResultVO> listForMap(AssessmentResultQueryDTO query) { return mapper.selectForMap(TenantContextHolder.getTenantId(), query); }
    public AssessmentSummaryVO summary(AssessmentResultQueryDTO query) { return mapper.selectSummary(TenantContextHolder.getTenantId(), query); }
}
