# Phase50.20 LangGraph Live Trace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show live LangGraph execution progress in the GIS one-map AI assistant while preserving the existing final `/api/agent/map-agent/run` response and unified `AiTraceDrawer`.

**Architecture:** The frontend generates `options.traceId` for every one-map AI run, starts the main request, and polls Java ops for a live trace snapshot until the request completes. Java proxies `GET /api/agent/orchestrator/ops/live-trace/{traceId}` to `srmp-ai-orchestrator`, where a new in-memory `LiveTraceStore` is updated by LangGraph workflow nodes and solution tool actions.

**Tech Stack:** Python 3 / FastAPI / Pydantic / LangGraph, Java 8 / Spring Boot 2.7 / JUnit4, Vue 3 / TypeScript / Element Plus / Node test.

---

## File Structure

- Create `srmp-ai-orchestrator/app/live_trace.py`: thread-safe runtime live trace snapshot store.
- Create `srmp-ai-orchestrator/tests/test_live_trace.py`: unit tests for store lifecycle, failure, and capacity pruning.
- Create `srmp-ai-orchestrator/tests/test_live_trace_api.py`: FastAPI endpoint smoke test for live trace lookup.
- Modify `srmp-ai-orchestrator/app/workflow.py`: emit live trace current step and completed steps for analysis/chat flows.
- Modify `srmp-ai-orchestrator/app/map_agent_run.py`: emit live trace for solution generation and draft save actions that bypass the base workflow graph.
- Modify `srmp-ai-orchestrator/app/main.py`: instantiate the shared store and expose `GET /api/srmp/langgraph/trace/live/{traceId}`.
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java`: add Java ops proxy endpoint.
- Create `srmp-agent/src/test/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsControllerLiveTraceTest.java`: verify runtime path encoding.
- Modify `srmp-web-ui/src/api/orchestrator.ts`: add `getOrchestratorLiveTrace(traceId)`.
- Create `srmp-web-ui/src/utils/liveTrace.ts`: traceId generation, live snapshot normalization, polling failure decision helpers.
- Create `srmp-web-ui/tests/liveTrace.test.mjs`: unit tests for the utility.
- Modify `srmp-web-ui/src/views/agent/components/aiExecution.ts`: normalize live snapshots into the existing drawer model.
- Modify `srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue`: show running current step and live summaries.
- Modify `srmp-web-ui/src/views/gis/components/AgentChatFloat.vue`: generate traceId, poll live trace, show current step in the wait panel, and open live/final Trace with the same drawer.

---

### Task 1: Python LiveTraceStore

**Files:**
- Create: `srmp-ai-orchestrator/tests/test_live_trace.py`
- Create: `srmp-ai-orchestrator/app/live_trace.py`

- [ ] **Step 1: Write the failing store tests**

Create `srmp-ai-orchestrator/tests/test_live_trace.py`:

```python
import unittest

from app.live_trace import LiveTraceStore


class LiveTraceStoreTest(unittest.TestCase):
    def test_records_running_success_and_current_step(self):
        store = LiveTraceStore(max_records=3)
        store.start_trace("trace-1", action="ANALYZE_REGION", graph_name="region_analysis_graph")
        store.start_step("trace-1", "tool_execute", "执行只读工具", data={"planned": 2})
        running = store.get("trace-1")

        self.assertEqual("RUNNING", running["status"])
        self.assertEqual("tool_execute", running["currentStep"]["name"])
        self.assertEqual("RUNNING", running["currentStep"]["status"])

        store.complete_step("trace-1", {
            "name": "tool_execute",
            "label": "执行只读工具",
            "status": "SUCCESS",
            "count": 2,
            "elapsedMs": 120,
            "data": {
                "success": 2,
                "failed": 0,
                "tools": ["knowledge.retrieve", "gis.region.summary"]
            }
        })
        store.complete_trace("trace-1", answer_meta={"llmStatus": "SUCCESS"})
        completed = store.get("trace-1")

        self.assertEqual("SUCCESS", completed["status"])
        self.assertIsNone(completed["currentStep"])
        self.assertEqual(1, len(completed["steps"]))
        self.assertEqual(2, completed["toolSummary"]["completed"])
        self.assertEqual("SUCCESS", completed["answerMeta"]["llmStatus"])

    def test_records_failure_and_not_found(self):
        store = LiveTraceStore(max_records=3)
        store.start_trace("trace-2", action="CHAT", graph_name="chat_graph")
        store.start_step("trace-2", "answer_generate", "生成回答")
        store.fail_step("trace-2", "answer_generate", "LLM timeout")
        failed = store.get("trace-2")

        self.assertEqual("FAILED", failed["status"])
        self.assertEqual("LLM timeout", failed["error"])
        self.assertEqual("FAILED", failed["steps"][0]["status"])
        self.assertEqual("NOT_FOUND", store.not_found("missing")["status"])

    def test_prunes_old_records(self):
        store = LiveTraceStore(max_records=2)
        store.start_trace("trace-a", action="CHAT", graph_name="chat_graph")
        store.start_trace("trace-b", action="CHAT", graph_name="chat_graph")
        store.start_trace("trace-c", action="CHAT", graph_name="chat_graph")

        self.assertIsNone(store.get("trace-a"))
        self.assertIsNotNone(store.get("trace-b"))
        self.assertIsNotNone(store.get("trace-c"))


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp/srmp-ai-orchestrator
python3 -m unittest discover -s tests -p 'test_live_trace.py'
```

Expected: FAIL with `ModuleNotFoundError: No module named 'app.live_trace'`.

- [ ] **Step 3: Implement the store**

Create `srmp-ai-orchestrator/app/live_trace.py`:

```python
import time
from collections import OrderedDict
from copy import deepcopy
from threading import Lock
from typing import Any, Dict, Optional


def now_ms() -> int:
    return int(time.time() * 1000)


