package com.smartroad.srmp.agent.controller;

import com.smartroad.srmp.agent.dto.AgentChatRequest;
import com.smartroad.srmp.agent.service.AgentChatService;
import com.smartroad.srmp.agent.vo.AgentChatResponse;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/agent")
public class AgentChatController {

    @Resource
    private AgentChatService agentChatService;

    @PostMapping("/chat")
    public R<AgentChatResponse> chat(@RequestBody AgentChatRequest request) {
        return R.ok(agentChatService.chat(request));
    }
}
