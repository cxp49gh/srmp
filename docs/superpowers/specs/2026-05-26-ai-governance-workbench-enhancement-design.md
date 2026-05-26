# AI Governance Workbench Enhancement Design

## Background

The AI maintenance assistant now has a governance page at `/agent/ai-governance`. It can display capabilities, tools, tool impact, readiness issues, policy examples, config drafts, publish records, and a plan simulator. The backend already exposes file-based governance config through `srmp-ai-orchestrator/app/governance_data/capabilities.json` and `tools.json`.

This is a good base, but it is still closer to a diagnostics page than a governance workbench:

- Capability and tool edits are raw JSON edits.
- Policy examples are embedded in capability JSON and are not managed as a test suite.
- Draft validation checks structure and readiness, but draft policy coverage is not a first-class workflow.
- Publish and rollback are audit-state records; rollback does not yet restore a usable draft package.
- The page helps developers inspect config, but administrators still cannot easily answer “which capability can call which tool, why, and how do I verify a change before release?”

The next step is to make governance operational: editable matrix, policy validation, publish and rollback workflow, and evaluation case sets.

## Decision

Use a phased C approach.

Phase 1 keeps the current file-based governance source of truth and `restart-required` release mode. The UI becomes a structured editor over the same `capabilitiesConfig` and `toolsConfig` JSON. Backend validation and publish records remain compatible with existing endpoints, with focused additions for draft policy coverage and rollback draft restoration.

Phase 2 can add runtime-mutable overlays or database-backed config after the model is stable. Hot publishing is intentionally not part of Phase 1.

## Goals

1. Let administrators edit the capability-tool matrix without touching raw JSON.
2. Let administrators validate a draft strategy before publish, including policy examples and selected plan simulations.
3. Make publish and rollback auditable and actionable, not only status labels.
4. Promote embedded examples into a visible evaluation case set.
5. Make it clear how to add a new capability or tool and how to verify it.
6. Preserve the existing production behavior until a reviewed draft is applied by the normal release process.

## Non-Goals

- No direct runtime config mutation in Phase 1.
- No database migration for governance config in Phase 1.
- No AI-generated policy changes without human review.
- No customer-facing AI panel changes in this work package.
- No rewrite of Java business tools or GIS query semantics.

## Product Model

The workbench has five primary concepts.

### Capability

A business-level AI ability such as `knowledge.metric_explain`, `map.route_analysis`, or `solution.generate`.

Editable fields in Phase 1:

- `enabled`
- `name`
- `category`
- `priority`
- `intent`
- trigger actions, modes, object types, and keywords
- context policy fields already present in config
- tool policy: required, optional, adaptive, prohibited
- examples used as evaluation cases

### Tool

A callable tool such as `knowledge.retrieve`, `gis.queryRegionSummary`, or `solution.generateDraft`.

Editable fields in Phase 1:

- `enabled`
- `label`
- `category`
- `description`
- `readOnly`
- `writeRisk`
- governance flags such as `adaptiveAllowed`, `customerVisible`, `adminVisible`, `allowUnboundUsage`

Tool implementation, Java registration, input schema, and output contract remain developer-owned. The UI can display them and validate references, but should not pretend it can create a working Java tool by config alone.

### Matrix

The matrix is the main editing surface:

- rows: capabilities
- columns or grouped selectors: required, optional, adaptive, prohibited
- tool options: active tools from the draft tool catalog
- row actions: validate row, simulate, open detail, reset row

The matrix writes back to `capability.toolPolicy` in the draft config.

### Evaluation Case

An evaluation case is a managed version of the current capability `examples` entry.

Fields:

- `id`
- `name`
- owning `capabilityId`
- request payload: `action`, `message`, `mapContext`, `options`
- expected capability
- required tools
- prohibited tools
- exact tool names when order or minimality matters

Cases continue to be stored under each capability in Phase 1 so existing config remains compatible.

### Publish Package

A publish package includes:

- base hashes
- draft hashes
- diff summary
- draft readiness
- draft policy coverage
- full config snapshot
- submitter, reason, status, and timeline

The package is reviewable and rollbackable.

## UI Design

Enhance `srmp-web-ui/src/views/agent/AiGovernancePage.vue` by extracting focused subcomponents instead of growing the existing file further.

Proposed components:

- `GovernanceMatrixEditor.vue`: structured capability-tool matrix.
- `GovernanceToolCatalogEditor.vue`: structured tool metadata editor.
- `GovernanceEvalCaseSet.vue`: evaluation case table and editor drawer.
- `GovernanceDraftReviewPanel.vue`: validation, coverage, diff, and publish readiness.
- `governanceDraft.ts`: pure draft transformation helpers.

The page tabs become:

1. `治理总览`
   - readiness status
   - policy coverage
   - publish mode
   - latest publish package
   - blockers

2. `能力-工具矩阵`
   - editable matrix
   - row-level validation
   - row-level plan simulation
   - impact warning when a tool moves between required, optional, adaptive, or prohibited

3. `工具目录`
   - editable metadata
   - Java registration and runtime whitelist status
   - reverse capability impact

4. `评测用例`
   - cases extracted from capability examples
   - filters by capability, status, tool, and keyword
   - run all, run selected, run current capability
   - failures linked to matrix rows

5. `草稿发布`
   - structured diff
   - validation issues
   - draft policy coverage
   - publish reason
   - publish timeline
   - rollback draft restore

