package com.smartroad.srmp.roadasset.service;

import com.smartroad.srmp.roadasset.vo.DataMgmtQualityReportVO;

public interface DataMgmtQualityService {
    DataMgmtQualityReportVO getQualityReport(String projectId);
}
