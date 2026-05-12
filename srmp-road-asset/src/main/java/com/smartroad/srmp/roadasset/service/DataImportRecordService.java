package com.smartroad.srmp.roadasset.service;

import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.roadasset.dto.DataImportRecordQueryDTO;
import com.smartroad.srmp.roadasset.vo.DataImportRecordVO;

public interface DataImportRecordService {

    PageResult<DataImportRecordVO> pageByProject(String projectId, DataImportRecordQueryDTO query);
}
