package com.smartroad.srmp.gis.vo;
import lombok.Data;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class GeoJsonFeatureVO {
    private String type = "Feature";
    private String id;
    private Object geometry;
    private Map<String, Object> properties = new LinkedHashMap<>();
}
