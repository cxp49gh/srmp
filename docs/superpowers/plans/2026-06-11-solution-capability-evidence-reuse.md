# Solution Capability Evidence Reuse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split solution generation into concrete business capabilities and reuse recent analysis evidence when a user generates a solution immediately after analysis.

**Architecture:** Keep one shared `solution_generation_graph` and one `solution.generateDraft` tool. Make governance return concrete solution child capabilities, then add a short-lived evidence snapshot store that analysis writes and generation reads when scope fingerprints match.

**Tech Stack:** Python 3.9/FastAPI/Pydantic, Java tool gateway through existing HTTP bridge, Vue 3 + Element Plus, Python `unittest`, existing one-map live E2E script.

---

## File Structure

- Modify `srmp-ai-orchestrator/app/governance_data/capabilities.json`: replace the single user-facing `solution.generate` capability with concrete child capabilities and keep a disabled or grouping-only parent entry.
- Modify `srmp-ai-orchestrator/app/governance.py`: normalize `family`, `pipeline`, `solutionType`, `templateExpectation`, and trigger `solutionTypes`; match solution capabilities using `actionInput.solutionType`.
- Modify `srmp-ai-orchestrator/app/planner.py`: allow solution capabilities to drive evidence tool planning while still filtering `solution.generateDraft` out of evidence collection.
- Modify `srmp-ai-orchestrator/app/map_agent_run.py`: pass `actionInput` into `MapAiAgentRequest`, enrich `answerMeta`/trace with child capability metadata, create analysis evidence snapshots, and reuse snapshots during generation.
- Create `srmp-ai-orchestrator/app/evidence_snapshot.py`: deterministic scope fingerprinting and short-lived in-memory evidence snapshot store.
- Modify `srmp-ai-orchestrator/app/main.py`: instantiate and wire the shared evidence snapshot store.
- Modify `srmp-ai-orchestrator/app/map_agent_e2e_acceptance.py`: expect concrete solution capability IDs and validate evidence reuse status for replay cases.
- Modify `srmp-ai-orchestrator/tests/test_governance_registry.py`: assert solution child capability matching.
- Modify `srmp-ai-orchestrator/tests/test_governance_api.py`: assert policy coverage and plan simulation for solution child capabilities.
- Create `srmp-ai-orchestrator/tests/test_evidence_snapshot.py`: unit-test fingerprinting, TTL, and mismatch behavior.
- Modify `srmp-ai-orchestrator/tests/test_map_agent_solution_evidence.py`: assert direct generation still collects evidence and reuse generation skips duplicate evidence tools when scope matches.
- Modify `srmp-web-ui/src/api/agent.ts`: add evidence snapshot fields to map agent request/response types.
- Modify `srmp-web-ui/src/views/gis/components/AgentChatFloat.vue`: retain the latest analysis snapshot for the active scope and pass it to generation.
- Modify `srmp-web-ui/src/views/gis/components/map-ai/MapAiSuggestedActions.vue`: remove remaining customer-facing “生成对象方案” fallback for known map object types.
- Modify `srmp-web-ui/src/views/agent/components/aiExecution.ts`: show capability family and evidence reuse status in admin execution diagnostics.
- Modify `docs/phase52-map-ai-agent-e2e-acceptance.md`: document concrete solution capabilities and evidence reuse cases.

---

### Task 1: Match Concrete Solution Capabilities

**Files:**
- Modify: `srmp-ai-orchestrator/app/governance.py`
- Modify: `srmp-ai-orchestrator/app/map_agent_run.py`
- Modify: `srmp-ai-orchestrator/app/governance_data/capabilities.json`
- Test: `srmp-ai-orchestrator/tests/test_governance_registry.py`

- [ ] **Step 1: Write failing governance tests for solution child matching**

Append these tests to `srmp-ai-orchestrator/tests/test_governance_registry.py`:

```python
    def test_route_report_matches_concrete_solution_capability(self):
        from app.governance import resolve_capability
        from app.schemas import MapAiAgentRequest, MapAiContext

        request = MapAiAgentRequest(
            action="GENERATE_ROUTE_REPORT",
            message="生成当前路线养护报告",
            mapContext=MapAiContext(mode="ROUTE", routeCode="Y016140727", year=2026),
            actionInput={"solutionType": "ROUTE_REPORT", "routeCode": "Y016140727"},
            options={"action": "GENERATE_ROUTE_REPORT"},
        )

        match = resolve_capability(
            request,
            fallback_intent="SOLUTION_GENERATE",
            intent_detail={"action": "GENERATE_ROUTE_REPORT"},
        )

        self.assertEqual("solution.route_report", match["capabilityId"])
        self.assertEqual("solution.generate", match["family"])
        self.assertEqual("solution_generate", match["pipeline"])
        self.assertEqual("ROUTE_REPORT", match["solutionType"])
        self.assertEqual("route_report_default", match["templateExpectation"]["templateCode"])

    def test_disease_review_and_treatment_are_separate_solution_capabilities(self):
        from app.governance import resolve_capability
        from app.schemas import MapAiAgentRequest, MapAiContext

        base_context = MapAiContext(
            mode="OBJECT",
            routeCode="Y016140727",
            year=2026,
            mapObject={"objectType": "DISEASE", "objectId": "disease-1", "routeCode": "Y016140727"},
        )
        review = resolve_capability(
            MapAiAgentRequest(
                action="GENERATE_OBJECT_SOLUTION",
                message="生成当前病害复核意见",
                mapContext=base_context,
                actionInput={"solutionType": "DISEASE_REVIEW"},
                options={"action": "GENERATE_OBJECT_SOLUTION"},
            ),
            fallback_intent="SOLUTION_GENERATE",
            intent_detail={"action": "GENERATE_OBJECT_SOLUTION", "objectType": "DISEASE"},
        )
        treatment = resolve_capability(
            MapAiAgentRequest(
                action="GENERATE_OBJECT_SOLUTION",
                message="生成当前病害处置建议",
                mapContext=base_context,
                actionInput={"solutionType": "DISEASE_TREATMENT"},
                options={"action": "GENERATE_OBJECT_SOLUTION"},
            ),
            fallback_intent="SOLUTION_GENERATE",
            intent_detail={"action": "GENERATE_OBJECT_SOLUTION", "objectType": "DISEASE"},
        )

        self.assertEqual("solution.disease_review", review["capabilityId"])
        self.assertEqual("solution.disease_treatment", treatment["capabilityId"])
```

- [ ] **Step 2: Run the focused tests and verify failure**

Run:

```bash
PYTHONPATH=. ./.venv/bin/python -m unittest tests.test_governance_registry -v
```

Expected: failures showing `solution.generate` is still matched, or missing `family`, `pipeline`, `solutionType`, and `templateExpectation`.

