package com.smartroad.srmp.agent.mapagent.controller;

import com.smartroad.srmp.agent.mapagent.dto.MapAiAgentRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAiAgentResponse;
import com.smartroad.srmp.agent.mapagent.service.MapAiAgentService;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/agent/map-agent")
public class MapAiAgentController {

    @Resource
    private MapAiAgentService mapAiAgentService;

    @PostMapping("/chat")
    public R<MapAiAgentResponse> chat(@RequestBody MapAiAgentRequest request) {
        return R.ok(mapAiAgentService.chat(request));
    }
}
