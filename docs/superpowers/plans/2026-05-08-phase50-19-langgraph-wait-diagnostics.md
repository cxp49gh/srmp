# Phase50.19 LangGraph Wait Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make long LangGraph waits visible in the GIS AI assistant and add a quick non-LLM status diagnostic.

**Architecture:** Add small frontend utility functions for timing and diagnostics, then wire them into `AgentChatFloat.vue`. Reuse existing orchestrator ops APIs from Java; do not change LangGraph generation.

**Tech Stack:** Vue 3, Element Plus, Node test runner, existing Java orchestrator ops endpoints.

---

### Task 1: Timing Feedback Utility

**Files:**
- Create: `srmp-web-ui/src/utils/aiRunFeedback.ts`
- Test: `srmp-web-ui/tests/aiRunFeedback.test.mjs`

- [ ] Write failing tests for elapsed formatting and long-wait messaging.
- [ ] Implement `formatElapsedMs`, `buildWaitFeedback`, and `summarizeRunTiming`.
- [ ] Verify with `node --no-warnings --test tests/aiRunFeedback.test.mjs`.

### Task 2: Diagnostic Normalization Utility

**Files:**
- Modify: `srmp-web-ui/src/utils/aiRunFeedback.ts`
- Test: `srmp-web-ui/tests/aiRunFeedback.test.mjs`

- [ ] Write failing tests that combine health-detail and summary payloads into a compact diagnostic object.
- [ ] Implement `normalizeLangGraphDiagnostics`.
- [ ] Verify with `node --no-warnings --test tests/aiRunFeedback.test.mjs`.

### Task 3: Frontend API Wrapper

**Files:**
- Modify: `srmp-web-ui/src/api/orchestrator.ts`

- [ ] Add `getOrchestratorQuickDiagnostics()` that calls health-detail without gateway/contract expansion and summary with `recentLimit=3`.
- [ ] Keep the wrapper frontend-only and non-LLM.

### Task 4: GIS AI Assistant Wiring

**Files:**
- Modify: `srmp-web-ui/src/views/gis/components/AgentChatFloat.vue`

- [ ] Track request start time and update elapsed time while loading.
- [ ] Show a compact wait panel during loading; after 8 seconds show the LangGraph long-wait message.
- [ ] Add "状态诊断" action and a compact diagnostics result panel.
- [ ] Include elapsed timing in assistant message metadata.

### Task 5: Verification

**Files:**
- No additional files.

- [ ] Run `node --no-warnings --test tests/latestRequestGuard.test.mjs tests/aiRunFeedback.test.mjs`.
- [ ] Run `npm run build` in `srmp-web-ui`.
- [ ] Run a Playwright smoke against `/gis/one-map` and click "状态诊断".
- [ ] Commit and push.
