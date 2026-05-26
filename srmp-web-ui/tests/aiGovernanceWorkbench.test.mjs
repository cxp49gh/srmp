import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

test('orchestrator api exposes draft governance coverage', () => {
  const source = readFileSync('srmp-web-ui/src/api/orchestrator.ts', 'utf8')
  assert.match(source, /export function getAiGovernanceDraftPolicyCoverage/)
  assert.match(source, /governance\/policies\/coverage\/draft/)
})
