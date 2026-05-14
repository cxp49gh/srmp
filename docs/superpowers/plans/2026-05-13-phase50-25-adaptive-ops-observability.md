# Phase50.25 Adaptive Ops Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Phase50.24 adaptive planning visible in Runtime config, audit summary, recent records, and the LangGraph Ops page.

**Architecture:** RuntimeAuditStore stores flattened adaptive planning fields per successful record and computes aggregate buckets in `summary()`. The existing config/snapshot endpoints expose adaptive settings, and `LangGraphOpsPage.vue` renders those fields without adding new APIs or changing execution behavior.

**Tech Stack:** Python 3.12-compatible unittest, FastAPI TestClient, Vue 3 Composition API, Element Plus, Node test runner.

---

## File Structure

- Create `srmp-ai-orchestrator/tests/test_observability_adaptive.py`: backend red/green tests for flattened audit records and adaptive summary buckets.
- Create `srmp-ai-orchestrator/tests/test_runtime_config_adaptive.py`: backend config endpoint test for adaptive settings.
- Modify `srmp-ai-orchestrator/app/observability.py`: flatten `adaptivePlanning` into records and add summary aggregation.
- Modify `srmp-ai-orchestrator/app/main.py`: expose adaptive settings in safe config and warnings.
- Create `srmp-web-ui/tests/langGraphOpsAdaptive.test.mjs`: source-level tests for ops page adaptive card and table column.
- Modify `srmp-web-ui/src/views/agent/LangGraphOpsPage.vue`: render adaptive metrics, config row, and recent table column.

## Task 1: Backend Adaptive Audit Fields

**Files:**
- Create: `srmp-ai-orchestrator/tests/test_observability_adaptive.py`
- Modify: `srmp-ai-orchestrator/app/observability.py`

- [ ] **Step 1: Add failing observability tests**

Create `srmp-ai-orchestrator/tests/test_observability_adaptive.py`:

```python
import unittest

from app.observability import RuntimeAuditStore
from app.schemas import MapAiAgentRequest, MapAiAgentResponse


class AdaptiveObservabilityTest(unittest.TestCase):
    def test_record_success_flattens_adaptive_planning(self):
        store = RuntimeAuditStore(max_records=10, persist_enabled=False)
        response = MapAiAgentResponse(
            answer="ok",
            data={
                "adaptivePlanning": {
                    "enabled": True,
                    "status": "EXECUTED",
                    "reason": "业务工具未命中，追加知识检索补充解释依据。",
                    "addedToolNames": ["knowledge.retrieve"],
                },
                "toolTotalCount": 2,
                "toolSuccessCount": 2,
                "toolFailedCount": 0,
            },
            trace={"traceId": "adaptive-trace-1"},
            toolResults=[
                {"toolName": "gis.queryRegionSummary", "success": True},
                {"toolName": "knowledge.retrieve", "success": True},
            ],
        )

        record = store.record_success(
            request=MapAiAgentRequest(message="分析区域"),
            response=response,
            tenant_id="default",
            trace_id="adaptive-trace-1",
            cost_ms=42,
        )

        self.assertEqual("EXECUTED", record["adaptivePlanningStatus"])
        self.assertTrue(record["adaptivePlanningEnabled"])
        self.assertEqual(1, record["adaptiveAddedToolCount"])
        self.assertEqual(["knowledge.retrieve"], record["adaptiveAddedToolNames"])
        self.assertEqual("业务工具未命中，追加知识检索补充解释依据。", record["adaptivePlanningReason"])

    def test_summary_aggregates_adaptive_planning(self):
        store = RuntimeAuditStore(max_records=10, persist_enabled=False)
        store.record_success(
            request=MapAiAgentRequest(message="a"),
            response=MapAiAgentResponse(
                answer="a",
                data={"adaptivePlanning": {"enabled": True, "status": "EXECUTED", "addedToolNames": ["knowledge.retrieve"]}},
            ),
            tenant_id="default",
            trace_id="trace-a",
            cost_ms=10,
        )
        store.record_success(
            request=MapAiAgentRequest(message="b"),
            response=MapAiAgentResponse(
                answer="b",
                data={"adaptivePlanning": {"enabled": True, "status": "SKIPPED_SUFFICIENT", "addedToolNames": []}},
            ),
            tenant_id="default",
            trace_id="trace-b",
            cost_ms=10,
        )
        store.record_success(
            request=MapAiAgentRequest(message="c"),
            response=MapAiAgentResponse(answer="c", data={}),
            tenant_id="default",
            trace_id="trace-c",
            cost_ms=10,
        )

        adaptive = store.summary()["adaptivePlanning"]

        self.assertEqual(1, adaptive["executedCount"])
        self.assertEqual(1, adaptive["skippedCount"])
        self.assertEqual(1, adaptive["addedToolCount"])
        self.assertEqual({"EXECUTED": 1, "SKIPPED_SUFFICIENT": 1}, adaptive["statusBuckets"])
        self.assertEqual({"knowledge.retrieve": 1}, adaptive["addedToolBuckets"])


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run tests and confirm failure**

Run:

```bash
cd srmp-ai-orchestrator
/Users/cxp/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 -m unittest tests.test_observability_adaptive
```

Expected: fails because adaptive flattened fields and summary are missing.

- [ ] **Step 3: Implement audit flattening**

In `srmp-ai-orchestrator/app/observability.py`, add helpers near `_compact_response()`:

```python
def _adaptive_planning_from_response(response: MapAiAgentResponse) -> Dict[str, Any]:
    data = response.data or {}
    trace = response.trace or {}
    adaptive = data.get("adaptivePlanning") if isinstance(data, dict) else None
    if not isinstance(adaptive, dict) and isinstance(trace, dict):
        adaptive = trace.get("adaptivePlanning")
    return adaptive if isinstance(adaptive, dict) else {}