- [ ] **Step 3: Pass `actionInput` into governance requests**

In `srmp-ai-orchestrator/app/map_agent_run.py`, replace `_to_agent_request` with:

```python
    def _to_agent_request(self, request: MapAgentRunRequest, action: str) -> MapAiAgentRequest:
        options = dict(request.options or {})
        options["action"] = action
        return MapAiAgentRequest(
            message=request.message,
            action=action,
            mapContext=request.mapContext,
            actionInput=dict(request.actionInput or {}),
            options=options,
        )
```

- [ ] **Step 4: Normalize solution capability metadata and triggers**

In `srmp-ai-orchestrator/app/governance.py`, update `_normalize_trigger_block` to include solution types:

```python
def _normalize_trigger_block(value: Dict[str, Any]) -> Dict[str, List[str]]:
    return {
        "actions": [item.upper() for item in _string_list(value.get("actions"))],
        "modes": [item.upper() for item in _string_list(value.get("modes"))],
        "objectTypes": [normalize_object_type(item) for item in _string_list(value.get("objectTypes"))],
        "solutionTypes": [item.upper() for item in _string_list(value.get("solutionTypes"))],
        "includeKeywords": _string_list(value.get("includeKeywords")),
        "questionKeywords": _string_list(value.get("questionKeywords")),
    }
```

In `normalize_capability`, keep child metadata:

```python
    data["family"] = str(data.get("family") or "").strip()
    data["pipeline"] = str(data.get("pipeline") or "").strip()
    data["solutionType"] = str(data.get("solutionType") or "").strip().upper()
    data["templateExpectation"] = dict(data.get("templateExpectation") or {})
```

In `capability_match`, return the metadata:

```python
        "family": capability.get("family") or "",
        "pipeline": capability.get("pipeline") or "",
        "solutionType": capability.get("solutionType") or "",
        "templateExpectation": capability.get("templateExpectation") or {},
```

In `_capability_summary`, include the same fields:

```python
        "family": capability.get("family") or "",
        "pipeline": capability.get("pipeline") or "",
        "solutionType": capability.get("solutionType") or "",
        "templateExpectation": capability.get("templateExpectation") or {},
```

- [ ] **Step 5: Match trigger solution types**

In `_match_capability`, after `object_type` is computed, add:

```python
    action_input = request.actionInput if isinstance(request.actionInput, dict) else {}
    solution_type = str(action_input.get("solutionType") or action_input.get("solution_type") or "").strip().upper()
```

After the object type block, add:

```python
    solution_types = triggers.get("solutionTypes") or []
    if solution_types and solution_type in solution_types:
        score += 200
        concrete_match = True
        rules.append("solutionType:" + solution_type)
    elif solution_types:
        return 0, [], ""
```

- [ ] **Step 6: Replace the single solution capability with concrete child capabilities**

In `srmp-ai-orchestrator/app/governance_data/capabilities.json`, replace the current `solution.generate` object with these seven entries:

```json
{
  "id": "solution.generate",
  "name": "方案生成",
  "category": "SOLUTION_FAMILY",
  "enabled": false,
  "priority": 0,
  "intent": "SOLUTION_GENERATE",
  "legacyIntents": [],
  "triggers": {},
  "contextPolicy": {"contextUsage": "GROUP_ONLY"},
  "toolPolicy": {"required": [], "optional": [], "adaptive": [], "prohibited": []},
  "examples": []
},
{
  "id": "solution.route_report",
  "name": "生成路线养护报告",
  "category": "SOLUTION",
  "family": "solution.generate",
  "pipeline": "solution_generate",
  "solutionType": "ROUTE_REPORT",
  "enabled": true,
  "priority": 95,
  "intent": "SOLUTION_GENERATE",
  "legacyIntents": ["SOLUTION_GENERATE"],
  "triggers": {"actions": ["GENERATE_ROUTE_REPORT"], "modes": ["ROUTE"], "solutionTypes": ["ROUTE_REPORT"]},
  "contextPolicy": {"contextUsage": "ROUTE_REQUIRED"},
  "toolPolicy": {
    "required": ["gis.queryRegionSummary", "gis.queryAssessmentResults", "gis.queryDiseases", "solution.generateDraft"],
    "optional": ["knowledge.retrieve"],
    "adaptive": ["knowledge.retrieve"],
    "prohibited": []
  },
  "templateExpectation": {"templateCode": "route_report_default"},
  "examples": [
    {
      "id": "solution.route_report.route",
      "name": "生成路线养护报告",
      "request": {
        "action": "GENERATE_ROUTE_REPORT",
        "message": "生成当前路线养护报告",
        "mapContext": {"mode": "ROUTE", "routeCode": "Y016140727", "year": 2026},
        "actionInput": {"solutionType": "ROUTE_REPORT", "routeCode": "Y016140727"},
        "options": {"useKnowledge": true}
      },
      "expect": {
        "capabilityId": "solution.route_report",
        "requiredTools": ["gis.queryRegionSummary", "gis.queryAssessmentResults", "gis.queryDiseases", "solution.generateDraft"]
      }
    }
  ]
},
{
  "id": "solution.section_plan",
  "name": "生成路段养护计划",
  "category": "SOLUTION",
  "family": "solution.generate",
  "pipeline": "solution_generate",
  "solutionType": "SECTION_PLAN",
  "enabled": true,
  "priority": 94,
  "intent": "SOLUTION_GENERATE",
  "legacyIntents": ["SOLUTION_GENERATE"],
  "triggers": {"actions": ["GENERATE_OBJECT_SOLUTION"], "objectTypes": ["ROAD_SECTION"], "solutionTypes": ["SECTION_PLAN"]},
  "contextPolicy": {"contextUsage": "OBJECT_REQUIRED"},
  "toolPolicy": {
    "required": ["gis.queryAssessmentResults", "gis.queryDiseases", "gis.queryDiseasesByStakeRange", "solution.generateDraft"],
    "optional": ["knowledge.retrieve"],
    "adaptive": ["knowledge.retrieve"],
    "prohibited": []
  },
  "templateExpectation": {"templateCode": "map_object_section_plan_default"},
  "examples": []
},
{
  "id": "solution.disease_treatment",
  "name": "生成病害处置建议",
  "category": "SOLUTION",
  "family": "solution.generate",
  "pipeline": "solution_generate",
  "solutionType": "DISEASE_TREATMENT",
  "enabled": true,
  "priority": 94,
  "intent": "SOLUTION_GENERATE",
  "legacyIntents": ["SOLUTION_GENERATE"],
  "triggers": {"actions": ["GENERATE_OBJECT_SOLUTION"], "objectTypes": ["DISEASE"], "solutionTypes": ["DISEASE_TREATMENT"]},
  "contextPolicy": {"contextUsage": "OBJECT_REQUIRED"},
  "toolPolicy": {
    "required": ["gis.queryNearbyObjects", "solution.generateDraft"],
    "optional": ["knowledge.retrieve"],
    "adaptive": ["knowledge.retrieve"],
    "prohibited": ["gis.queryRegionSummary"]
  },
  "templateExpectation": {"solutionType": "DISEASE_TREATMENT"},
  "examples": []
},
{
  "id": "solution.disease_review",
  "name": "生成病害复核意见",
  "category": "SOLUTION",
  "family": "solution.generate",
  "pipeline": "solution_generate",
  "solutionType": "DISEASE_REVIEW",
  "enabled": true,
  "priority": 95,
  "intent": "SOLUTION_GENERATE",
  "legacyIntents": ["SOLUTION_GENERATE"],
  "triggers": {"actions": ["GENERATE_OBJECT_SOLUTION"], "objectTypes": ["DISEASE"], "solutionTypes": ["DISEASE_REVIEW"]},
  "contextPolicy": {"contextUsage": "OBJECT_REQUIRED"},
  "toolPolicy": {
    "required": ["gis.queryNearbyObjects", "solution.generateDraft"],
    "optional": ["knowledge.retrieve"],
    "adaptive": ["knowledge.retrieve"],
    "prohibited": ["gis.queryRegionSummary"]
  },
  "templateExpectation": {"templateCode": "map_object_disease_review_default"},
  "examples": []
},
{
  "id": "solution.assessment_advice",
  "name": "生成评定养护建议",
  "category": "SOLUTION",
  "family": "solution.generate",
  "pipeline": "solution_generate",
  "solutionType": "EVALUATION_UNIT_ADVICE",
  "enabled": true,
  "priority": 94,
  "intent": "SOLUTION_GENERATE",
  "legacyIntents": ["SOLUTION_GENERATE"],
  "triggers": {"actions": ["GENERATE_OBJECT_SOLUTION"], "objectTypes": ["ASSESSMENT_RESULT"], "solutionTypes": ["EVALUATION_UNIT_ADVICE", "LOW_SCORE_TREATMENT"]},
  "contextPolicy": {"contextUsage": "OBJECT_REQUIRED"},
  "toolPolicy": {
    "required": ["gis.queryAssessmentResults", "gis.queryDiseasesByStakeRange", "solution.generateDraft"],
    "optional": ["knowledge.retrieve"],
    "adaptive": ["knowledge.retrieve"],
    "prohibited": []
  },
  "templateExpectation": {"templateCode": "map_object_evaluation_unit_advice_default"},
  "examples": []
},
{
  "id": "solution.region_advice",
  "name": "生成区域养护建议",
  "category": "SOLUTION",
  "family": "solution.generate",
  "pipeline": "solution_generate",
  "solutionType": "REGION_MAINTENANCE_SUGGESTION",
  "enabled": true,
  "priority": 95,
  "intent": "SOLUTION_GENERATE",
  "legacyIntents": ["SOLUTION_GENERATE"],
  "triggers": {"actions": ["GENERATE_REGION_SOLUTION"], "modes": ["REGION", "BOX", "POLYGON", "SELECTION"], "solutionTypes": ["REGION_MAINTENANCE_SUGGESTION"]},
  "contextPolicy": {"contextUsage": "REGION_REQUIRED"},
  "toolPolicy": {
    "required": ["gis.queryRegionSummary", "solution.generateDraft"],
    "optional": ["knowledge.retrieve"],
    "adaptive": ["knowledge.retrieve"],
    "prohibited": []
  },
  "templateExpectation": {"templateCode": "map_region_maintenance_advice_default"},
  "examples": []
}
```

