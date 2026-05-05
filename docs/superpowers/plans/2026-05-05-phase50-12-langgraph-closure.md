# Phase50.12 LangGraph Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the current `srmp-ai-orchestrator` and `srmp-web-ui` LangGraph operations module consistent, diagnosable, and verifiable after Phase50.11.

**Architecture:** Keep Java as the single business tool gateway and Python as a read-only LangGraph runtime. Fix the runtime graph so the compiled LangGraph path matches the sequential fallback path, then expose Phase50.11 diagnostics through Java proxy APIs and the frontend operations page.

**Tech Stack:** Python FastAPI + LangGraph runtime, Java 8 Spring Boot proxy APIs, Vue 3 + Element Plus frontend, shell-based acceptance checks.

---

### Task 1: Runtime Graph Closure

**Files:**
- Modify: `srmp-ai-orchestrator/app/workflow.py`
- Test: `scripts/check-phase50-12-langgraph-closure.sh`

- [ ] **Step 1: Add a static check for the graph terminal edge**

Create `scripts/check-phase50-12-langgraph-closure.sh` with checks that fail if `tool_planning` still points to `END`, and pass only when `quality_guard` is the terminal node.

- [ ] **Step 2: Run the new check and confirm it fails**

Run: `bash scripts/check-phase50-12-langgraph-closure.sh`

Expected: FAIL showing the premature `tool_planning -> END` edge.

- [ ] **Step 3: Fix the graph edge**

In `LangGraphWorkflow._build_graph`, replace:

```python
builder.add_edge("tool_planning", END)
```

with:

```python
builder.add_edge("quality_guard", END)
```

- [ ] **Step 4: Verify Python syntax and the new closure check**

Run:

```bash
python3 -m py_compile srmp-ai-orchestrator/app/*.py
bash scripts/check-phase50-12-langgraph-closure.sh
```

Expected: both commands pass.

### Task 2: Acceptance Script Alignment

**Files:**
- Modify: `scripts/check-phase50-7-langgraph-context-source-plan.sh`
- Modify: `scripts/check-phase50-8-langgraph-contract-plan-ops.sh`
- Modify: `scripts/check-phase50-9-langgraph-runtime-replay-export.sh`
- Modify: `docker-compose.langgraph.yml`

- [ ] **Step 1: Make Phase50.7 check support static-only mode**

Add `SKIP_LIVE="${SKIP_LIVE:-0}"`, keep the Java DTO/static assertions, and skip curl calls when `SKIP_LIVE=1`.

- [ ] **Step 2: Stop Phase50.8 and Phase50.9 checks from hard-coding old strategy versions**

Use:

```bash
EXPECTED_STRATEGY="${EXPECTED_STRATEGY:-phase50.11-config-health-guard-v1}"
EXPECTED_APP_VERSION="${EXPECTED_APP_VERSION:-50.11.0}"
```

Then assert those variables instead of older Phase50.8/50.9 literals.

- [ ] **Step 3: Update Docker image tag default**

In `docker-compose.langgraph.yml`, change the default image tag from `phase50.2` to `phase50.12`.

- [ ] **Step 4: Run static checks**

Run:

```bash
SKIP_LIVE=1 bash scripts/check-phase50-7-langgraph-context-source-plan.sh
SKIP_LIVE=1 bash scripts/check-phase50-8-langgraph-contract-plan-ops.sh
SKIP_LIVE=1 bash scripts/check-phase50-9-langgraph-runtime-replay-export.sh
bash scripts/check-phase50-12-langgraph-closure.sh
```

Expected: all pass without requiring local Java/Python services.

### Task 3: Frontend Operations API

**Files:**
- Modify: `srmp-web-ui/src/api/orchestrator.ts`

- [ ] **Step 1: Add typed wrappers for Phase50.11 Java proxy endpoints**

Add functions for:

```text
GET    /api/agent/orchestrator/ops/config
GET    /api/agent/orchestrator/ops/health-detail
GET    /api/agent/orchestrator/ops/persistence
GET    /api/agent/orchestrator/ops/snapshot
DELETE /api/agent/orchestrator/ops/prune
```

- [ ] **Step 2: Keep wrappers consistent with existing `aiRequest` style**

Return `Promise<Record<string, any>>`; pass query parameters through `params`.

### Task 4: Frontend Operations Page

**Files:**
- Modify: `srmp-web-ui/src/views/agent/LangGraphOpsPage.vue`

- [ ] **Step 1: Load Phase50.11 diagnostics with the summary**

On page load, fetch config, health-detail, persistence, and snapshot alongside the existing summary.

- [ ] **Step 2: Add a configuration health panel**

Show config fingerprint, warning count, runtime app version, and health status.

- [ ] **Step 3: Add an audit persistence panel**

Show persistence enabled/path/size/written/last error, with a prune control that keeps the latest N records.

- [ ] **Step 4: Add copy/export affordances**

Keep the existing diagnostic export, and add a snapshot preview using `/ops/snapshot`.

### Task 5: Verification and Documentation

**Files:**
- Create: `docs/phase50_12_langgraph_closure.md`
- Test: existing scripts and frontend build

- [ ] **Step 1: Document current Phase50.12 behavior**

Write a concise doc describing the graph fix, static acceptance commands, frontend operations additions, and runtime live checks.

- [ ] **Step 2: Run static verification**

Run:

```bash
python3 -m py_compile srmp-ai-orchestrator/app/*.py
SKIP_LIVE=1 bash scripts/check-phase50-7-langgraph-context-source-plan.sh
SKIP_LIVE=1 bash scripts/check-phase50-8-langgraph-contract-plan-ops.sh
SKIP_LIVE=1 bash scripts/check-phase50-9-langgraph-runtime-replay-export.sh
bash scripts/check-phase50-12-langgraph-closure.sh
```

- [ ] **Step 3: Run frontend verification**

Run:

```bash
npm --prefix srmp-web-ui install
npm --prefix srmp-web-ui run build
```

Expected: build succeeds. If network/dependency installation is blocked, document the blocker and keep static verification results.

- [ ] **Step 4: Commit**

Commit the completed Phase50.12 changes with:

```bash
git add docs/superpowers/plans/2026-05-05-phase50-12-langgraph-closure.md docs/phase50_12_langgraph_closure.md scripts/check-phase50-7-langgraph-context-source-plan.sh scripts/check-phase50-8-langgraph-contract-plan-ops.sh scripts/check-phase50-9-langgraph-runtime-replay-export.sh scripts/check-phase50-12-langgraph-closure.sh docker-compose.langgraph.yml srmp-ai-orchestrator/app/workflow.py srmp-web-ui/src/api/orchestrator.ts srmp-web-ui/src/views/agent/LangGraphOpsPage.vue
git commit -m "feat: close Phase50 LangGraph ops loop"
```

---

## Self-Review

- Spec coverage: The plan covers runtime graph correctness, stale acceptance scripts, Docker tag drift, frontend API coverage, frontend operations display, and documentation.
- Placeholder scan: No placeholder tasks remain; every task names exact files and verification commands.
- Type consistency: Frontend wrappers use existing `Record<string, any>` return style and existing `aiRequest` conventions.
