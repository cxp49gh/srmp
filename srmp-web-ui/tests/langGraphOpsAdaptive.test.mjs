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
  assert.match(source, /maxAdaptiveAddedTools/)
})

test('LangGraph ops page can trigger adaptive replay compare', () => {
  assert.match(source, /自适应对比/)
  assert.match(source, /adaptiveMode/)
  assert.match(source, /evidenceImproved/)
  assert.match(source, /toolDelta/)
  assert.match(source, /costDeltaMs/)
})

const apiSource = readFileSync(new URL('../src/api/orchestrator.ts', import.meta.url), 'utf8')

test('orchestrator replay API accepts adaptive mode parameter', () => {
  assert.match(apiSource, /adaptiveMode/)
  assert.match(apiSource, /params: \{ execute, adaptiveMode \}/)
})
