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

test('compact wait summary surfaces tool failures and suppresses skipped adaptive noise', () => {
  const summary = buildLiveTraceSummary({
    status: 'SUCCESS',
    currentStep: null,
    steps: [
      { label: '执行只读工具', status: 'FAILED' },
      { label: '融合 GIS 与知识库证据', status: 'SUCCESS' },
      { label: '无补充证据需要融合', status: 'SKIPPED' },
      { label: '生成回答', status: 'SUCCESS' }
    ],
    toolSummary: { planned: 4, completed: 4, success: 0, failed: 4 },
    sourceSummary: { business: 0, knowledge: 0, outline: 0 }
  })

  assert.equal(summary.currentLabel, '')
  assert.equal(summary.currentStatus, 'SUCCESS')
  assert.equal(summary.toolLabel, '工具 4/4 成功 0 失败 4')
  assert.deepEqual(
    summary.recentSteps.map((step) => step.label),
    ['执行只读工具', '融合 GIS 与知识库证据', '生成回答']
  )
})

test('compact wait summary falls back from skipped current step to latest meaningful step while running', () => {
  const summary = buildLiveTraceSummary({
    status: 'RUNNING',
    currentStep: { label: '无补充工具需要执行', status: 'SKIPPED' },
    steps: [
      { label: '执行只读工具', status: 'FAILED' },
      { label: '无补充工具需要执行', status: 'SKIPPED' }
    ],
    toolSummary: { planned: 1, completed: 1, success: 0, failed: 1 },
    sourceSummary: { business: 0, knowledge: 0, outline: 0 }
  })

  assert.equal(summary.currentLabel, '执行只读工具')
  assert.equal(summary.currentStatus, 'FAILED')
  assert.equal(summary.toolLabel, '工具 1/1 成功 0 失败 1')
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

test('AiTrace snapshot does not warn about missing answerMeta while execution is running', () => {
  const snapshot = toAiExecutionSnapshot({
    trace: {
      traceId: 'trace-running-no-meta',
      status: 'RUNNING',
      currentStep: { name: 'answer_generate', label: '生成回答', status: 'RUNNING', elapsedMs: 1200 },
      steps: [{ name: 'tool_execute', label: '执行只读工具', status: 'SUCCESS', elapsedMs: 80, count: 1 }]
    }
  })

  assert.equal(snapshot.summary.status, 'RUNNING')
  assert.equal(snapshot.currentStep.status, 'RUNNING')
  const legacyAnswerMetaNotice = new RegExp(['未返回', 'answerMeta'].join(' '))
  const legacyPipelineNotice = new RegExp(['旧任务', '旧接口', 'LangGraph'].join('|'))
  assert.doesNotMatch(snapshot.warnings.join('\n'), legacyAnswerMetaNotice)
  assert.doesNotMatch(snapshot.warnings.join('\n'), legacyPipelineNotice)
})

test('AiTrace snapshot uses admin diagnostics wording when model metadata is missing after finish', () => {
  const snapshot = toAiExecutionSnapshot({
    trace: {
      traceId: 'trace-success-no-meta',
      status: 'SUCCESS',
      steps: [{ name: 'answer_generate', label: '生成回答', status: 'SUCCESS', elapsedMs: 300 }]
    }
  })

  assert.equal(snapshot.summary.status, 'SUCCESS')
  assert.match(snapshot.warnings.join('\n'), /没有模型来源元数据/)
  const legacyNotice = new RegExp(`${['未返回', 'answerMeta'].join(' ')}|${['旧任务', '旧接口', 'LangGraph'].join('|')}`)
  assert.doesNotMatch(snapshot.warnings.join('\n'), legacyNotice)
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

test('AiTrace snapshot turns knowledge retrieval fallback into admin repair advice', () => {
  const snapshot = toAiExecutionSnapshot({
    trace: {
      traceId: 'trace-knowledge-fallback',
      status: 'SUCCESS',
      steps: [
        {
          step_name: 'tool_execute',
          step_label: '执行只读工具',
          status: 'SUCCESS',
          step_data: {
            toolResults: [
              {
                toolName: 'knowledge.retrieve',
                success: true,
                data: {
                  fallbackReason: 'no embedded chunks'
                }
              }
            ]
          }
        }
      ]
    },
    answerMeta: { llmStatus: 'SUCCESS', llmSuccess: true }
  })

  assert.equal(snapshot.tools[0].name, 'knowledge.retrieve')
  assert.equal(snapshot.tools[0].success, true)
  assert.match(snapshot.tools[0].diagnostic, /暂无可用向量切片/)
  assert.match(snapshot.warnings.join('\n'), /知识库向量未就绪/)
  assert.match(snapshot.warnings.join('\n'), /补向量或同步入库/)
  assert.deepEqual(
    snapshot.repairActions.map((item) => [item.key, item.path]),
    [
      ['VECTORIZE_OUTLINE', '/agent/ai-ops'],
      ['SYNC_OUTLINE', '/agent/outline/sync'],
      ['VERIFY_KNOWLEDGE', '/agent/knowledge-vector']
    ]
  )
})

test('AiTrace snapshot turns empty knowledge base fallback into sync advice', () => {
  const snapshot = toAiExecutionSnapshot({
    trace: {
      traceId: 'trace-empty-knowledge',
      status: 'SUCCESS',
      steps: [
        {
          step_name: 'tool_execute',
          step_label: '执行只读工具',
          status: 'SUCCESS',
          step_data: {
            toolResults: [
              {
                toolName: 'knowledge.retrieve',
                success: true,
                data: {
                  fallbackReason: 'no knowledge chunks'
                }
              }
            ]
          }
        }
      ]
    },
    answerMeta: { llmStatus: 'SUCCESS', llmSuccess: true }
  })

  assert.match(snapshot.tools[0].diagnostic, /本地知识库暂无切片/)
  assert.match(snapshot.warnings.join('\n'), /知识库暂无切片/)
  assert.match(snapshot.warnings.join('\n'), /同步 Outline 或导入知识文档/)
  assert.doesNotMatch(snapshot.warnings.join('\n'), /知识库检索提示/)
  assert.deepEqual(
    snapshot.repairActions.map((item) => [item.key, item.path]),
    [
      ['SYNC_OUTLINE', '/agent/outline/sync'],
      ['IMPORT_KNOWLEDGE', '/agent/knowledge-documents'],
      ['VERIFY_KNOWLEDGE', '/agent/knowledge-vector']
    ]
  )
})

test('AiTrace snapshot derives knowledge repair actions from answerMeta fallback when tool details are absent', () => {
  const snapshot = toAiExecutionSnapshot({
    trace: {
      traceId: 'trace-answer-meta-fallback',
      status: 'SUCCESS',
      steps: [{ step_name: 'llm_answer', step_label: '大模型回答', status: 'SUCCESS' }]
    },
    answerMeta: {
      llmStatus: 'SUCCESS',
      llmSuccess: true,
      fallbackReason: 'no embedded chunks'
    }
  })

  assert.deepEqual(
    snapshot.repairActions.map((item) => [item.key, item.path]),
    [
      ['VECTORIZE_OUTLINE', '/agent/ai-ops'],
      ['SYNC_OUTLINE', '/agent/outline/sync'],
      ['VERIFY_KNOWLEDGE', '/agent/knowledge-vector']
    ]
  )
})

test('AiTrace snapshot reads knowledge fallback from persisted rawResult data', () => {
  const snapshot = toAiExecutionSnapshot({
    record: {
      traceId: 'trace-raw-result-fallback',
      status: 'SUCCESS',
      toolResults: [
        {
          toolName: 'knowledge.retrieve',
          success: true,
          status: 'SUCCESS',
          rawResult: {
            data: {
              fallback: true,
              fallbackReason: 'no embedded chunks',
              searchMode: 'KEYWORD_FALLBACK'
            },
            count: 0,
            costMs: 9
          }
        }
      ]
    },
    answerMeta: { llmStatus: 'SUCCESS', llmSuccess: true }
  })

  assert.equal(snapshot.tools[0].fallbackReason, 'no embedded chunks')
  assert.match(snapshot.tools[0].diagnostic, /暂无可用向量切片/)
  assert.equal(snapshot.repairActions[0].key, 'VECTORIZE_OUTLINE')
})
