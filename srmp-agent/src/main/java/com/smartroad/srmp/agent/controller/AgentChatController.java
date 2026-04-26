package com.smartroad.srmp.agent.controller;

import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentChatController {

    @PostMapping("/chat")
    public R<Map<String, Object>> chat(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        result.put("answer", "阶段一基础骨架已接通，后续在 srmp-agent 中接入大模型 API 与业务工具。");
        result.put("request", request);
        return R.ok(result);
    }
}
