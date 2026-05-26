# AI Agent Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first read-only AI Agent governance loop: capability/tool configuration, policy-based tool planning, governance APIs, and a visual admin page.

**Architecture:** Keep the existing LangGraph Runtime and Java Tool Gateway stable. Add a Python governance registry that loads versioned JSON configuration, resolves a capability for each request, and constrains tool planning and adaptive planning. Expose the governance state through Runtime APIs, proxy them through Java ops APIs, and add a read-only Vue governance page with a plan simulator.

**Tech Stack:** Python 3.11/FastAPI/Pydantic, Java Spring MVC proxy controller, Vue 3 + Element Plus, existing `unittest` orchestrator tests.

---

### Task 1: Capability And Tool Registry

**Files:**
- Create: `srmp-ai-orchestrator/app/governance_data/capabilities.json`
- Create: `srmp-ai-orchestrator/app/governance_data/tools.json`
- Create: `srmp-ai-orchestrator/app/governance.py`
- Test: `srmp-ai-orchestrator/tests/test_governance_registry.py`

- [ ] **Step 1: Write failing tests for registry loading and metric capability matching**

Create `srmp-ai-orchestrator/tests/test_governance_registry.py` with:

```python
import unittest

from app.governance import governance_registry, resolve_capability
from app.schemas import MapAiAgentRequest, MapAiContext


class GovernanceRegistryTest(unittest.TestCase):
    def test_registry_loads_capabilities_and_tools(self):
        registry = governance_registry()

        self.assertTrue(registry.config_valid)
        self.assertGreaterEqual(len(registry.capabilities), 6)
        self.assertIn("knowledge.metric_explain", registry.capability_by_id)
        self.assertIn("knowledge.retrieve", registry.tool_by_name)

    def test_metric_explanation_matches_knowledge_only_capability(self):
        request = MapAiAgentRequest(
            message="解释 PCI 指标",
            mapContext=MapAiContext(mode="ROUTE", routeCode="Y016140727", year=2026),
        )

        match = resolve_capability(request, fallback_intent="ROUTE_ANALYSIS", intent_detail={})

        self.assertEqual("knowledge.metric_explain", match["capabilityId"])
        self.assertEqual("KNOWLEDGE_QA", match["intent"])
        self.assertEqual("KNOWLEDGE_ONLY", match["contextUsage"])
        self.assertIn("gis.queryRegionSummary", match["toolPolicy"]["prohibited"])
        self.assertTrue(any("keyword:PCI" == item for item in match["matchedRules"]))

    def test_explicit_route_action_matches_route_analysis(self):
        request = MapAiAgentRequest(
            action="ANALYZE_ROUTE",
            message="分析当前路线",
            mapContext=MapAiContext(mode="ROUTE", routeCode="Y016140727", year=2026),
            options={"action": "ANALYZE_ROUTE"},
        )

        match = resolve_capability(request, fallback_intent="ROUTE_ANALYSIS", intent_detail={"action": "ANALYZE_ROUTE"})

        self.assertEqual("map.route_analysis", match["capabilityId"])
        self.assertEqual("ROUTE_ANALYSIS", match["intent"])
        self.assertIn("gis.queryRegionSummary", match["toolPolicy"]["required"])


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
PYTHONPATH=srmp-ai-orchestrator srmp-ai-orchestrator/.venv/bin/python -m unittest srmp-ai-orchestrator.tests.test_governance_registry -v
```

Expected: import failure for `app.governance`.

- [ ] **Step 3: Add governance JSON configuration**

Create `capabilities.json` with enabled capabilities:

