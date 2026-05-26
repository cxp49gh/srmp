# AI Governance Workbench Enhancement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first editable AI governance workbench: capability-tool matrix editing, draft policy coverage, rollback draft restore, evaluation case management, and publish readiness.

**Architecture:** Keep `capabilities.json` and `tools.json` as the Phase 1 source of truth. Add draft-aware backend helpers that build an in-memory governance registry without mutating active runtime state, then expose them through Python Runtime APIs, Java ops proxy APIs, and structured Vue components that synchronize back to the existing draft JSON payload.

**Tech Stack:** Python 3.11/FastAPI/unittest, Java Spring MVC proxy controller/JUnit 4, Vue 3 + Element Plus, Node `node:test`, existing Docker Compose dev stack with `.env.dev` and `--no-deps` app service rebuilds.

---

## File Structure

- Modify `srmp-ai-orchestrator/app/governance.py`
  - Add draft registry helpers, draft policy coverage, draft plan simulation support, publish blockers that include coverage, and rollback draft snapshots.
- Modify `srmp-ai-orchestrator/app/main.py`
  - Add `POST /api/srmp/langgraph/governance/policies/coverage/draft`.
  - Enhance `POST /api/srmp/langgraph/governance/plan-simulate` to accept either the existing request shape or `{request, capabilitiesConfig, toolsConfig}`.
- Modify `srmp-ai-orchestrator/tests/test_governance_api.py`
  - Add backend API tests for draft coverage, active config isolation, publish blocking, and rollback draft restore.
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java`
  - Add Java proxy route for draft coverage.
  - Add small path helper methods where needed for testable URL encoding.
- Modify `srmp-agent/src/test/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsControllerLiveTraceTest.java`
  - Add controller path tests for new governance proxy helper paths.
- Modify `srmp-web-ui/src/api/orchestrator.ts`
  - Add `getAiGovernanceDraftPolicyCoverage`.
  - Allow `simulateAiGovernancePlan` to receive draft payloads.
- Create `srmp-web-ui/src/views/agent/components/governanceDraft.ts`
  - Pure functions for matrix rows, tool policy edits, evaluation case extraction/writeback, draft parsing, and publish blocker derivation.
- Create `srmp-web-ui/src/views/agent/components/GovernanceMatrixEditor.vue`
  - Structured capability-tool matrix editor.
- Create `srmp-web-ui/src/views/agent/components/GovernanceEvalCaseSet.vue`
  - Evaluation case table and editor drawer.
- Create `srmp-web-ui/src/views/agent/components/GovernanceDraftReviewPanel.vue`
  - Validation, coverage, diff, and publish readiness panel.
- Modify `srmp-web-ui/src/views/agent/AiGovernancePage.vue`
  - Integrate the new components while preserving raw JSON fallback.
- Create `srmp-web-ui/tests/aiGovernanceDraft.test.mjs`
  - Unit tests for pure draft helpers.
- Create `srmp-web-ui/tests/aiGovernanceWorkbench.test.mjs`
  - Static and light behavioral tests that ensure the governance page wires the new components and API calls.

## Task 1: Backend Draft Policy Coverage

**Files:**
- Modify: `srmp-ai-orchestrator/app/governance.py`
- Modify: `srmp-ai-orchestrator/app/main.py`
- Test: `srmp-ai-orchestrator/tests/test_governance_api.py`

- [ ] **Step 1: Write failing tests for draft policy coverage**

Append these tests to `GovernanceApiTest` in `srmp-ai-orchestrator/tests/test_governance_api.py`:

```python
    def test_draft_policy_coverage_uses_draft_config_without_mutating_active(self):
        client = TestClient(app)
        active = client.get("/api/srmp/langgraph/governance/config").json()
        capabilities_config = copy.deepcopy(active["capabilitiesConfig"])
        tools_config = copy.deepcopy(active["toolsConfig"])
        capability = next(item for item in capabilities_config["capabilities"] if item["id"] == "knowledge.metric_explain")
        capability["toolPolicy"]["required"] = ["gis.queryRegionSummary"]
        capability["toolPolicy"]["prohibited"] = []
        capability["examples"][0]["expect"]["exactToolNames"] = ["knowledge.retrieve"]

        response = client.post(
            "/api/srmp/langgraph/governance/policies/coverage/draft",
            json={
                "capabilitiesConfig": capabilities_config,
                "toolsConfig": tools_config,
                "caseIds": ["knowledge.metric_explain.basic"],
            },
        )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("DRAFT_POLICY_COVERAGE", body["mode"])
        self.assertEqual(1, body["caseCount"])
        self.assertEqual(1, body["failedCount"])
        self.assertEqual("knowledge.metric_explain.basic", body["cases"][0]["id"])
        self.assertIn("gis.queryRegionSummary", body["cases"][0]["actualToolNames"])

        active_coverage = client.get("/api/srmp/langgraph/governance/policies/coverage").json()
        active_case = next(item for item in active_coverage["cases"] if item["id"] == "knowledge.metric_explain.basic")
        self.assertEqual("PASS", active_case["status"])
        self.assertEqual(["knowledge.retrieve"], active_case["actualToolNames"])

    def test_draft_policy_coverage_filters_selected_cases(self):
        client = TestClient(app)
        active = client.get("/api/srmp/langgraph/governance/config").json()

        response = client.post(
            "/api/srmp/langgraph/governance/policies/coverage/draft",
            json={
                "capabilitiesConfig": active["capabilitiesConfig"],
                "toolsConfig": active["toolsConfig"],
                "caseIds": ["map.route_analysis.action"],
            },
        )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual(1, body["caseCount"])
        self.assertEqual(["map.route_analysis.action"], [item["id"] for item in body["cases"]])
