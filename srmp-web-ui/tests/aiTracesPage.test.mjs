import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')

function read(path) {
  return readFileSync(resolve(root, path), 'utf8')
}

test('AI traces page reuses the execution drawer for admin troubleshooting', () => {
  const content = read('src/views/agent/AiTracesPage.vue')

  assert.match(content, /import AiTraceDrawer from '\.\/components\/AiTraceDrawer\.vue'/)
  assert.match(content, /<AiTraceDrawer[\s\S]*:trace="selectedTracePayload"[\s\S]*:answer-meta="selectedExecutionSnapshot\?\.answerMeta \|\| null"[\s\S]*:tool-results="selectedExecutionSnapshot\?\.tools \|\| \[\]"[\s\S]*:sources="selectedExecutionSnapshot\?\.evidence\.sources \|\| \[\]"[\s\S]*:record="detail"/)
  assert.match(content, /排障概览/)
  assert.match(content, /打开排障抽屉/)
  assert.match(content, /失败原因|failureReason/)
})

test('AI traces page builds a unified snapshot from persisted trace detail', () => {
  const content = read('src/views/agent/AiTracesPage.vue')

  assert.match(content, /toAiExecutionSnapshot\(\{[\s\S]*trace: selectedTracePayload\.value[\s\S]*answerMeta: extractAnswerMeta\(detail\.value\)[\s\S]*toolResults: extractToolResults\(detail\.value\)[\s\S]*sources: extractSources\(detail\.value\)[\s\S]*record: detail\.value/)
  assert.match(content, /function buildTracePayload/)
  assert.match(content, /traceId: traceIdOf\(value\)/)
  assert.match(content, /totalCostMs: value\.total_cost_ms \?\? value\.totalCostMs/)
  assert.match(content, /function extractAnswerMeta/)
  assert.match(content, /function extractToolResults/)
})

test('AI trace drawer hides empty answerMeta notice while execution is still running', () => {
  const content = read('src/views/agent/components/AiTraceDrawer.vue')

  assert.match(content, /v-if="shouldShowAnswerSourceAlert\(snapshot\)"/)
  assert.match(content, /function shouldShowAnswerSourceAlert/)
  assert.match(content, /isRunningSnapshot/)
  assert.doesNotMatch(content, /<AnswerSourceAlert :meta="snapshot\.answerMeta" allow-empty \/>/)
})

test('AI trace drawer renders evidence source rows for admin troubleshooting', () => {
  const content = read('src/views/agent/components/AiTraceDrawer.vue')

  assert.match(content, /v-for="source in snapshot\.evidence\.sources"/)
  assert.match(content, /sourceTitle\(source\)/)
  assert.match(content, /sourceExcerpt\(source\)/)
  assert.match(content, /sourceType\(source\)/)
})
