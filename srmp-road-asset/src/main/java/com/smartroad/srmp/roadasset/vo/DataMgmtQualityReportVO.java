package com.smartroad.srmp.roadasset.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DataMgmtQualityReportVO {
    private String projectId;
    private String projectName;
    private long routeCount;
    private long sectionCount;
    private long diseaseCount;
    private Double routeMatchRate;
    private long unmatchedRouteCount;
    private long unmatchedSectionRouteCount;
    private long unmatchedDiseaseRouteCount;
    private long unclassifiedDiseaseCount;
    private long emptyGeometryCount;
    private long emptyRouteGeometryCount;
    private long emptySectionGeometryCount;
    private long emptyDiseaseGeometryCount;
    private long abnormalCoordinateCount;
    private List<MetricItemVO> sectionBreakdown = new ArrayList<>();
    private List<MetricItemVO> routeMatchBreakdown = new ArrayList<>();
    private List<MetricItemVO> geometryBreakdown = new ArrayList<>();
    private List<MetricItemVO> diseaseCategoryBreakdown = new ArrayList<>();
    private List<MetricItemVO> diseaseSeverityBreakdown = new ArrayList<>();
    private List<QualityIssueVO> issues = new ArrayList<>();

    @Data
    public static class MetricItemVO {
        private String label;
        private long value;
    }

    @Data
    public static class QualityIssueVO {
        private String issueType;
        private long count;
        private String suggestion;
    }
}
