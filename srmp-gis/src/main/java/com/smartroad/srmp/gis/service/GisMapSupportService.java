package com.smartroad.srmp.gis.service;

import com.smartroad.srmp.gis.vo.GeoJsonFeatureCollectionVO;

import java.util.Map;

public interface GisMapSupportService {

    Map<String, Object> mapStatistics(Map<String, Object> request);

    Map<String, Object> objectDetail(String objectType, String id);

    GeoJsonFeatureCollectionVO spatialQuery(Map<String, Object> query);
}
