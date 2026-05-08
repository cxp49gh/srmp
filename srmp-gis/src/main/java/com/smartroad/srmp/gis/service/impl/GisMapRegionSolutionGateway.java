package com.smartroad.srmp.gis.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.agent.solution.service.AiMapRegionSolutionGateway;
import com.smartroad.srmp.gis.dto.MapRegionSolutionRequest;
import com.smartroad.srmp.gis.dto.MapRegionSolutionResponse;
import com.smartroad.srmp.gis.service.MapRegionSolutionService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GisMapRegionSolutionGateway implements AiMapRegionSolutionGateway {

    @Resource
    private MapRegionSolutionService mapRegionSolutionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> generate(Map<String, Object> requestMap) {
        Map<String, Object> safeMap = requestMap == null ? new LinkedHashMap<String, Object>() : requestMap;
        MapRegionSolutionRequest request = objectMapper.convertValue(safeMap, MapRegionSolutionRequest.class);
        MapRegionSolutionResponse response = mapRegionSolutionService.generate(request);
        return objectMapper.convertValue(response, Map.class);
    }
}