```

- [ ] **Step 2: Run the backend test and verify it fails**

Run:

```bash
PYTHONPATH=srmp-ai-orchestrator srmp-ai-orchestrator/.venv/bin/python -m unittest srmp-ai-orchestrator.tests.test_governance_api.GovernanceApiTest.test_draft_policy_coverage_uses_draft_config_without_mutating_active -v
```

Expected: `404` for `/api/srmp/langgraph/governance/policies/coverage/draft`.

- [ ] **Step 3: Refactor capability resolution to support explicit registries**

In `srmp-ai-orchestrator/app/governance.py`, replace `resolve_capability` with a wrapper plus registry-aware helper:

```python
def resolve_capability(
    request: MapAiAgentRequest,
    fallback_intent: str,
    intent_detail: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    return resolve_capability_for_registry(
        registry=governance_registry(),
        request=request,
        fallback_intent=fallback_intent,
        intent_detail=intent_detail or {},
    )


def resolve_capability_for_registry(
    registry: GovernanceRegistry,
    request: MapAiAgentRequest,
    fallback_intent: str,
    intent_detail: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    candidates: List[Tuple[float, Dict[str, Any], List[str], str]] = []
    for capability in registry.capabilities:
        if not capability.get("enabled", True):
            continue
        score, rules, context_usage = _match_capability(capability, request, fallback_intent, intent_detail or {})
        if score <= 0:
            continue
        candidates.append((score, capability, rules, context_usage))

    if not candidates:
        return fallback_capability(fallback_intent, intent_detail or {})

    candidates.sort(key=lambda item: (item[0], int(item[1].get("priority") or 0)), reverse=True)
    score, capability, rules, context_usage = candidates[0]
    return capability_match(capability, score, rules, context_usage)
```

- [ ] **Step 4: Move policy case execution into `governance.py` and make it registry-aware**

In `srmp-ai-orchestrator/app/governance.py`, extend imports:

```python
from .intent import normalize_object_type, recognize_intent
from .plan_preview import enrich_tool_plan
from .planner import plan_tools
```

Then add these functions to `governance.py`. The `_run_governance_policy_case` body is the existing implementation from `main.py`, with the registry-aware capability line shown here:

```python
def governance_policy_examples_for_registry(registry: GovernanceRegistry) -> List[Dict[str, Any]]:
    examples: List[Dict[str, Any]] = []
    for capability in registry.capabilities:
        for example in capability.get("examples") or []:
            item = dict(example)
            item["capabilityId"] = capability.get("id")
            item["capabilityName"] = capability.get("name")
            item["capabilityCategory"] = capability.get("category")
            examples.append(item)
    return examples


def governance_policy_coverage_for_registry(
    registry: GovernanceRegistry,
    case_ids: Optional[List[str]] = None,
) -> Dict[str, Any]:
    selected_ids = {str(item).strip() for item in case_ids or [] if str(item or "").strip()}
    examples = governance_policy_examples_for_registry(registry)
    if selected_ids:
        examples = [example for example in examples if str(example.get("id") or "") in selected_ids]
    cases = [_run_governance_policy_case(example, registry=registry) for example in examples]
    failed = [item for item in cases if item.get("status") != "PASS"]
    return {
        "version": registry.version,
        "toolVersion": registry.tool_version,
        "caseCount": len(cases),
        "passedCount": len(cases) - len(failed),
        "failedCount": len(failed),
        "cases": cases,
    }


def governance_draft_policy_coverage(payload: Dict[str, Any]) -> Dict[str, Any]:
    capabilities_raw = dict((payload or {}).get("capabilitiesConfig") or {})
    tools_raw = dict((payload or {}).get("toolsConfig") or {})
    registry = build_governance_registry(capabilities_raw, tools_raw)
    coverage = governance_policy_coverage_for_registry(
        registry=registry,
        case_ids=_string_list((payload or {}).get("caseIds")),
    )
    coverage.update({
        "mode": "DRAFT_POLICY_COVERAGE",
        "draftId": _stable_hash({
            "capabilitiesConfig": capabilities_raw,
            "toolsConfig": tools_raw,
        })[:16],
    })
    return coverage

def _run_governance_policy_case(example: Dict[str, Any], registry: Optional[GovernanceRegistry] = None) -> Dict[str, Any]:
    request_payload = example.get("request") if isinstance(example.get("request"), dict) else {}
    expect = example.get("expect") if isinstance(example.get("expect"), dict) else {}
    request = MapAiAgentRequest(**request_payload)
    fallback_intent, detail = recognize_intent(request)
    active_registry = registry or governance_registry()
    capability = resolve_capability_for_registry(active_registry, request, fallback_intent=fallback_intent, intent_detail=detail)
    intent = capability.get("intent") or fallback_intent
    tool_plan = enrich_tool_plan(plan_tools(request, intent, detail, capability=capability))
    actual_tool_names = [str(item.get("toolName") or "") for item in tool_plan if item.get("toolName")]
    expected_capability = str(expect.get("capabilityId") or example.get("capabilityId") or "")
    required_tools = _string_list(expect.get("requiredTools"))
    prohibited_tools = _string_list(expect.get("prohibitedTools"))
    exact_tools = _string_list(expect.get("exactToolNames"))
    missing_tools = [name for name in required_tools if name not in actual_tool_names]
    prohibited_hits = [name for name in prohibited_tools if name in actual_tool_names]
    capability_mismatch = bool(expected_capability and expected_capability != capability.get("capabilityId"))
    exact_mismatch = bool(exact_tools and exact_tools != actual_tool_names)
    warnings: List[Dict[str, str]] = []
    if capability_mismatch:
        warnings.append({
            "code": "CAPABILITY_MISMATCH",
            "message": "期望能力 " + expected_capability + "，实际命中 " + str(capability.get("capabilityId") or ""),
        })
    if missing_tools:
        warnings.append({"code": "REQUIRED_TOOL_MISSING", "message": "缺少必选工具：" + ", ".join(missing_tools)})
    if prohibited_hits:
        warnings.append({"code": "PROHIBITED_TOOL_PLANNED", "message": "计划误用了禁用工具：" + ", ".join(prohibited_hits)})
    if exact_mismatch:
        warnings.append({"code": "EXACT_TOOLS_MISMATCH", "message": "计划工具与精确期望不一致。"})
    status = "PASS" if not warnings else "FAIL"
    return {
        "id": str(example.get("id") or ""),
        "name": str(example.get("name") or example.get("id") or ""),
        "status": status,
        "expectedCapabilityId": expected_capability,
        "actualCapabilityId": capability.get("capabilityId"),
        "actualCapabilityName": capability.get("name"),
        "actualIntent": intent,
        "fallbackIntent": fallback_intent,
        "expectedRequiredTools": required_tools,
        "expectedProhibitedTools": prohibited_tools,
        "expectedExactToolNames": exact_tools,
        "actualToolNames": actual_tool_names,
        "missingRequiredTools": missing_tools,
        "prohibitedToolNames": prohibited_hits,
        "warnings": warnings,
        "request": request.model_dump(exclude_none=True),
        "capability": capability,
        "toolPlan": tool_plan,
    }
```

After the move, delete `_run_governance_policy_case` and the local `_string_list` helper from `main.py` because coverage execution now belongs to `governance.py`.

- [ ] **Step 5: Wire active coverage through the new helper**

In `srmp-ai-orchestrator/app/main.py`, import `governance_draft_policy_coverage`, `governance_policy_coverage_for_registry`, and `governance_registry` from `.governance`. Remove unused imports that were only needed by the deleted `_run_governance_policy_case`.

Update `_policy_coverage_payload`:

```python
def _policy_coverage_payload() -> Dict[str, Any]:
    return governance_policy_coverage_for_registry(governance_registry())
```

- [ ] **Step 6: Add the draft coverage endpoint**

In `srmp-ai-orchestrator/app/main.py`, add:

```python
@app.post("/api/srmp/langgraph/governance/policies/coverage/draft")
async def governance_policy_coverage_draft(body: Optional[Dict[str, Any]] = None) -> dict:
    return governance_draft_policy_coverage(body or {})
```

- [ ] **Step 7: Run the backend coverage tests**

Run:

```bash
PYTHONPATH=srmp-ai-orchestrator srmp-ai-orchestrator/.venv/bin/python -m unittest srmp-ai-orchestrator.tests.test_governance_api.GovernanceApiTest.test_draft_policy_coverage_uses_draft_config_without_mutating_active srmp-ai-orchestrator.tests.test_governance_api.GovernanceApiTest.test_draft_policy_coverage_filters_selected_cases -v
```

Expected: `OK`.

- [ ] **Step 8: Commit Task 1**

```bash
git add srmp-ai-orchestrator/app/governance.py srmp-ai-orchestrator/app/main.py srmp-ai-orchestrator/tests/test_governance_api.py
git commit -m "feat: add draft governance policy coverage"
```

## Task 2: Publish Blocking And Rollback Draft Restore

**Files:**
- Modify: `srmp-ai-orchestrator/app/governance.py`
- Test: `srmp-ai-orchestrator/tests/test_governance_api.py`

- [ ] **Step 1: Write failing tests for publish coverage blockers and rollback draft**

Append these tests to `GovernanceApiTest`:

```python
    def test_publish_request_blocks_failed_draft_policy_coverage(self):
        client = TestClient(app)
        active = client.get("/api/srmp/langgraph/governance/config").json()
        capabilities_config = copy.deepcopy(active["capabilitiesConfig"])
        tools_config = copy.deepcopy(active["toolsConfig"])
        capability = next(item for item in capabilities_config["capabilities"] if item["id"] == "knowledge.metric_explain")
        capability["toolPolicy"]["required"] = ["gis.queryRegionSummary"]
        capability["toolPolicy"]["prohibited"] = []
        capability["examples"][0]["expect"]["exactToolNames"] = ["knowledge.retrieve"]

        with tempfile.TemporaryDirectory() as temp_dir:
            log_path = os.path.join(temp_dir, "publish.jsonl")
            with patch.dict(os.environ, {"SRMP_AGENT_GOVERNANCE_PUBLISH_LOG_PATH": log_path}):
                response = client.post(
                    "/api/srmp/langgraph/governance/config/publish/request",
                    json={
                        "capabilitiesConfig": capabilities_config,
                        "toolsConfig": tools_config,
                        "reason": "覆盖率失败阻断测试",
                    },
                )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("BLOCKED", body["status"])
        blocker_codes = [item["code"] for item in body["record"]["blockers"]]
        self.assertIn("DRAFT_POLICY_COVERAGE_FAILED", blocker_codes)
        self.assertEqual(1, body["record"]["policyCoverage"]["failedCount"])

    def test_rollback_request_contains_restore_draft_from_base_snapshot(self):
        client = TestClient(app)
        active = client.get("/api/srmp/langgraph/governance/config").json()
        capabilities_config = copy.deepcopy(active["capabilitiesConfig"])
        tools_config = copy.deepcopy(active["toolsConfig"])
        capabilities_config["capabilities"][0]["name"] = "可回滚发布包"

        with tempfile.TemporaryDirectory() as temp_dir:
            log_path = os.path.join(temp_dir, "publish.jsonl")
            with patch.dict(os.environ, {"SRMP_AGENT_GOVERNANCE_PUBLISH_LOG_PATH": log_path}):
                submit = client.post(
                    "/api/srmp/langgraph/governance/config/publish/request",
                    json={
                        "capabilitiesConfig": capabilities_config,
                        "toolsConfig": tools_config,
                        "reason": "回滚草稿测试",
                    },
                ).json()
                request_id = submit["record"]["requestId"]
                client.post(f"/api/srmp/langgraph/governance/config/publish/requests/{request_id}/approve", json={})
                client.post(f"/api/srmp/langgraph/governance/config/publish/requests/{request_id}/apply", json={})

                rollback = client.post(
                    f"/api/srmp/langgraph/governance/config/publish/requests/{request_id}/rollback",
                    json={"reason": "恢复上一版本"},
                ).json()

        self.assertEqual("ROLLBACK_SUBMITTED", rollback["status"])
        rollback_draft = rollback["rollbackDraft"]
        self.assertEqual(request_id, rollback_draft["targetRequestId"])
        self.assertEqual(active["capabilitiesConfig"], rollback_draft["capabilitiesConfig"])
        self.assertEqual(active["toolsConfig"], rollback_draft["toolsConfig"])
```

- [ ] **Step 2: Run the tests and verify failure**

Run:

```bash
PYTHONPATH=srmp-ai-orchestrator srmp-ai-orchestrator/.venv/bin/python -m unittest srmp-ai-orchestrator.tests.test_governance_api.GovernanceApiTest.test_publish_request_blocks_failed_draft_policy_coverage srmp-ai-orchestrator.tests.test_governance_api.GovernanceApiTest.test_rollback_request_contains_restore_draft_from_base_snapshot -v
```

Expected: failures because publish records do not include `policyCoverage`, and rollback lacks `rollbackDraft`.

- [ ] **Step 3: Include draft coverage in draft validation**

In `governance_config_draft_validate`, after `result["readiness"] = governance_readiness_for_registry(registry)`, add:

```python
    result["policyCoverage"] = governance_policy_coverage_for_registry(registry)
```

- [ ] **Step 4: Add coverage blockers to publish**

In `_publish_blockers`, add this block after validation/readiness blockers:

```python
    coverage = validation.get("policyCoverage") if isinstance(validation.get("policyCoverage"), dict) else {}
    failed_count = int(coverage.get("failedCount") or 0)
    if failed_count > 0:
        blockers.append({
            "code": "DRAFT_POLICY_COVERAGE_FAILED",
            "message": "草稿策略样例失败 " + str(failed_count) + " 个，请先修正能力触发或工具策略。",
        })
```

If `_publish_blockers` does not already initialize `blockers`, keep the existing initialization and append to that list.

- [ ] **Step 5: Store coverage on publish records**

In `governance_config_publish_submit`, add:

```python
        "policyCoverage": validation.get("policyCoverage") or {},
        "policyFailedCount": int(((validation.get("policyCoverage") or {}).get("failedCount")) or 0),
```

near `readinessStatus` and `validationErrorCount`.

- [ ] **Step 6: Add rollback draft extraction**

Add helper functions to `governance.py`:

```python
def _rollback_draft_from_publish_record(existing: Dict[str, Any], request_id: str) -> Dict[str, Any]:
    snapshot = existing.get("configSnapshot") if isinstance(existing.get("configSnapshot"), dict) else {}
    base_snapshot = existing.get("baseConfigSnapshot") if isinstance(existing.get("baseConfigSnapshot"), dict) else {}
    capabilities_config = dict(base_snapshot.get("capabilitiesConfig") or snapshot.get("capabilitiesConfig") or {})
    tools_config = dict(base_snapshot.get("toolsConfig") or snapshot.get("toolsConfig") or {})
    return {
        "capabilitiesConfig": capabilities_config,
        "toolsConfig": tools_config,
        "baseRequestId": str(existing.get("requestId") or request_id or ""),
        "targetRequestId": str(request_id or ""),
    }
```

In `governance_config_publish_submit`, store the active base snapshot:

```python
        "baseConfigSnapshot": {
            "capabilitiesConfig": _load_json(_capabilities_path()),
            "toolsConfig": _load_json(_tools_path()),
        },
```

In `governance_config_publish_rollback`, before returning, compute:

```python
    rollback_draft = _rollback_draft_from_publish_record(existing or {}, request_id) if not blockers else {}
```

and add to the response:

```python
        "rollbackDraft": rollback_draft,
```

- [ ] **Step 7: Run the Task 2 tests**

Run:

```bash
PYTHONPATH=srmp-ai-orchestrator srmp-ai-orchestrator/.venv/bin/python -m unittest srmp-ai-orchestrator.tests.test_governance_api.GovernanceApiTest.test_publish_request_blocks_failed_draft_policy_coverage srmp-ai-orchestrator.tests.test_governance_api.GovernanceApiTest.test_rollback_request_contains_restore_draft_from_base_snapshot -v
```

Expected: `OK`.

- [ ] **Step 8: Commit Task 2**

```bash
git add srmp-ai-orchestrator/app/governance.py srmp-ai-orchestrator/tests/test_governance_api.py
git commit -m "feat: enforce governance draft coverage before publish"
```

## Task 3: Draft Plan Simulation

**Files:**
- Modify: `srmp-ai-orchestrator/app/governance.py`
- Modify: `srmp-ai-orchestrator/app/main.py`
- Test: `srmp-ai-orchestrator/tests/test_governance_api.py`

- [ ] **Step 1: Write failing test for wrapper-style draft plan simulation**

Append this test to `GovernanceApiTest`:

```python
    def test_plan_simulate_uses_draft_registry_when_wrapper_payload_is_sent(self):
        client = TestClient(app)
        active = client.get("/api/srmp/langgraph/governance/config").json()
        capabilities_config = copy.deepcopy(active["capabilitiesConfig"])
        tools_config = copy.deepcopy(active["toolsConfig"])
        route_capability = next(item for item in capabilities_config["capabilities"] if item["id"] == "map.route_analysis")
        route_capability["toolPolicy"]["required"] = ["knowledge.retrieve"]
        route_capability["toolPolicy"]["optional"] = []
        route_capability["toolPolicy"]["adaptive"] = []
        route_capability["toolPolicy"]["prohibited"] = ["gis.queryRegionSummary", "gis.queryAssessmentResults", "gis.queryDiseases"]

        response = client.post(
            "/api/srmp/langgraph/governance/plan-simulate",
            json={
                "request": {
                    "action": "ANALYZE_ROUTE",
                    "message": "分析当前路线",
                    "mapContext": {"mode": "ROUTE", "routeCode": "Y016140727", "year": 2026},
                    "options": {"useKnowledge": True},
                },
                "capabilitiesConfig": capabilities_config,
                "toolsConfig": tools_config,
            },
        )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("DRAFT_PLAN_SIMULATE", body["mode"])
        self.assertEqual("map.route_analysis", body["capabilityId"])
        self.assertEqual(["knowledge.retrieve"], [item["toolName"] for item in body["toolPlan"]])
        self.assertIn("gis.queryRegionSummary", body["capability"]["toolPolicy"]["prohibited"])
```

- [ ] **Step 2: Run the draft plan simulation test and verify failure**

Run:

```bash
PYTHONPATH=srmp-ai-orchestrator srmp-ai-orchestrator/.venv/bin/python -m unittest srmp-ai-orchestrator.tests.test_governance_api.GovernanceApiTest.test_plan_simulate_uses_draft_registry_when_wrapper_payload_is_sent -v
```

Expected: request validation failure or missing `DRAFT_PLAN_SIMULATE` mode because the endpoint only accepts the direct `MapAiAgentRequest` shape.

- [ ] **Step 3: Add governance draft plan simulation helper**

In `srmp-ai-orchestrator/app/governance.py`, add:

```python
def governance_plan_simulate_for_payload(payload: Dict[str, Any]) -> Dict[str, Any]:
    request_payload = payload.get("request") if isinstance(payload.get("request"), dict) else payload
    capabilities_raw = payload.get("capabilitiesConfig") if isinstance(payload.get("capabilitiesConfig"), dict) else None
    tools_raw = payload.get("toolsConfig") if isinstance(payload.get("toolsConfig"), dict) else None
    is_draft = bool(capabilities_raw is not None and tools_raw is not None)
    registry = build_governance_registry(capabilities_raw or _load_json(_capabilities_path()), tools_raw or _load_json(_tools_path()))
    request = MapAiAgentRequest(**(request_payload or {}))
    fallback_intent, detail = recognize_intent(request)
    capability = resolve_capability_for_registry(registry, request, fallback_intent=fallback_intent, intent_detail=detail)
    intent = capability.get("intent") or fallback_intent
    tool_plan = enrich_tool_plan(plan_tools(request, intent, detail, capability=capability))
    return {
        "mode": "DRAFT_PLAN_SIMULATE" if is_draft else "ACTIVE_PLAN_SIMULATE",
        "action": request.action,
        "intent": intent,
        "capabilityId": capability.get("capabilityId"),
        "capability": capability,
        "contextSummary": {
            "mode": request.mapContext.mode if request.mapContext else None,
            "routeCode": request.mapContext.routeCode if request.mapContext else None,
            "year": request.mapContext.year if request.mapContext else None,
        },
        "toolPlan": tool_plan,
        "warnings": registry.validation.get("errors") or [],
        "draftId": _stable_hash({
            "capabilitiesConfig": capabilities_raw or {},
            "toolsConfig": tools_raw or {},
        })[:16] if is_draft else "",
    }
```

- [ ] **Step 4: Enhance plan-simulate endpoint without breaking existing payloads**

In `srmp-ai-orchestrator/app/main.py`, import `governance_plan_simulate_for_payload`. Replace the current endpoint body with:

```python
@app.post("/api/srmp/langgraph/governance/plan-simulate")
async def governance_plan_simulate(
    body: Optional[Dict[str, Any]] = None,
    x_tenant_id: Optional[str] = Header(default=None, alias="X-Tenant-Id"),
    x_ai_trace_id: Optional[str] = Header(default=None, alias="X-AI-Trace-Id"),
) -> dict:
    payload = body or {}
    if isinstance(payload.get("request"), dict) or isinstance(payload.get("capabilitiesConfig"), dict) or isinstance(payload.get("toolsConfig"), dict):
        return governance_plan_simulate_for_payload(payload)
    request = MapAiAgentRequest(**payload)
    plan = await workflow.plan(request=request, tenant_id=x_tenant_id, trace_id=x_ai_trace_id)
    return {
        "traceId": plan.get("traceId"),
        "strategy": plan.get("strategy"),
        "action": plan.get("action"),
        "intent": plan.get("intent"),
        "capabilityId": plan.get("capabilityId"),
        "capability": plan.get("capability") or {},
        "contextSummary": plan.get("contextSummary") or {},
        "toolPlan": plan.get("toolPlan") or [],
        "sourceHints": plan.get("sourceHints") or [],
        "warnings": plan.get("warnings") or [],
        "steps": plan.get("steps") or [],
    }
```

- [ ] **Step 5: Run plan simulation tests**

Run:

```bash
PYTHONPATH=srmp-ai-orchestrator srmp-ai-orchestrator/.venv/bin/python -m unittest srmp-ai-orchestrator.tests.test_governance_api.GovernanceApiTest.test_plan_simulate_returns_capability srmp-ai-orchestrator.tests.test_governance_api.GovernanceApiTest.test_plan_simulate_uses_draft_registry_when_wrapper_payload_is_sent -v
```

Expected: both tests pass.

- [ ] **Step 6: Commit Task 3**

```bash
git add srmp-ai-orchestrator/app/governance.py srmp-ai-orchestrator/app/main.py srmp-ai-orchestrator/tests/test_governance_api.py
git commit -m "feat: simulate governance plans against draft config"
```

## Task 4: Frontend Draft Model Helpers

**Files:**
- Create: `srmp-web-ui/src/views/agent/components/governanceDraft.ts`
- Create: `srmp-web-ui/tests/aiGovernanceDraft.test.mjs`

- [ ] **Step 1: Write failing helper tests**

Create `srmp-web-ui/tests/aiGovernanceDraft.test.mjs`:

```javascript
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import vm from 'node:vm'
import { test } from 'node:test'
import ts from '../node_modules/typescript/lib/typescript.js'

const source = readFileSync('srmp-web-ui/src/views/agent/components/governanceDraft.ts', 'utf8')
const require = createRequire(import.meta.url)

function loadGovernanceDraftModule() {
  const output = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020
    }
  }).outputText
  const module = { exports: {} }
  vm.runInNewContext(output, { module, exports: module.exports, require })
  return module.exports
}

