import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const source = readFileSync(new URL('../src/views/agent/LangGraphOpsPage.vue', import.meta.url), 'utf8')

test('LangGraph ops page renders adaptive planning metric card', () => {
  assert.match(source, /自适应规划/)
  assert.match(source, /adaptivePlanningSummary/)
  assert.match(source, /adaptiveExecutedCount/)
})

test('LangGraph ops recent table exposes adaptive planning column', () => {
  assert.match(source, /label="自适应"/)
  assert.match(source, /formatAdaptiveStatus/)
  assert.match(source, /adaptiveAddedToolNames/)
})

test('LangGraph ops config panel shows adaptive planning settings', () => {
  assert.match(source, /adaptivePlanningEnabled/)
  assert.match(source, /maxAdaptiveIterations/)
})
