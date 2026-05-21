package com.smartroad.srmp.roadasset.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DataMgmtProjectSummaryVO {
    private String projectStatus;
    private long routeCount;
    private long sectionCount;
    private long diseaseCount;
    private String lastImportType;
    private String lastImportStatus;
    private LocalDateTime lastImportTime;
    private LocalDateTime lastSuccessImportTime;
    private boolean roadNetworkReady;
}