- [ ] **Step 7: Run focused governance tests**

Run:

```bash
PYTHONPATH=. ./.venv/bin/python -m unittest tests.test_governance_registry -v
```

Expected: all tests pass, including concrete solution capability matching.

- [ ] **Step 8: Commit Task 1**

Run:

```bash
git add srmp-ai-orchestrator/app/governance.py srmp-ai-orchestrator/app/map_agent_run.py srmp-ai-orchestrator/app/governance_data/capabilities.json srmp-ai-orchestrator/tests/test_governance_registry.py
git commit -m "feat(agent): split solution capabilities"
```

---

### Task 2: Plan Solution Evidence Through Capability Policies

**Files:**
- Modify: `srmp-ai-orchestrator/app/planner.py`
- Modify: `srmp-ai-orchestrator/tests/test_governance_api.py`
- Test: `srmp-ai-orchestrator/tests/test_governance_api.py`

- [ ] **Step 1: Add failing API tests for solution plan simulation**

Append this test to `srmp-ai-orchestrator/tests/test_governance_api.py`:

```python
    def test_plan_simulate_returns_concrete_section_solution_tools(self):
        client = TestClient(app)

        response = client.post(
            "/api/srmp/langgraph/governance/plan-simulate",
            json={
                "action": "GENERATE_OBJECT_SOLUTION",
                "message": "生成当前路段养护计划",
                "mapContext": {
                    "mode": "OBJECT",
                    "routeCode": "Y016140727",
                    "year": 2026,
                    "mapObject": {
                        "objectType": "ROAD_SECTION",
                        "objectId": "section-1",
                        "routeCode": "Y016140727",
                        "startStake": 0,
                        "endStake": 14.072
                    }
                },
                "actionInput": {"solutionType": "SECTION_PLAN"},
                "options": {"useKnowledge": True, "traceId": "governance-section-solution-test"}
            },
        )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("solution.section_plan", body["capabilityId"])
        self.assertEqual(
            ["gis.queryAssessmentResults", "gis.queryDiseases", "gis.queryDiseasesByStakeRange", "solution.generateDraft", "knowledge.retrieve"],
            [item["toolName"] for item in body["toolPlan"]],
        )
        self.assertEqual("PASS", body["policyStatus"])
```

- [ ] **Step 2: Run the test and verify failure**

Run:

```bash
PYTHONPATH=. ./.venv/bin/python -m unittest tests.test_governance_api.GovernanceApiTest.test_plan_simulate_returns_concrete_section_solution_tools -v
```

Expected: failure because solution capability policies are not used by `plan_tools`.

- [ ] **Step 3: Allow solution capabilities to use policy planning**

In `srmp-ai-orchestrator/app/planner.py`, replace `should_use_capability_policy` with:

```python
def should_use_capability_policy(capability: Optional[Dict[str, Any]], intent: str) -> bool:
    if not isinstance(capability, dict):
        return False
    capability_id = str(capability.get("capabilityId") or "")
    category = str(capability.get("category") or "").upper()
    if not capability_id or capability_id.startswith("legacy."):
        return False
    return category in {"KNOWLEDGE", "MAP_ANALYSIS", "SOLUTION"}
```