```json
{
  "version": "phase52-governance-v1",
  "capabilities": [
    {
      "id": "knowledge.metric_explain",
      "name": "指标解释",
      "category": "KNOWLEDGE",
      "enabled": true,
      "priority": 100,
      "intent": "KNOWLEDGE_QA",
      "legacyIntents": ["KNOWLEDGE_QA"],
      "triggers": {
        "includeKeywords": ["指标", "MQI", "PQI", "PCI", "RQI", "RDI", "SCI", "BCI", "TCI"],
        "questionKeywords": ["解释", "说明", "介绍", "含义", "定义", "是什么", "什么意思", "如何理解", "怎么计算", "计算公式"]
      },
      "contextPolicy": {
        "contextUsage": "KNOWLEDGE_ONLY",
        "ignoreBusinessScopeByDefault": true,
        "businessScopeRequiresExplicitPhrase": ["当前", "这条", "该路线", "这个对象", "结合地图", "为什么低"]
      },
      "toolPolicy": {
        "required": ["knowledge.retrieve"],
        "optional": [],
        "adaptive": [],
        "prohibited": ["gis.queryRegionSummary", "gis.queryDiseases", "gis.queryAssessmentResults", "gis.queryDiseasesByStakeRange", "gis.queryNearbyObjects"]
      }
    },
    {
      "id": "knowledge.maintenance_qa",
      "name": "养护知识问答",
      "category": "KNOWLEDGE",
      "enabled": true,
      "priority": 70,
      "intent": "KNOWLEDGE_QA",
      "legacyIntents": ["KNOWLEDGE_QA", "GENERAL_CHAT"],
      "triggers": {
        "includeKeywords": ["规范", "标准", "工艺", "依据", "怎么处理", "如何处置"]
      },
      "contextPolicy": {"contextUsage": "KNOWLEDGE_ONLY"},
      "toolPolicy": {
        "required": ["knowledge.retrieve"],
        "optional": [],
        "adaptive": ["knowledge.retrieve"],
        "prohibited": ["gis.queryRegionSummary", "gis.queryDiseases", "gis.queryAssessmentResults", "gis.queryDiseasesByStakeRange", "gis.queryNearbyObjects"]
      }
    },
    {
      "id": "map.disease_analysis",
      "name": "病害分析",
      "category": "MAP_ANALYSIS",
      "enabled": true,
      "priority": 80,
      "intent": "OBJECT_ANALYSIS",
      "legacyIntents": ["OBJECT_ANALYSIS", "NEARBY_ANALYSIS"],
      "triggers": {"objectTypes": ["DISEASE"]},
      "contextPolicy": {"contextUsage": "OBJECT_REQUIRED"},
      "toolPolicy": {
        "required": ["gis.queryNearbyObjects"],
        "optional": ["knowledge.retrieve"],
        "adaptive": ["knowledge.retrieve"],
        "prohibited": ["gis.queryRegionSummary"]
      }
    },
    {
      "id": "map.assessment_analysis",
      "name": "评定结果分析",
      "category": "MAP_ANALYSIS",
      "enabled": true,
      "priority": 80,
      "intent": "OBJECT_ANALYSIS",
      "legacyIntents": ["OBJECT_ANALYSIS"],
      "triggers": {"objectTypes": ["ASSESSMENT_RESULT"]},
      "contextPolicy": {"contextUsage": "OBJECT_REQUIRED"},
      "toolPolicy": {
        "required": ["gis.queryAssessmentResults", "gis.queryDiseasesByStakeRange"],
        "optional": ["knowledge.retrieve"],
        "adaptive": ["knowledge.retrieve", "gis.queryAssessmentResults"],
        "prohibited": []
      }
    },
    {
      "id": "map.route_analysis",
      "name": "路线分析",
      "category": "MAP_ANALYSIS",
      "enabled": true,
      "priority": 75,
      "intent": "ROUTE_ANALYSIS",
      "legacyIntents": ["ROUTE_ANALYSIS"],
      "triggers": {"actions": ["ANALYZE_ROUTE"], "modes": ["ROUTE"], "objectTypes": ["ROAD_ROUTE"]},
      "contextPolicy": {"contextUsage": "ROUTE_REQUIRED"},
      "toolPolicy": {
        "required": ["gis.queryRegionSummary", "gis.queryAssessmentResults", "gis.queryDiseases"],
        "optional": ["knowledge.retrieve"],
        "adaptive": ["knowledge.retrieve", "gis.queryRegionSummary"],
        "prohibited": []
      }
    },
    {
      "id": "map.region_analysis",
      "name": "区域分析",
      "category": "MAP_ANALYSIS",
      "enabled": true,
      "priority": 75,
      "intent": "REGION_ANALYSIS",
      "legacyIntents": ["REGION_ANALYSIS"],
      "triggers": {"actions": ["ANALYZE_REGION"], "modes": ["REGION", "BOX", "POLYGON", "SELECTION"]},
      "contextPolicy": {"contextUsage": "REGION_REQUIRED"},
      "toolPolicy": {
        "required": ["gis.queryRegionSummary"],
        "optional": ["knowledge.retrieve"],
        "adaptive": ["knowledge.retrieve", "gis.queryRegionSummary"],
        "prohibited": []
      }
    },
    {
      "id": "solution.generate",
      "name": "方案生成",
      "category": "SOLUTION",
      "enabled": true,
      "priority": 90,
      "intent": "SOLUTION_GENERATE",
      "legacyIntents": ["SOLUTION_GENERATE"],
      "triggers": {"actions": ["GENERATE_OBJECT_SOLUTION", "GENERATE_REGION_SOLUTION", "GENERATE_ROUTE_REPORT"]},
      "contextPolicy": {"contextUsage": "BUSINESS_SCOPE_REQUIRED"},
      "toolPolicy": {
        "required": ["solution.generateDraft"],
        "optional": ["knowledge.retrieve"],
        "adaptive": ["knowledge.retrieve"],
        "prohibited": []
      }
    }
  ]
}
```

