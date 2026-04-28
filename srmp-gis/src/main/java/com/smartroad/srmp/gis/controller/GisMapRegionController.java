package com.smartroad.srmp.gis.controller;

import com.smartroad.srmp.common.core.R;
import com.smartroad.srmp.gis.dto.MapRegionAnalysisRequest;
import com.smartroad.srmp.gis.dto.MapRegionSolutionRequest;
import com.smartroad.srmp.gis.dto.MapRegionSolutionResponse;
import com.smartroad.srmp.gis.service.MapRegionAnalysisService;
import com.smartroad.srmp.gis.service.MapRegionSolutionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/api/gis/map-region")
public class GisMapRegionController {

    @Resource
    private MapRegionAnalysisService mapRegionAnalysisService;

    @Resource
    private MapRegionSolutionService mapRegionSolutionService;

    @PostMapping("/analysis")
    public R<Map<String, Object>> analysis(@RequestBody MapRegionAnalysisRequest request) {
        return R.ok(mapRegionAnalysisService.analyze(request));
    }

    @PostMapping("/solution")
    public R<MapRegionSolutionResponse> solution(@RequestBody MapRegionSolutionRequest request) {
        return R.ok(mapRegionSolutionService.generate(request));
    }
}