test('governanceDraft exports matrix and evaluation helpers', () => {
  for (const name of [
    'buildCapabilityMatrixRows',
    'updateCapabilityToolPolicy',
    'extractEvaluationCases',
    'writeEvaluationCases',
    'derivePublishBlockers'
  ]) {
    assert.match(source, new RegExp(`export function ${name}\\\\b`))
  }
})

test('policy editor removes a tool from conflicting buckets', async () => {
  const module = loadGovernanceDraftModule()
  const config = {
    version: 'test',
    capabilities: [
      {
        id: 'knowledge.metric_explain',
        name: '指标解释',
        toolPolicy: {
          required: ['knowledge.retrieve'],
          optional: ['gis.queryRegionSummary'],
          adaptive: ['knowledge.retrieve'],
          prohibited: []
        }
      }
    ]
  }

  const next = module.updateCapabilityToolPolicy(config, 'knowledge.metric_explain', 'prohibited', ['knowledge.retrieve'])
  const policy = next.capabilities[0].toolPolicy
  assert.deepEqual(policy.required, [])
  assert.deepEqual(policy.optional, ['gis.queryRegionSummary'])
  assert.deepEqual(policy.adaptive, [])
  assert.deepEqual(policy.prohibited, ['knowledge.retrieve'])
})

