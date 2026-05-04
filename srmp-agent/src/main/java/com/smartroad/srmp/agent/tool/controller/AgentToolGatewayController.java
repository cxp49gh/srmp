package com.smartroad.srmp.agent.tool.controller;

import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.tool.AiTool;
import com.smartroad.srmp.agent.tool.AiToolContext;
import com.smartroad.srmp.agent.tool.AiToolRegistry;
import com.smartroad.srmp.agent.tool.AiToolResult;
import com.smartroad.srmp.agent.tool.dto.AgentToolExecuteRequest;
import com.smartroad.srmp.agent.tool.dto.AgentToolInfo;
import com.smartroad.srmp.common.core.R;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase50.3：Java Tool Gateway 热修复。
 *
 * 该控制器不依赖 Phase50.1 的 Orchestrator 抽象，保证即使只部署 LangGraph Runtime，
 * Java 主工程仍能暴露 /api/agent/tools 与 /api/agent/tools/execute。
 *
 * 外部 LangGraph 服务只通过本入口调用已有 AiToolRegistry 中的工具，避免直接访问业务库。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/tools")
public class AgentToolGatewayController {

    private static final String DEFAULT_TENANT = "default";

    @Resource
    private AiToolRegistry aiToolRegistry;

    @Resource
    private Environment environment;

    /**
     * 工具清单。
     *
     * LangGraph Runtime 的 /api/srmp/langgraph/tools 会转发到此接口。
     */
    @GetMapping
    public R<List<AgentToolInfo>> listTools() {
        List<AgentToolInfo> list = new ArrayList<>();
        for (AiTool tool : aiToolRegistry.list()) {
            boolean writeTool = isWriteTool(tool.name());
            if (writeTool && !allowWriteTools()) {
                // 默认不向外暴露写工具，防止外部编排绕过前端人工确认流程。
                continue;
            }
            list.add(AgentToolInfo.of(tool.name(), tool.description(), writeTool));
        }
        return R.ok(list);
    }

    /**
     * 执行工具。
     *
     * 请求体兼容 Phase50.2 Python LangGraph Runtime 中 JavaToolGateway.execute_tool 的 payload。
     */
    @PostMapping("/execute")
    public R<AiToolResult> execute(@RequestBody AgentToolExecuteRequest request,
                                   @RequestHeader(value = "X-Tenant-Id", required = false) String headerTenantId,
                                   @RequestHeader(value = "X-AI-Trace-Id", required = false) String headerTraceId) {
        long start = System.currentTimeMillis();
        if (request == null || isBlank(request.getToolName())) {
            return R.fail("toolName 不能为空");
        }

        String toolName = request.getToolName().trim();
        AiTool tool = aiToolRegistry.get(toolName);
        if (tool == null) {
            return R.fail("未找到 AI 工具：" + toolName);
        }

        if (isWriteTool(toolName) && !allowWriteTools()) {
            return R.ok(AiToolResult.failed(toolName, "写操作工具未开放，请通过前端人工确认流程执行", System.currentTimeMillis() - start));
        }

        AiToolContext context = buildContext(request, headerTenantId, headerTraceId);
        try {
            TenantContextHolder.setTenantId(context.getTenantId());
            if (!tool.supports(context)) {
                return R.ok(AiToolResult.failed(toolName, "当前上下文不支持该工具", System.currentTimeMillis() - start));
            }
            Map<String, Object> args = request.getArgs() == null ? new LinkedHashMap<String, Object>() : request.getArgs();
            AiToolResult result = tool.execute(context, args);
            if (result != null && result.getCostMs() == null) {
                result.setCostMs(System.currentTimeMillis() - start);
            }
            return R.ok(result);
        } catch (Exception e) {
            log.warn("[AI-TOOL-GATEWAY] execute failed, tool={}, traceId={}, error={}", toolName, context.getTraceId(), e.getMessage(), e);
            return R.ok(AiToolResult.failed(toolName, e.getMessage(), System.currentTimeMillis() - start));
        } finally {
            TenantContextHolder.clear();
        }
    }

    private AiToolContext buildContext(AgentToolExecuteRequest request, String headerTenantId, String headerTraceId) {
        MapAiContext mapContext = request.getMapContext();
        if (mapContext == null) {
            mapContext = new MapAiContext();
        }

        String tenantId = firstNonBlank(request.getTenantId(), headerTenantId, mapContext.getTenantId(), TenantContextHolder.getTenantId(), DEFAULT_TENANT);
        String traceId = firstNonBlank(request.getTraceId(), headerTraceId);
        String userQuestion = firstNonBlank(request.getUserQuestion(), mapContext.getUserQuestion());

        mapContext.setTenantId(tenantId);
        mapContext.setUserQuestion(userQuestion);

        AiToolContext context = new AiToolContext();
        context.setTenantId(tenantId);
        context.setTraceId(traceId);
        context.setUserQuestion(userQuestion);
        context.setMapContext(mapContext);
        context.setOptions(request.getOptions() == null ? new LinkedHashMap<String, Object>() : request.getOptions());
        return context;
    }

    private boolean allowWriteTools() {
        Boolean direct = environment.getProperty("srmp.ai.tool-gateway.allow-write-tools", Boolean.class);
        if (direct != null) {
            return direct;
        }
        Boolean phase50 = environment.getProperty("srmp.ai.orchestrator.allow-write-tools", Boolean.class);
        return Boolean.TRUE.equals(phase50);
    }

    private boolean isWriteTool(String toolName) {
        String name = toolName == null ? "" : toolName.trim().toLowerCase(Locale.ROOT);
        return name.contains("save")
                || name.contains("delete")
                || name.contains("update")
                || name.contains("create")
                || name.contains("archive")
                || name.startsWith("solution.save")
                || name.startsWith("task.");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