def _adaptive_added_tools(adaptive: Dict[str, Any]) -> List[str]:
    values = adaptive.get("addedToolNames")
    if not isinstance(values, list):
        return []
    result: List[str] = []
    for item in values:
        text = str(item or "").strip()
        if text and text not in result:
            result.append(text)
    return result


def _adaptive_summary(records: List[Dict[str, Any]]) -> Dict[str, Any]:
    status_buckets: Dict[str, int] = {}
    tool_buckets: Dict[str, int] = {}
    executed = 0
    skipped = 0
    disabled = 0
    added_tool_count = 0
    for record in records or []:
        status = str(record.get("adaptivePlanningStatus") or "").strip()
        if status:
            status_buckets[status] = status_buckets.get(status, 0) + 1
            if status == "EXECUTED":
                executed += 1
            elif status.startswith("SKIPPED_"):
                skipped += 1
            elif status == "DISABLED":
                disabled += 1
        tools = record.get("adaptiveAddedToolNames")
        if not isinstance(tools, list):
            tools = []
        added_tool_count += len(tools)
        for tool_name in tools:
            name = str(tool_name or "").strip()
            if name:
                tool_buckets[name] = tool_buckets.get(name, 0) + 1
    return {
        "executedCount": executed,
        "skippedCount": skipped,
        "disabledCount": disabled,
        "addedToolCount": added_tool_count,
        "statusBuckets": dict(sorted(status_buckets.items(), key=lambda item: item[1], reverse=True)),
        "addedToolBuckets": dict(sorted(tool_buckets.items(), key=lambda item: item[1], reverse=True)),
    }
```

In `record_success()`, after `tool_success`, add:

```python
        adaptive_planning = _adaptive_planning_from_response(response)
        adaptive_added_tools = _adaptive_added_tools(adaptive_planning)
```

Add these record fields:

```python
            "adaptivePlanningStatus": adaptive_planning.get("status"),
            "adaptivePlanningEnabled": adaptive_planning.get("enabled"),
            "adaptiveAddedToolCount": len(adaptive_added_tools),
            "adaptiveAddedToolNames": adaptive_added_tools,
            "adaptivePlanningReason": adaptive_planning.get("reason"),
```

In `summary()`, add:

```python
                "adaptivePlanning": _adaptive_summary(records),
```

- [ ] **Step 4: Run observability tests**

Run the same unittest command. Expected: pass.

- [ ] **Step 5: Commit backend observability**

```bash
git add srmp-ai-orchestrator/app/observability.py srmp-ai-orchestrator/tests/test_observability_adaptive.py
git commit -m "feat: summarize adaptive planning observability"
```

## Task 2: Runtime Config Adaptive Fields

**Files:**
- Create: `srmp-ai-orchestrator/tests/test_runtime_config_adaptive.py`
- Modify: `srmp-ai-orchestrator/app/main.py`

- [ ] **Step 1: Add failing config test**

Create `srmp-ai-orchestrator/tests/test_runtime_config_adaptive.py`:

```python
import unittest

from fastapi.testclient import TestClient

from app.main import app


