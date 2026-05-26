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

test('AI traces page shows knowledge readiness beside repair actions', () => {
  const content = read('src/views/agent/AiTracesPage.vue')

  assert.match(content, /import \{ getAiKnowledgeStats, getEmbeddingHealth \} from '\.\.\/\.\.\/api\/agent'/)
  assert.match(content, /import \{ getOutlineKnowledgeStats, vectorizeOutline \} from '\.\.\/\.\.\/api\/outline'/)
  assert.match(content, /import \{ buildKnowledgeReadiness \} from '\.\.\/\.\.\/utils\/aiKnowledgeReadiness'/)
  assert.match(content, /const traceKnowledgeReadiness = computed\(\(\) => buildKnowledgeReadiness\(\{ knowledgeStats, outlineStats, embedding \}\)\)/)
  assert.match(content, /async function loadTraceReadiness\(\)/)
  assert.match(content, /getAiKnowledgeStats\(\)[\s\S]*getOutlineKnowledgeStats\(\)[\s\S]*getEmbeddingHealth\(\)/)
  assert.match(content, /知识库健康/)
  assert.match(content, /traceKnowledgeReadiness\.title/)
  assert.match(content, /文档：\{\{ traceKnowledgeReadiness\.documentCount \}\}/)
  assert.match(content, /切片：\{\{ traceKnowledgeReadiness\.chunkCount \}\}/)
  assert.match(content, /已向量：\{\{ traceKnowledgeReadiness\.embeddedChunkCount \}\}/)
})