Create `tools.json` with metadata for all eight current read tools.

- [ ] **Step 4: Implement `app/governance.py`**

Implement functions:

```python
def governance_registry() -> GovernanceRegistry: ...
def resolve_capability(request: MapAiAgentRequest, fallback_intent: str, intent_detail: Dict[str, Any]) -> Dict[str, Any]: ...
def governance_summary() -> Dict[str, Any]: ...
def validate_governance() -> Dict[str, Any]: ...
```

The resolver sorts enabled capabilities by priority, matches action/object/mode/keyword rules, falls back by `legacyIntents`, and returns a dict with `capabilityId`, `name`, `intent`, `confidence`, `matchedRules`, `contextUsage`, `toolPolicy`.

- [ ] **Step 5: Run tests and verify pass**

Run:

```bash
PYTHONPATH=srmp-ai-orchestrator srmp-ai-orchestrator/.venv/bin/python -m unittest srmp-ai-orchestrator.tests.test_governance_registry -v
```

Expected: all governance registry tests pass.

- [ ] **Step 6: Commit**

```bash
git add srmp-ai-orchestrator/app/governance.py srmp-ai-orchestrator/app/governance/capabilities.json srmp-ai-orchestrator/app/governance/tools.json srmp-ai-orchestrator/tests/test_governance_registry.py
git commit -m "feat: add ai governance registry"
```

### Task 2: Policy-Based Planning And Trace Metadata

**Files:**
- Modify: `srmp-ai-orchestrator/app/workflow.py`
- Modify: `srmp-ai-orchestrator/app/planner.py`
- Modify: `srmp-ai-orchestrator/app/adaptive_planner.py`
- Modify: `srmp-ai-orchestrator/app/plan_preview.py`
- Test: `srmp-ai-orchestrator/tests/test_governance_planning.py`

- [ ] **Step 1: Write failing tests for capability-aware planning**

Create `test_governance_planning.py`:

```python
import unittest

from app.governance import resolve_capability
from app.intent import recognize_intent
from app.planner import plan_tools
from app.schemas import MapAiAgentRequest, MapAiContext


class GovernancePlanningTest(unittest.TestCase):
    def test_metric_question_uses_governance_policy_tools_only(self):
        request = MapAiAgentRequest(
            message="解释 PCI 指标",
            mapContext=MapAiContext(mode="ROUTE", routeCode="Y016140727", year=2026),
            options={"useKnowledge": True, "topK": 3},
        )
        fallback_intent, detail = recognize_intent(request)
        capability = resolve_capability(request, fallback_intent, detail)

        calls = plan_tools(request, capability["intent"], detail, capability=capability)

        self.assertEqual("knowledge.metric_explain", capability["capabilityId"])
        self.assertEqual(["knowledge.retrieve"], [item.toolName for item in calls])

    def test_route_analysis_uses_policy_business_tools_and_knowledge(self):
        request = MapAiAgentRequest(
            action="ANALYZE_ROUTE",
            message="分析当前路线",
            mapContext=MapAiContext(mode="ROUTE", routeCode="Y016140727", year=2026),
            options={"useKnowledge": True},
        )
        fallback_intent, detail = recognize_intent(request)
        capability = resolve_capability(request, fallback_intent, detail)

        calls = plan_tools(request, capability["intent"], detail, capability=capability)
        names = [item.toolName for item in calls]

        self.assertEqual("map.route_analysis", capability["capabilityId"])
        self.assertIn("gis.queryRegionSummary", names)
        self.assertIn("gis.queryAssessmentResults", names)
        self.assertIn("gis.queryDiseases", names)
        self.assertIn("knowledge.retrieve", names)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
PYTHONPATH=srmp-ai-orchestrator srmp-ai-orchestrator/.venv/bin/python -m unittest srmp-ai-orchestrator.tests.test_governance_planning -v
```

Expected: `plan_tools()` does not accept `capability`.

- [ ] **Step 3: Update planner**

Change `plan_tools` signature to:

```python
def plan_tools(request: MapAiAgentRequest, intent: str, intent_detail: Optional[Dict[str, Any]] = None, capability: Optional[Dict[str, Any]] = None) -> List[ToolCall]:
```

If `capability.toolPolicy` is present, build calls from `required` plus `optional`; skip optional `knowledge.retrieve` when `options.useKnowledge=false`; remove tools listed in `prohibited`; keep old hard-coded branch as fallback.

- [ ] **Step 4: Update workflow**

In `_intent_recognize`, call `resolve_capability`, store it in `state["capability"]`, and set `intent` to `capability["intent"]`.

In `_tool_plan`, pass `capability=state.get("capability")`.

In `plan()`, include:

```python
"capability": final_state.get("capability", {}),
"capabilityId": (final_state.get("capability") or {}).get("capabilityId"),
```

- [ ] **Step 5: Update adaptive planner**

Read `capability.toolPolicy.prohibited` and `adaptive`; only return candidates allowed by current capability.

- [ ] **Step 6: Update plan preview**

Add capability source hints and warnings when a planned tool is not in required/optional/adaptive.

- [ ] **Step 7: Run focused tests**

Run:

```bash
PYTHONPATH=srmp-ai-orchestrator srmp-ai-orchestrator/.venv/bin/python -m unittest srmp-ai-orchestrator.tests.test_governance_planning srmp-ai-orchestrator.tests.test_business_scope srmp-ai-orchestrator.tests.test_adaptive_planner -v
```

Expected: all pass.

- [ ] **Step 8: Commit**

```bash
git add srmp-ai-orchestrator/app/workflow.py srmp-ai-orchestrator/app/planner.py srmp-ai-orchestrator/app/adaptive_planner.py srmp-ai-orchestrator/app/plan_preview.py srmp-ai-orchestrator/tests/test_governance_planning.py
git commit -m "feat: apply ai governance policy planning"
```

