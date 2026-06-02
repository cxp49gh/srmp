import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

test('orchestrator api exposes draft governance coverage', () => {
  const source = readFileSync('srmp-web-ui/src/api/orchestrator.ts', 'utf8')
  assert.match(source, /export function getAiGovernanceDraftPolicyCoverage/)
  assert.match(source, /governance\/policies\/coverage\/draft/)
})

test('governance page wires the matrix editor', () => {
  const page = readFileSync('srmp-web-ui/src/views/agent/AiGovernancePage.vue', 'utf8')
  assert.match(page, /GovernanceMatrixEditor/)
  assert.match(page, /name="matrix"/)
  assert.match(page, /能力-工具矩阵/)
})

test('governance page wires editable tool catalog to draft tools config', () => {
  const page = readFileSync('srmp-web-ui/src/views/agent/AiGovernancePage.vue', 'utf8')
  assert.match(page, /GovernanceToolCatalogEditor/)
  assert.match(page, /:tools-config="draftToolsConfig"/)
  assert.match(page, /applyStructuredToolsConfig/)
})

test('governance page sends draft configs into plan simulation', () => {
  const page = readFileSync('srmp-web-ui/src/views/agent/AiGovernancePage.vue', 'utf8')
  assert.match(page, /capabilitiesConfig:\s*draftCapabilitiesConfig/)
  assert.match(page, /toolsConfig:\s*draftToolsConfig/)
  assert.match(page, /request:\s*\{/)
  assert.match(page, /planResult\.mode/)
})

test('governance page wires evaluation cases and draft coverage', () => {
  const page = readFileSync('srmp-web-ui/src/views/agent/AiGovernancePage.vue', 'utf8')
  assert.match(page, /GovernanceEvalCaseSet/)
  assert.match(page, /runDraftPolicyCoverage/)
  assert.match(page, /name="eval-cases"/)
  const api = readFileSync('srmp-web-ui/src/api/orchestrator.ts', 'utf8')
  assert.match(api, /getAiGovernanceDraftPolicyCoverage/)
})

test('governance page wires draft review and rollback restore', () => {
  const page = readFileSync('srmp-web-ui/src/views/agent/AiGovernancePage.vue', 'utf8')
  assert.match(page, /GovernanceDraftReviewPanel/)
  assert.match(page, /applyRollbackDraft/)
  assert.match(page, /draftCoveragePayload/)
})

test('governance page surfaces policy checks and coverage comparison', () => {
  const page = readFileSync('srmp-web-ui/src/views/agent/AiGovernancePage.vue', 'utf8')
  assert.match(page, /coverageComparison/)
  assert.match(page, /regressedCount/)
  assert.match(page, /planPolicyChecks/)
  assert.match(page, /策略校验/)
})

test('trace drawer shows execution policy checks in one troubleshooting screen', () => {
  const drawer = readFileSync('srmp-web-ui/src/views/agent/components/AiTraceDrawer.vue', 'utf8')
  assert.match(drawer, /snapshot\.policyChecks/)
  assert.match(drawer, /策略校验/)
  assert.match(drawer, /policyCheckTagType/)
})