6. `高级 JSON`
   - existing raw JSON editors remain for emergency or developer use
   - structured editors and JSON editors stay synchronized

## Backend Design

Keep the current active endpoints and add draft-aware operations.

Existing endpoints remain:

- `GET /governance/config`
- `POST /governance/config/draft/validate`
- `GET /governance/policies/coverage`
- `POST /governance/config/publish/request`
- `POST /governance/config/publish/requests/{requestId}/{action}`
- `POST /governance/config/publish/requests/{requestId}/rollback`

New or enhanced endpoints:

### Draft Policy Coverage

`POST /governance/policies/coverage/draft`

Request:

```json
{
  "capabilitiesConfig": {},
  "toolsConfig": {},
  "caseIds": ["knowledge.metric_explain.basic"]
}
```

Response:

```json
{
  "mode": "DRAFT_POLICY_COVERAGE",
  "draftId": "hash",
  "caseCount": 1,
  "passedCount": 1,
  "failedCount": 0,
  "cases": []
}
```

This endpoint builds an in-memory `GovernanceRegistry` from the draft and runs policy cases against that registry, without changing the active runtime registry.

### Draft Plan Simulation

Enhance `POST /governance/plan-simulate` to accept optional draft config:

```json
{
  "request": {},
  "capabilitiesConfig": {},
  "toolsConfig": {}
}
```

For compatibility, the endpoint should still accept the current `MapAiAgentRequest` shape. If draft config is present, the simulator uses the draft registry only for capability resolution and tool policy planning.

### Rollback Draft Restore

Enhance rollback response and publish detail so an applied package can produce a new draft from the previous base snapshot.

Response includes:

```json
{
  "rollbackDraft": {
    "capabilitiesConfig": {},
    "toolsConfig": {},
    "baseRequestId": "request-id",
    "targetRequestId": "request-id"
  }
}
```

The UI can load this draft into the editor, validate it, run coverage, and submit it as a normal publish request.

## Validation Rules

Phase 1 validation should include these blockers and warnings.

Blockers:

- duplicate capability id
- duplicate tool name
- missing tool reference
- same tool both allowed and prohibited in one capability
- enabled capability with missing required Java/runtime tool contract, when contract check is available
- write-risk tool in required or adaptive policy
- publish request with failed draft policy coverage

Warnings:

- enabled capability with no required or optional tools
- tool not used by any capability and not explicitly marked `allowUnboundUsage`
- adaptive tool configured while tool governance disables adaptive usage
- evaluation case without expected capability
- evaluation case with no required or prohibited assertion
- knowledge-only capability that allows GIS tools
- map-analysis capability that lacks business data tools

## Data Flow

1. User opens governance page.
2. UI loads active config, tools, capabilities, readiness, coverage, and publish records.
3. UI normalizes config into matrix rows, tool catalog rows, and evaluation cases.
4. User edits structured controls.
5. UI writes edits back to draft JSON through pure helper functions.
6. User runs validation.
7. Backend validates structure, readiness, diff, and draft policy coverage.
8. User submits publish request only when blockers are clear.
9. Reviewer approves, rejects, or marks package applied.
10. Rollback creates a restore draft, not an invisible mutation.

## Error Handling

- JSON parse errors stay local and block validation calls.
- Draft validation failures do not change active runtime config.
- Coverage failures are shown as publish blockers with case-level detail.
- Rollback is blocked if the target package is not found or not applied.
- Contract checks are warnings in local development when Java tool gateway is unavailable, but publish can require them when gateway inspection succeeds.
- The UI must always show whether it is looking at active config, draft config, or publish snapshot.

## Testing Strategy

Backend tests:

- draft coverage endpoint runs examples against draft config
- draft coverage can fail without affecting active coverage
- draft plan simulation uses draft matrix changes
- rollback response includes a restorable draft snapshot
- publish request blocks when draft coverage fails

Frontend tests:

- structured matrix helpers update `toolPolicy` correctly
- moving a tool to prohibited removes it from required, optional, and adaptive
- evaluation cases are extracted from and written back to capability examples
- governance page contains matrix, eval case set, draft review, and JSON fallback sections
- publish button reflects validation and coverage blockers

Manual verification:

- Open `http://localhost:5174/agent/ai-governance`.
- Edit `knowledge.metric_explain` to prohibit GIS tools and require `knowledge.retrieve`.
- Run draft coverage and confirm `解释 PCI 指标` only plans `knowledge.retrieve`.
- Edit `map.route_analysis` to remove `gis.queryRegionSummary` and confirm route-analysis case fails.
- Restore config and submit a publish request.
- Open publish detail and confirm snapshots and rollback draft are visible.

## Implementation Slices

1. Add pure draft model helpers and frontend tests.
2. Add draft policy coverage backend API and tests.
3. Add matrix editor and wire it to draft JSON.
4. Add evaluation case set editor and selected-case coverage.
5. Enhance publish panel with draft coverage blockers and rollback draft restore.
6. Add draft plan simulation support.
7. Rebuild frontend and orchestrator with `.env.dev` and compose `--no-deps`.

## Acceptance Criteria

- Administrators can edit capability-tool relations without raw JSON.
- Raw JSON remains available and stays synchronized with structured edits.
- Draft validation includes readiness, diff, and policy coverage.
- Evaluation cases can be viewed, filtered, edited, and run from the governance page.
- Publish requests include draft coverage results and block failed drafts.
- Rollback can load a previous base snapshot as a new editable draft.
- Active runtime behavior does not change until the normal reviewed release process is completed.
