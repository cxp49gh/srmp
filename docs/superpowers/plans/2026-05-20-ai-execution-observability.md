# AI Execution Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the C-scheme AI execution observability foundation: normalized execution records, business scope, tool evidence, backend query APIs, and frontend consumption.

**Architecture:** Add a first-class `ai_execution_*` persistence model while keeping legacy `ai_trace_*` compatibility. Normalize business scope once and reuse it across Java AI tools, orchestrator audit payloads, and frontend execution diagnostics.

**Tech Stack:** Java Spring Boot/MyBatis, PostgreSQL JSONB, Python LangGraph orchestrator, Vue 3/TypeScript.

---

## File Structure

- Create `srmp-admin/src/main/resources/db/migration_202605_ai_execution_observability.sql`
  - Adds `ai_execution_run`, `ai_execution_scope`, `ai_execution_step`, `ai_execution_tool_call`, `ai_execution_evidence`, `ai_execution_answer`.
- Modify `srmp-admin/src/main/resources/db/srmp_full_init.sql`
  - Mirrors the new tables for fresh environments.
- Create Java execution DTOs under `srmp-agent/src/main/java/com/smartroad/srmp/agent/execution/dto/`
  - Query params, list item, detail record, scope, step, tool call, evidence, answer.
- Create Java mapper/service/controller under `srmp-agent/src/main/java/com/smartroad/srmp/agent/execution/`
  - Provides `/api/ai/executions` and `/api/ai/executions/{traceId}`.
- Create MyBatis mapper XML `srmp-agent/src/main/resources/mapper/agent/AiExecutionMapper.xml`.
- Create Java scope helper under `srmp-agent/src/main/java/com/smartroad/srmp/agent/tool/support/AiBusinessScope.java`.
- Modify `srmp-agent/src/main/java/com/smartroad/srmp/agent/tool/impl/AbstractJdbcAiTool.java`
  - Adds normalized scope extraction and common query params.
- Modify selected AI tools
  - `MapRegionSummaryTool`
  - `MapDiseaseQueryTool`
  - `MapAssessmentQueryTool`
  - `MapDiseaseStakeRangeTool`
  - `MapNearbyObjectTool`
  - `SolutionGenerateTool`
- Modify orchestrator files
  - `srmp-ai-orchestrator/app/planner.py`
  - `srmp-ai-orchestrator/app/observability.py`
  - `srmp-ai-orchestrator/app/live_trace.py`
- Modify frontend API and page
  - `srmp-web-ui/src/api/ai.ts`
  - `srmp-web-ui/src/views/agent/AiTracesPage.vue`
  - `srmp-web-ui/src/views/agent/components/aiExecution.ts`
  - `srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue`

## Tasks

### Task 1: Contract tests for business scope extraction

- [ ] Add Java tests proving `projectId`, `sectionTier`, object info, direction, and stake range are extracted from top-level `mapContext`, `extra.rawContext.query`, `actionInput`, and `mapObject.raw`.
- [ ] Run the test and verify it fails because the helper does not exist yet.
- [ ] Implement `AiBusinessScope`.
- [ ] Run the test and verify it passes.

### Task 2: Database migration and mapper tests

- [ ] Add the migration with normalized execution tables and indexes.
- [ ] Add MyBatis mapper test or lightweight service test for execution list/detail query shapes.
- [ ] Run the test and verify it fails before mapper implementation.
- [ ] Implement mapper XML and DTOs.
- [ ] Run the test and verify it passes.

### Task 3: Execution query API

- [ ] Add controller tests for `/api/ai/executions` and `/api/ai/executions/{traceId}`.
- [ ] Implement service fallback: new execution tables first, legacy trace diagnostics second.
- [ ] Return `legacy: true` and diagnostic warning for old records.
- [ ] Run targeted Java tests.

### Task 4: AI tool query scope

- [ ] Add tests for disease and assessment query parameter generation with `projectId`, `sectionTier`, direction, route, and stake overlap.
- [ ] Update AI tools to use shared scope params.
- [ ] Ensure tool results include `queryScope`, `totalCount`, `returnedCount`, `truncated`, `scopeWarnings`, and `failureReason`.
- [ ] Run targeted Java tests.

### Task 5: Orchestrator scope and audit enrichment

- [ ] Add Python tests for planner context args carrying `projectId`, `sectionTier`, object and stake range.
- [ ] Update runtime audit records to flatten `businessScope`.
- [ ] Update live trace payload to include business scope.
- [ ] Run targeted Python tests.

### Task 6: Frontend execution center consumption

- [ ] Add TypeScript normalization tests for new `AiExecutionRecord` shape and legacy fallback.
- [ ] Update API client to call `/api/ai/executions`.
- [ ] Update AI traces page filters and detail panel to consume normalized records.
- [ ] Keep customer-facing running state free of answerMeta/embedded chunk warnings.
- [ ] Run frontend tests or typecheck/build.

### Task 7: End-to-end verification

- [ ] Start the dev environment using `.env.dev` without local postgres/redis/minio.
- [ ] Verify `/api/ai/executions` returns list/detail data.
- [ ] Trigger one AI assistant request and confirm execution detail contains business scope, tool calls, and answer metadata.
- [ ] Verify `/agent/ai-traces` displays normalized diagnostics.

## First Deliverable

The first deliverable is a stable C-scheme foundation:

- New execution schema exists.
- Java API can return normalized records.
- Orchestrator and tools emit business scope and tool evidence.
- Frontend can read the new normalized shape while still supporting legacy records.

Advanced features such as long-term retention policy, raw payload desensitization, replay diff, and statistical dashboards are intentionally reserved for a later phase.
