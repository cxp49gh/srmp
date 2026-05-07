# Phase50.18 LangGraph-first Map Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the native-compatible map AI stack with a LangGraph-first `/run` action contract that drives one-map chat, object analysis, region analysis, solution generation, draft save, trace, and replay.

**Architecture:** `srmp-ai-orchestrator` becomes the only AI orchestration runtime and exposes `/api/srmp/langgraph/map-agent/run`. `srmp-agent` exposes only `/api/agent/map-agent/run`, proxies to LangGraph, and keeps Tool Gateway, GIS, template, and draft persistence services. `srmp-web-ui` calls only `mapAgentRun()` for AI workflows and renders `actionResult`, `suggestedActions`, and the Phase50.17 execution drawer.

**Tech Stack:** Python 3.11, FastAPI, Pydantic, LangGraph, Java 8/Spring Boot 2.7, Vue 3 Composition API, TypeScript, Element Plus, Bash regression scripts.

---

## Scope and Invariants

- This is one destructive refactor PR. Do not keep old AI wrappers or adapter endpoints.
- `/api/agent/map-agent/run` is the only Java one-map AI endpoint.
- `/api/srmp/langgraph/map-agent/run` is the only Python one-map AI endpoint.
- Java must not generate native AI answers.
- Java must keep Tool Gateway, GIS data, knowledge, template, and draft persistence boundaries.
- `SAVE_SOLUTION_DRAFT` must require `options.confirmed=true`; otherwise it returns `NEEDS_CONFIRMATION`.
- Phase50.17 `AiTraceDrawer` remains the trace viewer.
- The persistent untracked `deploy/outline/*` files are runtime files and are not touched.

## File Structure

- Create `srmp-ai-orchestrator/app/map_agent_run.py`
  - Owns the LangGraph-first action router and action result assembly.
- Modify `srmp-ai-orchestrator/app/schemas.py`
  - Adds Run request/response, action result, and suggested action schemas.
- Modify `srmp-ai-orchestrator/app/main.py`
  - Adds `/api/srmp/langgraph/map-agent/run`.
  - Removes `/api/srmp/langgraph/map-agent/chat` after Java and frontend have switched.
