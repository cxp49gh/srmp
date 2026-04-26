package com.smartroad.srmp.gis.vo;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class GeoJsonFeatureCollectionVO {
    private String type = "FeatureCollection";
    private List<GeoJsonFeatureVO> features = new ArrayList<>();
}
