package com.smartroad.srmp.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartroad.srmp.assessment.dto.AssessmentImportExistingRow;
import com.smartroad.srmp.assessment.dto.AssessmentImportNaturalKey;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AssessmentResultServiceImpl implements AssessmentResultService {
    @Resource private AssessmentResultMapper mapper;

    public PageResult<AssessmentResultVO> page(AssessmentResultQueryDTO query) {
        normalizeSectionTier(query);
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
    public List<AssessmentResultVO> listForMap(AssessmentResultQueryDTO query) {
        normalizeSectionTier(query);
        return mapper.selectForMap(TenantContextHolder.getTenantId(), query);
    }

    public AssessmentSummaryVO summary(AssessmentResultQueryDTO query) {
        normalizeSectionTier(query);
        return mapper.selectSummary(TenantContextHolder.getTenantId(), query);
    }

    /** 统一为 LINE|LEDGER|KM|HM，避免 OGNL/MyBatis 与前端大小写、空格不一致导致粒度条件整段失效 */
    private static void normalizeSectionTier(AssessmentResultQueryDTO query) {
        if (query == null) {
            return;
        }
        String raw = query.getSectionTier();
        if (raw == null || raw.trim().isEmpty()) {
            query.setSectionTier(null);
            return;
        }
        String t = raw.trim().toUpperCase(Locale.ROOT);
        switch (t) {
            case "LINE":
            case "LEDGER":
            case "KM":
            case "HM":
                query.setSectionTier(t);
                break;
            default:
                query.setSectionTier(null);
                break;
        }
    }

    @Override
    public void upsertForImport(AssessmentResultSaveDTO dto) {
        String tenantId = TenantContextHolder.getTenantId();
        String std = dto.getStandardCode() != null && !dto.getStandardCode().trim().isEmpty()
                ? dto.getStandardCode().trim() : "JTG_5210_2018";
        LambdaQueryWrapper<AssessmentResult> w = new LambdaQueryWrapper<>();
        w.eq(AssessmentResult::getTenantId, tenantId)
                .eq(AssessmentResult::getDeleted, false)
                .eq(AssessmentResult::getObjectType, dto.getObjectType())
                .eq(AssessmentResult::getObjectId, dto.getObjectId())
                .eq(AssessmentResult::getYear, dto.getYear())
                .eq(AssessmentResult::getStandardCode, std)
                .last("LIMIT 1");
        AssessmentResult existing = mapper.selectOne(w);
        if (existing == null) {
            dto.setStandardCode(std);
            create(dto);
        } else {
            dto.setStandardCode(std);
            update(existing.getId(), dto);
        }
    }

    private static final int IMPORT_ASSESSMENT_KEY_CHUNK = 200;
    private static final int IMPORT_ASSESSMENT_WRITE_CHUNK = 100;

    private static String importNaturalKey(String objectType, String objectId, Integer year, String standardCode) {
        return objectType + "\u0001" + objectId + "\u0001" + year + "\u0001" + standardCode;
    }

    @Override
    public void upsertBatchForImport(List<AssessmentResultSaveDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        String tenantId = TenantContextHolder.getTenantId();
        String userId = null;
        List<AssessmentImportNaturalKey> keys = new ArrayList<>(dtos.size());
        for (AssessmentResultSaveDTO dto : dtos) {
            String std = dto.getStandardCode() != null && !dto.getStandardCode().trim().isEmpty()
                    ? dto.getStandardCode().trim() : "JTG_5210_2018";
            dto.setStandardCode(std);
            keys.add(new AssessmentImportNaturalKey(dto.getObjectType(), dto.getObjectId(), dto.getYear(), std));
        }
        Map<String, String> existingIdByKey = new HashMap<>();
        for (int i = 0; i < keys.size(); i += IMPORT_ASSESSMENT_KEY_CHUNK) {
            int end = Math.min(i + IMPORT_ASSESSMENT_KEY_CHUNK, keys.size());
            List<AssessmentImportExistingRow> rows = mapper.selectExistingForImportKeys(tenantId, keys.subList(i, end));
            for (AssessmentImportExistingRow r : rows) {
                existingIdByKey.put(
                        importNaturalKey(r.getObjectType(), r.getObjectId(), r.getYear(), r.getStandardCode()),
                        r.getId());
            }
        }
        LocalDateTime now = LocalDateTime.now();
        List<AssessmentResult> toInsert = new ArrayList<>();
        List<AssessmentResult> toUpdate = new ArrayList<>();
        for (AssessmentResultSaveDTO dto : dtos) {
            String std = dto.getStandardCode();
            String k = importNaturalKey(dto.getObjectType(), dto.getObjectId(), dto.getYear(), std);
            String existingId = existingIdByKey.get(k);
            AssessmentResult e = new AssessmentResult();
            BeanUtils.copyProperties(dto, e);
            e.setTenantId(tenantId);
            e.setDeleted(false);
            e.setStandardCode(std);
            if (e.getObjectType() == null) {
                e.setObjectType("EVALUATION_UNIT");
            }
            if (e.getObjectId() == null) {
                e.setObjectId(e.getUnitId());
            }
            if (e.getAssessedAt() == null) {
                e.setAssessedAt(now);
            }
            e.setUpdatedAt(now);
            if (existingId != null) {
                e.setId(existingId);
                toUpdate.add(e);
            } else {
                e.setId(IdUtils.uuid());
                e.setCreatedAt(now);
                toInsert.add(e);
            }
        }
        for (int i = 0; i < toInsert.size(); i += IMPORT_ASSESSMENT_WRITE_CHUNK) {
            int end = Math.min(i + IMPORT_ASSESSMENT_WRITE_CHUNK, toInsert.size());
            mapper.insertImportBatch(tenantId, toInsert.subList(i, end), userId);
        }
        for (int i = 0; i < toUpdate.size(); i += IMPORT_ASSESSMENT_WRITE_CHUNK) {
            int end = Math.min(i + IMPORT_ASSESSMENT_WRITE_CHUNK, toUpdate.size());
            mapper.updateImportBatch(tenantId, toUpdate.subList(i, end), userId);
        }
    }
}
