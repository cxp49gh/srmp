import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildWaitFeedback,
  formatElapsedMs,
  normalizeLangGraphDiagnostics,
  summarizeRunTiming
} from '../src/utils/aiRunFeedback.ts'

test('formats elapsed milliseconds for short and long waits', () => {
  assert.equal(formatElapsedMs(950), '0.9s')
  assert.equal(formatElapsedMs(12_345), '12.3s')
  assert.equal(formatElapsedMs(65_000), '1m05s')
})

test('shows a long-wait notice after threshold', () => {
  assert.deepEqual(buildWaitFeedback(7_999), {
    elapsedLabel: '8.0s',
    longWait: false,
    title: '正在分析',
    message: '正在识别意图、检索证据并生成回答。'
  })
  assert.deepEqual(buildWaitFeedback(8_000), {
    elapsedLabel: '8.0s',
    longWait: true,
    title: '仍在生成',
    message: '复杂分析可能需要 30s 左右，可稍后查看执行过程确认各步骤耗时。'
  })
})

test('summarizes map-agent response timing from trace and meta', () => {
  const summary = summarizeRunTiming({
    answerMeta: { llmStatus: 'SUCCESS', llmModel: 'qwen3.6-plus' },
    trace: { costMs: 35_568, traceId: 'trace-1' },
    data: { remoteCostMs: 35_639 }
  })

  assert.equal(summary.elapsedLabel, '35.6s')
  assert.equal(summary.llmStatus, 'SUCCESS')
  assert.equal(summary.llmModel, 'qwen3.6-plus')
  assert.equal(summary.traceId, 'trace-1')
})

test('normalizes quick diagnostics without invoking LLM', () => {
  const diagnostics = normalizeLangGraphDiagnostics({
    healthDetail: {
      ok: true,
      costMs: 149,
      body: {
        status: 'UP',
        config: {
          safeConfig: {
            useLlm: true,
            llmModel: 'qwen3.6-plus',
            llmReadTimeoutSeconds: 180
          }
        },
        checks: [
          { name: 'runtime.config', ok: true },
          { name: 'java.toolGateway.list', ok: true },
          { name: 'java.toolGateway.contract', ok: true }
        ]
      }
    },
    summary: {
      provider: 'langgraph',
      langgraphReady: { ok: true, costMs: 139 },
      runtimeSummary: {
        body: {
          avgCostMs: 29_377,
          lastStatus: 'SUCCESS',
          successRate: 1,
          total: 36,
          failed: 0
        }
      }
    }
  })

  assert.deepEqual(diagnostics, {
    status: 'UP',
    provider: 'langgraph',
    runtimeOk: true,
    toolGatewayOk: true,
    contractOk: true,
    llmEnabled: true,
    llmModel: 'qwen3.6-plus',
    llmReadTimeoutSeconds: 180,
    successRateLabel: '100%',
    avgCostLabel: '29.4s',
    lastStatus: 'SUCCESS',
    total: 36,
    failed: 0,
    costLabel: '149ms'
  })
})
