package com.smartroad.srmp.agent.orchestrator.controller;

import com.smartroad.srmp.agent.mapagent.dto.MapAiAgentRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAiAgentResponse;
import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.orchestrator.AgentOrchestrator;
import com.smartroad.srmp.agent.orchestrator.AgentOrchestratorProperties;
import com.smartroad.srmp.agent.orchestrator.AgentOrchestratorRouter;
import com.smartroad.srmp.agent.tool.AiToolRegistry;
import com.smartroad.srmp.common.core.R;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase50.9：LangGraph 编排观测与灰度控制台接口。
 *
 * 目标：不用直接登录 Python 容器，也能从 Java 后端看到当前编排配置、
 * LangGraph Runtime 最近调用、失败/回退情况，并触发一条只读 smoke 请求。
 */
@RestController
@RequestMapping("/api/agent/orchestrator/ops")
public class AgentOrchestratorOpsController {

    @Resource
    private AgentOrchestratorProperties properties;

    @Resource
    private List<AgentOrchestrator> orchestrators;

    @Resource
    private AiToolRegistry aiToolRegistry;

    @Resource
    private AgentOrchestratorRouter router;

    @GetMapping("/summary")
    public R<Map<String, Object>> summary(@RequestParam(value = "recentLimit", required = false, defaultValue = "10") Integer recentLimit) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("provider", properties.getProvider());
        data.put("fallbackToNative", properties.getFallbackToNative());
        data.put("allowWriteTools", properties.getAllowWriteTools());
        data.put("availableProviders", providerNames());
        data.put("localToolCount", aiToolRegistry.list().size());
        data.put("langgraphUrl", properties.getLanggraphUrl());
        data.put("langgraphEndpointPath", properties.getLanggraphEndpointPath());
        data.put("langgraphReady", remoteGet(properties.getLanggraphReadyPath()));
        data.put("runtimeSummary", remoteGet("/api/srmp/langgraph/observability/summary"));
        data.put("runtimeRecent", remoteGet("/api/srmp/langgraph/observability/recent?limit=" + safeLimit(recentLimit)));
        data.put("toolGatewayDebug", remoteGet("/api/srmp/langgraph/debug/tool-gateway"));
        data.put("contractDebug", remoteGet("/api/srmp/langgraph/debug/contract"));
        return R.ok(data);
    }

    @GetMapping("/recent")
    public R<Object> recent(@RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit,
                            @RequestParam(value = "status", required = false) String status) {
        String path = "/api/srmp/langgraph/observability/recent?limit=" + safeLimit(limit);
        if (!isBlank(status)) {
            path = path + "&status=" + status.trim();
        }
        return R.ok(remoteGet(path));
    }

    @PostMapping("/smoke")
    public R<Map<String, Object>> smoke(@RequestBody(required = false) Map<String, Object> body) {
        MapAiAgentRequest request = new MapAiAgentRequest();
        request.setMessage(stringValue(body, "message", "Phase50.9 LangGraph 编排 smoke：请基于 G210 和知识库给出道路养护建议摘要"));

        MapAiContext context = new MapAiContext();
        context.setTenantId(stringValue(body, "tenantId", resolveTenantId()));
        context.setMode(stringValue(body, "mode", "ROUTE"));
        context.setRouteCode(stringValue(body, "routeCode", "G210"));
        context.setYear(intValue(body, "year", 2026));
        context.setUserQuestion(request.getMessage());
        request.setMapContext(context);

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("traceId", stringValue(body, "traceId", "phase50-9-" + UUID.randomUUID().toString().replace("-", "")));
        options.put("smoke", true);
        options.put("readOnly", true);
        request.setOptions(options);

        long start = System.currentTimeMillis();
        MapAiAgentResponse response = router.chat(request);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("costMs", System.currentTimeMillis() - start);
        result.put("request", request);
        result.put("response", response);
        result.put("provider", response != null && response.getData() != null ? response.getData().get("orchestratorProvider") : null);
        result.put("fallback", response != null && response.getData() != null ? response.getData().get("orchestratorFallback") : null);
        result.put("runtimeSummary", remoteGet("/api/srmp/langgraph/observability/summary"));
        return R.ok(result);
    }

    /**
     * 转发 Runtime 的工具契约诊断。
     *
     * 用于确认：Java 已注册工具、Runtime 白名单、只读写工具屏蔽三者是否一致。
     */
    @GetMapping("/contract")
    public R<Object> contract() {
        return R.ok(remoteGet("/api/srmp/langgraph/debug/contract"));
    }

    /**
     * 转发 Runtime 的 LangGraph LLM 探测。
     *
     * 与 /api/ai/llm/health 区分：这里验证 Python LangGraph Runtime 自己的 LLM 配置和调用链路。
     */
    @GetMapping("/llm-probe")
    public R<Object> llmProbe(@RequestParam(value = "probe", required = false, defaultValue = "false") Boolean probe) {
        return R.ok(remoteGet("/api/srmp/langgraph/debug/llm-probe?probe=" + Boolean.TRUE.equals(probe), 180000));
    }

    /**
     * 转发 Runtime 的 Plan Debug：只做归一化、意图识别和工具规划，不执行工具。
     */
    @PostMapping("/plan")
    public R<Object> plan(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> payload = body == null ? new LinkedHashMap<String, Object>() : body;
        if (!payload.containsKey("options") || !(payload.get("options") instanceof Map)) {
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("traceId", "phase50-9-plan-" + UUID.randomUUID().toString().replace("-", ""));
            payload.put("options", options);
        }
        return R.ok(remotePost("/api/srmp/langgraph/debug/plan", payload));
    }

    /**
     * 查看 Runtime 内存审计中的单条记录，支持通过 recordId 或 traceId 查询。
     */
    @GetMapping("/record/{recordId}")
    public R<Object> record(@PathVariable("recordId") String recordId) {
        return R.ok(remoteGet("/api/srmp/langgraph/observability/record/" + recordId));
    }

    /**
     * 导出 Runtime 诊断快照，便于把当前 summary/recent/requestPayload 一次性复制给研发排障。
     */
    @GetMapping("/export")
    public R<Object> export(@RequestParam(value = "limit", required = false, defaultValue = "30") Integer limit,
                            @RequestParam(value = "status", required = false) String status) {
        String path = "/api/srmp/langgraph/observability/export?limit=" + safeLimit(limit);
        if (!isBlank(status)) {
            path = path + "&status=" + status.trim();
        }
        return R.ok(remoteGet(path));
    }

    /**
     * 基于 Runtime 审计记录回放。execute=false 只回放 Plan；execute=true 会重新跑完整只读链路。
     */
    @PostMapping("/replay/{recordId}")
    public R<Object> replay(@PathVariable("recordId") String recordId,
                            @RequestParam(value = "execute", required = false, defaultValue = "false") Boolean execute) {
        return R.ok(remotePost("/api/srmp/langgraph/debug/replay/" + recordId + "?execute=" + Boolean.TRUE.equals(execute), new LinkedHashMap<String, Object>()));
    }

    private List<String> providerNames() {
        List<String> list = new ArrayList<>();
        if (orchestrators != null) {
            for (AgentOrchestrator orchestrator : orchestrators) {
                if (orchestrator != null && orchestrator.provider() != null) {
                    list.add(orchestrator.provider());
                }
            }
        }
        return list;
    }

    @GetMapping("/config")
    public R<Object> config() {
        return R.ok(remoteGet("/api/srmp/langgraph/runtime/config"));
    }

    @GetMapping("/health-detail")
    public R<Object> healthDetail(@RequestParam(value = "includeGateway", required = false, defaultValue = "true") Boolean includeGateway,
                                  @RequestParam(value = "includeContract", required = false, defaultValue = "true") Boolean includeContract) {
        String path = "/api/srmp/langgraph/diagnostics/health?includeGateway=" + Boolean.TRUE.equals(includeGateway)
                + "&includeContract=" + Boolean.TRUE.equals(includeContract);
        return R.ok(remoteGet(path));
    }

    @GetMapping("/persistence")
    public R<Object> persistence() {
        return R.ok(remoteGet("/api/srmp/langgraph/observability/persistence"));
    }

    @GetMapping("/snapshot")
    public R<Object> snapshot(@RequestParam(value = "limit", required = false, defaultValue = "30") Integer limit,
                              @RequestParam(value = "status", required = false) String status) {
        String path = "/api/srmp/langgraph/observability/snapshot?limit=" + safeLimit(limit);
        if (!isBlank(status)) {
            path = path + "&status=" + status.trim();
        }
        return R.ok(remoteGet(path));
    }

    @DeleteMapping("/prune")
    public R<Object> prune(@RequestParam(value = "status", required = false) String status,
                           @RequestParam(value = "beforeMs", required = false) Long beforeMs,
                           @RequestParam(value = "retainLatest", required = false, defaultValue = "20") Integer retainLatest,
                           @RequestParam(value = "includePersist", required = false, defaultValue = "true") Boolean includePersist) {
        String path = "/api/srmp/langgraph/observability/prune?retainLatest=" + safeLimitForPrune(retainLatest)
                + "&includePersist=" + Boolean.TRUE.equals(includePersist);
        if (!isBlank(status)) {
            path = path + "&status=" + status.trim();
        }
        if (beforeMs != null) {
            path = path + "&beforeMs=" + beforeMs;
        }
        return R.ok(remoteDelete(path));
    }

    private int safeLimitForPrune(Integer limit) {
        if (limit == null) {
            return 20;
        }
        if (limit < 0) {
            return 0;
        }
        if (limit > 1000) {
            return 1000;
        }
        return limit;
    }

    private Map<String, Object> remoteGet(String path) {
        Map<String, Object> data = new LinkedHashMap<>();
        String url = buildUrl(properties.getLanggraphUrl(), path);
        data.put("url", url);
        long start = System.currentTimeMillis();
        try {
            RestTemplate restTemplate = buildRestTemplate();
            ResponseEntity<Object> entity = restTemplate.getForEntity(url, Object.class);
            data.put("ok", entity.getStatusCode().is2xxSuccessful());
            data.put("httpStatus", entity.getStatusCodeValue());
            data.put("costMs", System.currentTimeMillis() - start);
            data.put("body", entity.getBody());
        } catch (Exception e) {
            data.put("ok", false);
            data.put("costMs", System.currentTimeMillis() - start);
            data.put("error", e.getMessage());
        }
        return data;
    }

    private Map<String, Object> remoteGet(String path, int readTimeoutMs) {
        Map<String, Object> data = new LinkedHashMap<>();
        String url = buildUrl(properties.getLanggraphUrl(), path);
        data.put("url", url);
        long start = System.currentTimeMillis();
        try {
            RestTemplate restTemplate = buildRestTemplate(3000, readTimeoutMs);
            ResponseEntity<Object> entity = restTemplate.getForEntity(url, Object.class);
            data.put("ok", entity.getStatusCode().is2xxSuccessful());
            data.put("httpStatus", entity.getStatusCodeValue());
            data.put("costMs", System.currentTimeMillis() - start);
            data.put("body", entity.getBody());
        } catch (Exception e) {
            data.put("ok", false);
            data.put("costMs", System.currentTimeMillis() - start);
            data.put("error", e.getMessage());
        }
        return data;
    }

    private Map<String, Object> remotePost(String path, Object payload) {
        Map<String, Object> data = new LinkedHashMap<>();
        String url = buildUrl(properties.getLanggraphUrl(), path);
        data.put("url", url);
        long start = System.currentTimeMillis();
        try {
            RestTemplate restTemplate = buildRestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
            String tenantId = resolveTenantId();
            if (!isBlank(tenantId)) {
                headers.set("X-Tenant-Id", tenantId);
            }
            HttpEntity<Object> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.POST, entity, Object.class);
            data.put("ok", response.getStatusCode().is2xxSuccessful());
            data.put("httpStatus", response.getStatusCodeValue());
            data.put("costMs", System.currentTimeMillis() - start);
            data.put("body", response.getBody());
        } catch (Exception e) {
            data.put("ok", false);
            data.put("costMs", System.currentTimeMillis() - start);
            data.put("error", e.getMessage());
        }
        return data;
    }

    private Map<String, Object> remoteDelete(String path) {
        Map<String, Object> data = new LinkedHashMap<>();
        String url = buildUrl(properties.getLanggraphUrl(), path);
        data.put("url", url);
        long start = System.currentTimeMillis();
        try {
            RestTemplate restTemplate = buildRestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
            String tenantId = resolveTenantId();
            if (!isBlank(tenantId)) {
                headers.set("X-Tenant-Id", tenantId);
            }
            HttpEntity<Object> entity = new HttpEntity<>(new LinkedHashMap<String, Object>(), headers);
            ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.DELETE, entity, Object.class);
            data.put("ok", response.getStatusCode().is2xxSuccessful());
            data.put("httpStatus", response.getStatusCodeValue());
            data.put("costMs", System.currentTimeMillis() - start);
            data.put("body", response.getBody());
        } catch (Exception e) {
            data.put("ok", false);
            data.put("costMs", System.currentTimeMillis() - start);
            data.put("error", e.getMessage());
        }
        return data;
    }

    private RestTemplate buildRestTemplate() {
        return buildRestTemplate(3000, 8000);
    }

    private RestTemplate buildRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }

    private String buildUrl(String baseUrl, String path) {
        String base = isBlank(baseUrl) ? "http://127.0.0.1:18080" : baseUrl.trim();
        String p = isBlank(path) ? "/health" : path.trim();
        if (base.endsWith("/") && p.startsWith("/")) {
            return base.substring(0, base.length() - 1) + p;
        }
        if (!base.endsWith("/") && !p.startsWith("/")) {
            return base + "/" + p;
        }
        return base + p;
    }

    private int safeLimit(Integer limit) {
        if (limit == null) {
            return 20;
        }
        if (limit < 1) {
            return 1;
        }
        if (limit > 100) {
            return 100;
        }
        return limit;
    }

    private String resolveTenantId() {
        String tenantId = TenantContextHolder.getTenantId();
        return isBlank(tenantId) ? "default" : tenantId;
    }

    private String stringValue(Map<String, Object> map, String key, String defaultValue) {
        if (map == null || map.get(key) == null) {
            return defaultValue;
        }
        String value = String.valueOf(map.get(key));
        return isBlank(value) ? defaultValue : value.trim();
    }

    private Integer intValue(Map<String, Object> map, String key, Integer defaultValue) {
        if (map == null || map.get(key) == null) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(String.valueOf(map.get(key)));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
