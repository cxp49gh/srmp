import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildLiveTraceSummary,
  createWebTraceId,
  normalizeLiveTraceSnapshot,
  shouldPauseLiveTracePolling
} from '../src/utils/liveTrace.ts'

test('creates stable web trace ids with prefix', () => {
  const id = createWebTraceId(() => 1710000000000, () => 0.123456)
  assert.equal(id, 'web-lg-1710000000000-4fzyo8')
})

test('normalizes running live trace snapshots', () => {
  const snapshot = normalizeLiveTraceSnapshot({
    body: {
      traceId: 'trace-1',
      status: 'RUNNING',
      currentStep: { name: 'tool_execute', label: '执行只读工具', status: 'RUNNING', elapsedMs: 2300 },
      steps: [{ name: 'intent_recognize', label: '识别用户意图', status: 'SUCCESS', elapsedMs: 25 }],
      toolSummary: { planned: 3, completed: 1, success: 1, failed: 0 },
      sourceSummary: { business: 12, knowledge: 4, outline: 0 },
      costMs: 4500
    }
  })

  assert.equal(snapshot.traceId, 'trace-1')
  assert.equal(snapshot.status, 'RUNNING')
  assert.equal(snapshot.currentStep.name, 'tool_execute')
  assert.equal(snapshot.steps.length, 1)
  assert.equal(snapshot.toolSummary.planned, 3)
  assert.equal(snapshot.sourceSummary.knowledge, 4)
})

test('builds compact wait summary', () => {
  const summary = buildLiveTraceSummary({
    status: 'RUNNING',
    currentStep: { label: '生成回答', elapsedMs: 9000 },
    steps: [{ label: '识别用户意图', status: 'SUCCESS' }],
    toolSummary: { planned: 2, completed: 2, success: 2, failed: 0 },
    sourceSummary: { business: 5, knowledge: 1, outline: 0 }
  })

  assert.equal(summary.currentLabel, '生成回答')
  assert.equal(summary.toolLabel, '工具 2/2 成功 2')
  assert.equal(summary.sourceLabel, '业务 5｜知识库 1')
})

test('pauses polling after three failures', () => {
  assert.equal(shouldPauseLiveTracePolling(2), false)
  assert.equal(shouldPauseLiveTracePolling(3), true)
})
