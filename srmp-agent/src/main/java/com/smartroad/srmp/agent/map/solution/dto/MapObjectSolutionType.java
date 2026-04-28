package com.smartroad.srmp.agent.map.solution.dto;

public enum MapObjectSolutionType {
    DISEASE_REVIEW("病害复核意见"),
    DISEASE_TREATMENT("病害处置建议"),
    LOW_SCORE_TREATMENT("低分单元处置建议"),
    EVALUATION_UNIT_ADVICE("评定单元养护建议"),
    SECTION_PLAN("路段养护计划草稿"),
    ROUTE_REPORT("路线技术状况报告草稿"),
    GENERAL_ADVICE("通用养护建议");

    private final String label;

    MapObjectSolutionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static MapObjectSolutionType of(String value, String objectType) {
        if (value != null && value.trim().length() > 0) {
            String normalized = value.trim().toUpperCase().replace("-", "_");
            for (MapObjectSolutionType type : values()) {
                if (type.name().equals(normalized)) {
                    return type;
                }
            }
        }
        return defaultForObjectType(objectType);
    }

    public static MapObjectSolutionType defaultForObjectType(String objectType) {
        String type = objectType == null ? "" : objectType.trim().toUpperCase();
        if ("DISEASE".equals(type) || "DISEASE_RECORD".equals(type)) {
            return DISEASE_TREATMENT;
        }
        if ("ASSESSMENT".equals(type) || "ASSESSMENT_RESULT".equals(type)) {
            return LOW_SCORE_TREATMENT;
        }
        if ("EVALUATION_UNIT".equals(type)) {
            return EVALUATION_UNIT_ADVICE;
        }
        if ("ROAD_SECTION".equals(type)) {
            return SECTION_PLAN;
        }
        if ("ROAD_ROUTE".equals(type)) {
            return ROUTE_REPORT;
        }
        return GENERAL_ADVICE;
    }
}