### Task 3: Governance Runtime And Java Proxy APIs

**Files:**
- Modify: `srmp-ai-orchestrator/app/main.py`
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java`
- Test: `srmp-ai-orchestrator/tests/test_governance_api.py`

- [ ] **Step 1: Write failing FastAPI tests**

Create `test_governance_api.py`:

```python
import unittest

from fastapi.testclient import TestClient

from app.main import app


class GovernanceApiTest(unittest.TestCase):
    def test_capabilities_endpoint_returns_registry(self):
        client = TestClient(app)

        response = client.get("/api/srmp/langgraph/governance/capabilities")

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertTrue(body["configValid"])
        self.assertTrue(any(item["id"] == "knowledge.metric_explain" for item in body["capabilities"]))

    def test_plan_simulate_returns_capability(self):
        client = TestClient(app)

        response = client.post(
            "/api/srmp/langgraph/governance/plan-simulate",
            json={
                "message": "解释 PCI 指标",
                "mapContext": {"mode": "ROUTE", "routeCode": "Y016140727", "year": 2026},
                "options": {"topK": 3, "traceId": "governance-plan-test"},
            },
        )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("knowledge.metric_explain", body["capabilityId"])
        self.assertEqual(["knowledge.retrieve"], [item["toolName"] for item in body["toolPlan"]])


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
PYTHONPATH=srmp-ai-orchestrator srmp-ai-orchestrator/.venv/bin/python -m unittest srmp-ai-orchestrator.tests.test_governance_api -v
```

Expected: endpoints return 404.

- [ ] **Step 3: Add Runtime governance endpoints**

Add:

```python
@app.get("/api/srmp/langgraph/governance/capabilities")
async def governance_capabilities() -> dict: ...

@app.get("/api/srmp/langgraph/governance/tools")
async def governance_tools() -> dict: ...

@app.get("/api/srmp/langgraph/governance/policies/validate")
async def governance_validate() -> dict: ...

@app.post("/api/srmp/langgraph/governance/plan-simulate")
async def governance_plan_simulate(request: MapAiAgentRequest, ... ) -> dict: ...
```

`plan-simulate` should call `workflow.plan` and flatten `capabilityId`, `capability`, `toolPlan`, `sourceHints`, `warnings`.

- [ ] **Step 4: Add Java proxy endpoints**

In `AgentOrchestratorOpsController`, add:

```java
@GetMapping("/governance/capabilities")
public R<Object> governanceCapabilities() {
    return R.ok(remoteGet("/api/srmp/langgraph/governance/capabilities"));
}

@GetMapping("/governance/tools")
public R<Object> governanceTools() {
    return R.ok(remoteGet("/api/srmp/langgraph/governance/tools"));
}

@GetMapping("/governance/policies/validate")
public R<Object> governanceValidate() {
    return R.ok(remoteGet("/api/srmp/langgraph/governance/policies/validate"));
}

@PostMapping("/governance/plan-simulate")
public R<Object> governancePlanSimulate(@RequestBody(required = false) Map<String, Object> body) {
    return R.ok(remotePost("/api/srmp/langgraph/governance/plan-simulate", body == null ? new LinkedHashMap<String, Object>() : body));
}
```

- [ ] **Step 5: Run tests**

Run:

```bash
PYTHONPATH=srmp-ai-orchestrator srmp-ai-orchestrator/.venv/bin/python -m unittest srmp-ai-orchestrator.tests.test_governance_api -v
```

Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add srmp-ai-orchestrator/app/main.py srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java srmp-ai-orchestrator/tests/test_governance_api.py
git commit -m "feat: expose ai governance APIs"
```

### Task 4: Read-Only Governance Page

**Files:**
- Modify: `srmp-web-ui/src/api/orchestrator.ts`
- Modify: `srmp-web-ui/src/router/index.ts`
- Modify: `srmp-web-ui/src/views/agent/components/AgentSidebar.vue`
- Create: `srmp-web-ui/src/views/agent/AiGovernancePage.vue`