- [ ] **Step 4: Keep solution draft as planned but not evidence-collected**

Verify `_collect_solution_evidence` in `srmp-ai-orchestrator/app/map_agent_run.py` still filters:

```python
        calls = [
            call for call in plan_tools(agent_request, "SOLUTION_GENERATE", {}, capability=capability)
            if call.toolName not in {"solution.generateDraft", "template.match"}
        ]
```

If the existing call omits `capability=capability`, change it to the snippet above.

- [ ] **Step 5: Run governance API tests**

Run:

```bash
PYTHONPATH=. ./.venv/bin/python -m unittest tests.test_governance_api -v
```

Expected: all governance API tests pass.

- [ ] **Step 6: Commit Task 2**

Run:

```bash
git add srmp-ai-orchestrator/app/planner.py srmp-ai-orchestrator/app/map_agent_run.py srmp-ai-orchestrator/tests/test_governance_api.py
git commit -m "feat(agent): govern solution evidence planning"
```

---

### Task 3: Update Live E2E To Expect Concrete Solution Capabilities

**Files:**
- Modify: `srmp-ai-orchestrator/app/map_agent_e2e_acceptance.py`
- Modify: `srmp-ai-orchestrator/tests/test_map_agent_e2e_acceptance.py`
- Modify: `docs/phase52-map-ai-agent-e2e-acceptance.md`

- [ ] **Step 1: Update failing e2e unit expectations**

In `srmp-ai-orchestrator/tests/test_map_agent_e2e_acceptance.py`, add:

```python
    def test_solution_cases_expect_concrete_capabilities(self):
        samples = {
            "projectId": "project-1",
            "route": {"objectType": "ROAD_ROUTE", "objectId": "route-1", "routeCode": "Y016140727"},
            "section": {"objectType": "ROAD_SECTION", "objectId": "section-1", "routeCode": "Y016140727", "startStake": 0, "endStake": 1},
            "disease": {"objectType": "DISEASE", "objectId": "disease-1", "routeCode": "Y016140727", "startStake": 0.2, "endStake": 0.2},
            "assessment": {"objectType": "ASSESSMENT_RESULT", "objectId": "assessment-1", "routeCode": "Y016140727", "year": 2026, "startStake": 0, "endStake": 1},
            "geometry": {"type": "Polygon", "coordinates": [[[112.1, 37.1], [112.2, 37.1], [112.2, 37.2], [112.1, 37.2], [112.1, 37.1]]]},
        }

        cases = {case.case_id: case for case in build_acceptance_cases(samples, require_ai=True)}

        self.assertEqual("solution.route_report", cases["solution.route_report"].expected_capability)
        self.assertEqual("solution.section_plan", cases["solution.section_plan"].expected_capability)
        self.assertEqual("solution.disease_review", cases["solution.disease_review"].expected_capability)
        self.assertEqual("solution.assessment_advice", cases["solution.assessment_advice"].expected_capability)
        self.assertEqual("solution.region_advice", cases["solution.region_advice"].expected_capability)
```

- [ ] **Step 2: Run the e2e unit test and verify failure**

Run:

```bash
PYTHONPATH=. ./.venv/bin/python tests/test_map_agent_e2e_acceptance.py
```

Expected: failure because existing solution cases expect `solution.generate`.

- [ ] **Step 3: Update expected capabilities**

In `srmp-ai-orchestrator/app/map_agent_e2e_acceptance.py`, change generation cases:

```python
expected_capability="solution.route_report"
expected_capability="solution.section_plan"
expected_capability="solution.disease_review"
expected_capability="solution.assessment_advice"
expected_capability="solution.region_advice"
```

Keep `required_tools` unchanged for each case.

- [ ] **Step 4: Document concrete capability IDs**

In `docs/phase52-map-ai-agent-e2e-acceptance.md`, update the generation rows so expected capability is:

```markdown
| `solution.route_report` | 生成路线养护报告 | `solution.route_report` | 路线统计、评定、病害、知识库、`solution.generateDraft` | 模板 `route_report_default`，不得兜底 |
| `solution.section_plan` | 生成路段养护计划 | `solution.section_plan` | 路段评定、病害、桩号病害、知识库、`solution.generateDraft` | 模板 `map_object_section_plan_default`，不得兜底 |
| `solution.disease_review` | 生成病害复核意见 | `solution.disease_review` | 周边对象、知识库、`solution.generateDraft` | 模板 `map_object_disease_review_default`，不得兜底 |
| `solution.assessment_advice` | 生成评定处置建议 | `solution.assessment_advice` | 评定、桩号病害、知识库、`solution.generateDraft` | 模板 `map_object_evaluation_unit_advice_default`，不得兜底 |
| `solution.region_advice` | 生成区域养护建议 | `solution.region_advice` | 区域统计、知识库、`solution.generateDraft` | 模板 `map_region_maintenance_advice_default`，不得出现 `routeCode` 变量缺失 |
```

- [ ] **Step 5: Run e2e unit tests**

Run:

```bash
PYTHONPATH=. ./.venv/bin/python tests/test_map_agent_e2e_acceptance.py
```

Expected: all tests pass.

- [ ] **Step 6: Commit Task 3**

Run:

```bash
git add srmp-ai-orchestrator/app/map_agent_e2e_acceptance.py srmp-ai-orchestrator/tests/test_map_agent_e2e_acceptance.py docs/phase52-map-ai-agent-e2e-acceptance.md
git commit -m "test(agent): expect concrete solution capabilities"
```

---

### Task 4: Add Evidence Snapshot Store

**Files:**
- Create: `srmp-ai-orchestrator/app/evidence_snapshot.py`
- Test: `srmp-ai-orchestrator/tests/test_evidence_snapshot.py`

- [ ] **Step 1: Write failing tests for fingerprint and TTL behavior**

Create `srmp-ai-orchestrator/tests/test_evidence_snapshot.py`:

```python
import time
import unittest

from app.evidence_snapshot import EvidenceSnapshotStore, build_scope_fingerprint


class EvidenceSnapshotTest(unittest.TestCase):
    def test_scope_fingerprint_is_stable_for_equivalent_context(self):
        first = {
            "mode": "OBJECT",
            "routeCode": "Y016140727",
            "mapObject": {"objectType": "ROAD_SECTION", "objectId": "section-1", "startStake": 0, "endStake": 14.072},
            "extra": {"rawContext": {"query": {"projectId": "project-1", "indexCode": "MQI"}}},
            "selectedLayers": ["ROAD_ROUTE", "DISEASE", "ASSESSMENT_RESULT"],
        }
        second = {
            "selectedLayers": ["ROAD_ROUTE", "DISEASE", "ASSESSMENT_RESULT"],
            "extra": {"rawContext": {"query": {"indexCode": "MQI", "projectId": "project-1"}}},
            "mapObject": {"endStake": 14.072, "startStake": 0, "objectId": "section-1", "objectType": "ROAD_SECTION"},
            "routeCode": "Y016140727",
            "mode": "OBJECT",
        }

        self.assertEqual(build_scope_fingerprint(first), build_scope_fingerprint(second))

    def test_store_returns_snapshot_only_when_scope_matches_and_not_expired(self):
        store = EvidenceSnapshotStore(ttl_seconds=60)
        context = {"mode": "ROUTE", "routeCode": "Y016140727", "extra": {"rawContext": {"query": {"projectId": "project-1"}}}}
        snapshot = store.create(
            analysis_trace_id="trace-1",
            capability_id="map.route_analysis",
            map_context=context,
            tool_results=[{"toolName": "gis.queryDiseases", "success": True}],
            sources=[{"sourceType": "BUSINESS_DATA", "mapTarget": {"routeCode": "Y016140727"}}],
            evidence={"businessHitCount": 1},
            now_ms=1000,
        )

        self.assertEqual(snapshot["evidenceSnapshotId"], store.get(snapshot["evidenceSnapshotId"], context, now_ms=2000)["evidenceSnapshotId"])
        self.assertIsNone(store.get(snapshot["evidenceSnapshotId"], {"mode": "ROUTE", "routeCode": "G210"}, now_ms=2000))
        self.assertIsNone(store.get(snapshot["evidenceSnapshotId"], context, now_ms=70000))


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the tests and verify failure**

Run:

```bash
PYTHONPATH=. ./.venv/bin/python -m unittest tests.test_evidence_snapshot -v
```

Expected: import failure for `app.evidence_snapshot`.

- [ ] **Step 3: Implement snapshot store**

Create `srmp-ai-orchestrator/app/evidence_snapshot.py`:

```python
import hashlib
import json
import time
from typing import Any, Dict, List, Optional


DEFAULT_EVIDENCE_TTL_SECONDS = 1800


class EvidenceSnapshotStore:
    def __init__(self, ttl_seconds: int = DEFAULT_EVIDENCE_TTL_SECONDS, max_records: int = 200) -> None:
        self.ttl_seconds = max(1, int(ttl_seconds or DEFAULT_EVIDENCE_TTL_SECONDS))
        self.max_records = max(1, int(max_records or 200))
        self._records: Dict[str, Dict[str, Any]] = {}

    def create(
        self,
        *,
        analysis_trace_id: str,
        capability_id: str,
        map_context: Dict[str, Any],
        tool_results: List[Dict[str, Any]],
        sources: List[Dict[str, Any]],
        evidence: Dict[str, Any],
        now_ms: Optional[int] = None,
    ) -> Dict[str, Any]:
        created_at_ms = int(now_ms if now_ms is not None else time.time() * 1000)
        fingerprint = build_scope_fingerprint(map_context)
        raw_id = f"{analysis_trace_id}:{fingerprint}:{created_at_ms}"
        snapshot_id = "evs_" + hashlib.sha256(raw_id.encode("utf-8")).hexdigest()[:24]
        record = {
            "evidenceSnapshotId": snapshot_id,
            "analysisTraceId": analysis_trace_id,
            "capabilityId": capability_id,
            "scopeFingerprint": fingerprint,
            "scope": build_scope(map_context),
            "toolResults": list(tool_results or []),
            "sources": list(sources or []),
            "evidence": dict(evidence or {}),
            "createdAtMs": created_at_ms,
            "ttlSeconds": self.ttl_seconds,
        }
        self._records[snapshot_id] = record
        self._trim(created_at_ms)
        return dict(record)

    def get(self, snapshot_id: str, map_context: Dict[str, Any], now_ms: Optional[int] = None) -> Optional[Dict[str, Any]]:
        record = self._records.get(str(snapshot_id or ""))
        if not record:
            return None
        current_ms = int(now_ms if now_ms is not None else time.time() * 1000)
        if current_ms - int(record.get("createdAtMs") or 0) > self.ttl_seconds * 1000:
            return None
        if record.get("scopeFingerprint") != build_scope_fingerprint(map_context):
            return None
        return dict(record)

    def _trim(self, now_ms: int) -> None:
        expired = [
            key
            for key, value in self._records.items()
            if now_ms - int(value.get("createdAtMs") or 0) > self.ttl_seconds * 1000
        ]
        for key in expired:
            self._records.pop(key, None)
        if len(self._records) <= self.max_records:
            return
        ordered = sorted(self._records.items(), key=lambda item: int(item[1].get("createdAtMs") or 0))
        for key, _value in ordered[: len(self._records) - self.max_records]:
            self._records.pop(key, None)


def build_scope_fingerprint(map_context: Dict[str, Any]) -> str:
    canonical = json.dumps(build_scope(map_context), ensure_ascii=True, sort_keys=True, separators=(",", ":"))
    return "sha256:" + hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:16]


def build_scope(map_context: Dict[str, Any]) -> Dict[str, Any]:
    ctx = map_context if isinstance(map_context, dict) else {}
    extra = ctx.get("extra") if isinstance(ctx.get("extra"), dict) else {}
    raw_context = extra.get("rawContext") if isinstance(extra.get("rawContext"), dict) else {}
    query = raw_context.get("query") if isinstance(raw_context.get("query"), dict) else {}
    obj = ctx.get("mapObject") if isinstance(ctx.get("mapObject"), dict) else {}
    geometry = ctx.get("geometry") or ctx.get("regionGeometry")
    return compact({
        "projectId": first_present(extra.get("projectId"), query.get("projectId"), query.get("project_id")),
        "mode": ctx.get("mode"),
        "objectType": first_present(obj.get("objectType"), obj.get("object_type")),
        "objectId": first_present(obj.get("objectId"), obj.get("object_id"), obj.get("id")),
        "routeCode": first_present(obj.get("routeCode"), obj.get("route_code"), ctx.get("routeCode"), ctx.get("route_code")),
        "startStake": first_present(obj.get("startStake"), obj.get("start_stake"), obj.get("stakeStart"), obj.get("stake_start")),
        "endStake": first_present(obj.get("endStake"), obj.get("end_stake"), obj.get("stakeEnd"), obj.get("stake_end")),
        "geometry": geometry,
        "metric": first_present(query.get("indexCode"), query.get("index_code"), extra.get("indexCode"), extra.get("index_code")),
        "selectedLayers": ctx.get("selectedLayers"),
        "grade": query.get("grade"),
        "sectionTier": first_present(query.get("sectionTier"), query.get("section_tier")),
    })


