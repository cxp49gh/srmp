package com.smartroad.srmp.assessment.service;

import com.smartroad.srmp.assessment.dto.AssessmentResultQueryDTO;
import com.smartroad.srmp.assessment.dto.AssessmentResultSaveDTO;
import com.smartroad.srmp.assessment.vo.AssessmentResultVO;
import com.smartroad.srmp.assessment.vo.AssessmentSummaryVO;
import com.smartroad.srmp.common.core.PageResult;

import java.util.List;

public interface AssessmentResultService {
    PageResult<AssessmentResultVO> page(AssessmentResultQueryDTO query);
    AssessmentResultVO getById(String id);
    String create(AssessmentResultSaveDTO dto);
    void update(String id, AssessmentResultSaveDTO dto);
    void delete(String id);
    List<AssessmentResultVO> listForMap(AssessmentResultQueryDTO query);
    AssessmentSummaryVO summary(AssessmentResultQueryDTO query);

    /** 导入路段包：按租户+objectType+objectId+year+standardCode 存在则更新，否则插入 */
    void upsertForImport(AssessmentResultSaveDTO dto);
}
