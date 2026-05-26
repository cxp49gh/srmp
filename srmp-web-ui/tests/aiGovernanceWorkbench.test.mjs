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