def first_present(*values: Any) -> Any:
    for value in values:
        if value not in (None, "", [], {}):
            return value
    return None


def compact(value: Dict[str, Any]) -> Dict[str, Any]:
    return {key: item for key, item in value.items() if item not in (None, "", [], {})}
```

- [ ] **Step 4: Run snapshot tests**

Run:

```bash
PYTHONPATH=. ./.venv/bin/python -m unittest tests.test_evidence_snapshot -v
```

Expected: both tests pass.

- [ ] **Step 5: Commit Task 4**

Run:

```bash
git add srmp-ai-orchestrator/app/evidence_snapshot.py srmp-ai-orchestrator/tests/test_evidence_snapshot.py
git commit -m "feat(agent): add evidence snapshot store"
```

---

### Task 5: Write Analysis Snapshots And Reuse Them During Generation

**Files:**
- Modify: `srmp-ai-orchestrator/app/map_agent_run.py`
- Modify: `srmp-ai-orchestrator/app/main.py`
- Modify: `srmp-ai-orchestrator/tests/test_map_agent_solution_evidence.py`

- [ ] **Step 1: Add failing tests for snapshot creation and reuse**

Add a fake store and test to `srmp-ai-orchestrator/tests/test_map_agent_solution_evidence.py`:

```python
class FakeSnapshotStore:
    def __init__(self, snapshot=None):
        self.snapshot = snapshot
        self.created = []

    def create(self, **kwargs):
        record = {
            "evidenceSnapshotId": "evs-test",
            "analysisTraceId": kwargs.get("analysis_trace_id"),
            "scopeFingerprint": "sha256:test",
            "toolResults": kwargs.get("tool_results") or [],
            "sources": kwargs.get("sources") or [],
            "evidence": kwargs.get("evidence") or {},
        }
        self.created.append(record)
        return record

    def get(self, snapshot_id, map_context, now_ms=None):
        return self.snapshot if snapshot_id == "evs-test" else None
```

Add this async test:

```python
    async def test_solution_generation_reuses_matching_analysis_snapshot(self):
        snapshot = {
            "evidenceSnapshotId": "evs-test",
            "analysisTraceId": "analysis-trace-1",
            "scopeFingerprint": "sha256:test",
            "toolResults": [
                {"toolName": "gis.queryAssessmentResults", "success": True, "data": {"items": [{"routeCode": "Y016140727"}]}, "count": 1},
                {"toolName": "gis.queryDiseasesByStakeRange", "success": True, "data": {"items": [{"routeCode": "Y016140727"}]}, "count": 1},
            ],
            "sources": [{"sourceType": "BUSINESS_DATA", "mapTarget": {"routeCode": "Y016140727", "objectId": "section-1"}}],
            "evidence": {"toolSuccessCount": 2, "businessHitCount": 2},
        }
        gateway = FakeGateway()
        workflow = MapAgentRunWorkflow(base_workflow=None, gateway=gateway, evidence_snapshot_store=FakeSnapshotStore(snapshot))

        response = await workflow.run(
            MapAgentRunRequest(
                action="GENERATE_OBJECT_SOLUTION",
                message="生成当前路段养护计划",
                mapContext=MapAiContext(
                    mode="OBJECT",
                    routeCode="Y016140727",
                    mapObject={"objectType": "ROAD_SECTION", "objectId": "section-1", "routeCode": "Y016140727"},
                ),
                actionInput={"solutionType": "SECTION_PLAN", "evidenceSnapshotId": "evs-test"},
                options={"traceId": "generate-trace-1", "useKnowledge": True},
            ),
            tenant_id="default",
            trace_id="generate-trace-1",
        )

        executed_names = [call.toolName for call in gateway.calls]
        self.assertEqual(["solution.generateDraft"], executed_names)
        self.assertEqual("REUSED", response.answerMeta["evidenceReuseStatus"])
        self.assertEqual("analysis-trace-1", response.answerMeta["basedOnAnalysisTraceId"])
        self.assertEqual("evs-test", response.answerMeta["evidenceSnapshotId"])
```

- [ ] **Step 2: Run the focused test and verify failure**

Run:

```bash
PYTHONPATH=. ./.venv/bin/python -m unittest tests.test_map_agent_solution_evidence -v
```

Expected: failure because `MapAgentRunWorkflow` does not accept `evidence_snapshot_store` and generation executes evidence tools again.

- [ ] **Step 3: Wire snapshot store into workflow**

In `srmp-ai-orchestrator/app/map_agent_run.py`, import:

```python
from .evidence_snapshot import EvidenceSnapshotStore
```

Change the constructor:

```python
    def __init__(
        self,
        base_workflow: LangGraphWorkflow,
        gateway: JavaToolGateway,
        live_trace_store: Optional[LiveTraceStore] = None,
        evidence_snapshot_store: Optional[EvidenceSnapshotStore] = None,
    ):
        self.base_workflow = base_workflow
        self.gateway = gateway
        self.live_trace_store = live_trace_store
        self.evidence_snapshot_store = evidence_snapshot_store
```

- [ ] **Step 4: Create snapshots after analysis**

In `_run_analysis`, store the response before returning:

```python
        response = self._from_agent_response(
            request,
            agent_response,
            action,
            graph_name=graph_name_for(action),
            action_result=action_result,
        )
        self._attach_analysis_evidence_snapshot(response, resolved_trace_id=trace_id or self._trace_id(request, trace_id))
        return response
```

Add helper:

```python
    def _attach_analysis_evidence_snapshot(self, response: MapAgentRunResponse, resolved_trace_id: str) -> None:
        if not self.evidence_snapshot_store or not response.mapContext:
            return
        snapshot = self.evidence_snapshot_store.create(
            analysis_trace_id=resolved_trace_id or str((response.answerMeta or {}).get("traceId") or ""),
            capability_id=str((response.answerMeta or {}).get("capabilityId") or ""),
            map_context=response.mapContext,
            tool_results=response.toolResults or [],
            sources=response.sources or response.knowledgeSources or [],
            evidence=(response.data or {}).get("evidence") or {},
        )
        response.data["evidenceSnapshot"] = snapshot
        response.answerMeta = dict(response.answerMeta or {})
        response.answerMeta["evidenceSnapshotId"] = snapshot.get("evidenceSnapshotId")
        response.answerMeta["scopeFingerprint"] = snapshot.get("scopeFingerprint")
        response.trace = dict(response.trace or {})
        response.trace["evidenceSnapshot"] = {
            "evidenceSnapshotId": snapshot.get("evidenceSnapshotId"),
            "scopeFingerprint": snapshot.get("scopeFingerprint"),
            "ttlSeconds": snapshot.get("ttlSeconds"),
        }
