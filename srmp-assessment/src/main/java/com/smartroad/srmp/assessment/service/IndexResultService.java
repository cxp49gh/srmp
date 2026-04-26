package com.smartroad.srmp.assessment.service;

import com.smartroad.srmp.assessment.dto.IndexResultQueryDTO;
import com.smartroad.srmp.assessment.dto.IndexResultSaveDTO;
import com.smartroad.srmp.assessment.vo.IndexResultVO;
import com.smartroad.srmp.common.core.PageResult;

public interface IndexResultService {
    PageResult<IndexResultVO> page(IndexResultQueryDTO query);
    IndexResultVO getById(String id);
    String create(IndexResultSaveDTO dto);
    void update(String id, IndexResultSaveDTO dto);
    void delete(String id);
}
