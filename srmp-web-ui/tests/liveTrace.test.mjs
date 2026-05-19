import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildLiveTraceSummary,
  createWebTraceId,
  normalizeLiveTraceSnapshot,
  shouldPauseLiveTracePolling
} from '../src/utils/liveTrace.ts'
import { toAiExecutionSnapshot } from '../src/views/agent/components/aiExecution.ts'

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

test('AiTrace snapshot accepts live running trace', () => {
  const snapshot = toAiExecutionSnapshot({
    trace: {
      traceId: 'trace-live',
      status: 'RUNNING',
      currentStep: { name: 'answer_generate', label: '生成回答', status: 'RUNNING', elapsedMs: 8000 },
      steps: [{ name: 'tool_execute', label: '执行只读工具', status: 'SUCCESS', elapsedMs: 200, count: 2 }],
      toolSummary: { planned: 2, completed: 2, success: 2, failed: 0 },
      sourceSummary: { business: 4, knowledge: 1, outline: 0 },
      costMs: 8200
    },
    answerMeta: { llmStatus: 'RUNNING' }
  })

  assert.equal(snapshot.summary.traceId, 'trace-live')
  assert.equal(snapshot.summary.status, 'RUNNING')
  assert.equal(snapshot.currentStep.name, 'answer_generate')
  assert.equal(snapshot.summary.toolTotalCount, 2)
  assert.equal(snapshot.evidence.businessCount, 4)
})

test('AiTrace snapshot redacts internal strategy metadata from user-facing diagnostics', () => {
  const snapshot = toAiExecutionSnapshot({
    trace: {
      traceId: 'trace-secret',
      status: 'SUCCESS',
      steps: [
        {
          name: 'quality_guard',
          label: '回答质量保护',
          status: 'SUCCESS',
          data: {
            strategyVersion: 'phase50.11-config-health-guard-v1',
            visible: '保留'
          }
        }
      ],
      quality: {
        strategyVersion: 'phase50.11-config-health-guard-v1',
        changed: []
      }
    },
    answerMeta: {
      orchestratorStrategy: 'phase50.11-config-health-guard-v1',
      llmStatus: 'SKIPPED'
    }
  })

  const rendered = JSON.stringify(snapshot)
  assert.doesNotMatch(rendered, /phase50\.11-config-health-guard-v1/)
  assert.doesNotMatch(rendered, /strategyVersion|orchestratorStrategy/)
  assert.match(rendered, /保留/)
})

test('AiTrace snapshot normalizes persisted ai trace rows for ops troubleshooting', () => {
  const snapshot = toAiExecutionSnapshot({
    trace: {
      trace_id: 'ai-db-trace',
      request_type: 'MAP_OBJECT_SOLUTION',
      status: 'FAILED',
      total_cost_ms: 1234,
      fallback: true,
      steps: [
        {
          id: '1',
          step_name: 'evidence_fuse',
          step_label: '融合 GIS 与知识库证据',
          status: 'SUCCESS',
          cost_ms: 25,
          step_data: {
            toolSuccessCount: 1,
            toolFailedCount: 1,
            businessHitCount: 8,
            knowledgeHitCount: 2,
            toolSummary: [
              { toolName: 'gis.queryDiseases', success: true, hitCount: 8, costMs: 12 },
              { toolName: 'knowledge.retrieve', success: false, hitCount: 0, error: 'timeout' }
            ]
          }
        },
        {
          id: '2',
          step_name: 'llm_answer',
          step_label: '大模型回答',
          status: 'FAILED',
          error_message: 'LLM 返回为空',
          step_data: {
            llmStatus: 'FAILED',
            errorType: 'EMPTY_RESPONSE',
            errorMessage: 'LLM 返回为空'
          }
        }
      ]
    }
  })

  assert.equal(snapshot.summary.traceId, 'ai-db-trace')
  assert.equal(snapshot.summary.requestType, 'MAP_OBJECT_SOLUTION')
  assert.equal(snapshot.summary.costMs, 1234)
  assert.equal(snapshot.summary.toolTotalCount, 2)
  assert.equal(snapshot.summary.toolSuccessCount, 1)
  assert.equal(snapshot.summary.toolFailedCount, 1)
  assert.equal(snapshot.evidence.businessCount, 8)
  assert.equal(snapshot.evidence.knowledgeCount, 2)
  assert.equal(snapshot.tools[1].name, 'knowledge.retrieve')
  assert.equal(snapshot.tools[1].success, false)
  assert.equal(snapshot.answerMeta.llmStatus, 'FAILED')
  assert.equal(snapshot.answerMeta.errorMessage, 'LLM 返回为空')
})