class LiveTraceStore:
    def __init__(self, max_records: int = 200) -> None:
        self.max_records = max(1, int(max_records or 200))
        self._records: "OrderedDict[str, Dict[str, Any]]" = OrderedDict()
        self._lock = Lock()

    def start_trace(self, trace_id: str, action: str = "", graph_name: str = "") -> Dict[str, Any]:
        trace_id = self._safe_trace_id(trace_id)
        ts = now_ms()
        with self._lock:
            record = self._records.get(trace_id) or {
                "traceId": trace_id,
                "steps": [],
                "toolSummary": {"planned": 0, "completed": 0, "success": 0, "failed": 0},
                "sourceSummary": {"business": 0, "knowledge": 0, "outline": 0},
                "answerMeta": {},
                "error": "",
                "startedAtMs": ts,
            }
            record.update({
                "status": "RUNNING",
                "action": action or record.get("action") or "",
                "graphName": graph_name or record.get("graphName") or "",
                "currentStep": record.get("currentStep"),
                "updatedAtMs": ts,
                "costMs": max(0, ts - int(record.get("startedAtMs") or ts)),
            })
            self._records[trace_id] = record
            self._records.move_to_end(trace_id)
            self._prune_locked()
            return deepcopy(record)

    def start_step(self, trace_id: str, name: str, label: str = "", data: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        trace_id = self._safe_trace_id(trace_id)
        ts = now_ms()
        with self._lock:
            record = self._ensure_record_locked(trace_id)
            record["status"] = "RUNNING"
            record["currentStep"] = {
                "name": name,
                "label": label or name,
                "status": "RUNNING",
                "startedAtMs": ts,
                "elapsedMs": 0,
                "data": data or {},
            }
            self._touch_locked(record, ts)
            return deepcopy(record)

    def complete_step(self, trace_id: str, step: Dict[str, Any]) -> Dict[str, Any]:
        trace_id = self._safe_trace_id(trace_id)
        ts = now_ms()
        item = dict(step or {})
        name = str(item.get("name") or item.get("node") or "unknown")
        item["name"] = name
        item["label"] = str(item.get("label") or item.get("message") or name)
        item["status"] = str(item.get("status") or "SUCCESS").upper()
        item["elapsedMs"] = int(item.get("elapsedMs") or item.get("costMs") or 0)
        with self._lock:
            record = self._ensure_record_locked(trace_id)
            record.setdefault("steps", []).append(item)
            record["currentStep"] = None
            self._merge_step_summaries_locked(record, item)
            self._touch_locked(record, ts)
            return deepcopy(record)

    def fail_step(self, trace_id: str, name: str, error: str) -> Dict[str, Any]:
        step = {
            "name": name,
            "label": name,
            "status": "FAILED",
            "elapsedMs": 0,
            "error": error,
            "errorMessage": error,
            "data": {},
        }
        snapshot = self.complete_step(trace_id, step)
        with self._lock:
            record = self._ensure_record_locked(trace_id)
            record["status"] = "FAILED"
            record["error"] = error
            self._touch_locked(record, now_ms())
            return deepcopy(record)

    def complete_trace(
        self,
        trace_id: str,
        status: str = "SUCCESS",
        answer_meta: Optional[Dict[str, Any]] = None,
        trace: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        trace_id = self._safe_trace_id(trace_id)
        ts = now_ms()
        with self._lock:
            record = self._ensure_record_locked(trace_id)
            record["status"] = status
            record["currentStep"] = None
            if answer_meta:
                record["answerMeta"] = dict(answer_meta)
            if trace:
                record["finalTrace"] = deepcopy(trace)
                if isinstance(trace.get("steps"), list):
                    record["steps"] = deepcopy(trace.get("steps"))
            self._touch_locked(record, ts)
            return deepcopy(record)

    def get(self, trace_id: str) -> Optional[Dict[str, Any]]:
        trace_id = self._safe_trace_id(trace_id)
        with self._lock:
            record = self._records.get(trace_id)
            if not record:
                return None
            snapshot = deepcopy(record)
        self._refresh_current_step(snapshot)
        return snapshot

    def not_found(self, trace_id: str) -> Dict[str, Any]:
        return {
            "traceId": self._safe_trace_id(trace_id),
            "status": "NOT_FOUND",
            "currentStep": None,
            "steps": [],
            "toolSummary": {"planned": 0, "completed": 0, "success": 0, "failed": 0},
            "sourceSummary": {"business": 0, "knowledge": 0, "outline": 0},
            "answerMeta": {},
            "error": "",
            "startedAtMs": None,
            "updatedAtMs": now_ms(),
            "costMs": 0,
        }

    def _safe_trace_id(self, trace_id: str) -> str:
        return str(trace_id or "").strip()

    def _ensure_record_locked(self, trace_id: str) -> Dict[str, Any]:
        record = self._records.get(trace_id)
        if not record:
            ts = now_ms()
            record = {
                "traceId": trace_id,
                "status": "RUNNING",
                "action": "",
                "graphName": "",
                "currentStep": None,
                "steps": [],
                "toolSummary": {"planned": 0, "completed": 0, "success": 0, "failed": 0},
                "sourceSummary": {"business": 0, "knowledge": 0, "outline": 0},
                "answerMeta": {},
                "error": "",
                "startedAtMs": ts,
                "updatedAtMs": ts,
                "costMs": 0,
            }
            self._records[trace_id] = record
        return record

    def _touch_locked(self, record: Dict[str, Any], ts: int) -> None:
        record["updatedAtMs"] = ts
        started = int(record.get("startedAtMs") or ts)
        record["costMs"] = max(0, ts - started)
        trace_id = record.get("traceId")
        if trace_id:
            self._records.move_to_end(trace_id)
        self._prune_locked()

    def _prune_locked(self) -> None:
        while len(self._records) > self.max_records:
            self._records.popitem(last=False)

    def _merge_step_summaries_locked(self, record: Dict[str, Any], step: Dict[str, Any]) -> None:
        data = step.get("data") if isinstance(step.get("data"), dict) else {}
        name = str(step.get("name") or "")
        if name in {"tool_plan", "tool_planning"}:
            tools = data.get("tools")
            if isinstance(tools, list):
                record["toolSummary"]["planned"] = len(tools)
        if name == "tool_execute":
            record["toolSummary"]["completed"] = int(step.get("count") or record["toolSummary"].get("completed") or 0)
            record["toolSummary"]["success"] = int(data.get("success") or 0)
            record["toolSummary"]["failed"] = int(data.get("failed") or 0)
        if name == "evidence_fuse":
            record["sourceSummary"]["business"] = int(data.get("businessHitCount") or 0)
            record["sourceSummary"]["knowledge"] = int(data.get("knowledgeHitCount") or 0)

    def _refresh_current_step(self, snapshot: Dict[str, Any]) -> None:
        current = snapshot.get("currentStep")
        if not isinstance(current, dict) or not current.get("startedAtMs"):
            return
        current["elapsedMs"] = max(0, now_ms() - int(current.get("startedAtMs") or now_ms()))
```

- [ ] **Step 4: Run the store tests**

Run:

```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp/srmp-ai-orchestrator
python3 -m unittest discover -s tests -p 'test_live_trace.py'
```

Expected: PASS, `Ran 3 tests`.

- [ ] **Step 5: Commit**

```bash
git add srmp-ai-orchestrator/app/live_trace.py srmp-ai-orchestrator/tests/test_live_trace.py
git commit -m "feat: add LangGraph live trace store"
```

---

### Task 2: Runtime Workflow Wiring and API

**Files:**
- Modify: `srmp-ai-orchestrator/app/workflow.py`
- Modify: `srmp-ai-orchestrator/app/map_agent_run.py`
- Modify: `srmp-ai-orchestrator/app/main.py`
- Create: `srmp-ai-orchestrator/tests/test_live_trace_api.py`

- [ ] **Step 1: Write the failing API test**

Create `srmp-ai-orchestrator/tests/test_live_trace_api.py`:

```python
import unittest

from fastapi.testclient import TestClient

from app.main import app, live_trace_store


class LiveTraceApiTest(unittest.TestCase):
    def test_live_trace_endpoint_returns_snapshot_and_not_found_payload(self):
        live_trace_store.start_trace("api-trace-1", action="CHAT", graph_name="chat_graph")
        live_trace_store.start_step("api-trace-1", "answer_generate", "生成回答")

        client = TestClient(app)
        found = client.get("/api/srmp/langgraph/trace/live/api-trace-1")
        self.assertEqual(200, found.status_code)
        self.assertEqual("RUNNING", found.json()["status"])
        self.assertEqual("answer_generate", found.json()["currentStep"]["name"])

        missing = client.get("/api/srmp/langgraph/trace/live/missing-trace")
        self.assertEqual(200, missing.status_code)
        self.assertEqual("NOT_FOUND", missing.json()["status"])
```

- [ ] **Step 2: Run the API test to verify it fails**

Run:

```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp/srmp-ai-orchestrator
python3 -m unittest discover -s tests -p 'test_live_trace_api.py'
```

Expected: FAIL because `app.main` does not export `live_trace_store` or the endpoint returns 404.

- [ ] **Step 3: Wire `LiveTraceStore` into workflow**

In `srmp-ai-orchestrator/app/workflow.py`, add the import and labels near the current imports and node constants:

```python
from .live_trace import LiveTraceStore
```

```python
NODE_LABELS = {
    "request_normalize": "归一化请求",
    "context_build": "构建地图上下文",
    "intent_recognize": "识别用户意图",
    "context_enrich": "补全上下文",
    "tool_planning": "规划工具",
    "tool_execute": "执行只读工具",
    "evidence_fuse": "融合证据",
    "answer_generate": "生成回答",
    "quality_guard": "质量保护",
    "llm_answer": "大模型回答",
}
```

Change the constructor and graph node registration:

```python
class LangGraphWorkflow:
    def __init__(self, gateway: JavaToolGateway, llm_client: LlmClient, live_trace_store: Optional[LiveTraceStore] = None):
        self.gateway = gateway
        self.llm_client = llm_client
        self.live_trace_store = live_trace_store
        self.graph = self._build_graph() if LANGGRAPH_AVAILABLE else None
```

```python
builder.add_node("request_normalize", self._wrap_node("request_normalize", self._request_normalize))
builder.add_node("context_build", self._wrap_node("context_build", self._context_build))
builder.add_node("intent_recognize", self._wrap_node("intent_recognize", self._intent_recognize))
builder.add_node("context_enrich", self._wrap_node("context_enrich", self._context_enrich))
builder.add_node("tool_planning", self._wrap_node("tool_planning", self._tool_plan))
builder.add_node("tool_execute", self._wrap_node("tool_execute", self._tool_execute))
builder.add_node("evidence_fuse", self._wrap_node("evidence_fuse", self._evidence_fuse))
builder.add_node("answer_generate", self._wrap_node("answer_generate", self._answer_generate))
builder.add_node("quality_guard", self._wrap_node("quality_guard", self._quality_guard))
```

Add helpers inside `LangGraphWorkflow`:

```python
    def _action_from_state(self, state: AgentState) -> str:
        request = state.get("request")
        options = request.options if request and request.options else {}
        return str(options.get("action") or "CHAT").upper()

    def _graph_name_from_action(self, action: str) -> str:
        if action == "ANALYZE_OBJECT":
            return "object_analysis_graph"
        if action == "ANALYZE_REGION":
            return "region_analysis_graph"
        if action == "ANALYZE_ROUTE":
            return "route_report_graph"
        if action == "PLAN_ONLY":
            return "plan_graph"
        return "chat_graph"

    def _live_start_trace(self, state: AgentState) -> None:
        if not self.live_trace_store:
            return
        action = self._action_from_state(state)
        self.live_trace_store.start_trace(
            state.get("trace_id") or "",
            action=action,
            graph_name=self._graph_name_from_action(action),
        )

    def _wrap_node(self, node: str, handler):
        async def wrapped(state: AgentState) -> Dict[str, Any]:
            if self.live_trace_store:
                self.live_trace_store.start_step(
                    state.get("trace_id") or "",
                    node,
                    NODE_LABELS.get(node, node),
                )
            try:
                return await handler(state)
            except Exception as exc:
                if self.live_trace_store:
                    self.live_trace_store.fail_step(state.get("trace_id") or "", node, str(exc))
                raise
        return wrapped
```

Change `_run_sequential()` so non-LangGraph fallback gets the same start/fail behavior:

```python
    async def _run_sequential(self, state: AgentState, nodes: List[str]) -> AgentState:
        for node in nodes:
            updates = await self._wrap_node(node, self._node_handler(node))(state)
            state.update(updates or {})
        return state
```

At the start of `run()` and `plan()`, after `_new_state(...)`, call:

```python
self._live_start_trace(state)
```

In `_step()`, after appending the step, complete the live step:

```python
        if self.live_trace_store:
            self.live_trace_store.complete_step(state.get("trace_id") or "", item)
```

In `_to_response()`, build `trace_payload` once and complete the live trace:

```python
        trace_payload = {
            "traceId": state.get("trace_id"),
            "orchestratorProvider": "langgraph",
            "strategyVersion": settings.strategy_version,
            "nodeFlow": NODE_FLOW,
            "steps": state.get("steps", []),
            "costMs": int((time.perf_counter() - state.get("started_at", time.perf_counter())) * 1000),
        }
        if self.live_trace_store:
            self.live_trace_store.complete_trace(
                state.get("trace_id") or "",
                status="SUCCESS",
                answer_meta=answer_meta,
                trace=trace_payload,
            )
```

Use `trace=trace_payload` in the returned `MapAiAgentResponse`.

- [ ] **Step 4: Wire solution actions in `MapAgentRunWorkflow`**

In `srmp-ai-orchestrator/app/map_agent_run.py`, import `LiveTraceStore` and change constructor:

```python
from .live_trace import LiveTraceStore
```

```python
class MapAgentRunWorkflow:
    def __init__(self, base_workflow: LangGraphWorkflow, gateway: JavaToolGateway, live_trace_store: Optional[LiveTraceStore] = None):
        self.base_workflow = base_workflow
        self.gateway = gateway
        self.live_trace_store = live_trace_store
```

Add helpers:

```python
    def _start_live_trace(self, trace_id: Optional[str], action: str, graph_name: str) -> None:
        if self.live_trace_store and trace_id:
            self.live_trace_store.start_trace(trace_id, action=action, graph_name=graph_name)

    def _start_live_step(self, trace_id: Optional[str], name: str, label: str) -> None:
        if self.live_trace_store and trace_id:
            self.live_trace_store.start_step(trace_id, name, label)

    def _complete_live_step(self, trace_id: Optional[str], step: Dict[str, Any]) -> None:
        if self.live_trace_store and trace_id:
            self.live_trace_store.complete_step(trace_id, step)

    def _complete_live_trace(self, trace_id: Optional[str], response: MapAgentRunResponse, status: str = "SUCCESS") -> None:
        if self.live_trace_store and trace_id:
            self.live_trace_store.complete_trace(trace_id, status=status, answer_meta=response.answerMeta, trace=response.trace)
```

In `_run_solution_generation()`, before `gateway.execute_tool(...)`:

```python
self._start_live_trace(trace_id, normalize_action(request.action), "solution_generation_graph")
self._start_live_step(trace_id, "solution_generate", "生成方案预览")
```

After building `response`, before return:

```python
self._complete_live_step(trace_id, {
    "name": "solution_generate",
    "label": "生成方案预览",
    "status": action_result.status,
    "count": 1 if tool_result.success else 0,
    "elapsedMs": 0,
    "data": {"toolName": "solution.generateDraft"},
    "error": action_result.errorMessage,
})
self._complete_live_trace(trace_id, response, status=action_result.status)
return response
```

Use a local `response = MapAgentRunResponse(...)` instead of returning the constructor directly.

In `_run_draft_save()`, wrap both confirmation-check and save paths with equivalent `solution_save_task` live steps.

- [ ] **Step 5: Add the Runtime endpoint**

In `srmp-ai-orchestrator/app/main.py`, import and instantiate the store:

```python
from .live_trace import LiveTraceStore
```

```python
live_trace_store = LiveTraceStore(max_records=settings.audit_max_records)
workflow = LangGraphWorkflow(gateway=gateway, llm_client=LlmClient(), live_trace_store=live_trace_store)
map_agent_run_workflow = MapAgentRunWorkflow(base_workflow=workflow, gateway=gateway, live_trace_store=live_trace_store)
```

Add endpoint before debug endpoints:

```python
@app.get("/api/srmp/langgraph/trace/live/{trace_id}")
async def live_trace(trace_id: str) -> dict:
    snapshot = live_trace_store.get(trace_id)
    if snapshot:
        return snapshot
    return live_trace_store.not_found(trace_id)
```

- [ ] **Step 6: Run Python tests**

Run:

```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp/srmp-ai-orchestrator
python3 -m unittest discover -s tests -p 'test_live_trace*.py'
```

Expected: PASS, store and API tests both pass.

- [ ] **Step 7: Commit**

```bash
git add srmp-ai-orchestrator/app/workflow.py srmp-ai-orchestrator/app/map_agent_run.py srmp-ai-orchestrator/app/main.py srmp-ai-orchestrator/tests/test_live_trace_api.py
git commit -m "feat: expose LangGraph live trace snapshots"
```

---

### Task 3: Java Ops Live Trace Proxy

**Files:**
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java`
- Create: `srmp-agent/src/test/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsControllerLiveTraceTest.java`

- [ ] **Step 1: Write the failing Java path test**

Create `srmp-agent/src/test/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsControllerLiveTraceTest.java`:

```java
package com.smartroad.srmp.agent.orchestrator.controller;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AgentOrchestratorOpsControllerLiveTraceTest {

    @Test
    public void liveTraceRuntimePathEncodesTraceId() {
        AgentOrchestratorOpsController controller = new AgentOrchestratorOpsController();

        assertEquals(
                "/api/srmp/langgraph/trace/live/web-lg-1-a%20b",
                controller.liveTraceRuntimePath("web-lg-1-a b")
        );
    }
}
```

- [ ] **Step 2: Run the Java test to verify it fails**

Run:

```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp
mvn -pl srmp-agent -Dtest=AgentOrchestratorOpsControllerLiveTraceTest test
```

Expected: compilation FAIL because `liveTraceRuntimePath(String)` does not exist.

- [ ] **Step 3: Add the proxy endpoint and path helper**

In `AgentOrchestratorOpsController.java`, add imports:

```java
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
```

Add endpoint near the other ops endpoints:

```java
    @GetMapping("/live-trace/{traceId}")
    public R<Object> liveTrace(@PathVariable("traceId") String traceId) {
        return R.ok(remoteGet(liveTraceRuntimePath(traceId), 3000));
    }

    String liveTraceRuntimePath(String traceId) {
        String safeTraceId = traceId == null ? "" : traceId;
        return "/api/srmp/langgraph/trace/live/" + UriUtils.encodePathSegment(safeTraceId, StandardCharsets.UTF_8);
    }
```

- [ ] **Step 4: Run the Java test**

Run:

```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp
mvn -pl srmp-agent -Dtest=AgentOrchestratorOpsControllerLiveTraceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java srmp-agent/src/test/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsControllerLiveTraceTest.java
git commit -m "feat: proxy LangGraph live trace snapshots"
```

---

### Task 4: Frontend Live Trace API and Utilities

**Files:**
- Modify: `srmp-web-ui/src/api/orchestrator.ts`
- Create: `srmp-web-ui/src/utils/liveTrace.ts`
- Create: `srmp-web-ui/tests/liveTrace.test.mjs`

- [ ] **Step 1: Write failing frontend utility tests**

Create `srmp-web-ui/tests/liveTrace.test.mjs`:

```javascript
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildLiveTraceSummary,
  createWebTraceId,
  normalizeLiveTraceSnapshot,
  shouldPauseLiveTracePolling
} from '../src/utils/liveTrace.ts'

test('creates stable web trace ids with prefix', () => {
  const id = createWebTraceId(() => 1710000000000, () => 0.123456)
  assert.equal(id, 'web-lg-1710000000000-4fzyo8')
})

test('normalizes running live trace snapshots', () => {
  const snapshot = normalizeLiveTraceSnapshot({
    body: {
      traceId: 'trace-1',
      status: 'RUNNING',
      currentStep: { name: 'tool_execute', label: '执行只读工具', status: 'RUNNING', elapsedMs: 2300 },
      steps: [{ name: 'intent_recognize', label: '识别用户意图', status: 'SUCCESS', elapsedMs: 25 }],
      toolSummary: { planned: 3, completed: 1, success: 1, failed: 0 },
      sourceSummary: { business: 12, knowledge: 4, outline: 0 },
      costMs: 4500
    }
  })

  assert.equal(snapshot.traceId, 'trace-1')
  assert.equal(snapshot.status, 'RUNNING')
  assert.equal(snapshot.currentStep.name, 'tool_execute')
  assert.equal(snapshot.steps.length, 1)
  assert.equal(snapshot.toolSummary.planned, 3)
  assert.equal(snapshot.sourceSummary.knowledge, 4)
})

test('builds compact wait summary', () => {
  const summary = buildLiveTraceSummary({
    status: 'RUNNING',
    currentStep: { label: '生成回答', elapsedMs: 9000 },
    steps: [{ label: '识别用户意图', status: 'SUCCESS' }],
    toolSummary: { planned: 2, completed: 2, success: 2, failed: 0 },
    sourceSummary: { business: 5, knowledge: 1, outline: 0 }
  })

  assert.equal(summary.currentLabel, '生成回答')
  assert.equal(summary.toolLabel, '工具 2/2 成功 2')
  assert.equal(summary.sourceLabel, '业务 5｜知识库 1')
})

test('pauses polling after three failures', () => {
  assert.equal(shouldPauseLiveTracePolling(2), false)
  assert.equal(shouldPauseLiveTracePolling(3), true)
})
```

- [ ] **Step 2: Run utility tests to verify they fail**

Run:

```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp/srmp-web-ui
node --no-warnings --test tests/liveTrace.test.mjs
```

Expected: FAIL with module not found.

- [ ] **Step 3: Implement the API and utility**

Add to `srmp-web-ui/src/api/orchestrator.ts`:

```typescript
export function getOrchestratorLiveTrace(traceId: string): Promise<Record<string, any>> {
  return aiRequest.get(`/api/agent/orchestrator/ops/live-trace/${encodeURIComponent(traceId)}`)
}
```

Create `srmp-web-ui/src/utils/liveTrace.ts`:

```typescript
export interface LiveTraceStep {
  name?: string
  label?: string
  status?: string
  elapsedMs?: number
  costMs?: number
  count?: number | string
  error?: string
  data?: Record<string, any>
}

export interface LiveTraceSnapshot {
  traceId?: string
  status: string
  action?: string
  graphName?: string
  currentStep?: LiveTraceStep | null
  steps: LiveTraceStep[]
  toolSummary: { planned: number; completed: number; success: number; failed: number }
  sourceSummary: { business: number; knowledge: number; outline: number }
  answerMeta: Record<string, any>
  error?: string
  costMs?: number
}

export function createWebTraceId(now = Date.now, random = Math.random): string {
  const suffix = Math.floor(random() * 2176782336).toString(36).padStart(6, '0').slice(0, 6)
  return `web-lg-${now()}-${suffix}`
}

export function normalizeLiveTraceSnapshot(input: any): LiveTraceSnapshot {
  const raw = unwrapBody(input)
  const toolSummary = raw.toolSummary || {}
  const sourceSummary = raw.sourceSummary || {}
  return {
    traceId: stringValue(raw.traceId || raw.trace_id),
    status: stringValue(raw.status) || 'UNKNOWN',
    action: stringValue(raw.action),
    graphName: stringValue(raw.graphName || raw.graph_name),
    currentStep: normalizeStep(raw.currentStep || raw.current_step),
    steps: Array.isArray(raw.steps) ? raw.steps.map((step: any) => normalizeStep(step)).filter(Boolean) as LiveTraceStep[] : [],
    toolSummary: {
      planned: numberValue(toolSummary.planned),
      completed: numberValue(toolSummary.completed),
      success: numberValue(toolSummary.success),
      failed: numberValue(toolSummary.failed)
    },
    sourceSummary: {
      business: numberValue(sourceSummary.business),
      knowledge: numberValue(sourceSummary.knowledge),
      outline: numberValue(sourceSummary.outline)
    },
    answerMeta: raw.answerMeta && typeof raw.answerMeta === 'object' ? raw.answerMeta : {},
    error: stringValue(raw.error || raw.errorMessage),
    costMs: optionalNumber(raw.costMs)
  }
}

export function buildLiveTraceSummary(snapshot: Partial<LiveTraceSnapshot>) {
  const current = snapshot.currentStep || null
  const tool = snapshot.toolSummary || { planned: 0, completed: 0, success: 0, failed: 0 }
  const source = snapshot.sourceSummary || { business: 0, knowledge: 0, outline: 0 }
  const sourceParts = []
  if (source.business) sourceParts.push(`业务 ${source.business}`)
  if (source.knowledge) sourceParts.push(`知识库 ${source.knowledge}`)
  if (source.outline) sourceParts.push(`Outline ${source.outline}`)
  return {
    currentLabel: current?.label || current?.name || '',
    currentStatus: current?.status || snapshot.status || '',
    recentSteps: (snapshot.steps || []).slice(-4),
    toolLabel: tool.planned || tool.completed ? `工具 ${tool.completed}/${tool.planned} 成功 ${tool.success}` : '',
    sourceLabel: sourceParts.join('｜'),
    error: snapshot.error || current?.error || ''
  }
}

export function shouldPauseLiveTracePolling(failureCount: number): boolean {
  return Number(failureCount || 0) >= 3
}

function unwrapBody(input: any): any {
  if (input?.body && typeof input.body === 'object') return input.body
  if (input?.data?.body && typeof input.data.body === 'object') return input.data.body
  if (input?.data && typeof input.data === 'object' && input.data.status) return input.data
  return input && typeof input === 'object' ? input : {}
}

function normalizeStep(input: any): LiveTraceStep | null {
  if (!input || typeof input !== 'object') return null
  return {
    name: stringValue(input.name || input.node || input.step_name),
    label: stringValue(input.label || input.message || input.step_label || input.name || input.node),
    status: stringValue(input.status || 'UNKNOWN'),
    elapsedMs: optionalNumber(input.elapsedMs ?? input.costMs ?? input.cost_ms),
    costMs: optionalNumber(input.costMs ?? input.cost_ms),
    count: input.count ?? input.hit_count ?? input.resultCount,
    error: stringValue(input.error || input.errorMessage || input.error_message),
    data: input.data && typeof input.data === 'object' ? input.data : {}
  }
}

function stringValue(value: any): string | undefined {
  if (value === undefined || value === null || value === '') return undefined
  return String(value)
}

function numberValue(value: any): number {
  const num = Number(value)
  return Number.isFinite(num) ? num : 0
}

function optionalNumber(value: any): number | undefined {
  const num = Number(value)
  return Number.isFinite(num) ? num : undefined
}
```

- [ ] **Step 4: Run utility tests**

Run:

```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp/srmp-web-ui
node --no-warnings --test tests/liveTrace.test.mjs tests/aiRunFeedback.test.mjs
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add srmp-web-ui/src/api/orchestrator.ts srmp-web-ui/src/utils/liveTrace.ts srmp-web-ui/tests/liveTrace.test.mjs
git commit -m "feat: add frontend live trace utilities"
```

---

### Task 5: Unified Trace Drawer Normalization

**Files:**
- Modify: `srmp-web-ui/src/views/agent/components/aiExecution.ts`
- Modify: `srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue`
- Modify: `srmp-web-ui/tests/liveTrace.test.mjs`

- [ ] **Step 1: Extend the frontend test for drawer-compatible live snapshots**

Append to `srmp-web-ui/tests/liveTrace.test.mjs`:

```javascript
import { toAiExecutionSnapshot } from '../src/views/agent/components/aiExecution.ts'

test('AiTrace snapshot accepts live running trace', () => {
  const snapshot = toAiExecutionSnapshot({
    trace: {
      traceId: 'trace-live',
      status: 'RUNNING',
      currentStep: { name: 'answer_generate', label: '生成回答', status: 'RUNNING', elapsedMs: 8000 },
      steps: [{ name: 'tool_execute', label: '执行只读工具', status: 'SUCCESS', elapsedMs: 200, count: 2 }],
      toolSummary: { planned: 2, completed: 2, success: 2, failed: 0 },
      sourceSummary: { business: 4, knowledge: 1, outline: 0 },
      costMs: 8200
    },
    answerMeta: { llmStatus: 'RUNNING' }
  })

  assert.equal(snapshot.summary.traceId, 'trace-live')
  assert.equal(snapshot.summary.status, 'RUNNING')
  assert.equal(snapshot.currentStep.name, 'answer_generate')
  assert.equal(snapshot.summary.toolTotalCount, 2)
  assert.equal(snapshot.evidence.businessCount, 4)
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp/srmp-web-ui
node --no-warnings --test tests/liveTrace.test.mjs
```

Expected: FAIL because `AiExecutionSnapshot` has no `currentStep` and source summary is not read from live trace.

- [ ] **Step 3: Extend `aiExecution.ts`**

In `AiExecutionSnapshot`, add:

```typescript
  currentStep?: AiExecutionStep | null
```

In `toAiExecutionSnapshot()`, compute live summaries:

```typescript
  const currentStep = normalizeStep(trace?.currentStep || trace?.current_step, -1)
  const liveToolSummary = firstRecord(trace?.toolSummary, trace?.tool_summary)
  const liveSourceSummary = firstRecord(trace?.sourceSummary, trace?.source_summary)
```

Change summary counts:

```typescript
    status: stringValue(record?.status || trace?.status || replayResult?.status || responsePreview.status || (replayResult ? 'SUCCESS' : undefined)),
    toolTotalCount: numberValue(responseData.toolTotalCount ?? record?.toolTotalCount ?? liveToolSummary.planned ?? tools.length),
    toolSuccessCount: numberValue(responseData.toolSuccessCount ?? record?.toolSuccessCount ?? liveToolSummary.success ?? tools.filter((item) => item.success).length),
    toolFailedCount: numberValue(responseData.toolFailedCount ?? record?.toolFailedCount ?? liveToolSummary.failed ?? tools.filter((item) => !item.success).length),
```

In the returned object, add:

```typescript
    currentStep,
```

Change evidence counts:

```typescript
      knowledgeCount: numberValue(liveSourceSummary.knowledge) ?? countSources(sources, 'KNOWLEDGE'),
      businessCount: numberValue(liveSourceSummary.business) ?? countSources(sources, 'BUSINESS'),
      outlineCount: numberValue(liveSourceSummary.outline) ?? countSources(sources, 'OUTLINE'),
```

Change `normalizeSteps()` to use a new reusable helper:

```typescript
function normalizeSteps(steps: any[]): AiExecutionStep[] {
  return steps
    .map((step, index) => normalizeStep(step, index))
    .filter((step): step is AiExecutionStep => Boolean(step))
}

function normalizeStep(step: any, index: number): AiExecutionStep | null {
  const item = asRecord(step)
  if (!Object.keys(item).length) return null
  const key = stringValue(item.key || item.id || item.step_name || item.name || `step-${index}`) || `step-${index}`
  return {
    key,
    label: stringValue(item.step_label || item.label || item.message || item.step_name || item.name || key) || key,
    status: stringValue(item.status || 'UNKNOWN') || 'UNKNOWN',
    costMs: numberValue(item.cost_ms ?? item.costMs ?? item.elapsedMs),
    count: item.hit_count ?? item.count ?? item.resultCount,
    phase: stringValue(item.phase || item.node || item.group),
    error: stringValue(item.error_message || item.error || item.errorMessage),
    data: firstRecord(item.data, item.detail, item.details)
  }
}
```

- [ ] **Step 4: Show current step in `AiTraceDrawer.vue`**

Add after `<AnswerSourceAlert ... />`:

```vue
      <section v-if="snapshot.currentStep" class="trace-section current-step">
        <h3>当前步骤</h3>
        <div class="step-title">
          <strong>{{ snapshot.currentStep.label }}</strong>
          <el-tag size="small" :type="tagType(snapshot.currentStep.status)">{{ snapshot.currentStep.status }}</el-tag>
        </div>
        <div class="step-meta">{{ snapshot.currentStep.costMs || 0 }}ms</div>
        <div v-if="snapshot.currentStep.error" class="error">{{ snapshot.currentStep.error }}</div>
      </section>
```

Add CSS:

```css
.current-step {
  padding: 10px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #eff6ff;
}
```

- [ ] **Step 5: Run frontend tests**

Run:

```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp/srmp-web-ui
node --no-warnings --test tests/liveTrace.test.mjs tests/aiRunFeedback.test.mjs tests/latestRequestGuard.test.mjs
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add srmp-web-ui/src/views/agent/components/aiExecution.ts srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue srmp-web-ui/tests/liveTrace.test.mjs
git commit -m "feat: normalize live trace for AI drawer"
```

---

### Task 6: GIS Assistant Live Polling UI

**Files:**
- Modify: `srmp-web-ui/src/views/gis/components/AgentChatFloat.vue`

- [ ] **Step 1: Add live trace imports and state**

In `AgentChatFloat.vue`, update imports:

```typescript
import { getOrchestratorLiveTrace, getOrchestratorQuickDiagnostics } from '../../../api/orchestrator'
import {
  buildLiveTraceSummary,
  createWebTraceId,
  normalizeLiveTraceSnapshot,
  shouldPauseLiveTracePolling,
  type LiveTraceSnapshot
} from '../../../utils/liveTrace'
```

Add state near existing wait state:

```typescript
const activeTraceId = ref('')
const liveTrace = ref<LiveTraceSnapshot | null>(null)
const liveTraceError = ref('')
const liveTraceFailureCount = ref(0)
let liveTraceTimer: ReturnType<typeof window.setInterval> | null = null

const liveTraceSummary = computed(() => liveTrace.value ? buildLiveTraceSummary(liveTrace.value) : null)
```

- [ ] **Step 2: Add polling helpers**

Add functions before `beginAiRun()`:

```typescript
function beginLiveTrace(traceId: string) {
  activeTraceId.value = traceId
  liveTrace.value = null
  liveTraceError.value = ''
  liveTraceFailureCount.value = 0
  stopLiveTracePolling()
  window.setTimeout(() => {
    if (activeTraceId.value === traceId) {
      void pollLiveTraceOnce(traceId)
      liveTraceTimer = window.setInterval(() => {
        void pollLiveTraceOnce(traceId)
      }, 1500)
    }
  }, 500)
}

async function pollLiveTraceOnce(traceId = activeTraceId.value) {
  if (!traceId) return
  try {
    const res = await getOrchestratorLiveTrace(traceId)
    const snapshot = normalizeLiveTraceSnapshot(res)
    liveTrace.value = snapshot
    liveTraceError.value = ''
    liveTraceFailureCount.value = 0
    if (['SUCCESS', 'FAILED', 'TIMEOUT'].includes(snapshot.status)) {
      stopLiveTracePolling()
    }
  } catch (error: any) {
    liveTraceFailureCount.value += 1
    liveTraceError.value = error?.message || '实时过程暂不可用'
    if (shouldPauseLiveTracePolling(liveTraceFailureCount.value)) {
      stopLiveTracePolling()
    }
  }
}

function stopLiveTracePolling() {
  if (!liveTraceTimer) return
  window.clearInterval(liveTraceTimer)
  liveTraceTimer = null
}

function openLiveTrace() {
  if (!liveTrace.value) return
  openTrace({ trace: liveTrace.value, answerMeta: liveTrace.value.answerMeta || {} })
}
```

Update `onUnmounted()`:

```typescript
onUnmounted(() => {
  stopAiElapsedTimer()
  stopLiveTracePolling()
})
```

- [ ] **Step 3: Pass traceId into chat requests**

In `send()`, after `requestStartedAt`:

```typescript
const traceId = createWebTraceId()
beginLiveTrace(traceId)
```

Add `traceId` into options:

```typescript
options: { ...options, useTools: useAgentTools.value, traceId }
```

In the `finally` block, before `loading.value = false` or after it:

```typescript
await pollLiveTraceOnce(traceId)
stopLiveTracePolling()
```

When pushing the assistant message, prefer final trace but preserve live fallback:

```typescript
trace: payload.data?.trace || payload.trace || liveTrace.value || null,
```

- [ ] **Step 4: Pass traceId into solution generation**

In `generateSolutionDraft()`, before calling `mapAgentRun(...)`:

```typescript
const traceId = createWebTraceId()
beginLiveTrace(traceId)
```

Add to options:

```typescript
options: { ...options, requireAi: true, traceId }
```

In `finally`:

```typescript
await pollLiveTraceOnce(traceId)
stopLiveTracePolling()
```

- [ ] **Step 5: Render live trace in wait panel**

Inside `<section v-if="aiBusy" class="ai-wait-panel"...>`, after `<p>{{ waitFeedback.message }}</p>` add:

```vue
        <div v-if="liveTraceSummary" class="live-trace-panel">
          <div class="live-current">
            <span>当前步骤</span>
            <strong>{{ liveTraceSummary.currentLabel || liveTrace?.status || '等待 Runtime 上报' }}</strong>
          </div>
          <div class="live-meta">
            <span v-if="liveTraceSummary.toolLabel">{{ liveTraceSummary.toolLabel }}</span>
            <span v-if="liveTraceSummary.sourceLabel">{{ liveTraceSummary.sourceLabel }}</span>
          </div>
          <div v-if="liveTraceSummary.recentSteps.length" class="live-steps">
            <span v-for="step in liveTraceSummary.recentSteps" :key="step.name || step.label">
              {{ step.label || step.name }} · {{ step.status }}
            </span>
          </div>
          <el-button size="small" text @click="openLiveTrace">查看 Trace</el-button>
        </div>
        <div v-else-if="liveTraceError" class="live-trace-error">
          {{ liveTraceError }}，最终结果仍在等待。
        </div>
```

Add CSS near existing wait styles:

```css
.live-trace-panel {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid rgba(148, 163, 184, 0.32);
}

.live-current {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  color: #334155;
  font-size: 12px;
}

.live-current strong {
  color: #0f172a;
}

.live-meta,
.live-steps {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
  color: #64748b;
  font-size: 11px;
}

.live-trace-error {
  margin-top: 8px;
  color: #b45309;
  font-size: 12px;
}
```

- [ ] **Step 6: Run frontend tests and build**

Run:

```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp/srmp-web-ui
node --no-warnings --test tests/liveTrace.test.mjs tests/aiRunFeedback.test.mjs tests/latestRequestGuard.test.mjs
npm run build
```

Expected: tests PASS and build succeeds. The existing Vite chunk-size warning may still appear.

- [ ] **Step 7: Commit**

```bash
git add srmp-web-ui/src/views/gis/components/AgentChatFloat.vue
git commit -m "feat: show live LangGraph progress in one-map AI"
```

---

### Task 7: End-to-End Verification and PR

**Files:**
- Optional modify: `docs/superpowers/plans/2026-05-08-phase50-20-langgraph-live-trace.md` if implementation notes reveal a mismatch.

- [ ] **Step 1: Run backend and frontend verification**

Run:

```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp/srmp-ai-orchestrator
python3 -m unittest discover -s tests -p 'test_live_trace*.py'
```

Expected: PASS.

Run:

```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp
mvn -pl srmp-agent -Dtest=AgentOrchestratorOpsControllerLiveTraceTest,RemoteLangGraphOrchestratorContractTest test
```

Expected: PASS.

Run:

```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp/srmp-web-ui
node --no-warnings --test tests/liveTrace.test.mjs tests/aiRunFeedback.test.mjs tests/latestRequestGuard.test.mjs
npm run build
```

Expected: tests PASS and build succeeds.

- [ ] **Step 2: Run a local API smoke if services are available**

With Java on `8080` and Runtime on `18080`, run:

```bash
TRACE_ID="web-lg-smoke-$(date +%s)"
curl -fsS -X POST 'http://localhost:8080/api/agent/map-agent/run' \
  -H 'Content-Type: application/json' \
  -d "{\"action\":\"CHAT\",\"message\":\"分析 G210 当前养护重点\",\"mapContext\":{\"mode\":\"ROUTE\",\"routeCode\":\"G210\",\"year\":2026},\"options\":{\"traceId\":\"${TRACE_ID}\",\"useTools\":true}}" >/tmp/srmp-live-trace-run.json &
sleep 2
curl -fsS "http://localhost:8080/api/agent/orchestrator/ops/live-trace/${TRACE_ID}" | python3 -m json.tool
wait
```

Expected: live trace payload contains `status` as `RUNNING`, `SUCCESS`, or `FAILED`, and includes either `currentStep` or `steps`.

- [ ] **Step 3: Run browser smoke**

Run a temporary frontend dev server:

```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp/srmp-web-ui
npm run dev -- --host 127.0.0.1 --port 5174
```

Open `http://localhost:5174/gis/one-map`, open the AI assistant, send a route question, and verify:

- Wait panel shows elapsed time.
- Wait panel shows current live step when backend services are available.
- `查看 Trace` opens the existing drawer.
- Final message still has Trace button and source/tool tags.

- [ ] **Step 4: Final git checks**

Run:

```bash
cd /Users/cxp/.codex/worktrees/d1e1/srmp
git diff --check HEAD
git status --short --branch
```

Expected: `git diff --check HEAD` exits 0. `git status` only shows ignored existing untracked `deploy/outline/*` files or a clean worktree.

- [ ] **Step 5: Push and create PR after user validation**

```bash
git push -u origin codex/phase50-20-langgraph-live-trace
```

Create a PR against `main` with summary:

```markdown
## Summary
- add live LangGraph trace snapshots in srmp-ai-orchestrator
- proxy live trace lookup through Java ops
- show current LangGraph progress in the GIS one-map AI assistant

## Verification
- python3 -m unittest discover -s tests -p 'test_live_trace*.py'
- mvn -pl srmp-agent -Dtest=AgentOrchestratorOpsControllerLiveTraceTest,RemoteLangGraphOrchestratorContractTest test
- node --no-warnings --test tests/liveTrace.test.mjs tests/aiRunFeedback.test.mjs tests/latestRequestGuard.test.mjs
- npm run build
```

---

## Self-Review

- Spec coverage: Task 1 creates the live snapshot store; Task 2 writes Runtime node progress and endpoint; Task 3 adds Java proxy; Tasks 4-6 add traceId, polling, wait UI, and unified drawer support; Task 7 verifies the end-to-end path.
- Placeholder scan: the plan contains no placeholder markers, no unspecified test commands, and every code-changing step includes the intended code block.
- Type consistency: `traceId`, `currentStep`, `steps`, `toolSummary`, `sourceSummary`, `answerMeta`, `costMs`, and `status` are used consistently across Python, Java proxy, and frontend normalization.
