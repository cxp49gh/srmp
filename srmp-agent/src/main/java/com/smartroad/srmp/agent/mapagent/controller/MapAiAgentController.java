package com.smartroad.srmp.agent.mapagent.controller;

import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunResponse;
import com.smartroad.srmp.agent.mapagent.service.MapAiAgentService;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/agent/map-agent")
public class MapAiAgentController {

    @Resource
    private MapAiAgentService mapAiAgentService;

    @PostMapping("/run")
    public R<MapAgentRunResponse> run(@RequestBody MapAgentRunRequest request) {
        return R.ok(mapAiAgentService.run(request));
    }
}
