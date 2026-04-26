package com.smartroad.srmp.common.util;

public class GeoJsonUtils {
    private GeoJsonUtils() {}

    public static boolean hasText(String geoJson) {
        return geoJson != null && geoJson.trim().length() > 0;
    }
}
