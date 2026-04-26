package com.smartroad.srmp.gis.util;

public class GisStyleUtils {
    private GisStyleUtils() {}

    public static String colorByGrade(String grade) {
        if (grade == null) return "#999999";
        switch (grade) {
            case "EXCELLENT": return "#2E7D32";
            case "GOOD": return "#1976D2";
            case "MEDIUM": return "#FBC02D";
            case "POOR": return "#F57C00";
            case "BAD": return "#D32F2F";
            default: return "#999999";
        }
    }

    public static String colorBySeverity(String severity) {
        if (severity == null) return "#607D8B";
        switch (severity) {
            case "HEAVY": return "#D32F2F";
            case "MEDIUM": return "#F57C00";
            case "LIGHT": return "#FBC02D";
            default: return "#607D8B";
        }
    }
}