```

- [ ] **Step 5: Reuse snapshots in generation evidence collection**

Add helper:

```python
    def _matching_evidence_snapshot(self, request: MapAgentRunRequest) -> Optional[Dict[str, Any]]:
        if not self.evidence_snapshot_store or not request.mapContext:
            return None
        action_input = request.actionInput or {}
        snapshot_id = action_input.get("evidenceSnapshotId") or action_input.get("evidence_snapshot_id")
        if not snapshot_id:
            return None
        return self.evidence_snapshot_store.get(str(snapshot_id), request.mapContext.model_dump(exclude_none=True))
```

At the start of `_run_solution_generation`, before `_collect_solution_evidence`, add:

```python
        snapshot = self._matching_evidence_snapshot(request)
        if snapshot:
            evidence_results = [ToolResult(**item) for item in snapshot.get("toolResults") or []]
            planned_evidence_tool_names = [item.toolName for item in evidence_results]
            blocked_tool_names = []
            evidence_reuse = {
                "status": "REUSED",
                "evidenceSnapshotId": snapshot.get("evidenceSnapshotId"),
                "basedOnAnalysisTraceId": snapshot.get("analysisTraceId"),
                "scopeFingerprint": snapshot.get("scopeFingerprint"),
                "reusedToolNames": planned_evidence_tool_names,
                "supplementalToolNames": [],
            }
        else:
            evidence_results, planned_evidence_tool_names, blocked_tool_names = await self._collect_solution_evidence(
                agent_request,
                tenant_id or "default",
                resolved_trace_id,
                capability,
            )
            evidence_reuse = {
                "status": "MISS",
                "evidenceSnapshotId": (request.actionInput or {}).get("evidenceSnapshotId"),
                "basedOnAnalysisTraceId": (request.actionInput or {}).get("basedOnAnalysisTraceId"),
                "reusedToolNames": [],
                "supplementalToolNames": planned_evidence_tool_names,
            }
```

Remove the old direct call to `_collect_solution_evidence` in that method so evidence is collected only once.

- [ ] **Step 6: Add reuse metadata to answerMeta, trace, and solution args**

After `answer_meta = dict(answer_meta)`, add:

```python
        answer_meta.update({
            "evidenceReuseStatus": evidence_reuse.get("status"),
            "evidenceSnapshotId": evidence_reuse.get("evidenceSnapshotId"),
            "basedOnAnalysisTraceId": evidence_reuse.get("basedOnAnalysisTraceId"),
            "scopeFingerprint": evidence_reuse.get("scopeFingerprint"),
        })
```

Before returning the response, add:

```python
        response.trace["evidenceReuse"] = evidence_reuse
        response.data["evidenceReuse"] = evidence_reuse
