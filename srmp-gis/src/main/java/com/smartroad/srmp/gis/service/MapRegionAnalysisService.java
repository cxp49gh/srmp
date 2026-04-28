package com.smartroad.srmp.gis.service;

import com.smartroad.srmp.gis.dto.MapRegionAnalysisRequest;

import java.util.Map;

public interface MapRegionAnalysisService {
    Map<String, Object> analyze(MapRegionAnalysisRequest request);
}