class RuntimeConfigAdaptiveTest(unittest.TestCase):
    def test_runtime_config_exposes_adaptive_settings(self):
        client = TestClient(app)

        response = client.get("/api/srmp/langgraph/runtime/config")

        self.assertEqual(200, response.status_code)
        safe_config = response.json()["safeConfig"]
        self.assertIn("adaptivePlanningEnabled", safe_config)
        self.assertIn("maxAdaptiveIterations", safe_config)
        self.assertIsInstance(safe_config["adaptivePlanningEnabled"], bool)
        self.assertGreaterEqual(safe_config["maxAdaptiveIterations"], 0)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run config test and confirm failure**

```bash
cd srmp-ai-orchestrator
/Users/cxp/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 -m unittest tests.test_runtime_config_adaptive
```

Expected: fails because safeConfig lacks adaptive fields.

- [ ] **Step 3: Add config fields and warnings**

In `srmp-ai-orchestrator/app/main.py`, add these fields to `_safe_runtime_config()` after `requireEvidencePrefix`:

```python
        "adaptivePlanningEnabled": settings.adaptive_planning_enabled,
        "maxAdaptiveIterations": settings.max_adaptive_iterations,
```

In `_config_warnings()`, after the `MAX_TOOL_CALLS_INVALID` check, add:

```python
    if bool(config.get("adaptivePlanningEnabled")) and int(config.get("maxToolCalls") or 0) < 2:
        warnings.append({"level": "WARN", "code": "ADAPTIVE_WITH_LOW_TOOL_LIMIT", "message": "自适应规划已开启，但 maxToolCalls 小于 2，几乎没有追加工具空间。"})
    if bool(config.get("adaptivePlanningEnabled")) and int(config.get("maxAdaptiveIterations") or 0) <= 0:
        warnings.append({"level": "WARN", "code": "ADAPTIVE_ITERATIONS_DISABLED", "message": "自适应规划已开启，但 maxAdaptiveIterations 为 0。"})
```

- [ ] **Step 4: Run config and adaptive workflow tests**

```bash
cd srmp-ai-orchestrator
/Users/cxp/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 -m unittest tests.test_runtime_config_adaptive tests.test_adaptive_workflow
```

Expected: pass.

- [ ] **Step 5: Commit config fields**

```bash
git add srmp-ai-orchestrator/app/main.py srmp-ai-orchestrator/tests/test_runtime_config_adaptive.py
git commit -m "feat: expose adaptive planning config"
```

## Task 3: Ops Page Adaptive Metrics

**Files:**
- Create: `srmp-web-ui/tests/langGraphOpsAdaptive.test.mjs`
- Modify: `srmp-web-ui/src/views/agent/LangGraphOpsPage.vue`

- [ ] **Step 1: Add failing source test**

Create `srmp-web-ui/tests/langGraphOpsAdaptive.test.mjs`:

```js
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('../src/views/agent/LangGraphOpsPage.vue', import.meta.url), 'utf8')

test('LangGraph ops page renders adaptive planning metric card', () => {
  assert.match(source, /自适应规划/)
  assert.match(source, /adaptivePlanningSummary/)
  assert.match(source, /adaptiveExecutedCount/)
})

test('LangGraph ops recent table exposes adaptive planning column', () => {
  assert.match(source, /label="自适应"/)
  assert.match(source, /formatAdaptiveStatus/)
  assert.match(source, /adaptiveAddedToolNames/)
})

test('LangGraph ops config panel shows adaptive planning settings', () => {
  assert.match(source, /adaptivePlanningEnabled/)
  assert.match(source, /maxAdaptiveIterations/)
})
```

- [ ] **Step 2: Run frontend test and confirm failure**

```bash
cd srmp-web-ui
node --no-warnings --test tests/langGraphOpsAdaptive.test.mjs
```

Expected: fails because the page does not render adaptive metrics yet.

- [ ] **Step 3: Add metric card**

In `LangGraphOpsPage.vue`, insert a metric card in the `metric-grid` after the Runtime 调用 card:

```vue
        <el-card shadow="never" class="metric-card">
          <div class="metric-head">
            <span>自适应规划</span>
            <el-tag :type="adaptivePlanningTagType">{{ adaptivePlanningEnabled ? '启用' : '关闭' }}</el-tag>
          </div>
          <div class="metric-value">{{ adaptiveExecutedCount }}</div>
          <div class="metric-desc">跳过 {{ adaptiveSkippedCount }}；禁用 {{ adaptiveDisabledCount }}；追加工具 {{ adaptiveAddedToolCount }}；max {{ maxAdaptiveIterationsText }}</div>
        </el-card>
```

- [ ] **Step 4: Add config health row**

In the 配置健康 `diag-list`, add:

```vue
            <div class="diag-row">
              <span>自适应规划</span>
              <strong>enabled={{ adaptivePlanningEnabled ? 'true' : 'false' }}；max={{ maxAdaptiveIterationsText }}</strong>
            </div>
```

- [ ] **Step 5: Add recent table column**

In the recent table after the 工具 column, add:

```vue
          <el-table-column label="自适应" width="140">
            <template #default="scope">
              <span :title="displayList(scope.row.adaptiveAddedToolNames || [])">{{ formatAdaptiveStatus(scope.row) }}</span>
            </template>
          </el-table-column>
```

- [ ] **Step 6: Add computed values and helpers**

In `<script setup>`, after `runtimeSummary`, add:

```ts
const adaptivePlanningSummary = computed(() => value(runtimeSummary.value, ['adaptivePlanning']) || {})
const adaptiveExecutedCount = computed(() => Number(value(adaptivePlanningSummary.value, ['executedCount']) || 0))
const adaptiveSkippedCount = computed(() => Number(value(adaptivePlanningSummary.value, ['skippedCount']) || 0))
const adaptiveDisabledCount = computed(() => Number(value(adaptivePlanningSummary.value, ['disabledCount']) || 0))
const adaptiveAddedToolCount = computed(() => Number(value(adaptivePlanningSummary.value, ['addedToolCount']) || 0))
const adaptivePlanningEnabled = computed(() => Boolean(value(configBody.value, ['safeConfig', 'adaptivePlanningEnabled']) ?? value(healthBody.value, ['strategy', 'adaptivePlanning'])))
const maxAdaptiveIterationsText = computed(() => String(value(configBody.value, ['safeConfig', 'maxAdaptiveIterations']) ?? value(healthBody.value, ['strategy', 'maxAdaptiveIterations']) ?? '-'))
const adaptivePlanningTagType = computed(() => adaptivePlanningEnabled.value ? (adaptiveExecutedCount.value > 0 ? 'warning' : 'info') : 'info')
```

Add helpers near `asStringArray()`:

```ts
function displayList(values: any[]) {
  return Array.isArray(values) && values.length ? values.join('、') : '-'
}

function formatAdaptiveStatus(row: Record<string, any>) {
  const status = String(row.adaptivePlanningStatus || '')
  if (!status) return '-'
  const count = Number(row.adaptiveAddedToolCount || 0)
  return count > 0 ? `${status} +${count}` : status
}
```

- [ ] **Step 7: Run frontend tests**

```bash
cd srmp-web-ui
node --no-warnings --test tests/langGraphOpsAdaptive.test.mjs tests/mapAiPlanPreview.test.mjs tests/mapAiWorkbenchSplit.test.mjs
```

Expected: pass.

- [ ] **Step 8: Commit frontend ops metrics**

```bash
git add srmp-web-ui/src/views/agent/LangGraphOpsPage.vue srmp-web-ui/tests/langGraphOpsAdaptive.test.mjs
git commit -m "feat: show adaptive planning in ops"
```

## Task 4: Final Verification

**Files:**
- Verify only.

- [ ] **Step 1: Run backend regression**

```bash
cd srmp-ai-orchestrator
/Users/cxp/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 -m unittest tests.test_observability_adaptive tests.test_runtime_config_adaptive tests.test_adaptive_workflow tests.test_plan_execution tests.test_debug_plan_preview tests.test_live_trace tests.test_live_trace_api
```

Expected: all tests pass.

- [ ] **Step 2: Run frontend focused tests**

```bash
cd srmp-web-ui
node --no-warnings --test tests/langGraphOpsAdaptive.test.mjs tests/mapAiPlanPreview.test.mjs tests/mapAiWorkbenchSplit.test.mjs tests/liveTrace.test.mjs tests/aiRunFeedback.test.mjs tests/latestRequestGuard.test.mjs
```

Expected: all tests pass.

- [ ] **Step 3: Build frontend**

```bash
cd srmp-web-ui
npm run build
```

Expected: build succeeds. Existing Vite chunk-size warnings are acceptable.

- [ ] **Step 4: Merge and push**

```bash
git switch main
git merge --ff-only codex/phase50-25-adaptive-ops-observability
git push origin main
```

Expected: `origin/main` advances to the final Phase50.25 commit.

## Self-Review

- Spec coverage: config fields, audit flattening, summary aggregation, ops metric card, recent column, and verification are covered.
- Placeholder scan: no placeholder tasks remain.
- Type consistency: backend uses `adaptivePlanningStatus`, `adaptiveAddedToolCount`, `adaptiveAddedToolNames`; frontend reads the same names.
