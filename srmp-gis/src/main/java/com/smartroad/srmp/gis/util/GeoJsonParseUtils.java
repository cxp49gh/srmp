package com.smartroad.srmp.gis.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;

public class GeoJsonParseUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private GeoJsonParseUtils() {}
    public static Object parse(String geoJson) {
        if (geoJson == null || geoJson.trim().isEmpty()) return null;
        try { return MAPPER.readValue(geoJson, Object.class); }
        catch (Exception e) { return Collections.emptyMap(); }
    }
}