test('evaluation cases round-trip through capability examples', async () => {
  const module = loadGovernanceDraftModule()
  const config = {
    version: 'test',
    capabilities: [
      {
        id: 'map.route_analysis',
        name: '路线分析',
        examples: [
          {
            id: 'map.route_analysis.action',
            name: '点击分析路线',
            request: { action: 'ANALYZE_ROUTE', message: '分析当前路线' },
            expect: { capabilityId: 'map.route_analysis', requiredTools: ['gis.queryRegionSummary'] }
          }
        ]
      }
    ]
  }

  const cases = module.extractEvaluationCases(config)
  assert.equal(cases.length, 1)
  assert.equal(cases[0].capabilityId, 'map.route_analysis')
  const updated = [{ ...cases[0], name: '路线分析改名', requiredTools: ['gis.queryDiseases'] }]
  const next = module.writeEvaluationCases(config, updated)
  assert.equal(next.capabilities[0].examples[0].name, '路线分析改名')
  assert.deepEqual(next.capabilities[0].examples[0].expect.requiredTools, ['gis.queryDiseases'])
})
```

- [ ] **Step 2: Run the helper tests and verify failure**

Run:

```bash
node --test srmp-web-ui/tests/aiGovernanceDraft.test.mjs
```

Expected: file not found for `governanceDraft.ts`.

- [ ] **Step 3: Add `governanceDraft.ts`**

Create `srmp-web-ui/src/views/agent/components/governanceDraft.ts` with:

```ts
export type ToolPolicyKey = 'required' | 'optional' | 'adaptive' | 'prohibited'

