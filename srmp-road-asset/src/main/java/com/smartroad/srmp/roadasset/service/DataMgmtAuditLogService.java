package com.smartroad.srmp.roadasset.service;

import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.roadasset.dto.DataMgmtAuditLogQueryDTO;
import com.smartroad.srmp.roadasset.vo.DataMgmtAuditLogVO;

public interface DataMgmtAuditLogService {

    void log(String projectId, String projectName, String operationType, String result,
             String reason, String snapshotBefore, String snapshotAfter);

    PageResult<DataMgmtAuditLogVO> page(DataMgmtAuditLogQueryDTO query);

    DataMgmtAuditLogVO getById(String id);
}