```

Change `_solution_args` signature and payload:

```python
    def _solution_args(
        self,
        request: MapAgentRunRequest,
        evidence_results: Optional[List[ToolResult]] = None,
        evidence_reuse: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        args = dict(request.actionInput or {})
        args["action"] = normalize_action(request.action)
        business_evidence = self._business_evidence_from_results(evidence_results or [])
        if business_evidence:
            args["businessEvidence"] = business_evidence
        if evidence_reuse:
            args["evidenceReuse"] = evidence_reuse
        if request.mapContext:
            args["mapContext"] = request.mapContext.model_dump(exclude_none=True)
        return args
```

Call it with:

```python
call=ToolCall(toolName="solution.generateDraft", args=self._solution_args(request, evidence_results, evidence_reuse), reason="LangGraph-first solution preview")
```

- [ ] **Step 7: Instantiate the store in app main**

In `srmp-ai-orchestrator/app/main.py`, import and instantiate:

```python
from .evidence_snapshot import EvidenceSnapshotStore

evidence_snapshot_store = EvidenceSnapshotStore()
map_agent_run_workflow = MapAgentRunWorkflow(
    base_workflow=workflow,
    gateway=gateway,
    live_trace_store=live_trace_store,
    evidence_snapshot_store=evidence_snapshot_store,
)
```

- [ ] **Step 8: Run map agent solution evidence tests**

Run:

```bash
PYTHONPATH=. ./.venv/bin/python -m unittest tests.test_map_agent_solution_evidence -v
```

Expected: all tests pass.

- [ ] **Step 9: Commit Task 5**

Run:

```bash
git add srmp-ai-orchestrator/app/map_agent_run.py srmp-ai-orchestrator/app/main.py srmp-ai-orchestrator/tests/test_map_agent_solution_evidence.py
git commit -m "feat(agent): reuse analysis evidence for solution generation"
```

---

### Task 6: Pass Evidence Snapshots From One-Map UI

**Files:**
- Modify: `srmp-web-ui/src/api/agent.ts`
- Modify: `srmp-web-ui/src/views/gis/components/AgentChatFloat.vue`
- Modify: `srmp-web-ui/src/views/gis/components/map-ai/MapAiSuggestedActions.vue`

- [ ] **Step 1: Add frontend type fields**

In `srmp-web-ui/src/api/agent.ts`, extend `MapAgentRunRequest` and `MapAgentRunResponse`:

```ts
export interface MapAgentRunRequest {
  message?: string
  action: MapAgentAction
  mapContext?: MapAiContext
  actionInput?: Record<string, any>
  options?: Record<string, any>
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
```

No new type is strictly required because snapshot fields live in `answerMeta` and `data.evidenceSnapshot`.

- [ ] **Step 2: Store latest analysis snapshot in the chat panel**

In `srmp-web-ui/src/views/gis/components/AgentChatFloat.vue`, add state near other refs:

```ts
const latestEvidenceSnapshot = ref<Record<string, any> | null>(null)
```

Add helpers:

```ts
function responseEvidenceSnapshot(response: any) {
  return response?.data?.evidenceSnapshot || response?.answerMeta?.evidenceSnapshot || null
}

function rememberAnalysisEvidenceSnapshot(action: string, response: any) {
  if (!['ANALYZE_OBJECT', 'ANALYZE_ROUTE', 'ANALYZE_REGION'].includes(String(action || '').toUpperCase())) return
  const snapshot = responseEvidenceSnapshot(response)
  if (!snapshot?.evidenceSnapshotId) return
  latestEvidenceSnapshot.value = snapshot
}

function currentEvidenceSnapshotInput() {
  const snapshot = latestEvidenceSnapshot.value
  if (!snapshot?.evidenceSnapshotId) return {}
  return {
    evidenceSnapshotId: snapshot.evidenceSnapshotId,
    basedOnAnalysisTraceId: snapshot.analysisTraceId
  }
}
```

- [ ] **Step 3: Remember snapshot after successful analysis**

In the method that handles `mapAgentRun` responses, after `normalized` is created and before pushing the assistant message, call:

```ts
rememberAnalysisEvidenceSnapshot(action, normalized)
```

Use the existing local variable name for the action if it is not called `action`.

- [ ] **Step 4: Include snapshot IDs in generation actionInput**

In the generation request builder for `GENERATE_OBJECT_SOLUTION` and `GENERATE_ROUTE_REPORT`, merge:

```ts
const snapshotInput = currentEvidenceSnapshotInput()
actionInput: Object.assign({
  objectType: activeMapObject.value?.objectType || activeMapObject.value?.object_type,
  objectId: activeMapObject.value?.objectId || activeMapObject.value?.object_id || activeMapObject.value?.id,
  routeCode: activeMapObject.value?.routeCode || activeMapObject.value?.route_code || props.context?.routeCode,
  solutionType,
  mapObject: activeMapObject.value
}, snapshotInput)
```

For route report, keep `solutionType: 'ROUTE_REPORT'` and include the same spread.

- [ ] **Step 5: Remove customer-facing generic object label fallback for known objects**

In `srmp-web-ui/src/views/gis/components/map-ai/MapAiSuggestedActions.vue`, keep:

```ts
function displayLabel(item: MapAgentSuggestedAction) {
  const action = String(item.action || '')
  if (action === 'GENERATE_OBJECT_SOLUTION') return objectSolutionLabel.value
  if (action === 'GENERATE_REGION_SOLUTION') return '生成区域养护建议'
  if (action === 'GENERATE_ROUTE_REPORT') return '生成路线养护报告'
  return item.label || action
}
```

Change the unknown fallback in `objectSolutionLabel` to:

```ts
  return '生成养护建议'
```

- [ ] **Step 6: Build frontend**

Run:

```bash
npm --prefix srmp-web-ui run build
```

Expected: build succeeds.

- [ ] **Step 7: Commit Task 6**

Run:

```bash
git add srmp-web-ui/src/api/agent.ts srmp-web-ui/src/views/gis/components/AgentChatFloat.vue srmp-web-ui/src/views/gis/components/map-ai/MapAiSuggestedActions.vue
git commit -m "feat(web): pass analysis evidence to solution generation"
```

---

### Task 7: Show Evidence Reuse In Admin Execution Diagnostics

**Files:**
- Modify: `srmp-web-ui/src/views/agent/components/aiExecution.ts`
- Test: `npm --prefix srmp-web-ui run build`

- [ ] **Step 1: Add extraction helper**

In `srmp-web-ui/src/views/agent/components/aiExecution.ts`, add:

```ts
function extractEvidenceReuse(answerMeta: Record<string, any>, data: Record<string, any>, trace: Record<string, any>) {
  const reuse = firstRecord(data.evidenceReuse, data.evidence_reuse, trace.evidenceReuse, trace.evidence_reuse)
  return {
    status: stringValue(answerMeta.evidenceReuseStatus, answerMeta.evidence_reuse_status, reuse.status),
    evidenceSnapshotId: stringValue(answerMeta.evidenceSnapshotId, answerMeta.evidence_snapshot_id, reuse.evidenceSnapshotId, reuse.evidence_snapshot_id),
    basedOnAnalysisTraceId: stringValue(answerMeta.basedOnAnalysisTraceId, answerMeta.based_on_analysis_trace_id, reuse.basedOnAnalysisTraceId, reuse.based_on_analysis_trace_id),
    scopeFingerprint: stringValue(answerMeta.scopeFingerprint, answerMeta.scope_fingerprint, reuse.scopeFingerprint, reuse.scope_fingerprint)
  }
}
```

- [ ] **Step 2: Include reuse status in summary tags**

Where summary or diagnosis tags are built, append:

```ts
const evidenceReuse = extractEvidenceReuse(answerMeta, responseData, trace)
if (evidenceReuse.status) {
  tags.push({
    label: '证据复用',
    value: evidenceReuse.status,
    type: evidenceReuse.status === 'REUSED' || evidenceReuse.status === 'PARTIAL_REUSED' ? 'success' : 'info'
  })
}
```

Use the local tag array variable name in that function. If the file uses a returned object instead of `tags`, add `evidenceReuse` to that returned object and display it in the existing drawer component where summary chips are rendered.

- [ ] **Step 3: Build frontend**

Run:

```bash
npm --prefix srmp-web-ui run build
```

Expected: build succeeds.

- [ ] **Step 4: Commit Task 7**

Run:

```bash
git add srmp-web-ui/src/views/agent/components/aiExecution.ts
git commit -m "feat(web): show evidence reuse diagnostics"
```

---

### Task 8: Full Verification And Live E2E

**Files:**
- Modify: `docs/phase52-map-ai-agent-e2e-acceptance.md`
- Test: orchestrator tests, frontend build, live e2e script

- [ ] **Step 1: Update acceptance docs**

Add this note to `docs/phase52-map-ai-agent-e2e-acceptance.md`:

```markdown
方案生成类 case 现在按具体子能力验收。直接生成必须完整查询证据；先分析再生成的 UI 链路应携带 `evidenceSnapshotId`，生成 trace 中应展示 `evidenceReuseStatus`、`basedOnAnalysisTraceId` 和 `scopeFingerprint`。
```

- [ ] **Step 2: Run all orchestrator tests**

Run:

```bash
PYTHONPATH=. ./.venv/bin/python -m unittest discover -s tests
```

Expected: all tests pass.

- [ ] **Step 3: Build frontend**

Run:

```bash
npm --prefix srmp-web-ui run build
```

Expected: build succeeds.

- [ ] **Step 4: Rebuild only app services**

Run:

```bash
./scripts/dev-rebuild-services.sh frontend srmp-ai-orchestrator
```

Expected: frontend and orchestrator rebuild and restart; local postgres/redis/minio are not started by this command.

- [ ] **Step 5: Run live one-map AI e2e**

Run:

```bash
./scripts/check-map-agent-e2e.sh --project-id d20a49cdee904299a4967e196f676c9e
```

Expected: all cases pass. Solution cases report concrete capability IDs. `followup.source_replay` still passes.

- [ ] **Step 6: Commit final docs**

Run:

```bash
git add docs/phase52-map-ai-agent-e2e-acceptance.md
git commit -m "docs(agent): document solution evidence reuse acceptance"
```

---

## Final Completion Checklist

- [ ] `git status --short` contains only unrelated local files such as `.env.dev.example`.
- [ ] Orchestrator unit tests pass.
- [ ] Frontend build passes.
- [ ] Live one-map AI e2e passes.
- [ ] Generated answers and trace no longer expose customer-facing “生成对象方案” for known objects.
- [ ] Solution generation trace uses concrete child capability IDs.
- [ ] Evidence reuse metadata is visible in `answerMeta`, `trace`, and admin diagnostics.
