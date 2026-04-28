# Phase 33 Solution Draft Versioning Design

## Background

Phase 32 has completed the first map-object solution loop:

```text
map object -> solution draft generation -> preview -> copy/download
```

Phase 33 extends that loop with durable draft management. The existing project already has a mature AI solution task path:

- `ai_solution_task` stores generated solution output.
- `ai_solution_source` stores template, business data, and knowledge sources.
- `AiSolutionGenerateService` creates task records.
- `AiSolutionQualityService` checks and exports task markdown.
- `SolutionTasksPage.vue` lists existing solution tasks.

The chosen approach is to reuse this path rather than introduce a separate map-object draft module.

## Goals

1. Save a Phase 32 map-object solution preview as a durable solution task.
2. Keep version history whenever saved draft content is created or updated.
3. Support a simple draft status flow for saved drafts:

```text
DRAFT -> CONFIRMED -> ARCHIVED
DRAFT -> ARCHIVED
```

4. Let users find saved map-object drafts from the existing solution task page.
5. Preserve the Phase 32 preview/copy/download flow.

## Non-Goals

Phase 33 does not implement:

- approval workflow;
- conversion to work order;
- permission model changes;
- collaborative editing;
- full template binding for map-object generated drafts.

These stay reserved for Phase 34 and later.

## Approach

Use the existing `ai_solution_task` table as the canonical draft record. Add map-object metadata fields and introduce a version table:

- `ai_solution_task` remains the current latest draft.
- `ai_solution_task_version` stores immutable snapshots.
- `ai_solution_source` can continue to store provenance.

This keeps task listing, quality checks, and markdown export reusable, while avoiding duplicate task pages and duplicate quality/export logic.

## Data Model

Add a Phase 33 migration file:

```text
srmp-admin/src/main/resources/db/phase33_ai_solution_draft_version.sql
```

Extend `ai_solution_task`:

```sql
ALTER TABLE ai_solution_task
ADD COLUMN IF NOT EXISTS origin_type VARCHAR(50),
ADD COLUMN IF NOT EXISTS object_type VARCHAR(100),
ADD COLUMN IF NOT EXISTS object_id VARCHAR(100),
ADD COLUMN IF NOT EXISTS object_summary JSONB,
ADD COLUMN IF NOT EXISTS current_version_no INTEGER DEFAULT 1;
```

Add `ai_solution_task_version`:

```sql
CREATE TABLE IF NOT EXISTS ai_solution_task_version (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    task_id         VARCHAR(64) NOT NULL,
    version_no      INTEGER NOT NULL,
    title           VARCHAR(300),
    result_content  TEXT,
    object_summary  JSONB,
    change_note     VARCHAR(500),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

Add indexes:

```sql
CREATE INDEX IF NOT EXISTS idx_ai_solution_task_origin
ON ai_solution_task(tenant_id, origin_type, object_type, object_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_solution_task_version_no
ON ai_solution_task_version(tenant_id, task_id, version_no);
```

Keep the existing `ai_solution_task.status` column as the generation execution status. Current code writes values such as `SUCCESS`, and task queries already filter on that column, so Phase 33 must not reuse it for draft business state.

Add a separate draft lifecycle column:

```sql
ALTER TABLE ai_solution_task
ADD COLUMN IF NOT EXISTS draft_status VARCHAR(30);
```

Existing tasks remain valid with `draft_status = null`. Saved map-object drafts set:

```text
status = SUCCESS
draft_status = DRAFT
```

The draft lifecycle uses `DRAFT`, `CONFIRMED`, and `ARCHIVED`.

## Backend Design

Create a focused persistence service:

```text
AiSolutionDraftService
AiSolutionDraftServiceImpl
```

Responsibilities:

- create a saved draft from a Phase 32 response;
- update draft title/content and append a new version;
- list versions;
- update draft status with allowed transition checks.

Add request DTOs:

```text
AiSolutionDraftSaveRequest
AiSolutionDraftUpdateRequest
AiSolutionStatusUpdateRequest
```

New APIs:

```http
POST /api/agent/map-object/solution/drafts
```

Creates a task with:

- `origin_type = MAP_OBJECT`;
- `status = SUCCESS`;
- `draft_status = DRAFT`;
- `result_content = markdown`;
- `object_summary` from Phase 32 response;
- first row in `ai_solution_task_version`.

```http
PUT /api/ai/solution/tasks/{id}
```

Updates title/content and writes a new version.

```http
GET /api/ai/solution/tasks/{id}/versions
```

Returns version snapshots ordered by `version_no desc`.

```http
POST /api/ai/solution/tasks/{id}/draft-status
```

Accepts `DRAFT`, `CONFIRMED`, or `ARCHIVED` and rejects invalid transitions.

Existing task detail/list APIs should include the new metadata columns when present. `AiSolutionTaskQuery.status` continues to mean execution status, and Phase 33 adds `draftStatus` when the UI needs to filter draft lifecycle state.

## Frontend Design

Update `SolutionPreviewDialog.vue`:

- enable the existing "save draft" button;
- emit a save action to the parent;
- show save loading state;
- after save succeeds, show the saved task id and draft status.

Update `AgentChatFloat.vue`:

- call `saveMapObjectSolutionDraft()` with the generated Phase 32 response;
- pass current `mapObject`, `context`, and options for traceability;
- show success/failure message without closing the preview.

Update `src/api/agent.ts`:

- add `saveMapObjectSolutionDraft()`.

Update `src/api/solution.ts`:

- add `updateSolutionTask()`;
- add `updateSolutionTaskDraftStatus()`;
- add `getSolutionTaskVersions()`.

Update `SolutionTasksPage.vue`:

- list saved map-object drafts using existing task list;
- display origin/object metadata when present;
- add draft status actions for valid next states;
- add a version history drawer/table.

## Error Handling

- Saving without title or markdown returns a validation error.
- Updating or changing draft status for a missing task returns a clear not-found error.
- Invalid draft status transitions return an `IllegalArgumentException` message.
- Frontend surfaces errors with `ElMessage.error`.
- Save action is idempotent only from the user's perspective: repeated saves create distinct task records unless an existing `taskId` is explicitly updated later.

## Testing

Add a lightweight source check:

```text
scripts/check-phase33-solution-draft-versioning.sh
```

It should verify:

- Phase 33 migration exists;
- draft service and DTOs exist;
- draft save endpoint exists;
- versions endpoint exists;
- draft status update endpoint exists;
- frontend save API exists;
- preview dialog calls save;
- task page references versions and draft status.

Run verification:

```bash
bash scripts/check-phase33-solution-draft-versioning.sh
/tmp/codex-maven/apache-maven-3.9.6/bin/mvn -pl srmp-agent,srmp-gis -am package -DskipTests
npm run build
git diff --check
```

## Rollout

1. Apply database migration before using save/draft-status endpoints.
2. Phase 32 behavior remains available if saving fails.
3. Existing `ai_solution_task` rows stay readable because new columns are nullable/defaulted.
4. The implementation branch should not include local environment config changes.

## Self Review

- No placeholders or TODO items remain.
- Scope is limited to durable draft save, version history, and simple draft status flow.
- Approval and work-order conversion are explicitly out of scope.
- The design reuses existing task, quality, and export paths to reduce duplicate maintenance.
