package com.smartroad.srmp.agent.map.solution.controller;

import com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionRequest;
import com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionResponse;
import com.smartroad.srmp.agent.map.solution.service.MapObjectSolutionService;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/agent/map-object")
public class MapObjectSolutionController {

    @Resource
    private MapObjectSolutionService mapObjectSolutionService;

    @PostMapping("/solution")
    public R<MapObjectSolutionResponse> solution(@RequestBody MapObjectSolutionRequest request) {
        return R.ok(mapObjectSolutionService.generate(request));
    }
}