export interface CapabilityMatrixRow {
  id: string
  name: string
  category: string
  intent: string
  enabled: boolean
  required: string[]
  optional: string[]
  adaptive: string[]
  prohibited: string[]
  raw: Record<string, any>
}

export interface EvaluationCaseRow {
  id: string
  name: string
  capabilityId: string
  capabilityName: string
  request: Record<string, any>
  expect: Record<string, any>
  expectedCapabilityId: string
  requiredTools: string[]
  prohibitedTools: string[]
  exactToolNames: string[]
}

const POLICY_KEYS: ToolPolicyKey[] = ['required', 'optional', 'adaptive', 'prohibited']

export function buildCapabilityMatrixRows(capabilitiesConfig: Record<string, any>): CapabilityMatrixRow[] {
  return arrayValue(capabilitiesConfig?.capabilities).map((capability) => {
    const policy = objectValue(capability.toolPolicy)
    return {
      id: stringValue(capability.id),
      name: stringValue(capability.name || capability.id),
      category: stringValue(capability.category),
      intent: stringValue(capability.intent),
      enabled: capability.enabled !== false,
      required: stringList(policy.required),
      optional: stringList(policy.optional),
      adaptive: stringList(policy.adaptive),
      prohibited: stringList(policy.prohibited),
      raw: capability
    }
  })
}

export function updateCapabilityToolPolicy(
  capabilitiesConfig: Record<string, any>,
  capabilityId: string,
  policyKey: ToolPolicyKey,
  toolNames: string[]
): Record<string, any> {
  const next = cloneObject(capabilitiesConfig)
  next.capabilities = arrayValue(next.capabilities).map((capability) => {
    if (stringValue(capability.id) !== capabilityId) return capability
    const nextPolicy: Record<string, string[]> = {}
    const selected = uniqueStrings(toolNames)
    for (const key of POLICY_KEYS) {
      const current = key === policyKey ? selected : stringList(capability.toolPolicy?.[key])
      nextPolicy[key] = key === policyKey ? current : current.filter((tool) => !selected.includes(tool))
    }
    return {
      ...capability,
      toolPolicy: nextPolicy
    }
  })
  return next
}

export function extractEvaluationCases(capabilitiesConfig: Record<string, any>): EvaluationCaseRow[] {
  const rows: EvaluationCaseRow[] = []
  for (const capability of arrayValue(capabilitiesConfig?.capabilities)) {
    for (const example of arrayValue(capability.examples)) {
      const expect = objectValue(example.expect)
      rows.push({
        id: stringValue(example.id),
        name: stringValue(example.name || example.id),
        capabilityId: stringValue(capability.id),
        capabilityName: stringValue(capability.name || capability.id),
        request: objectValue(example.request),
        expect,
        expectedCapabilityId: stringValue(expect.capabilityId || capability.id),
        requiredTools: stringList(expect.requiredTools),
        prohibitedTools: stringList(expect.prohibitedTools),
        exactToolNames: stringList(expect.exactToolNames)
      })
    }
  }
  return rows
}

export function writeEvaluationCases(
  capabilitiesConfig: Record<string, any>,
  cases: EvaluationCaseRow[]
): Record<string, any> {
  const next = cloneObject(capabilitiesConfig)
  const byCapability = new Map<string, EvaluationCaseRow[]>()
  for (const item of cases) {
    const key = stringValue(item.capabilityId)
    if (!key) continue
    byCapability.set(key, [...(byCapability.get(key) || []), item])
  }
  next.capabilities = arrayValue(next.capabilities).map((capability) => {
    const capabilityId = stringValue(capability.id)
    const rows = byCapability.get(capabilityId) || []
    return {
      ...capability,
      examples: rows.map((row) => ({
        id: row.id,
        name: row.name,
        request: row.request || {},
        expect: {
          capabilityId: row.expectedCapabilityId || row.capabilityId,
          requiredTools: row.requiredTools || [],
          prohibitedTools: row.prohibitedTools || [],
          exactToolNames: row.exactToolNames || []
        }
      }))
    }
  })
  return next
}

export function derivePublishBlockers(validationPayload: Record<string, any>, coveragePayload: Record<string, any>): string[] {
  const blockers: string[] = []
  const validation = objectValue(validationPayload.validation)
  const readiness = objectValue(validationPayload.readiness)
  const coverage = Object.keys(coveragePayload || {}).length ? coveragePayload : objectValue(validationPayload.policyCoverage)
  if (Number(validation.errorCount || 0) > 0) blockers.push('配置校验存在错误')
  if (stringValue(readiness.status) === 'FAIL') blockers.push('治理体检存在阻断项')
  if (Number(coverage.failedCount || 0) > 0) blockers.push(`策略样例失败 ${Number(coverage.failedCount || 0)} 个`)
  return blockers
}

export function parseJsonObject(text: string): Record<string, any> | null {
  try {
    const parsed = JSON.parse(text || '{}')
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null
  } catch {
    return null
  }
}

function cloneObject(value: Record<string, any>): Record<string, any> {
  return JSON.parse(JSON.stringify(value || {}))
}

function arrayValue(value: any): any[] {
  return Array.isArray(value) ? value : []
}

function objectValue(value: any): Record<string, any> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
}

function stringList(value: any): string[] {
  return uniqueStrings(arrayValue(value).map((item) => stringValue(item)).filter(Boolean))
}

function stringValue(value: any): string {
  return value == null ? '' : String(value)
}

function uniqueStrings(values: string[]): string[] {
  return Array.from(new Set(values.map((item) => item.trim()).filter(Boolean)))
}
```

- [ ] **Step 4: Run the helper tests**

Run:

```bash
node --test srmp-web-ui/tests/aiGovernanceDraft.test.mjs
```

Expected: `pass 3`.

- [ ] **Step 5: Commit Task 4**

```bash
git add srmp-web-ui/src/views/agent/components/governanceDraft.ts srmp-web-ui/tests/aiGovernanceDraft.test.mjs
git commit -m "feat: add governance draft model helpers"
```

## Task 5: Java Proxy And Frontend API For Draft Coverage

**Files:**
- Modify: `srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java`
- Modify: `srmp-agent/src/test/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsControllerLiveTraceTest.java`
- Modify: `srmp-web-ui/src/api/orchestrator.ts`
- Test: `srmp-web-ui/tests/aiGovernanceWorkbench.test.mjs`

- [ ] **Step 1: Write failing Java proxy path test**

Add to `AgentOrchestratorOpsControllerLiveTraceTest`:

```java
    @Test
    public void governancePolicyCoverageDraftPathIsStable() {
        AgentOrchestratorOpsController controller = new AgentOrchestratorOpsController();

        assertEquals(
                "/api/srmp/langgraph/governance/policies/coverage/draft",
                controller.governancePolicyCoverageDraftPath()
        );
    }