test('AI traces page prioritizes current knowledge readiness over stale trace fallback actions', () => {
  const content = read('src/views/agent/AiTracesPage.vue')

  assert.match(content, /const selectedRepairActions = computed\(\(\) => reconcileRepairActions/)
  assert.match(content, /traceKnowledgeReadiness\.value\.status/)
  assert.match(content, /v-if="selectedRepairActions\.length"/)
  assert.match(content, /v-for="action in selectedRepairActions"/)
  assert.match(content, /:repair-actions="selectedRepairActions"/)
  assert.match(content, /if \(status === 'NO_CHUNKS'\) return \[syncOutlineForNoChunks, importKnowledgeForNoChunks, verifyKnowledgeForNoChunks\]/)
  assert.match(content, /description: '同步或导入后，用同类问题验证知识库是否能返回命中。'/)
  assert.match(content, /if \(status === 'NO_EMBEDDED_CHUNKS'\) return \[vectorizeOutline, syncOutline, verifyKnowledge\]/)
})

test('AI traces page can run safe vector repair without leaving the trace page', () => {
  const content = read('src/views/agent/AiTracesPage.vue')

  assert.match(content, /import \{ getOutlineKnowledgeStats, vectorizeOutline \} from '\.\.\/\.\.\/api\/outline'/)
  assert.match(content, /const repairActionLoadingKey = ref\(''\)/)
  assert.match(content, /const lastRepairActionResult = ref\(''\)/)
  assert.match(content, /@click="handleRepairAction\(action\)"/)
  assert.match(content, /:loading="repairActionLoadingKey === action\.key"/)
  assert.match(content, /async function handleRepairAction\(action: Record<string, any>\)/)
  assert.match(content, /if \(action\.key !== 'VECTORIZE_OUTLINE'\) \{\s*go\(action\.path\)\s*return\s*\}/)
  assert.match(content, /await vectorizeOutline\(\{ force: false, limit: 200 \}\)/)
  assert.match(content, /lastRepairActionResult\.value = JSON\.stringify\(data \|\| \{\}, null, 2\)/)
  assert.match(content, /await loadTraceReadiness\(\)/)
  assert.match(content, /最近处理结果/)
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

test('AI traces page shows loading state before empty state', () => {
  const content = read('src/views/agent/AiTracesPage.vue')

  assert.match(content, /const tracesLoading = ref\(false\)/)
  assert.match(content, /v-loading="tracesLoading"/)
  assert.match(content, /v-if="!tracesLoading && traces\.length === 0"/)
  assert.match(content, /finally\s*\{\s*tracesLoading\.value = false\s*\}/)
})

test('AI traces page opens a troubleshooting overview after loading traces', () => {
  const content = read('src/views/agent/AiTracesPage.vue')

  assert.match(content, /const loadedTraces = await listAiExecutions\(query\)/)
  assert.match(content, /await selectDefaultTrace\(loadedTraces\)/)
  assert.match(content, /function selectDefaultTrace\(items: Record<string, any>\[\]\)/)
  assert.match(content, /traceIdOf\(selected\.value\)/)
  assert.match(content, /await selectTrace\(next\)/)
})

test('AI traces page shows detail loading state while fetching selected trace', () => {
  const content = read('src/views/agent/AiTracesPage.vue')

  assert.match(content, /const detailLoading = ref\(false\)/)
  assert.match(content, /<el-card class="middle-card" v-loading="detailLoading">/)
  assert.match(content, /<el-empty v-if="!detailLoading && !detail && !detailError" description="请选择 trace" \/>/)
  assert.match(content, /<template v-else-if="detail">/)
  assert.match(content, /detail\.value = null[\s\S]*detailLoading\.value = true[\s\S]*const loadedDetail = await getAiExecution\(traceIdOf\(item\)\)[\s\S]*detail\.value = loadedDetail/)
  assert.match(content, /finally\s*\{\s*if \(requestSeq === detailRequestSeq\) \{\s*detailLoading\.value = false\s*\}/)
})

test('AI traces page ignores stale detail responses after switching traces', () => {
  const content = read('src/views/agent/AiTracesPage.vue')

  assert.match(content, /let detailRequestSeq = 0/)
  assert.match(content, /const requestSeq = \+\+detailRequestSeq/)
  assert.match(content, /const loadedDetail = await getAiExecution\(traceIdOf\(item\)\)/)
  assert.match(content, /if \(requestSeq !== detailRequestSeq\) return/)
  assert.match(content, /detail\.value = loadedDetail/)
  assert.match(content, /if \(requestSeq === detailRequestSeq\) \{\s*detailLoading\.value = false\s*\}/)
})

test('AI traces page shows detail error state instead of select prompt on failed detail request', () => {
  const content = read('src/views/agent/AiTracesPage.vue')

  assert.match(content, /const detailError = ref\(''\)/)
  assert.match(content, /<el-empty v-if="!detailLoading && !detail && !detailError" description="请选择 trace" \/>/)
  assert.match(content, /<el-alert[\s\S]*v-if="detailError"[\s\S]*title="详情加载失败"[\s\S]*:description="detailError"/)
  assert.match(content, /catch \(error\)[\s\S]*if \(requestSeq !== detailRequestSeq\) return[\s\S]*detailError\.value = detailErrorMessage\(error\)/)
  assert.match(content, /function detailErrorMessage\(error: unknown\)/)
})

test('AI traces page surfaces plan execution comparison in troubleshooting overview', () => {
  const content = read('src/views/agent/AiTracesPage.vue')

  assert.match(content, /const planExecution = computed/)
  assert.match(content, /计划与实际/)
  assert.match(content, /planExecution\.plannedToolNames/)
  assert.match(content, /planExecution\.actualToolNames/)
  assert.match(content, /planExecution\.missingToolNames/)
  assert.match(content, /planExecution\.extraToolNames/)
  assert.match(content, /planExecution\.adaptiveExtraToolNames/)
  assert.match(content, /planExecution\.adaptiveReason/)
})

test('AI trace drawer renders plan execution comparison for admin troubleshooting', () => {
  const content = read('src/views/agent/components/AiTraceDrawer.vue')

  assert.match(content, /v-if="snapshot\.planExecution\.available"/)
  assert.match(content, /计划与实际/)
  assert.match(content, /snapshot\.planExecution\.plannedToolNames/)
  assert.match(content, /snapshot\.planExecution\.actualToolNames/)
  assert.match(content, /snapshot\.planExecution\.missingToolNames/)
  assert.match(content, /snapshot\.planExecution\.extraToolNames/)
  assert.match(content, /snapshot\.planExecution\.adaptiveExtraToolNames/)
  assert.match(content, /snapshot\.planExecution\.adaptiveReason/)
})