- Modify `srmp-ai-orchestrator/app/observability.py`
  - Records `action`, `graphName`, `actionResultType`, write blocked, and confirmation counters.
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/dto/*`
  - Adds Java Run DTOs.
  - Deletes old running dependency on `MapAiAgentRequest/Response`.
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/controller/MapAiAgentController.java`
  - Keeps only `POST /api/agent/map-agent/run`.
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/*`
  - Simplifies router to remote LangGraph only.
  - Removes provider selection and native fallback.
- Delete `srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/impl/NativeMapAgentOrchestrator.java`
- Delete native planner/enhancer files under `srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/plan` and `srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/enhance`
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/tool/impl/SolutionGenerateTool.java`
  - Produces real solution preview payloads through existing Java solution services.
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/tool/impl/SolutionSaveTool.java`
  - Saves drafts only after confirmation.
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/map/solution/controller/MapObjectSolutionController.java`
  - Delete the old AI endpoint.
- Modify `srmp-gis/src/main/java/com/smartroad/srmp/gis/controller/GisMapRegionController.java`
  - Delete the old region solution AI endpoint; keep analysis and drafts.
- Modify `srmp-web-ui/src/api/agent.ts`
  - Adds `mapAgentRun()` and Run types.
  - Deletes old AI helpers.
- Modify `srmp-web-ui/src/api/gis.ts`
  - Deletes `generateMapRegionSolution()`.
- Modify `srmp-web-ui/src/views/agent/AiChatPage.vue`, `KnowledgeVectorPage.vue`, `srmp-web-ui/src/views/gis/OneMap.vue`, and GIS AI components
  - Calls `mapAgentRun()` only.
- Create Workbench components under `srmp-web-ui/src/views/gis/components/map-ai/`
  - Splits one-map AI UI from `OneMap.vue` and old `AgentChatFloat.vue`.
- Create `scripts/check-phase50-18-langgraph-first-map-agent.sh`
  - Static and build checks for the destructive switch.

## Task 1: Add Python Run Schemas

**Files:**
- Modify: `srmp-ai-orchestrator/app/schemas.py`
- Test: `srmp-ai-orchestrator/.venv/bin/python -m py_compile srmp-ai-orchestrator/app/*.py`

- [ ] **Step 1: Add Run schema classes**

Append these classes after `MapAiAgentResponse` in `srmp-ai-orchestrator/app/schemas.py`:

```python
class SuggestedAction(BaseModel):
    model_config = ConfigDict(extra="allow")

    action: str
    label: str
    requiresConfirmation: bool = False
    disabled: bool = False
    reason: Optional[str] = None
    payload: Dict[str, Any] = Field(default_factory=dict)


class ActionResult(BaseModel):
    model_config = ConfigDict(extra="allow")

    type: str = "ANSWER"
    status: str = "SUCCESS"
    title: Optional[str] = None
    markdown: Optional[str] = None
    objectSummary: Dict[str, Any] = Field(default_factory=dict)
    regionSummary: Dict[str, Any] = Field(default_factory=dict)
    routeSummary: Dict[str, Any] = Field(default_factory=dict)
    templateMeta: Dict[str, Any] = Field(default_factory=dict)
    qualityCheck: Dict[str, Any] = Field(default_factory=dict)
    draftTask: Optional[Dict[str, Any]] = None
    errorMessage: Optional[str] = None


class MapAgentRunRequest(BaseModel):
    model_config = ConfigDict(extra="allow")

    message: Optional[str] = None
    action: str = "CHAT"
    mapContext: Optional[MapAiContext] = None
    actionInput: Dict[str, Any] = Field(default_factory=dict)
    options: Dict[str, Any] = Field(default_factory=dict)


class MapAgentRunResponse(BaseModel):
    model_config = ConfigDict(extra="allow")

    answer: str
    mode: str = "LANGGRAPH_MAP_AGENT"
    action: str
    intent: Optional[str] = None
    mapContext: Optional[Dict[str, Any]] = None
    actionResult: ActionResult = Field(default_factory=ActionResult)
    suggestedActions: List[SuggestedAction] = Field(default_factory=list)
    toolResults: List[Dict[str, Any]] = Field(default_factory=list)
    knowledgeSources: List[Dict[str, Any]] = Field(default_factory=list)
    sources: List[Dict[str, Any]] = Field(default_factory=list)
    answerMeta: Dict[str, Any] = Field(default_factory=dict)
    trace: Dict[str, Any] = Field(default_factory=dict)
    data: Dict[str, Any] = Field(default_factory=dict)
```

- [ ] **Step 2: Compile Python**

Run:

```bash
srmp-ai-orchestrator/.venv/bin/python -m py_compile srmp-ai-orchestrator/app/*.py
```

Expected: command exits with code 0.

- [ ] **Step 3: Commit**

```bash
git add srmp-ai-orchestrator/app/schemas.py
git commit -m "feat: add LangGraph map agent run schemas"
```

## Task 2: Add Python LangGraph-first Run Workflow

**Files:**
- Create: `srmp-ai-orchestrator/app/map_agent_run.py`
- Modify: `srmp-ai-orchestrator/app/main.py`
- Test: `srmp-ai-orchestrator/.venv/bin/python -m py_compile srmp-ai-orchestrator/app/*.py`

- [ ] **Step 1: Create `map_agent_run.py`**

Create `srmp-ai-orchestrator/app/map_agent_run.py` with this structure:

```python
import time
from typing import Any, Dict, List, Optional

from .java_tools import JavaToolGateway
from .schemas import (
    ActionResult,
    MapAgentRunRequest,
    MapAgentRunResponse,
    MapAiAgentRequest,
    SuggestedAction,
    ToolCall,
)
from .workflow import LangGraphWorkflow


ANALYSIS_ACTIONS = {"CHAT", "ANALYZE_OBJECT", "ANALYZE_ROUTE", "ANALYZE_REGION", "PLAN_ONLY"}
SOLUTION_ACTIONS = {"GENERATE_OBJECT_SOLUTION", "GENERATE_REGION_SOLUTION", "GENERATE_ROUTE_REPORT"}


class MapAgentRunWorkflow:
    def __init__(self, base_workflow: LangGraphWorkflow, gateway: JavaToolGateway):
        self.base_workflow = base_workflow
        self.gateway = gateway

    async def run(self, request: MapAgentRunRequest, tenant_id: Optional[str], trace_id: Optional[str]) -> MapAgentRunResponse:
        action = normalize_action(request.action)
        started_at = time.perf_counter()
        if action == "SAVE_SOLUTION_DRAFT":
            response = await self._run_draft_save(request, tenant_id, trace_id)
        elif action in SOLUTION_ACTIONS:
            response = await self._run_solution_generation(request, tenant_id, trace_id)
        else:
            response = await self._run_analysis(request, tenant_id, trace_id, action)
        response.data["action"] = action
        response.data["costMs"] = int((time.perf_counter() - started_at) * 1000)
        response.trace["action"] = action
        response.trace["graphName"] = response.data.get("graphName")
        response.trace["actionResultType"] = response.actionResult.type
        response.trace["suggestedActionCount"] = len(response.suggestedActions)
        return response

    async def _run_analysis(self, request: MapAgentRunRequest, tenant_id: Optional[str], trace_id: Optional[str], action: str) -> MapAgentRunResponse:
        agent_request = self._to_agent_request(request, action)
        agent_response = await self.base_workflow.run(agent_request, tenant_id=tenant_id, trace_id=trace_id)
        result_type = "REGION_SUMMARY" if action == "ANALYZE_REGION" else "OBJECT_SUMMARY" if action == "ANALYZE_OBJECT" else "ANSWER"
        action_result = ActionResult(type=result_type, status="SUCCESS")
        if action == "ANALYZE_REGION" and agent_request.mapContext and agent_request.mapContext.regionSummary:
            action_result.regionSummary = agent_request.mapContext.regionSummary
        if action == "ANALYZE_OBJECT" and agent_request.mapContext and agent_request.mapContext.mapObject:
            action_result.objectSummary = agent_request.mapContext.mapObject
        return self._from_agent_response(request, agent_response, action, graph_name=graph_name_for(action), action_result=action_result)

    async def _run_solution_generation(self, request: MapAgentRunRequest, tenant_id: Optional[str], trace_id: Optional[str]) -> MapAgentRunResponse:
        tool_result = await self.gateway.execute_tool(
            call=ToolCall(toolName="solution.generateDraft", args=self._solution_args(request), reason="LangGraph-first solution preview"),
            request=self._to_agent_request(request, request.action),
            tenant_id=tenant_id or "default",
            trace_id=trace_id,
        )
        data = tool_result.data if isinstance(tool_result.data, dict) else {}
        action_result = ActionResult(
            type="SOLUTION_PREVIEW",
            status="SUCCESS" if tool_result.success else "FAILED",
            title=str(data.get("title") or ""),
            markdown=str(data.get("markdown") or ""),
            objectSummary=data.get("objectSummary") if isinstance(data.get("objectSummary"), dict) else {},
            regionSummary=data.get("regionSummary") if isinstance(data.get("regionSummary"), dict) else {},
            templateMeta=data.get("templateMeta") if isinstance(data.get("templateMeta"), dict) else {},
            qualityCheck=data.get("qualityCheck") if isinstance(data.get("qualityCheck"), dict) else {},
            errorMessage=tool_result.errorMessage or tool_result.error,
        )
        answer = action_result.markdown or tool_result.summary or action_result.errorMessage or "方案生成未返回内容"
        return MapAgentRunResponse(
            answer=answer,
            action=normalize_action(request.action),
            intent="SOLUTION_GENERATE",
            mapContext=request.mapContext.model_dump(exclude_none=True) if request.mapContext else None,
            actionResult=action_result,
            suggestedActions=[SuggestedAction(action="SAVE_SOLUTION_DRAFT", label="保存为方案任务", requiresConfirmation=True)] if action_result.status == "SUCCESS" else [],
            toolResults=[tool_result.model_dump(exclude_none=True)],
            sources=data.get("sources") if isinstance(data.get("sources"), list) else [],
            answerMeta={"answerSource": "TOOL_RESULT", "llmSuccess": False, "llmStatus": "SKIPPED"},
            trace={"steps": [{"name": "solution_generate", "label": "生成方案预览", "status": action_result.status, "count": 1 if tool_result.success else 0}]},
            data={"orchestratorProvider": "langgraph", "graphName": "solution_generation_graph"},
        )

    async def _run_draft_save(self, request: MapAgentRunRequest, tenant_id: Optional[str], trace_id: Optional[str]) -> MapAgentRunResponse:
        if not bool((request.options or {}).get("confirmed")):
            return MapAgentRunResponse(
                answer="保存方案任务需要确认。",
                action="SAVE_SOLUTION_DRAFT",
                intent="SOLUTION_SAVE",
                mapContext=request.mapContext.model_dump(exclude_none=True) if request.mapContext else None,
                actionResult=ActionResult(type="DRAFT_SAVE", status="NEEDS_CONFIRMATION", errorMessage="options.confirmed 必须为 true"),
                suggestedActions=[SuggestedAction(action="SAVE_SOLUTION_DRAFT", label="确认保存方案任务", requiresConfirmation=True, payload=request.actionInput)],
                answerMeta={"answerSource": "SYSTEM", "llmSuccess": False, "llmStatus": "SKIPPED"},
                trace={"steps": [{"name": "write_confirmation_check", "label": "写入确认检查", "status": "SKIPPED", "data": {"confirmed": False}}]},
                data={"orchestratorProvider": "langgraph", "graphName": "draft_save_graph", "writeConfirmation": False},
            )
        save_args = dict(request.actionInput or {})
        save_args["confirmed"] = True
        tool_result = await self.gateway.execute_tool(
            call=ToolCall(toolName="solution.saveTask", args=save_args, reason="Save confirmed solution draft"),
            request=self._to_agent_request(request, request.action),
            tenant_id=tenant_id or "default",
            trace_id=trace_id,
        )
        data = tool_result.data if isinstance(tool_result.data, dict) else {}
        return MapAgentRunResponse(
            answer=tool_result.summary or ("方案任务已保存" if tool_result.success else "方案任务保存失败"),
            action="SAVE_SOLUTION_DRAFT",
            intent="SOLUTION_SAVE",
            mapContext=request.mapContext.model_dump(exclude_none=True) if request.mapContext else None,
            actionResult=ActionResult(type="DRAFT_SAVE", status="SUCCESS" if tool_result.success else "FAILED", draftTask=data, errorMessage=tool_result.errorMessage or tool_result.error),
            toolResults=[tool_result.model_dump(exclude_none=True)],
            answerMeta={"answerSource": "TOOL_RESULT", "llmSuccess": False, "llmStatus": "SKIPPED"},
            trace={"steps": [{"name": "solution_save_task", "label": "保存方案任务", "status": "SUCCESS" if tool_result.success else "FAILED", "count": 1 if tool_result.success else 0}]},
            data={"orchestratorProvider": "langgraph", "graphName": "draft_save_graph", "writeConfirmation": True},
        )

    def _from_agent_response(self, request: MapAgentRunRequest, response: Any, action: str, graph_name: str, action_result: ActionResult) -> MapAgentRunResponse:
        data = response.data or {}
        sources = response.sources or response.knowledgeSources or []
        return MapAgentRunResponse(
            answer=response.answer,
            action=action,
            intent=response.intent,
            mapContext=response.mapContext,
            actionResult=action_result,
            suggestedActions=suggested_actions_for(action),
            toolResults=response.toolResults or [],
            knowledgeSources=response.knowledgeSources or [],
            sources=sources,
            answerMeta=data.get("answerMeta") or {},
            trace=response.trace or {},
            data={**data, "orchestratorProvider": "langgraph", "graphName": graph_name},
        )

    def _to_agent_request(self, request: MapAgentRunRequest, action: str) -> MapAiAgentRequest:
        options = dict(request.options or {})
        options["action"] = action
        return MapAiAgentRequest(message=request.message, mapContext=request.mapContext, options=options)

    def _solution_args(self, request: MapAgentRunRequest) -> Dict[str, Any]:
        args = dict(request.actionInput or {})
        args["action"] = normalize_action(request.action)
        if request.mapContext:
            args["mapContext"] = request.mapContext.model_dump(exclude_none=True)
        return args


def normalize_action(action: Optional[str]) -> str:
    normalized = str(action or "CHAT").strip().upper()
    allowed = ANALYSIS_ACTIONS | SOLUTION_ACTIONS | {"SAVE_SOLUTION_DRAFT"}
    return normalized if normalized in allowed else "CHAT"


def graph_name_for(action: str) -> str:
    if action == "ANALYZE_OBJECT":
        return "object_analysis_graph"
    if action == "ANALYZE_REGION":
        return "region_analysis_graph"
    if action == "ANALYZE_ROUTE":
        return "route_report_graph"
    if action == "PLAN_ONLY":
        return "plan_graph"
    return "chat_graph"


def suggested_actions_for(action: str) -> List[SuggestedAction]:
    if action == "ANALYZE_OBJECT":
        return [SuggestedAction(action="GENERATE_OBJECT_SOLUTION", label="生成对象方案", requiresConfirmation=False)]
    if action == "ANALYZE_REGION":
        return [SuggestedAction(action="GENERATE_REGION_SOLUTION", label="生成区域养护建议", requiresConfirmation=False)]
    if action == "ANALYZE_ROUTE":
        return [SuggestedAction(action="GENERATE_ROUTE_REPORT", label="生成路线报告", requiresConfirmation=False)]
    return []
```

- [ ] **Step 2: Wire the new FastAPI endpoint**

In `srmp-ai-orchestrator/app/main.py`, change the schema import to include `MapAgentRunRequest`:

```python
from .schemas import MapAgentRunRequest, MapAiAgentRequest, MapAiAgentResponse, ToolCall
```

Import and instantiate the run workflow after the existing `workflow`:

```python
from .map_agent_run import MapAgentRunWorkflow

map_agent_run_workflow = MapAgentRunWorkflow(base_workflow=workflow, gateway=gateway)
```

Add the endpoint near the existing map-agent endpoint:

```python
@app.post("/api/srmp/langgraph/map-agent/run")
async def map_agent_run(
    request: MapAgentRunRequest,
    x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-Id"),
    x_ai_trace_id: Optional[str] = Header(default=None, alias="X-AI-Trace-Id"),
):
    started_at = time.perf_counter()
    try:
        response = await map_agent_run_workflow.run(request=request, tenant_id=x_tenant_id, trace_id=x_ai_trace_id)
        cost_ms = int((time.perf_counter() - started_at) * 1000)
        audit_record = runtime_audit_store.record_success(
            request=request,
            response=response,
            tenant_id=x_tenant_id,
            trace_id=x_ai_trace_id,
            cost_ms=cost_ms,
        )
        response.data.setdefault("runtimeAuditId", audit_record.get("id"))
        response.data.setdefault("runtimeCostMs", cost_ms)
        response.trace.setdefault("runtimeAuditId", audit_record.get("id"))
        return response
    except Exception as exc:  # noqa: BLE001
        cost_ms = int((time.perf_counter() - started_at) * 1000)
        runtime_audit_store.record_failure(
            request=request,
            tenant_id=x_tenant_id,
            trace_id=x_ai_trace_id,
            cost_ms=cost_ms,
            error=exc,
        )
        raise
```

- [ ] **Step 3: Compile Python**

Run:

```bash
srmp-ai-orchestrator/.venv/bin/python -m py_compile srmp-ai-orchestrator/app/*.py
```

Expected: command exits with code 0.

- [ ] **Step 4: Commit**

```bash
git add srmp-ai-orchestrator/app/schemas.py srmp-ai-orchestrator/app/map_agent_run.py srmp-ai-orchestrator/app/main.py
git commit -m "feat: add LangGraph map agent run endpoint"
```

## Task 3: Add Java Run DTOs and Remote Run Client

**Files:**
- Create: `srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/dto/MapAgentRunRequest.java`
- Create: `srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/dto/MapAgentRunResponse.java`
- Create: `srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/dto/MapAgentActionResult.java`
- Create: `srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/dto/MapAgentSuggestedAction.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/impl/RemoteLangGraphOrchestrator.java`
- Test: `mvn -pl srmp-agent test`

- [ ] **Step 1: Create Java Run request DTO**

Create `MapAgentRunRequest.java`:

```java
package com.smartroad.srmp.agent.mapagent.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class MapAgentRunRequest {
    private String message;
    private String action = "CHAT";
    private MapAiContext mapContext;
    private Map<String, Object> actionInput = new LinkedHashMap<>();
    private Map<String, Object> options = new LinkedHashMap<>();
}
```

- [ ] **Step 2: Create Java Run response support DTOs**

Create `MapAgentActionResult.java`:

```java
package com.smartroad.srmp.agent.mapagent.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class MapAgentActionResult {
    private String type = "ANSWER";
    private String status = "SUCCESS";
    private String title;
    private String markdown;
    private Map<String, Object> objectSummary = new LinkedHashMap<>();
    private Map<String, Object> regionSummary = new LinkedHashMap<>();
    private Map<String, Object> routeSummary = new LinkedHashMap<>();
    private Map<String, Object> templateMeta = new LinkedHashMap<>();
    private Map<String, Object> qualityCheck = new LinkedHashMap<>();
    private Map<String, Object> draftTask;
    private String errorMessage;
}
```

Create `MapAgentSuggestedAction.java`:

```java
package com.smartroad.srmp.agent.mapagent.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class MapAgentSuggestedAction {
    private String action;
    private String label;
    private Boolean requiresConfirmation = false;
    private Boolean disabled = false;
    private String reason;
    private Map<String, Object> payload = new LinkedHashMap<>();
}
```

Create `MapAgentRunResponse.java`:

```java
package com.smartroad.srmp.agent.mapagent.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class MapAgentRunResponse {
    private String answer;
    private String mode = "LANGGRAPH_MAP_AGENT";
    private String action;
    private String intent;
    private Map<String, Object> mapContext;
    private MapAgentActionResult actionResult = new MapAgentActionResult();
    private List<MapAgentSuggestedAction> suggestedActions = new ArrayList<>();
    private List<Map<String, Object>> toolResults = new ArrayList<>();
    private List<Map<String, Object>> knowledgeSources = new ArrayList<>();
    private List<Map<String, Object>> sources = new ArrayList<>();
    private Map<String, Object> answerMeta = new LinkedHashMap<>();
    private Map<String, Object> trace = new LinkedHashMap<>();
    private Map<String, Object> data = new LinkedHashMap<>();
}
```

- [ ] **Step 3: Add Run client method to RemoteLangGraphOrchestrator**

In `RemoteLangGraphOrchestrator.java`:

Keep the existing `provider()` and `chat()` methods for this task so the current router still compiles. Add this new method:

```java
public MapAgentRunResponse run(MapAgentRunRequest request) {
    MapAgentRunRequest safeRequest = request == null ? new MapAgentRunRequest() : request;
    String url = buildUrl(properties.getLanggraphUrl(), properties.getLanggraphEndpointPath());
    RestTemplate restTemplate = buildRestTemplate(defaultInt(properties.getConnectTimeoutMs(), 10000),
            defaultInt(properties.getReadTimeoutMs(), 300000));

    String tenantId = resolveTenantId(safeRequest);
    String traceId = resolveTraceId(safeRequest);
    HttpHeaders headers = buildHeaders(tenantId, traceId);
    HttpEntity<MapAgentRunRequest> httpEntity = new HttpEntity<>(safeRequest, headers);

    long start = System.currentTimeMillis();
    ResponseEntity<Map> entity = restTemplate.exchange(url, HttpMethod.POST, httpEntity, Map.class);
    if (!entity.getStatusCode().is2xxSuccessful()) {
        throw new IllegalStateException("LangGraph 编排服务 HTTP 状态异常：" + entity.getStatusCodeValue());
    }
    Map body = entity.getBody();
    if (body == null) {
        throw new IllegalStateException("LangGraph 编排服务返回为空");
    }
    Object responseBody = unwrapResponseBody(body);
    MapAgentRunResponse response = objectMapper.convertValue(responseBody, MapAgentRunResponse.class);
    normalizeRunResponse(response, url, tenantId, traceId, System.currentTimeMillis() - start);
    return response;
}
```

Add `normalizeRunResponse`:

```java
private void normalizeRunResponse(MapAgentRunResponse response, String url, String tenantId, String traceId, long costMs) {
    if (response == null) {
        throw new IllegalStateException("LangGraph 编排服务响应无法转换为 MapAgentRunResponse");
    }
    if (isBlank(response.getAnswer())) {
        response.setAnswer("LangGraph 未返回回答正文，请查看 AI 执行过程。");
    }
    if (isBlank(response.getMode())) {
        response.setMode("LANGGRAPH_MAP_AGENT");
    }
    if (response.getData() == null) {
        response.setData(new LinkedHashMap<String, Object>());
    }
    response.getData().put("orchestratorProvider", "langgraph");
    response.getData().put("orchestratorFallback", false);
    response.getData().put("remoteLangGraphUrl", url);
    response.getData().put("remoteCostMs", costMs);
    response.getData().put("tenantId", tenantId);
    response.getData().put("traceId", traceId);
    if (response.getTrace() == null) {
        response.setTrace(new LinkedHashMap<String, Object>());
    }
    response.getTrace().put("traceId", traceId);
    response.getTrace().put("mode", response.getMode());
    response.getTrace().put("orchestratorProvider", "langgraph");
    response.getTrace().put("orchestratorFallback", false);
    response.getTrace().put("remoteCostMs", costMs);
}
```

Add overloads for `MapAgentRunRequest`; keep the existing `MapAiAgentRequest` overloads until Task 4 deletes `chat()`:

```java
private String resolveTenantId(MapAgentRunRequest safeRequest) {
    if (safeRequest != null && safeRequest.getMapContext() != null && !isBlank(safeRequest.getMapContext().getTenantId())) {
        return safeRequest.getMapContext().getTenantId();
    }
    String tenant = TenantContextHolder.getTenantId();
    return isBlank(tenant) ? "default" : tenant;
}

private String resolveTraceId(MapAgentRunRequest safeRequest) {
    if (safeRequest != null && safeRequest.getOptions() != null) {
        Object value = safeRequest.getOptions().get("traceId");
        if (value != null && !isBlank(String.valueOf(value))) {
            return String.valueOf(value);
        }
    }
    return "java-lg-" + UUID.randomUUID().toString().replace("-", "");
}
```

- [ ] **Step 4: Run Java tests**

Run:

```bash
mvn -pl srmp-agent test
```

Expected: Maven exits with code 0.

- [ ] **Step 5: Commit**

```bash
git add srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/dto \
  srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/impl/RemoteLangGraphOrchestrator.java
git commit -m "feat: add Java LangGraph run client"
```

## Task 4: Make Java Router LangGraph-only and Add `/run`

**Files:**
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/AgentOrchestratorRouter.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/AgentOrchestrator.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/AgentOrchestratorProperties.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/impl/RemoteLangGraphOrchestrator.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorHealthController.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/controller/MapAiAgentController.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/MapAiAgentService.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/MapAiAgentServiceImpl.java`
- Test: `mvn -pl srmp-agent test`

- [ ] **Step 1: Replace router with remote-only implementation**

Replace `AgentOrchestrator.java` with:

```java
package com.smartroad.srmp.agent.orchestrator;

import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunResponse;

public interface AgentOrchestrator {
    MapAgentRunResponse run(MapAgentRunRequest request);
}
```

In `RemoteLangGraphOrchestrator.java`, delete the old `provider()` and `chat(MapAiAgentRequest request)` methods because provider selection and native-compatible chat are removed.

Replace `AgentOrchestratorRouter.java` with:

```java
package com.smartroad.srmp.agent.orchestrator;

import com.smartroad.srmp.agent.mapagent.dto.MapAgentActionResult;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class AgentOrchestratorRouter {

    @Resource
    private AgentOrchestrator remoteLangGraphOrchestrator;

    public MapAgentRunResponse run(MapAgentRunRequest request) {
        try {
            MapAgentRunResponse response = remoteLangGraphOrchestrator.run(request);
            markLangGraph(response);
            return response;
        } catch (Exception e) {
            log.warn("[AI-ORCHESTRATOR] langgraph failed without native fallback: {}", e.getMessage(), e);
            return errorResponse(request, e.getMessage());
        }
    }

    private void markLangGraph(MapAgentRunResponse response) {
        if (response == null) {
            return;
        }
        if (response.getData() == null) {
            response.setData(new LinkedHashMap<String, Object>());
        }
        response.getData().put("orchestratorProvider", "langgraph");
        response.getData().put("orchestratorFallback", false);
        if (response.getTrace() == null) {
            response.setTrace(new LinkedHashMap<String, Object>());
        }
        response.getTrace().put("orchestratorProvider", "langgraph");
        response.getTrace().put("orchestratorFallback", false);
    }

    private MapAgentRunResponse errorResponse(MapAgentRunRequest request, String message) {
        MapAgentRunResponse response = new MapAgentRunResponse();
        response.setAction(request == null ? "CHAT" : request.getAction());
        response.setIntent("ERROR");
        response.setAnswer("LangGraph Runtime 不可用：" + safe(message));
        MapAgentActionResult result = new MapAgentActionResult();
        result.setType("ERROR");
        result.setStatus("FAILED");
        result.setErrorMessage(safe(message));
        response.setActionResult(result);
        Map<String, Object> answerMeta = new LinkedHashMap<>();
        answerMeta.put("answerSource", "ERROR");
        answerMeta.put("llmSuccess", false);
        answerMeta.put("llmStatus", "FAILED");
        answerMeta.put("fallbackReason", safe(message));
        response.setAnswerMeta(answerMeta);
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("orchestratorProvider", "langgraph");
        trace.put("orchestratorFallback", false);
        trace.put("steps", java.util.Collections.singletonList(step(safe(message))));
        response.setTrace(trace);
        response.getData().put("orchestratorProvider", "langgraph");
        response.getData().put("orchestratorFallback", false);
        return response;
    }

    private Map<String, Object> step(String message) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("name", "remote_langgraph_call");
        step.put("label", "远程 LangGraph 调用");
        step.put("status", "FAILED");
        step.put("error", message);
        return step;
    }

    private String safe(String value) {
        return value == null || value.trim().length() == 0 ? "未知错误" : value.trim();
    }
}
```

- [ ] **Step 2: Remove provider/fallback properties**

In `AgentOrchestratorProperties.java`, set the endpoint path to run and remove `provider` and `fallbackToNative` fields. Keep:

```java
private String langgraphEndpointPath = "/api/srmp/langgraph/map-agent/run";
```

- [ ] **Step 3: Update health and ops controllers**

In `AgentOrchestratorHealthController.java` and `AgentOrchestratorOpsController.java`:

- Replace `providerNames()` usage with `java.util.Collections.singletonList("langgraph")`.
- Replace `properties.getProvider()` with literal `"langgraph"`.
- Replace `properties.getFallbackToNative()` with `false`.
- Remove loops over `List<AgentOrchestrator>`.

The summary payload must still include:

```java
data.put("provider", "langgraph");
data.put("availableProviders", java.util.Collections.singletonList("langgraph"));
```

Remove `fallbackToNative` from Java health and ops payloads.

- [ ] **Step 4: Change MapAiAgent service and controller to run**

Replace `MapAiAgentService.java` with:

```java
package com.smartroad.srmp.agent.mapagent.service;

import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunResponse;

public interface MapAiAgentService {
    MapAgentRunResponse run(MapAgentRunRequest request);
}
```

Replace `MapAiAgentServiceImpl.java` with:

```java
package com.smartroad.srmp.agent.mapagent.service.impl;

import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunRequest;
import com.smartroad.srmp.agent.mapagent.dto.MapAgentRunResponse;
import com.smartroad.srmp.agent.mapagent.service.MapAiAgentService;
import com.smartroad.srmp.agent.orchestrator.AgentOrchestratorRouter;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class MapAiAgentServiceImpl implements MapAiAgentService {

    @Resource
    private AgentOrchestratorRouter agentOrchestratorRouter;

    @Override
    public MapAgentRunResponse run(MapAgentRunRequest request) {
        return agentOrchestratorRouter.run(request);
    }
}
```

Replace `MapAiAgentController.java` with:

```java
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
```

- [ ] **Step 5: Run Java tests**

Run:

```bash
mvn -pl srmp-agent test
```

Expected: Maven exits with code 0.

- [ ] **Step 6: Commit**

```bash
git add srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator \
  srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/controller/MapAiAgentController.java \
  srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service
git commit -m "feat: make map agent router LangGraph-only"
```

## Task 5: Upgrade Solution Tools for Real Preview and Confirmed Save

**Files:**
- Create: `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/AiMapRegionSolutionGateway.java`
- Create: `srmp-gis/src/main/java/com/smartroad/srmp/gis/service/impl/GisMapRegionSolutionGateway.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/tool/impl/SolutionGenerateTool.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/tool/impl/SolutionSaveTool.java`
- Test: `mvn -pl srmp-agent,srmp-gis test`

- [ ] **Step 1: Add a region solution gateway SPI without creating a Maven cycle**

Create `srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/AiMapRegionSolutionGateway.java`:

```java
package com.smartroad.srmp.agent.solution.service;

import java.util.Map;

public interface AiMapRegionSolutionGateway {
    Map<String, Object> generate(Map<String, Object> request);
}
```

Create `srmp-gis/src/main/java/com/smartroad/srmp/gis/service/impl/GisMapRegionSolutionGateway.java`:

```java
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
```

- [ ] **Step 2: Implement real `solution.generateDraft`**

Replace the capability-message body with service-backed generation:

Add import:

```java
import javax.annotation.Resource;
```

Then add fields and replace `execute()`:

```java
@Resource
private org.springframework.beans.factory.ObjectProvider<com.smartroad.srmp.agent.solution.service.AiMapRegionSolutionGateway> mapRegionSolutionGatewayProvider;

@Resource
private com.smartroad.srmp.agent.map.solution.service.MapObjectSolutionService mapObjectSolutionService;

private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

@Override
public AiToolResult execute(AiToolContext context, Map<String, Object> args) {
    long start = System.currentTimeMillis();
    Map<String, Object> safeArgs = args == null ? new LinkedHashMap<String, Object>() : args;
    String action = stringValue(safeArgs.get("action"));
    Map<String, Object> mapContext = mapContextMap(safeArgs, context);
    if ("GENERATE_REGION_SOLUTION".equals(action)) {
        com.smartroad.srmp.agent.solution.service.AiMapRegionSolutionGateway gateway = mapRegionSolutionGatewayProvider.getIfAvailable();
        if (gateway == null) {
            return AiToolResult.failed(name(), "区域方案生成服务未注册", System.currentTimeMillis() - start);
        }
        Map<String, Object> requestMap = regionRequestMap(safeArgs, mapContext, context);
        Map<String, Object> data = gateway.generate(requestMap);
        return AiToolResult.success(name(), "区域养护建议预览已生成", data, 1, System.currentTimeMillis() - start);
    }
    if ("GENERATE_OBJECT_SOLUTION".equals(action)) {
        com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionRequest request = objectSolutionRequest(safeArgs, mapContext, context, "GENERAL_ADVICE", null);
        com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionResponse response = mapObjectSolutionService.generate(request);
        Map<String, Object> data = objectMapper.convertValue(response, Map.class);
        return AiToolResult.success(name(), "对象方案预览已生成", data, 1, System.currentTimeMillis() - start);
    }
    if ("GENERATE_ROUTE_REPORT".equals(action)) {
        com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionRequest request = objectSolutionRequest(safeArgs, mapContext, context, "ROUTE_REPORT", "ROAD_ROUTE");
        com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionResponse response = mapObjectSolutionService.generate(request);
        Map<String, Object> data = objectMapper.convertValue(response, Map.class);
        return AiToolResult.success(name(), "路线报告预览已生成", data, 1, System.currentTimeMillis() - start);
    }
    return AiToolResult.failed(name(), "不支持的方案生成动作：" + action, System.currentTimeMillis() - start);
}
```

Add fields/helpers to the class:

```java
private com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionRequest objectSolutionRequest(Map<String, Object> args,
                                                                                                  Map<String, Object> mapContext,
                                                                                                  AiToolContext context,
                                                                                                  String defaultSolutionType,
                                                                                                  String forcedObjectType) {
    Map<String, Object> mapObject = mapValue(firstNonNull(args.get("mapObject"), mapContext.get("mapObject")));
    String objectType = firstNonBlank(forcedObjectType, stringValue(args.get("objectType")), stringValue(mapObject.get("objectType")), stringValue(mapObject.get("object_type")));
    com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionRequest request = new com.smartroad.srmp.agent.map.solution.dto.MapObjectSolutionRequest();
    request.setTenantId(context == null ? null : context.getTenantId());
    request.setObjectType(objectType);
    request.setObjectId(firstNonBlank(stringValue(args.get("objectId")), stringValue(mapObject.get("objectId")), stringValue(mapObject.get("object_id")), stringValue(mapObject.get("id"))));
    request.setRouteCode(firstNonBlank(stringValue(args.get("routeCode")), stringValue(mapContext.get("routeCode")), stringValue(mapContext.get("route_code")), stringValue(mapObject.get("routeCode")), stringValue(mapObject.get("route_code"))));
    request.setYear(intValue(firstNonNull(args.get("year"), mapContext.get("year"), mapObject.get("year"))));
    request.setSolutionType(firstNonBlank(stringValue(args.get("solutionType")), defaultSolutionType));
    request.setMapObject(mapObject);
    request.setOptions(mergeOptions(args, context));
    return request;
}

private Map<String, Object> regionRequestMap(Map<String, Object> args, Map<String, Object> mapContext, AiToolContext context) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("solutionType", firstNonBlank(stringValue(args.get("solutionType")), "REGION_MAINTENANCE_SUGGESTION"));
    request.put("geometry", firstNonNull(args.get("geometry"), mapContext.get("geometry")));
    request.put("query", firstNonNull(args.get("query"), regionQuery(mapContext)));
    request.put("layers", firstNonNull(args.get("layers"), mapContext.get("selectedLayers")));
    request.put("options", mergeOptions(args, context));
    return request;
}

private Map<String, Object> regionQuery(Map<String, Object> mapContext) {
    Map<String, Object> query = new LinkedHashMap<>();
    putIfPresent(query, "routeCode", firstNonNull(mapContext.get("routeCode"), mapContext.get("route_code")));
    putIfPresent(query, "year", mapContext.get("year"));
    Map<String, Object> extra = mapValue(mapContext.get("extra"));
    putIfPresent(query, "indexCode", extra.get("indexCode"));
    putIfPresent(query, "grade", extra.get("grade"));
    return query;
}

private Map<String, Object> mapContextMap(Map<String, Object> args, AiToolContext context) {
    Object raw = args.get("mapContext");
    if (raw instanceof Map) {
        return (Map<String, Object>) raw;
    }
    if (context.getMapContext() != null) {
        return objectMapper.convertValue(context.getMapContext(), Map.class);
    }
    return new LinkedHashMap<>();
}

private Map<String, Object> mapValue(Object value) {
    return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>();
}

private Map<String, Object> mergeOptions(Map<String, Object> args, AiToolContext context) {
    Map<String, Object> options = new LinkedHashMap<>();
    if (context != null && context.getOptions() != null) {
        options.putAll(context.getOptions());
    }
    options.putAll(mapValue(args.get("options")));
    return options;
}

private void putIfPresent(Map<String, Object> map, String key, Object value) {
    if (value != null) {
        map.put(key, value);
    }
}

private String stringValue(Object value) {
    return value == null ? null : String.valueOf(value);
}

private String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String value : values) {
        if (value != null && value.trim().length() > 0) {
            return value.trim();
        }
    }
    return null;
}

private Object firstNonNull(Object... values) {
    if (values == null) return null;
    for (Object value : values) {
        if (value != null) {
            return value;
        }
    }
    return null;
}

private Integer intValue(Object value) {
    if (value == null) return null;
    try {
        return Integer.valueOf(String.valueOf(value));
    } catch (Exception e) {
        return null;
    }
}
```

- [ ] **Step 3: Implement confirmed save guard**

In `SolutionSaveTool.java`, replace the capability-message body with a confirmed save implementation:

Add import:

```java
import javax.annotation.Resource;
```

Then add fields and replace `execute()`:

```java
@Resource
private com.smartroad.srmp.agent.solution.service.AiSolutionDraftService aiSolutionDraftService;

private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

@Override
public AiToolResult execute(AiToolContext context, Map<String, Object> args) {
    long start = System.currentTimeMillis();
    Map<String, Object> safeArgs = args == null ? new LinkedHashMap<String, Object>() : args;
    if (!Boolean.TRUE.equals(safeArgs.get("confirmed"))) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "NEEDS_CONFIRMATION");
        data.put("message", "保存方案任务需要 confirmed=true");
        return AiToolResult.success(name(), "保存前需要确认", data, 0, System.currentTimeMillis() - start);
    }
    return saveDraft(context, safeArgs, start);
}

private AiToolResult saveDraft(AiToolContext context, Map<String, Object> args, long start) {
    com.smartroad.srmp.agent.solution.dto.AiSolutionDraftSaveRequest request =
            objectMapper.convertValue(args, com.smartroad.srmp.agent.solution.dto.AiSolutionDraftSaveRequest.class);
    request.setTenantId(context == null ? null : context.getTenantId());
    Map<String, Object> saved = "MAP_REGION".equalsIgnoreCase(request.getOriginType())
            ? aiSolutionDraftService.saveMapRegionDraft(request)
            : aiSolutionDraftService.saveMapObjectDraft(request);
    return AiToolResult.success(name(), "方案任务已保存", saved, 1, System.currentTimeMillis() - start);
}
```

- [ ] **Step 4: Run Java tests**

Run:

```bash
mvn -pl srmp-agent,srmp-gis test
```

Expected: Maven exits with code 0.

- [ ] **Step 5: Commit**

```bash
git add srmp-agent/src/main/java/com/smartroad/srmp/agent/solution/service/AiMapRegionSolutionGateway.java \
  srmp-gis/src/main/java/com/smartroad/srmp/gis/service/impl/GisMapRegionSolutionGateway.java
git add srmp-agent/src/main/java/com/smartroad/srmp/agent/tool/impl/SolutionGenerateTool.java \
  srmp-agent/src/main/java/com/smartroad/srmp/agent/tool/impl/SolutionSaveTool.java
git commit -m "feat: make solution tools execute real workflows"
```

## Task 6: Delete Old Java AI Endpoints and Native Classes

**Files:**
- Delete: `srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/impl/NativeMapAgentOrchestrator.java`
- Delete: `srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/plan/MapAiToolPlanner.java`
- Delete: `srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/plan/MapAiToolPlannerImpl.java`
- Delete: `srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/enhance/MapAiAnswerEnhanceContext.java`
- Delete: `srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/enhance/MapAiAnswerEnhancer.java`
- Delete: `srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/enhance/MapAiAnswerEnhancerRegistry.java`
- Delete: `srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/AssessmentResultAdviceEnhancer.java`
- Delete: `srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/MapAiAnswerPolisher.java`
- Delete: `srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/MapObjectDiseaseAdviceEnhancer.java`
- Delete: `srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/RoadAssetAdviceEnhancer.java`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/map/solution/controller/MapObjectSolutionController.java`
- Modify: `srmp-gis/src/main/java/com/smartroad/srmp/gis/controller/GisMapRegionController.java`
- Modify: `srmp-ai-orchestrator/app/main.py`
- Test: `mvn -pl srmp-agent,srmp-gis test`
- Test: `srmp-ai-orchestrator/.venv/bin/python -m py_compile srmp-ai-orchestrator/app/*.py`

- [ ] **Step 1: Delete native classes**

Run:

```bash
git rm srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/impl/NativeMapAgentOrchestrator.java \
  srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/plan/MapAiToolPlanner.java \
  srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/plan/MapAiToolPlannerImpl.java \
  srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/enhance/MapAiAnswerEnhanceContext.java \
  srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/enhance/MapAiAnswerEnhancer.java \
  srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/enhance/MapAiAnswerEnhancerRegistry.java \
  srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/AssessmentResultAdviceEnhancer.java \
  srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/MapAiAnswerPolisher.java \
  srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/MapObjectDiseaseAdviceEnhancer.java \
  srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/service/impl/RoadAssetAdviceEnhancer.java
```

- [ ] **Step 2: Remove object solution controller endpoint**

Replace `MapObjectSolutionController.java` with a package-private empty marker-free file removal:

```bash
git rm srmp-agent/src/main/java/com/smartroad/srmp/agent/map/solution/controller/MapObjectSolutionController.java
```

The service classes remain because `solution.generateDraft` uses them.

- [ ] **Step 3: Remove region solution endpoint but keep analysis/drafts**

In `GisMapRegionController.java`, remove:

```java
@Resource
private MapRegionSolutionService mapRegionSolutionService;

@PostMapping("/solution")
public R<MapRegionSolutionResponse> solution(@RequestBody MapRegionSolutionRequest request) {
    return R.ok(mapRegionSolutionService.generate(request));
}
```

Also remove unused imports for `MapRegionSolutionRequest`, `MapRegionSolutionResponse`, and `MapRegionSolutionService`.

- [ ] **Step 4: Remove Python chat endpoint**

In `srmp-ai-orchestrator/app/main.py`, delete the old function decorated with:

```python
@app.post("/api/srmp/langgraph/map-agent/chat")
```

Keep `/api/srmp/langgraph/map-agent/run` as the only one-map AI endpoint.

- [ ] **Step 5: Search for deleted symbols**

Run:

```bash
rg -n "NativeMapAgentOrchestrator|MapAiToolPlanner|MapAiAnswerEnhancer|MapAiAnswerPolisher|map-object/solution|map-region/solution|/map-agent/chat" srmp-agent/src/main/java srmp-gis/src/main/java srmp-ai-orchestrator/app
```

Expected: no matches under Java or Python runtime source, except non-runtime documentation comments that name old endpoints for historical explanation. Remove runtime comments that tell callers to use old endpoints.

- [ ] **Step 6: Run Java and Python tests**

Run:

```bash
mvn -pl srmp-agent,srmp-gis test
srmp-ai-orchestrator/.venv/bin/python -m py_compile srmp-ai-orchestrator/app/*.py
```

Expected: both commands exit with code 0.

- [ ] **Step 7: Commit**

```bash
git add -u srmp-agent/src/main/java srmp-gis/src/main/java srmp-ai-orchestrator/app/main.py
git commit -m "refactor: remove native map agent endpoints"
```

## Task 7: Add Frontend `mapAgentRun` API and Delete Old AI Helpers

**Files:**
- Modify: `srmp-web-ui/src/api/agent.ts`
- Modify: `srmp-web-ui/src/api/gis.ts`
- Test: `npm --prefix srmp-web-ui run build`

- [ ] **Step 1: Replace old AI helpers in `agent.ts`**

In `srmp-web-ui/src/api/agent.ts`, delete:

- `AgentChatRequest`
- `MapObjectSolutionRequest`
- `MapObjectSolutionResponse`
- `chat()`
- `generateMapObjectSolution()`
- `MapAiAgentRequest`
- `MapAiAgentResponse`
- `mapAgentChat()`

Add:

```ts
export type MapAgentAction =
  | 'CHAT'
  | 'ANALYZE_OBJECT'
  | 'ANALYZE_ROUTE'
  | 'ANALYZE_REGION'
  | 'GENERATE_OBJECT_SOLUTION'
  | 'GENERATE_REGION_SOLUTION'
  | 'GENERATE_ROUTE_REPORT'
  | 'SAVE_SOLUTION_DRAFT'
  | 'PLAN_ONLY'

export interface MapAgentRunRequest {
  message?: string
  action: MapAgentAction
  mapContext?: MapAiContext
  actionInput?: Record<string, any>
  options?: Record<string, any>
}

export interface MapAgentActionResult {
  type: string
  status: string
  title?: string
  markdown?: string
  objectSummary?: Record<string, any>
  regionSummary?: Record<string, any>
  routeSummary?: Record<string, any>
  templateMeta?: Record<string, any>
  qualityCheck?: Record<string, any>
  draftTask?: Record<string, any> | null
  errorMessage?: string
}

export interface MapAgentSuggestedAction {
  action: MapAgentAction
  label: string
  requiresConfirmation?: boolean
  disabled?: boolean
  reason?: string
  payload?: Record<string, any>
}

export interface MapAgentRunResponse {
  answer: string
  mode?: string
  action: MapAgentAction | string
  intent?: string
  mapContext?: MapAiContext
  actionResult?: MapAgentActionResult
  suggestedActions?: MapAgentSuggestedAction[]
  toolResults?: Record<string, any>[]
  knowledgeSources?: Record<string, any>[]
  sources?: Record<string, any>[]
  answerMeta?: Record<string, any>
  trace?: Record<string, any>
  data?: Record<string, any>
}

export function mapAgentRun(data: MapAgentRunRequest): Promise<MapAgentRunResponse> {
  return aiRequest.post('/api/agent/map-agent/run', data)
}
```

- [ ] **Step 2: Remove region solution helper in `gis.ts`**

In `srmp-web-ui/src/api/gis.ts`, delete:

```ts
export interface MapRegionSolutionRequest extends MapRegionAnalysisRequest {
  solutionType?: string
}

export interface MapRegionSolutionResponse {
  ...
}

export function generateMapRegionSolution(data: MapRegionSolutionRequest): Promise<MapRegionSolutionResponse> {
  return aiRequest.post('/api/gis/map-region/solution', data)
}
```

Keep `analyzeMapRegion()` and `saveMapRegionSolutionDraft()`.

- [ ] **Step 3: Run frontend build and observe expected failures**

Run:

```bash
npm --prefix srmp-web-ui run build
```

Expected: build fails with imports still referencing deleted helpers. Record the failing files and fix them in Tasks 8 and 9.

- [ ] **Step 4: Commit after downstream callers are fixed**

Do not commit this task until Tasks 8 and 9 compile. The commit command is in Task 9.

## Task 8: Switch Generic AI Pages to `mapAgentRun`

**Files:**
- Modify: `srmp-web-ui/src/views/agent/AiChatPage.vue`
- Modify: `srmp-web-ui/src/views/agent/KnowledgeVectorPage.vue`
- Test: `npm --prefix srmp-web-ui run build`

- [ ] **Step 1: Update `AiChatPage.vue` imports**

Replace:

```ts
import { chat } from '../../api/agent'
```

With:

```ts
import { mapAgentRun } from '../../api/agent'
```

- [ ] **Step 2: Update `AiChatPage.vue` send call**

Replace:

```ts
const result = await chat({
  message: text,
  context: context.value,
  options: options.value
})
```

With:

```ts
const result = await mapAgentRun({
  action: 'CHAT',
  message: text,
  mapContext: {
    mode: 'ROUTE',
    routeCode: context.value.routeCode,
    year: Number(context.value.year),
    extra: {
      indexCode: context.value.indexCode
    }
  },
  options: options.value
})
```

Then replace `const data = result?.data || {}` with:

```ts
const data = result?.data || {}
const actionResult = result?.actionResult || {}
```

Use `result.answer` and keep existing trace drawer fields:

```ts
content: result?.answer || actionResult.markdown || JSON.stringify(result),
trace: result?.trace || null,
answerMeta: result?.answerMeta || data.answerMeta || null,
toolResults: result?.toolResults || [],
sources
```

- [ ] **Step 3: Update `KnowledgeVectorPage.vue`**

Replace `mapAgentChat` import with `mapAgentRun`.

Replace the call:

```ts
const data: any = await mapAgentChat({
  message: form.question,
  mapContext: {
    tenantId: form.tenantId,
    mode: 'FREE',
    userQuestion: form.question
  },
  options: { useKnowledge: true, topK: form.topK }
})
```

With:

```ts
const data: any = await mapAgentRun({
  action: 'CHAT',
  message: form.question,
  mapContext: {
    tenantId: form.tenantId,
    mode: 'FREE',
    userQuestion: form.question
  },
  options: { useKnowledge: true, topK: form.topK }
})
```

- [ ] **Step 4: Run build**

Run:

```bash
npm --prefix srmp-web-ui run build
```

Expected: remaining failures only come from GIS one-map files and old panel files handled in Task 9.

## Task 9: Switch One-map AI Flow to `mapAgentRun`

**Files:**
- Modify: `srmp-web-ui/src/views/gis/OneMap.vue`
- Modify: `srmp-web-ui/src/views/gis/components/AgentChatFloat.vue`
- Modify: `srmp-web-ui/src/views/gis/components/AgentChatPanel.vue`
- Modify: `srmp-web-ui/src/views/gis/components/SolutionPreviewDialog.vue`
- Test: `npm --prefix srmp-web-ui run build`

- [ ] **Step 1: Replace AgentChatFloat imports**

Replace:

```ts
import {
  chat,
  mapAgentChat,
  generateMapObjectSolution,
  type MapObjectSolutionResponse,
  type MapObjectSolutionType
} from '../../../api/agent'
```

With:

```ts
import {
  mapAgentRun,
  type MapAgentRunResponse
} from '../../../api/agent'
```

Add local type:

```ts
type MapObjectSolutionType =
  | 'DISEASE_REVIEW'
  | 'DISEASE_TREATMENT'
  | 'LOW_SCORE_TREATMENT'
  | 'EVALUATION_UNIT_ADVICE'
  | 'SECTION_PLAN'
  | 'ROUTE_REPORT'
  | 'GENERAL_ADVICE'

type PreviewSolution = MapAgentRunResponse & {
  solutionType?: string
  title?: string
  markdown?: string
  qualityCheck?: Record<string, any>
  templateMeta?: Record<string, any>
  sourceSummaries?: Record<string, any>[]
}
```

- [ ] **Step 2: Replace object solution generation**

Replace `generateMapObjectSolution({...})` call with:

```ts
const result = await mapAgentRun({
  action: 'GENERATE_OBJECT_SOLUTION',
  message: '生成当前对象的结构化养护建议',
  mapContext: buildMapAiContext('生成当前对象的结构化养护建议'),
  actionInput: {
    solutionType,
    objectType: activeMapObject.value?.objectType || activeMapObject.value?.object_type,
    mapObject: activeMapObject.value
  },
  options: { ...options, requireAi: true }
})
solutionResult.value = normalizeSolutionRunResult(result)
```

Add helper:

```ts
function normalizeSolutionRunResult(result: MapAgentRunResponse): PreviewSolution {
  const actionResult = result.actionResult || {}
  return {
    ...result,
    solutionType: String(result.action || ''),
    title: actionResult.title || '方案草稿',
    markdown: actionResult.markdown || result.answer || '',
    qualityCheck: actionResult.qualityCheck || {},
    templateMeta: actionResult.templateMeta || {},
    trace: result.trace,
    answerMeta: result.answerMeta,
    toolResults: result.toolResults || [],
    sources: result.sources || result.knowledgeSources || []
  } as PreviewSolution
}
```

- [ ] **Step 3: Replace chat send call**

Replace the ternary `useAgentTools ? await mapAgentChat(...) : await chat(...)` with:

```ts
const res: any = await mapAgentRun({
  action: contextMode === 'REGION' ? 'ANALYZE_REGION' : contextMode === 'OBJECT' ? 'ANALYZE_OBJECT' : 'CHAT',
  message: text,
  mapContext: buildMapAiContext(text),
  actionInput: {
    mapObject: activeMapObject.value
  },
  options: { ...options, useTools: useAgentTools.value }
})
```

Keep the existing `normalizeResponse()` pattern, but update it so it accepts Run responses:

```ts
function normalizeResponse(res: any) {
  if (res?.answer || res?.answerMeta || res?.actionResult) {
    return { ...res, data: { ...(res.data || {}), answerMeta: res.answerMeta, toolResults: res.toolResults, sources: res.sources, trace: res.trace, intent: res.intent } }
  }
  if (res?.data?.answer || res?.data?.data) return res.data
  return res || {}
}
```

- [ ] **Step 4: Replace OneMap region solution call**

In `OneMap.vue`, replace import `generateMapRegionSolution` with `mapAgentRun`.

Replace the call inside `generateRegionSolution()`:

```ts
const result = unwrapApiPayload(await mapAgentRun({
  action: 'GENERATE_REGION_SOLUTION',
  message: '生成框选区域养护建议',
  mapContext: {
    tenantId: 'default',
    mode: 'REGION',
    routeCode: query.routeCode,
    year: Number(query.year),
    geometry: regionGeometry.value,
    regionSummary: regionSummary.value || undefined,
    selectedLayers: activeLayerNames(),
    extra: {
      indexCode: query.indexCode,
      grade: query.grade,
      geometryType: regionGeometryType.value
    }
  },
  actionInput: {
    solutionType: 'REGION_MAINTENANCE_SUGGESTION'
  },
  options: { useBusinessData: true, useKnowledge: true, useOutline: false, topK: 5, requireAi: true }
})) as any
```

Then set region solution from `actionResult`:

```ts
const actionResult = result.actionResult || {}
regionSolution.value = {
  solutionType: String(result.action || 'GENERATE_REGION_SOLUTION'),
  title: actionResult.title || '区域养护建议',
  markdown: actionResult.markdown || result.answer || '',
  regionSummary: actionResult.regionSummary || regionSummary.value || {},
  qualityCheck: actionResult.qualityCheck || {},
  templateMeta: actionResult.templateMeta || {},
  answerMeta: result.answerMeta || {},
  toolResults: result.toolResults || [],
  sources: result.sources || result.knowledgeSources || [],
  trace: result.trace || {}
} as any
```

- [ ] **Step 5: Update SolutionPreviewDialog types**

In `SolutionPreviewDialog.vue`, delete:

```ts
import type { MapObjectSolutionResponse } from '../../../api/agent'
import type { MapRegionSolutionResponse } from '../../../api/gis'
```

Replace:

```ts
type PreviewSolution = MapObjectSolutionResponse | MapRegionSolutionResponse
```

With:

```ts
type PreviewSolution = {
  solutionType?: string
  title?: string
  markdown?: string
  objectSummary?: Record<string, any>
  regionSummary?: Record<string, any>
  qualityCheck?: Record<string, any>
  templateMeta?: Record<string, any>
  answerMeta?: Record<string, any>
  toolResults?: Record<string, any>[]
  sources?: Record<string, any>[]
  knowledgeSources?: Record<string, any>[]
  trace?: Record<string, any>
}
```

- [ ] **Step 6: Update AgentChatPanel**

Replace `chat` import with `mapAgentRun`, and replace send call with:

```ts
const r = await mapAgentRun({
  action: props.mapObject ? 'ANALYZE_OBJECT' : 'CHAT',
  message: text,
  mapContext: {
    mode: props.mapObject ? 'OBJECT' : 'FREE',
    mapObject: props.mapObject,
    extra: props.context || {}
  },
  options: { useKnowledge: true, useBusinessData: true }
})
```

- [ ] **Step 7: Run build**

Run:

```bash
npm --prefix srmp-web-ui run build
```

Expected: build completes without TypeScript errors.

- [ ] **Step 8: Commit API and caller switch**

```bash
git add srmp-web-ui/src/api/agent.ts srmp-web-ui/src/api/gis.ts \
  srmp-web-ui/src/views/agent/AiChatPage.vue \
  srmp-web-ui/src/views/agent/KnowledgeVectorPage.vue \
  srmp-web-ui/src/views/gis/OneMap.vue \
  srmp-web-ui/src/views/gis/components/AgentChatFloat.vue \
  srmp-web-ui/src/views/gis/components/AgentChatPanel.vue \
  srmp-web-ui/src/views/gis/components/SolutionPreviewDialog.vue
git commit -m "refactor: switch frontend AI flows to mapAgentRun"
```

## Task 10: Split One-map AI Workbench Components

**Files:**
- Create: `srmp-web-ui/src/views/gis/components/map-ai/MapAiWorkbench.vue`
- Create: `srmp-web-ui/src/views/gis/components/map-ai/MapAiContextPanel.vue`
- Create: `srmp-web-ui/src/views/gis/components/map-ai/MapAiConversation.vue`
- Create: `srmp-web-ui/src/views/gis/components/map-ai/MapAiSuggestedActions.vue`
- Create: `srmp-web-ui/src/views/gis/components/map-ai/MapAiActionResultPanel.vue`
- Modify: `srmp-web-ui/src/views/gis/components/AgentChatFloat.vue`
- Modify: `srmp-web-ui/src/views/gis/OneMap.vue`
- Test: `npm --prefix srmp-web-ui run build`

- [ ] **Step 1: Create context panel**

Create `MapAiContextPanel.vue`:

```vue
<template>
  <section class="map-ai-context-panel">
    <el-tag size="small" type="info">{{ scope || 'ROUTE' }}</el-tag>
    <strong>{{ title }}</strong>
    <span>{{ subtitle }}</span>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  scope?: string
  context?: Record<string, any>
  mapObject?: Record<string, any> | null
}>()

const title = computed(() => {
  if (props.mapObject) return String(props.mapObject.name || props.mapObject.objectName || props.mapObject.disease_name || '当前对象')
  return String(props.context?.routeCode || props.context?.route_code || '当前地图')
})

const subtitle = computed(() => {
  const year = props.context?.year || props.context?.detectYear
  return [props.context?.indexCode, year].filter(Boolean).join(' / ') || '一张图上下文'
})
</script>

<style scoped>
.map-ai-context-panel {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-bottom: 1px solid #e2e8f0;
  color: #475569;
  font-size: 12px;
}
</style>
```

- [ ] **Step 2: Create suggested actions component**

Create `MapAiSuggestedActions.vue`:

```vue
<template>
  <div v-if="actions.length" class="map-ai-suggested-actions">
    <el-button
      v-for="item in actions"
      :key="item.action + item.label"
      size="small"
      :type="item.requiresConfirmation ? 'warning' : 'primary'"
      plain
      :disabled="item.disabled"
      @click="$emit('run-action', item)"
    >
      {{ item.label }}
    </el-button>
  </div>
</template>

<script setup lang="ts">
import type { MapAgentSuggestedAction } from '../../../../api/agent'

defineProps<{ actions?: MapAgentSuggestedAction[] }>()
defineEmits<{ (e: 'run-action', action: MapAgentSuggestedAction): void }>()
</script>

<style scoped>
.map-ai-suggested-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 0;
}
</style>
```

- [ ] **Step 3: Create action result panel**

Create `MapAiActionResultPanel.vue`:

```vue
<template>
  <section v-if="result" class="map-ai-action-result">
    <div class="result-head">
      <strong>{{ result.title || result.type }}</strong>
      <el-tag size="small" :type="result.status === 'SUCCESS' ? 'success' : result.status === 'NEEDS_CONFIRMATION' ? 'warning' : 'danger'">{{ result.status }}</el-tag>
    </div>
    <p v-if="result.errorMessage" class="error">{{ result.errorMessage }}</p>
    <article v-if="result.markdown" class="markdown">{{ result.markdown }}</article>
  </section>
</template>

<script setup lang="ts">
import type { MapAgentActionResult } from '../../../../api/agent'

defineProps<{ result?: MapAgentActionResult | null }>()
</script>

<style scoped>
.map-ai-action-result {
  margin-top: 10px;
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}
.result-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}
.error {
  color: #dc2626;
}
.markdown {
  white-space: pre-wrap;
  line-height: 1.6;
}
</style>
```

- [ ] **Step 4: Create conversation and workbench shells**

Create `MapAiConversation.vue` with props for messages and emits for send/open trace. Move the message list template from `AgentChatFloat.vue` into this component without changing behavior.

Create `MapAiWorkbench.vue` that composes:

```vue
<MapAiContextPanel :scope="contextScope" :context="context" :map-object="mapObject" />
<MapAiConversation :messages="messages" @send="$emit('send', $event)" @open-trace="$emit('open-trace', $event)" />
<MapAiActionResultPanel :result="latestActionResult" />
<MapAiSuggestedActions :actions="latestSuggestedActions" @run-action="$emit('run-action', $event)" />
```

- [ ] **Step 5: Wire `AgentChatFloat.vue` to use workbench**

Replace the internal message UI with `MapAiWorkbench`. Keep the existing state, `send()`, trace drawer, solution preview, and emits in `AgentChatFloat.vue` for this phase.

- [ ] **Step 6: Run build**

Run:

```bash
npm --prefix srmp-web-ui run build
```

Expected: build completes without TypeScript errors.

- [ ] **Step 7: Commit**

```bash
git add srmp-web-ui/src/views/gis/components/map-ai \
  srmp-web-ui/src/views/gis/components/AgentChatFloat.vue \
  srmp-web-ui/src/views/gis/OneMap.vue
git commit -m "refactor: split one-map AI workbench"
```

## Task 11: Update Ops and Observability for Action Buckets

**Files:**
- Modify: `srmp-ai-orchestrator/app/observability.py`
- Modify: `srmp-web-ui/src/views/agent/LangGraphOpsPage.vue`
- Test: `npm --prefix srmp-web-ui run build`
- Test: `srmp-ai-orchestrator/.venv/bin/python -m py_compile srmp-ai-orchestrator/app/*.py`

- [ ] **Step 1: Make audit extraction work for Run responses**

In `RuntimeAuditStore.record_success()`, keep the existing signature but make it tolerate both `MapAiAgentResponse` and `MapAgentRunResponse`. Add this helper near `_compact_response()`:

```python
def _model_or_dict(value: Any) -> Dict[str, Any]:
    if value is None:
        return {}
    if isinstance(value, dict):
        return value
    if hasattr(value, "model_dump"):
        return value.model_dump(exclude_none=True)
    if hasattr(value, "dict"):
        return value.dict(exclude_none=True)
    return {}
```

In `record_success()`, replace the existing first lines:

```python
trace = response.trace or {}
data = response.data or {}
```

With:

```python
trace = response.trace or {}
data = response.data or {}
action_result = _model_or_dict(getattr(response, "actionResult", None))
action = getattr(response, "action", None)
if not action and isinstance(data, dict):
    action = data.get("action")
graph_name = None
if isinstance(data, dict):
    graph_name = data.get("graphName")
if not graph_name and isinstance(trace, dict):
    graph_name = trace.get("graphName")
```

Add record keys:

```python
"action": action,
"graphName": graph_name,
"actionResultType": action_result.get("type"),
"actionResultStatus": action_result.get("status"),
"writeBlocked": bool(data.get("writeBlocked") or data.get("writeConfirmation") is False) if isinstance(data, dict) else False,
"needsConfirmation": action_result.get("status") == "NEEDS_CONFIRMATION",
```

In `summary()`, add:

```python
"actionBuckets": _bucket(records, "action", max_items=12),
"graphBuckets": _bucket(records, "graphName", max_items=12),
"writeBlockedCount": sum(1 for item in records if item.get("writeBlocked")),
"needsConfirmationCount": sum(1 for item in records if item.get("needsConfirmation")),
```

- [ ] **Step 2: Update Ops page**

In `LangGraphOpsPage.vue`, add columns to Recent table:

```vue
<el-table-column prop="action" label="Action" width="160" show-overflow-tooltip />
<el-table-column prop="graphName" label="Graph" width="170" show-overflow-tooltip />
```

Remove the fallback display:

```ts
const fallbackToNative = computed(() => Boolean(summary.fallbackToNative))
```

Replace the provider metric description:

```vue
<div class="metric-desc">写工具：{{ allowWriteTools ? '允许' : '禁止' }}；失败不回退 native</div>
```

Add a small metric card:

```vue
<el-card shadow="never" class="metric-card">
  <div class="metric-head">
    <span>Action 执行</span>
    <el-tag type="info">{{ value(runtimeSummary, ['needsConfirmationCount']) || 0 }} 待确认</el-tag>
  </div>
  <div class="metric-value">{{ Object.keys(value(runtimeSummary, ['actionBuckets']) || {}).length }}</div>
  <div class="metric-desc">写阻断 {{ value(runtimeSummary, ['writeBlockedCount']) || 0 }}；Graph {{ Object.keys(value(runtimeSummary, ['graphBuckets']) || {}).length }}</div>
</el-card>
```

- [ ] **Step 3: Run checks**

```bash
srmp-ai-orchestrator/.venv/bin/python -m py_compile srmp-ai-orchestrator/app/*.py
npm --prefix srmp-web-ui run build
```

Expected: both commands exit with code 0.

- [ ] **Step 4: Commit**

```bash
git add srmp-ai-orchestrator/app/observability.py srmp-web-ui/src/views/agent/LangGraphOpsPage.vue
git commit -m "feat: show LangGraph action observability"
```

## Task 12: Add Phase50.18 Verification Script

**Files:**
- Create: `scripts/check-phase50-18-langgraph-first-map-agent.sh`
- Test: `bash scripts/check-phase50-18-langgraph-first-map-agent.sh`

- [ ] **Step 1: Create script**

Create `scripts/check-phase50-18-langgraph-first-map-agent.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

require_pattern() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if ! grep -qE "$pattern" "$file"; then
    echo "[FAIL] $message"
    echo "       file: $file"
    echo "       pattern: $pattern"
    exit 1
  fi
  echo "[OK] $message"
}

reject_pattern() {
  local path="$1"
  local pattern="$2"
  local message="$3"
  if rg -n "$pattern" "$path" >/tmp/srmp-phase50-18-rg.txt; then
    echo "[FAIL] $message"
    cat /tmp/srmp-phase50-18-rg.txt
    exit 1
  fi
  echo "[OK] $message"
}

require_pattern "srmp-ai-orchestrator/app/schemas.py" "class MapAgentRunRequest" "Python run request schema exists"
require_pattern "srmp-ai-orchestrator/app/schemas.py" "class MapAgentRunResponse" "Python run response schema exists"
require_pattern "srmp-ai-orchestrator/app/main.py" "/api/srmp/langgraph/map-agent/run" "Python run endpoint exists"
require_pattern "srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/controller/MapAiAgentController.java" "/run" "Java run endpoint exists"
require_pattern "srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/AgentOrchestratorRouter.java" "orchestratorFallback.*false" "Java router disables native fallback"
require_pattern "srmp-web-ui/src/api/agent.ts" "mapAgentRun" "Frontend mapAgentRun exists"
require_pattern "srmp-web-ui/src/views/gis/components/AgentChatFloat.vue" "mapAgentRun" "One-map chat uses mapAgentRun"
require_pattern "srmp-web-ui/src/views/gis/OneMap.vue" "GENERATE_REGION_SOLUTION" "Region solution uses run action"
require_pattern "srmp-web-ui/src/views/agent/LangGraphOpsPage.vue" "actionBuckets" "Ops page exposes action buckets"

test ! -f "srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/impl/NativeMapAgentOrchestrator.java" || { echo "[FAIL] native orchestrator file still exists"; exit 1; }
test ! -f "srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent/plan/MapAiToolPlannerImpl.java" || { echo "[FAIL] native planner file still exists"; exit 1; }

reject_pattern "srmp-web-ui/src" "mapAgentChat|generateMapObjectSolution|generateMapRegionSolution|/api/agent/map-agent/chat|/api/agent/map-object/solution|/api/gis/map-region/solution" "old frontend AI helpers and endpoints removed"
reject_pattern "srmp-web-ui/src" "fallbackToNative" "frontend no longer displays native fallback"
reject_pattern "srmp-agent/src/main/java" "fallbackToNative|NativeMapAgentOrchestrator|provider\\(\\).*native|/chat" "old Java native provider and chat endpoint removed"
reject_pattern "srmp-ai-orchestrator/app" "map-agent/chat" "old Python map-agent chat endpoint removed"

srmp-ai-orchestrator/.venv/bin/python -m py_compile srmp-ai-orchestrator/app/*.py
mvn -pl srmp-agent,srmp-gis test
npm --prefix srmp-web-ui run build

echo "[OK] Phase50.18 LangGraph-first map agent checks passed"
```

- [ ] **Step 2: Make executable and run**

```bash
chmod +x scripts/check-phase50-18-langgraph-first-map-agent.sh
bash scripts/check-phase50-18-langgraph-first-map-agent.sh
```

Expected: all checks pass.

- [ ] **Step 3: Commit**

```bash
git add scripts/check-phase50-18-langgraph-first-map-agent.sh
git commit -m "test: add LangGraph-first map agent checks"
```

## Task 13: Remove Native Parity Scripts and Update Runtime Defaults

**Files:**
- Delete: `scripts/check-phase50-15-langgraph-native-parity.sh`
- Modify: `scripts/check-phase50-16-langgraph-llm-live-closure.sh`
- Modify: Docker/env files that set `SRMP_AI_ORCHESTRATOR_PROVIDER` or fallback flags
- Test: `bash scripts/check-phase50-18-langgraph-first-map-agent.sh`

- [ ] **Step 1: Delete native parity script**

Run:

```bash
git rm scripts/check-phase50-15-langgraph-native-parity.sh
```

- [ ] **Step 2: Update docs/scripts references**

Run:

```bash
rg -n "check-phase50-15-langgraph-native-parity|fallbackToNative|SRMP_AI_ORCHESTRATOR_PROVIDER=.*native|SRMP_AI_ORCHESTRATOR_FALLBACK" docs scripts deploy srmp-agent srmp-web-ui srmp-ai-orchestrator
```

For every runtime config reference, change the default to LangGraph-first:

```text
SRMP_AI_ORCHESTRATOR_PROVIDER=langgraph
SRMP_AI_ORCHESTRATOR_FALLBACK_TO_NATIVE=false
```

Then delete the fallback variable from active Docker/env templates where the Java code no longer reads it.

- [ ] **Step 3: Run verification**

```bash
bash scripts/check-phase50-18-langgraph-first-map-agent.sh
```

Expected: all checks pass.

- [ ] **Step 4: Commit**

```bash
git add -u docs scripts deploy srmp-agent srmp-web-ui srmp-ai-orchestrator
git commit -m "chore: remove native parity runtime defaults"
```

## Task 14: Full Regression and Manual Acceptance

**Files:**
- No planned file edits.
- Test: full verification suite.

- [ ] **Step 1: Run full automated checks**

```bash
bash scripts/check-phase50-18-langgraph-first-map-agent.sh
bash scripts/check-phase50-14-langgraph-map-agent-parity.sh
bash scripts/check-phase50-16-langgraph-llm-live-closure.sh
srmp-ai-orchestrator/.venv/bin/python -m py_compile srmp-ai-orchestrator/app/*.py
mvn -pl srmp-agent,srmp-gis test
npm --prefix srmp-web-ui run build
```

Expected: every command exits with code 0. The Phase50.14 script remains acceptable only if it checks tool execution and not native parity. If it references old `/chat`, update it to `/run` before accepting the result.

- [ ] **Step 2: Manual smoke with running services**

Use the browser or curl against local services:

```bash
curl -sS http://localhost:8080/api/agent/map-agent/run \
  -H 'Content-Type: application/json' \
  -d '{"action":"CHAT","message":"分析 G210 2026 年整体路况","mapContext":{"mode":"ROUTE","routeCode":"G210","year":2026},"options":{"useKnowledge":true,"topK":3}}'
```

Expected JSON includes:

```json
{
  "mode": "LANGGRAPH_MAP_AGENT",
  "action": "CHAT",
  "data": {
    "orchestratorProvider": "langgraph",
    "orchestratorFallback": false
  }
}
```

Then verify `/gis/one-map`:

- Send a normal question.
- Select an object and run analysis.
- Draw a polygon region and generate a solution.
- Open `AI 执行过程`.
- Try saving a draft once without confirmation and observe `NEEDS_CONFIRMATION`.
- Save with confirmation and observe a draft task result.

- [ ] **Step 3: Inspect git status**

```bash
git status --short
```

Expected only unrelated runtime files remain:

```text
?? deploy/outline/data/
?? deploy/outline/outline.env
?? deploy/outline/outline.env.bak
```

- [ ] **Step 4: Final commit if needed**

If any verification-only edits remain:

```bash
git add -u
git commit -m "chore: finish LangGraph-first map agent refactor"
```

Expected: commit succeeds or there is nothing to commit.

## Acceptance Checklist

- [ ] `/api/agent/map-agent/run` is the only Java one-map AI endpoint.
- [ ] `/api/srmp/langgraph/map-agent/run` is the only Python one-map AI endpoint.
- [ ] Frontend exports `mapAgentRun()` and no old AI helper wrappers.
- [ ] Old object/region solution AI endpoints are removed.
- [ ] Native orchestrator, native planner, and native answer enhancers are removed.
- [ ] LangGraph failure does not produce native fallback content.
- [ ] `AI 执行过程` works for chat, object, route, region, solution generation, and draft save.
- [ ] Tool Gateway remains the only business data/write boundary.
- [ ] `SAVE_SOLUTION_DRAFT` requires confirmation.
- [ ] Phase50.18 verification script passes.
