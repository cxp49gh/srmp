package com.smartroad.srmp.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartroad.srmp.assessment.dto.AssessmentImportExistingRow;
import com.smartroad.srmp.assessment.dto.AssessmentImportNaturalKey;
import com.smartroad.srmp.assessment.dto.AssessmentResultQueryDTO;
import com.smartroad.srmp.assessment.entity.AssessmentResult;
import com.smartroad.srmp.assessment.vo.AssessmentResultVO;
import com.smartroad.srmp.assessment.vo.AssessmentSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AssessmentResultMapper extends BaseMapper<AssessmentResult> {
    Page<AssessmentResultVO> selectPageVO(Page<?> page, @Param("tenantId") String tenantId, @Param("q") AssessmentResultQueryDTO query);
    AssessmentResultVO selectDetail(@Param("tenantId") String tenantId, @Param("id") String id);
    List<AssessmentResultVO> selectForMap(@Param("tenantId") String tenantId, @Param("q") AssessmentResultQueryDTO query);
    AssessmentSummaryVO selectSummary(@Param("tenantId") String tenantId, @Param("q") AssessmentResultQueryDTO query);

    List<AssessmentImportExistingRow> selectExistingForImportKeys(@Param("tenantId") String tenantId,
                                                                  @Param("keys") List<AssessmentImportNaturalKey> keys);

    int insertImportBatch(@Param("tenantId") String tenantId, @Param("list") List<AssessmentResult> list,
                          @Param("userId") String userId);

    int updateImportBatch(@Param("tenantId") String tenantId, @Param("list") List<AssessmentResult> list,
                          @Param("userId") String userId);
}