```

- [ ] **Step 2: Run the Java test and verify failure**

Run:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -pl srmp-agent -Dtest=AgentOrchestratorOpsControllerLiveTraceTest test
```

Expected: compilation failure because `governancePolicyCoverageDraftPath` does not exist.

- [ ] **Step 3: Add Java proxy helper and endpoint**

In `AgentOrchestratorOpsController.java`, add after `governanceCoverage`:

```java
    @PostMapping("/governance/policies/coverage/draft")
    public R<Object> governanceDraftCoverage(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> payload = body == null ? new LinkedHashMap<String, Object>() : body;
        return R.ok(remotePost(governancePolicyCoverageDraftPath(), payload));
    }

    String governancePolicyCoverageDraftPath() {
        return "/api/srmp/langgraph/governance/policies/coverage/draft";
    }
```

- [ ] **Step 4: Add frontend API and static test**

Create `srmp-web-ui/tests/aiGovernanceWorkbench.test.mjs`:

```javascript
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

test('orchestrator api exposes draft governance coverage', () => {
  const source = readFileSync('srmp-web-ui/src/api/orchestrator.ts', 'utf8')
  assert.match(source, /export function getAiGovernanceDraftPolicyCoverage/)
  assert.match(source, /governance\\/policies\\/coverage\\/draft/)
})
```

In `srmp-web-ui/src/api/orchestrator.ts`, add:

```ts
export function getAiGovernanceDraftPolicyCoverage(data: Record<string, any>): Promise<Record<string, any>> {
  return aiRequest.post('/api/agent/orchestrator/ops/governance/policies/coverage/draft', data || {})
}
```

- [ ] **Step 5: Run proxy/API tests**

Run:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -pl srmp-agent -Dtest=AgentOrchestratorOpsControllerLiveTraceTest test
node --test srmp-web-ui/tests/aiGovernanceWorkbench.test.mjs
```

Expected: Java test passes and Node test passes.

- [ ] **Step 6: Commit Task 5**

```bash
git add srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java srmp-agent/src/test/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsControllerLiveTraceTest.java srmp-web-ui/src/api/orchestrator.ts srmp-web-ui/tests/aiGovernanceWorkbench.test.mjs
git commit -m "feat: proxy governance draft coverage"
```

## Task 6: Capability-Tool Matrix Editor

**Files:**
- Create: `srmp-web-ui/src/views/agent/components/GovernanceMatrixEditor.vue`
- Modify: `srmp-web-ui/src/views/agent/AiGovernancePage.vue`
- Modify: `srmp-web-ui/tests/aiGovernanceWorkbench.test.mjs`

- [ ] **Step 1: Extend static test for matrix component wiring**

Add to `srmp-web-ui/tests/aiGovernanceWorkbench.test.mjs`:

```javascript
test('governance page wires the matrix editor', () => {
  const page = readFileSync('srmp-web-ui/src/views/agent/AiGovernancePage.vue', 'utf8')
  assert.match(page, /GovernanceMatrixEditor/)
  assert.match(page, /name="matrix"/)
  assert.match(page, /能力-工具矩阵/)
})
```

- [ ] **Step 2: Run the static test and verify failure**

Run:

```bash
node --test srmp-web-ui/tests/aiGovernanceWorkbench.test.mjs
```

Expected: failure because `GovernanceMatrixEditor` is not wired.

- [ ] **Step 3: Add `GovernanceMatrixEditor.vue`**

Create the component:

```vue
<template>
  <section class="matrix-editor">
    <div class="matrix-head">
      <div>
        <strong>能力-工具矩阵</strong>
        <span>编辑每个能力允许和禁止调用的工具</span>
      </div>
      <el-input v-model="keyword" size="small" clearable placeholder="搜索能力或工具" />
    </div>

    <el-table :data="filteredRows" border stripe>
      <el-table-column prop="name" label="能力" min-width="170">
        <template #default="{ row }">
          <div class="capability-cell">
            <strong>{{ row.name }}</strong>
            <span>{{ row.id }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-for="block in policyBlocks" :key="block.key" :label="block.label" min-width="230">
        <template #default="{ row }">
          <el-select
            :model-value="row[block.key]"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            clearable
            size="small"
            @update:model-value="(value) => updatePolicy(row.id, block.key, value)"
          >
            <el-option
              v-for="tool in toolOptions"
              :key="tool.name"
              :label="tool.label ? `${tool.label} (${tool.name})` : tool.name"
              :value="tool.name"
              :disabled="tool.enabled === false"
            />
          </el-select>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { buildCapabilityMatrixRows, updateCapabilityToolPolicy, type ToolPolicyKey } from './governanceDraft'

const props = defineProps<{
  capabilitiesConfig: Record<string, any>
  toolsConfig: Record<string, any>
}>()

const emit = defineEmits<{
  (event: 'update:capabilitiesConfig', value: Record<string, any>): void
}>()

const keyword = ref('')
const policyBlocks: Array<{ key: ToolPolicyKey, label: string }> = [
  { key: 'required', label: 'Required' },
  { key: 'optional', label: 'Optional' },
  { key: 'adaptive', label: 'Adaptive' },
  { key: 'prohibited', label: 'Prohibited' }
]

const rows = computed(() => buildCapabilityMatrixRows(props.capabilitiesConfig || {}))
const toolOptions = computed(() => Array.isArray(props.toolsConfig?.tools) ? props.toolsConfig.tools : [])
const filteredRows = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  if (!text) return rows.value
  return rows.value.filter((row) => JSON.stringify(row).toLowerCase().includes(text))
})

function updatePolicy(capabilityId: string, key: ToolPolicyKey, value: string[]) {
  emit('update:capabilitiesConfig', updateCapabilityToolPolicy(props.capabilitiesConfig || {}, capabilityId, key, value))
}
</script>

<style scoped>
.matrix-editor {
  display: grid;
  gap: 12px;
}

.matrix-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.matrix-head > div,
.capability-cell {
  display: grid;
  gap: 4px;
}