- [ ] **Step 1: Add frontend API helpers**

Add to `orchestrator.ts`:

```ts
export function getAiGovernanceCapabilities(): Promise<Record<string, any>> {
  return aiRequest.get('/api/agent/orchestrator/ops/governance/capabilities')
}

export function getAiGovernanceTools(): Promise<Record<string, any>> {
  return aiRequest.get('/api/agent/orchestrator/ops/governance/tools')
}

export function validateAiGovernancePolicies(): Promise<Record<string, any>> {
  return aiRequest.get('/api/agent/orchestrator/ops/governance/policies/validate')
}

export function simulateAiGovernancePlan(data: OrchestratorPlanRequest): Promise<Record<string, any>> {
  return aiRequest.post('/api/agent/orchestrator/ops/governance/plan-simulate', data || {})
}
```

- [ ] **Step 2: Add route and sidebar link**

Import `AiGovernancePage` and add route path `ai-governance` with title `AI 能力治理`. Add a sidebar link near LangGraph 编排.

- [ ] **Step 3: Create read-only governance page**

`AiGovernancePage.vue` should use `AgentPageShell` and show:

- top metrics: capability count, tool count, validation status, config version.
- tabs: 能力矩阵, 工具目录, 编排策略, Plan 模拟器.
- plan form fields: message, action, mode, objectType, routeCode, year.
- plan result summary: capabilityId, intent, matchedRules, planned tools, prohibited tools, warnings.

- [ ] **Step 4: Run frontend build**

Run:

```bash
npm --prefix srmp-web-ui run build
```

Expected: build succeeds.

- [ ] **Step 5: Commit**

```bash
git add srmp-web-ui/src/api/orchestrator.ts srmp-web-ui/src/router/index.ts srmp-web-ui/src/views/agent/components/AgentSidebar.vue srmp-web-ui/src/views/agent/AiGovernancePage.vue
git commit -m "feat: add ai governance console"
```

### Task 5: End-To-End Verification

**Files:**
- No new files unless verification reveals defects.

- [ ] **Step 1: Run orchestrator tests**

Run:

```bash
PYTHONPATH=srmp-ai-orchestrator srmp-ai-orchestrator/.venv/bin/python -m unittest discover -s srmp-ai-orchestrator/tests -v
```

Expected: all tests pass.

- [ ] **Step 2: Rebuild only orchestrator service**

Run:

```bash
docker compose --env-file /Users/cxp/.codex/worktrees/d1e1/srmp/.env.dev -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.langgraph.yml -f docker-compose.dev.yml up -d --build --no-deps srmp-ai-orchestrator
```

Expected: only `srmp-ai-orchestrator` is recreated.

- [ ] **Step 3: Verify live governance endpoints**

Run:

```bash
curl -s http://127.0.0.1:18080/api/srmp/langgraph/governance/capabilities | head -c 2000
curl -s -X POST http://127.0.0.1:8080/api/agent/orchestrator/ops/governance/plan-simulate -H 'Content-Type: application/json' -d '{"message":"解释 PCI 指标","mapContext":{"mode":"ROUTE","routeCode":"Y016140727","year":2026},"options":{"topK":3,"traceId":"governance-live-pci"}}' | head -c 4000
```

Expected: capability `knowledge.metric_explain` and tool plan `knowledge.retrieve`.

- [ ] **Step 4: Verify frontend route**

Open `http://localhost:5174/agent/ai-governance` and confirm:

- ability matrix loads.
- tool directory loads.
- plan simulator for `解释 PCI 指标` shows `knowledge.metric_explain` and only `knowledge.retrieve`.

- [ ] **Step 5: Final status**

Run:

```bash
git status --short
git log --oneline -8
```

Expected: only unrelated pre-existing dirty files remain; latest commits are governance implementation commits.
