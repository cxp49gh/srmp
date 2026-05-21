package com.smartroad.srmp.roadasset.service;

import com.smartroad.srmp.roadasset.vo.DataMgmtProjectSummaryVO;
import com.smartroad.srmp.roadasset.vo.DataMgmtProjectVO;

import java.util.List;
import java.util.Map;

public interface DataMgmtStatsService {

    DataMgmtProjectSummaryVO getSummary(String projectId);

    void enrichSummaries(List<DataMgmtProjectVO> projects);

    long countRoutes(String projectId);

    boolean isRoadNetworkReady(String projectId);

    Map<String, DataMgmtProjectSummaryVO> batchSummaries(List<String> projectIds);
}
