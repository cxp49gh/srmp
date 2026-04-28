package com.smartroad.srmp.gis.service;

import com.smartroad.srmp.gis.dto.MapRegionSolutionRequest;
import com.smartroad.srmp.gis.dto.MapRegionSolutionResponse;

public interface MapRegionSolutionService {
    MapRegionSolutionResponse generate(MapRegionSolutionRequest request);
}