.matrix-head span,
.capability-cell span {
  color: #64748b;
  font-size: 12px;
}
</style>
```

- [ ] **Step 4: Wire matrix into `AiGovernancePage.vue`**

Import:

```ts
import GovernanceMatrixEditor from './components/GovernanceMatrixEditor.vue'
```

Add computed draft configs:

```ts
const draftCapabilitiesConfig = computed(() => parseDraftJsonSilent(draftCapabilitiesText.value))
const draftToolsConfig = computed(() => parseDraftJsonSilent(draftToolsText.value))
```

Add functions:

```ts
function parseDraftJsonSilent(value: string): Record<string, any> {
  try {
    const parsed = JSON.parse(value || '{}')
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch {
    return {}
  }
}

function applyStructuredCapabilitiesConfig(value: Record<string, any>) {
  draftCapabilitiesText.value = prettyJson(value || {})
  draftValidationPayload.value = {}
}
```

Add a new tab before the old `能力矩阵` tab:

```vue
        <el-tab-pane label="能力-工具矩阵" name="matrix">
          <GovernanceMatrixEditor
            :capabilities-config="draftCapabilitiesConfig"
            :tools-config="draftToolsConfig"
            @update:capabilities-config="applyStructuredCapabilitiesConfig"
          />
        </el-tab-pane>
```

- [ ] **Step 5: Run frontend tests**

Run:

```bash
node --test srmp-web-ui/tests/aiGovernanceDraft.test.mjs
node --test srmp-web-ui/tests/aiGovernanceWorkbench.test.mjs
```

Expected: both tests pass.

- [ ] **Step 6: Commit Task 6**

```bash
git add srmp-web-ui/src/views/agent/components/GovernanceMatrixEditor.vue srmp-web-ui/src/views/agent/AiGovernancePage.vue srmp-web-ui/tests/aiGovernanceWorkbench.test.mjs
git commit -m "feat: add editable governance matrix"
```

## Task 7: Evaluation Case Set And Draft Coverage UI

**Files:**
- Create: `srmp-web-ui/src/views/agent/components/GovernanceEvalCaseSet.vue`
- Modify: `srmp-web-ui/src/views/agent/AiGovernancePage.vue`
- Modify: `srmp-web-ui/tests/aiGovernanceWorkbench.test.mjs`

- [ ] **Step 1: Extend static test for evaluation case set and draft coverage**

Add:

```javascript
test('governance page wires evaluation cases and draft coverage', () => {
  const page = readFileSync('srmp-web-ui/src/views/agent/AiGovernancePage.vue', 'utf8')
  assert.match(page, /GovernanceEvalCaseSet/)
  assert.match(page, /runDraftPolicyCoverage/)
  assert.match(page, /name="eval-cases"/)
  const api = readFileSync('srmp-web-ui/src/api/orchestrator.ts', 'utf8')
  assert.match(api, /getAiGovernanceDraftPolicyCoverage/)
})
```

- [ ] **Step 2: Run the static test and verify failure**

Run:

```bash
node --test srmp-web-ui/tests/aiGovernanceWorkbench.test.mjs
```

Expected: failure because the component and function are not wired.

- [ ] **Step 3: Add `GovernanceEvalCaseSet.vue`**

Create:

```vue
<template>
  <section class="eval-set">
    <div class="eval-head">
      <div>
        <strong>评测用例集</strong>
        <span>从能力 examples 提取，用于校验能力命中和工具边界</span>
      </div>
      <div class="eval-actions">
        <el-select v-model="capabilityFilter" size="small" clearable placeholder="能力">
          <el-option v-for="item in capabilityOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-button size="small" type="primary" :loading="running" @click="$emit('run', selectedCaseIds)">运行选中</el-button>
      </div>
    </div>

    <el-table :data="filteredCases" border stripe @selection-change="updateSelection">
      <el-table-column type="selection" width="45" />
      <el-table-column prop="id" label="用例 ID" min-width="230" />
      <el-table-column prop="name" label="场景" min-width="170" />
      <el-table-column prop="capabilityName" label="能力" min-width="160" />
      <el-table-column label="期望工具" min-width="260">
        <template #default="{ row }">
          <div class="tag-list">
            <el-tag v-for="tool in row.requiredTools" :key="tool" size="small">{{ tool }}</el-tag>
            <el-tag v-for="tool in row.prohibitedTools" :key="tool" size="small" type="danger">{{ tool }}</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="请求" min-width="260">
        <template #default="{ row }">{{ row.request.message || row.request.action || '-' }}</template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { extractEvaluationCases } from './governanceDraft'

const props = defineProps<{
  capabilitiesConfig: Record<string, any>
  running?: boolean
}>()

defineEmits<{
  (event: 'run', caseIds: string[]): void
}>()

const capabilityFilter = ref('')
const selectedCaseIds = ref<string[]>([])
const cases = computed(() => extractEvaluationCases(props.capabilitiesConfig || {}))
const capabilityOptions = computed(() => {
  const seen = new Map<string, string>()
  for (const row of cases.value) seen.set(row.capabilityId, row.capabilityName)
  return Array.from(seen.entries()).map(([id, name]) => ({ id, name }))
})
const filteredCases = computed(() => capabilityFilter.value ? cases.value.filter((item) => item.capabilityId === capabilityFilter.value) : cases.value)

function updateSelection(rows: any[]) {
  selectedCaseIds.value = rows.map((row) => row.id).filter(Boolean)
}
</script>

<style scoped>
.eval-set {
  display: grid;
  gap: 12px;
}

.eval-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.eval-head > div {
  display: grid;
  gap: 4px;
}

.eval-head span {
  color: #64748b;
  font-size: 12px;
}

.eval-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
```

- [ ] **Step 4: Wire draft coverage into `AiGovernancePage.vue`**

Import:

```ts
import GovernanceEvalCaseSet from './components/GovernanceEvalCaseSet.vue'
import { getAiGovernanceDraftPolicyCoverage } from '../../api/orchestrator'
```

Add state:

```ts
const draftCoverageLoading = ref(false)
const draftCoveragePayload = ref<Record<string, any>>({})
```

Add function:

```ts
async function runDraftPolicyCoverage(caseIds: string[] = []) {
  const capabilitiesConfig = parseDraftJson(draftCapabilitiesText.value, '能力配置')
  const toolsConfig = parseDraftJson(draftToolsText.value, '工具配置')
  if (!capabilitiesConfig || !toolsConfig) return
  draftCoverageLoading.value = true
  try {
    const response = await getAiGovernanceDraftPolicyCoverage({
      capabilitiesConfig,
      toolsConfig,
      caseIds
    })
    draftCoveragePayload.value = extractBody(response)
    activeTab.value = 'eval-cases'
  } finally {
    draftCoverageLoading.value = false
  }
}
```

Add tab:

```vue
        <el-tab-pane label="评测用例" name="eval-cases">
          <div class="coverage-head">
            <div>
              <strong>草稿策略覆盖率</strong>
              <span>基于当前草稿运行，不影响 Runtime 活动配置</span>
            </div>
            <el-button size="small" type="primary" :loading="draftCoverageLoading" @click="runDraftPolicyCoverage([])">运行全部草稿用例</el-button>
          </div>
          <section class="plan-summary config-summary" v-if="draftCoveragePayload.mode">
            <div><span>状态</span><strong>{{ Number(draftCoveragePayload.failedCount || 0) > 0 ? '异常' : '通过' }}</strong></div>
            <div><span>用例</span><strong>{{ draftCoveragePayload.caseCount || 0 }}</strong></div>
            <div><span>通过</span><strong>{{ draftCoveragePayload.passedCount || 0 }}</strong></div>
            <div><span>失败</span><strong>{{ draftCoveragePayload.failedCount || 0 }}</strong></div>
          </section>
          <GovernanceEvalCaseSet
            :capabilities-config="draftCapabilitiesConfig"
            :running="draftCoverageLoading"
            @run="runDraftPolicyCoverage"
          />
        </el-tab-pane>
```

- [ ] **Step 5: Run frontend tests**

Run:

```bash
node --test srmp-web-ui/tests/aiGovernanceDraft.test.mjs
node --test srmp-web-ui/tests/aiGovernanceWorkbench.test.mjs
```

Expected: both pass.

- [ ] **Step 6: Commit Task 7**

```bash
git add srmp-web-ui/src/views/agent/components/GovernanceEvalCaseSet.vue srmp-web-ui/src/views/agent/AiGovernancePage.vue srmp-web-ui/tests/aiGovernanceWorkbench.test.mjs
git commit -m "feat: add governance evaluation case set"
```

## Task 8: Draft Review Panel, Publish Blocking, And Rollback Restore UI

**Files:**
- Create: `srmp-web-ui/src/views/agent/components/GovernanceDraftReviewPanel.vue`
- Modify: `srmp-web-ui/src/views/agent/AiGovernancePage.vue`
- Modify: `srmp-web-ui/tests/aiGovernanceWorkbench.test.mjs`

- [ ] **Step 1: Extend static test for draft review and rollback restore**

Add:

```javascript
test('governance page wires draft review and rollback restore', () => {
  const page = readFileSync('srmp-web-ui/src/views/agent/AiGovernancePage.vue', 'utf8')
  assert.match(page, /GovernanceDraftReviewPanel/)
  assert.match(page, /applyRollbackDraft/)
  assert.match(page, /draftCoveragePayload/)
})
```

- [ ] **Step 2: Run static test and verify failure**

Run:

```bash
node --test srmp-web-ui/tests/aiGovernanceWorkbench.test.mjs
```

Expected: missing component wiring.

- [ ] **Step 3: Add `GovernanceDraftReviewPanel.vue`**

Create:

```vue
<template>
  <section class="draft-review">
    <div class="review-head">
      <div>
        <strong>草稿发布检查</strong>
        <span>提交前汇总配置校验、治理体检、策略覆盖率和变更范围</span>
      </div>
      <el-tag :type="blockers.length ? 'danger' : 'success'">{{ blockers.length ? '存在阻断' : '可提交' }}</el-tag>
    </div>

    <section class="review-grid">
      <div><span>配置错误</span><strong>{{ validationErrorCount }}</strong></div>
      <div><span>治理状态</span><strong>{{ readinessStatus }}</strong></div>
      <div><span>样例失败</span><strong>{{ coverageFailedCount }}</strong></div>
      <div><span>变更数</span><strong>{{ changeCount }}</strong></div>
    </section>

    <el-alert
      v-for="item in blockers"
      :key="item"
      class="blocker"
      type="error"
      show-icon
      :title="item"
    />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { derivePublishBlockers } from './governanceDraft'

const props = defineProps<{
  validationPayload: Record<string, any>
  coveragePayload: Record<string, any>
}>()

const validation = computed(() => props.validationPayload?.validation || {})
const readiness = computed(() => props.validationPayload?.readiness || {})
const diff = computed(() => props.validationPayload?.diff || {})
const diffSummary = computed(() => diff.value.summary || {})
const coverage = computed(() => Object.keys(props.coveragePayload || {}).length ? props.coveragePayload : props.validationPayload?.policyCoverage || {})
const blockers = computed(() => derivePublishBlockers(props.validationPayload || {}, props.coveragePayload || {}))
const validationErrorCount = computed(() => Number(validation.value.errorCount || 0))
const readinessStatus = computed(() => readiness.value.status || '-')
const coverageFailedCount = computed(() => Number(coverage.value.failedCount || 0))
const changeCount = computed(() => Number(diffSummary.value.changeCount || 0))
</script>

<style scoped>
.draft-review {
  display: grid;
  gap: 12px;
}

.review-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.review-head > div {
  display: grid;
  gap: 4px;
}

.review-head span,
.review-grid span {
  color: #64748b;
  font-size: 12px;
}

.review-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.review-grid > div {
  display: grid;
  gap: 4px;
  padding: 10px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}

.blocker {
  margin: 0;
}
</style>
```

- [ ] **Step 4: Wire draft review panel into config tab**

Import:

```ts
import GovernanceDraftReviewPanel from './components/GovernanceDraftReviewPanel.vue'
```

Add this above the existing publish panel in the `配置草稿` tab:

```vue
          <GovernanceDraftReviewPanel
            class="mb"
            :validation-payload="draftValidationPayload"
            :coverage-payload="draftCoveragePayload"
          />
```

- [ ] **Step 5: Apply rollback draft returned from backend**

In `requestRollback`, after `const body = extractBody(response)`, add:

```ts
    applyRollbackDraft(objectValue(body.rollbackDraft))
```

Add function:

```ts
function applyRollbackDraft(rollbackDraft: Record<string, any>) {
  if (!rollbackDraft.capabilitiesConfig || !rollbackDraft.toolsConfig) return
  draftCapabilitiesText.value = prettyJson(rollbackDraft.capabilitiesConfig)
  draftToolsText.value = prettyJson(rollbackDraft.toolsConfig)
  draftValidationPayload.value = {}
  draftCoveragePayload.value = {}
  activeTab.value = 'config'
  ElMessage.success('已载入回滚草稿，请校验后重新提交发布申请')
}
```

- [ ] **Step 6: Run frontend tests**

Run:

```bash
node --test srmp-web-ui/tests/aiGovernanceDraft.test.mjs
node --test srmp-web-ui/tests/aiGovernanceWorkbench.test.mjs
```

Expected: both pass.

- [ ] **Step 7: Commit Task 8**

```bash
git add srmp-web-ui/src/views/agent/components/GovernanceDraftReviewPanel.vue srmp-web-ui/src/views/agent/AiGovernancePage.vue srmp-web-ui/tests/aiGovernanceWorkbench.test.mjs
git commit -m "feat: add governance draft publish review"
```

## Task 9: Full Verification And App Restart

**Files:**
- No new source files.
- Verify all files changed by Tasks 1-7.

- [ ] **Step 1: Run orchestrator tests**

Run:

```bash
PYTHONPATH=srmp-ai-orchestrator srmp-ai-orchestrator/.venv/bin/python -m unittest discover -s srmp-ai-orchestrator/tests
```

Expected: all tests pass.

- [ ] **Step 2: Run Java proxy test**

Run:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -pl srmp-agent -Dtest=AgentOrchestratorOpsControllerLiveTraceTest test
```

Expected: build success.

- [ ] **Step 3: Run frontend tests**

Run:

```bash
for f in srmp-web-ui/tests/*.test.mjs; do node --test "$f" || exit 1; done
```

Expected: every Node test passes.

- [ ] **Step 4: Build frontend**

Run:

```bash
npm --prefix srmp-web-ui run build
```

Expected: Vite build completes. Large chunk warnings are acceptable.

- [ ] **Step 5: Compile Java app modules**

Run:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home mvn -pl srmp-admin -am -DskipTests compile
```

Expected: compile success.

- [ ] **Step 6: Rebuild only app services with `.env.dev`**

Run:

```bash
set -a
source /Users/cxp/.codex/worktrees/d1e1/srmp/.env.dev
set +a
docker compose -f docker-compose.yml -f docker-compose.app.yml -f docker-compose.langgraph.yml -f docker-compose.dev.yml up -d --no-deps --build frontend srmp-ai-orchestrator srmp-admin
```

Expected: app services rebuild and start. Do not start local postgres, redis, or minio.

- [ ] **Step 7: Verify Runtime health and governance endpoint**

Run:

```bash
curl -s http://127.0.0.1:18080/health
curl -s http://127.0.0.1:18080/api/srmp/langgraph/governance/policies/coverage/draft \
  -H 'Content-Type: application/json' \
  -d '{"capabilitiesConfig":{"version":"empty","capabilities":[]},"toolsConfig":{"version":"empty","tools":[]}}'
```

Expected: health status `UP`; draft coverage response contains `"mode":"DRAFT_POLICY_COVERAGE"`.

- [ ] **Step 8: Browser smoke test**

Open `http://localhost:5174/agent/ai-governance`.

Verify:

- `能力-工具矩阵` tab renders.
- Editing `knowledge.metric_explain` policy updates the JSON fallback text.
- `评测用例` tab can run all draft cases.
- `草稿发布` panel shows coverage blockers when a draft case fails.
- Existing `Plan 模拟器` still runs.

- [ ] **Step 9: Final commit if verification fixes were needed**

If Task 9 required source fixes, commit them:

```bash
git add srmp-ai-orchestrator/app/governance.py srmp-ai-orchestrator/app/main.py srmp-ai-orchestrator/tests/test_governance_api.py srmp-agent/src/main/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsController.java srmp-agent/src/test/java/com/smartroad/srmp/agent/orchestrator/controller/AgentOrchestratorOpsControllerLiveTraceTest.java srmp-web-ui/src/api/orchestrator.ts srmp-web-ui/src/views/agent/AiGovernancePage.vue srmp-web-ui/src/views/agent/components/governanceDraft.ts srmp-web-ui/src/views/agent/components/GovernanceMatrixEditor.vue srmp-web-ui/src/views/agent/components/GovernanceEvalCaseSet.vue srmp-web-ui/src/views/agent/components/GovernanceDraftReviewPanel.vue srmp-web-ui/tests/aiGovernanceDraft.test.mjs srmp-web-ui/tests/aiGovernanceWorkbench.test.mjs
git commit -m "fix: stabilize governance workbench verification"
```

If no source fixes were needed, skip this commit.
