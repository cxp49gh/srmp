<template>
  <AgentPageShell title="AI 调用监控" description="按 traceId 快速定位模型、工具、证据和失败原因。">
    <div class="page-grid">
      <el-card class="left-card" v-loading="tracesLoading">
        <template #header>
          <div class="card-header">
            <span>调用列表</span>
            <el-button size="small" :loading="tracesLoading" @click="loadTraces">刷新</el-button>
          </div>
        </template>

        <el-form :inline="true" class="query-form">
          <el-form-item>
            <el-select v-model="query.status" clearable placeholder="状态" style="width: 130px">
              <el-option label="SUCCESS" value="SUCCESS" />
              <el-option label="FAILED" value="FAILED" />
              <el-option label="TIMEOUT" value="TIMEOUT" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-input v-model="query.keyword" clearable placeholder="traceId / 问题" style="width: 220px" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="query.projectId" clearable placeholder="项目ID" style="width: 180px" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="query.routeCode" clearable placeholder="路线编码" style="width: 140px" />
          </el-form-item>
          <el-form-item>
            <el-input v-model="query.toolName" clearable placeholder="工具名" style="width: 180px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="tracesLoading" @click="loadTraces">查询</el-button>
          </el-form-item>
        </el-form>

        <el-empty v-if="!tracesLoading && traces.length === 0" description="暂无 trace" />
        <div
          v-for="item in traces"
          :key="traceIdOf(item)"
          :class="['trace-item', traceIdOf(selected) === traceIdOf(item) ? 'active' : '']"
          @click="selectTrace(item)"
        >
          <div class="row">
            <strong>{{ traceIdOf(item) || '-' }}</strong>
            <el-tag size="small" :type="tagType(item.status)">{{ item.status || '-' }}</el-tag>
          </div>
          <p>{{ item.user_message || item.userMessage || '-' }}</p>
          <div class="meta-line">
            <span>{{ item.request_type || item.requestType || item.mode || '-' }}</span>
            <span v-if="item.businessScope?.projectId">项目 {{ item.businessScope.projectId }}</span>
            <span v-if="item.businessScope?.routeCode">路线 {{ item.businessScope.routeCode }}</span>
            <span>{{ item.total_cost_ms ?? item.totalCostMs ?? 0 }}ms</span>
            <span v-if="isFallback(item)">降级</span>
            <span v-if="item.legacy">旧记录</span>
            <span v-if="item.created_at || item.createdAt">{{ item.created_at || item.createdAt }}</span>
          </div>
        </div>
      </el-card>

      <el-card class="middle-card" v-loading="detailLoading">
        <template #header>
          <div class="card-header">
            <span>排障概览</span>
            <div class="header-actions">
              <el-button v-if="detailTraceId" size="small" @click="copyTraceId">复制 traceId</el-button>
              <el-button v-if="detailTraceId" size="small" type="primary" @click="openTraceDrawer">打开排障抽屉</el-button>
            </div>
          </div>
        </template>

        <el-empty v-if="!detailLoading && !detail" description="请选择 trace" />
        <template v-else-if="detail">
          <section class="summary-grid">
            <div class="summary-cell">
              <span>状态</span>
              <strong>{{ selectedExecutionSnapshot?.summary.status || detail.status || '-' }}</strong>
            </div>
            <div class="summary-cell">
              <span>模型</span>
              <strong>{{ llmStatusLabel }}</strong>
            </div>
            <div class="summary-cell">
              <span>工具</span>
              <strong>{{ toolSummaryLabel }}</strong>
            </div>
            <div class="summary-cell">
              <span>证据</span>
              <strong>{{ evidenceSummaryLabel }}</strong>
            </div>
          </section>

          <el-alert
            v-if="failureReason"
            class="mb"
            type="error"
            show-icon
            :closable="false"
            :title="failureReason"
          />

          <AnswerSourceAlert
            v-if="shouldShowAnswerSourceAlert(selectedExecutionSnapshot)"
            :meta="selectedExecutionSnapshot?.answerMeta"
            allow-empty
          />

          <section v-if="selectedExecutionSnapshot?.businessScope && Object.keys(selectedExecutionSnapshot.businessScope).length" class="detail-section">
            <div class="section-title">
              <h3>业务范围</h3>
              <span>{{ scopeBrief(selectedExecutionSnapshot.businessScope) }}</span>
            </div>
            <div class="scope-grid">
              <div><span>项目</span><strong>{{ selectedExecutionSnapshot.businessScope.projectId || '-' }}</strong></div>
              <div><span>路线</span><strong>{{ selectedExecutionSnapshot.businessScope.routeCode || '-' }}</strong></div>
              <div><span>年份</span><strong>{{ selectedExecutionSnapshot.businessScope.year || '-' }}</strong></div>
              <div><span>层级</span><strong>{{ selectedExecutionSnapshot.businessScope.sectionTier || '-' }}</strong></div>
              <div><span>对象</span><strong>{{ selectedExecutionSnapshot.businessScope.objectType || '-' }}</strong></div>
              <div><span>桩号</span><strong>{{ stakeRangeLabel(selectedExecutionSnapshot.businessScope) }}</strong></div>
            </div>
          </section>

          <section class="detail-section">
            <h3>用户问题</h3>
            <pre>{{ detail.user_message || detail.userMessage || '-' }}</pre>
          </section>

          <section class="detail-section">
            <div class="section-title">
              <h3>工具与证据</h3>
              <span>{{ toolSummaryLabel }} / {{ evidenceSummaryLabel }}</span>
            </div>
            <el-empty
              v-if="(selectedExecutionSnapshot?.tools.length || 0) === 0"
              description="暂无工具明细"
            />
            <div v-else class="tool-list">
              <div v-for="tool in selectedExecutionSnapshot?.tools" :key="tool.name" class="tool-row">
                <span>{{ tool.name }}</span>
                <el-tag size="small" :type="tool.success ? 'success' : 'danger'">{{ tool.success ? 'SUCCESS' : 'FAILED' }}</el-tag>
                <span class="muted">count={{ tool.count ?? '-' }}</span>
                <span v-if="tool.costMs !== undefined" class="muted">{{ tool.costMs }}ms</span>
                <span v-if="tool.error" class="error">{{ tool.error }}</span>
              </div>
            </div>
          </section>

          <section class="detail-section">
            <h3>调用链路</h3>
            <el-timeline>
              <el-timeline-item
                v-for="step in selectedExecutionSnapshot?.steps || []"
                :key="step.key"
                :type="timelineType(step.status)"
                :timestamp="`${step.costMs || 0}ms`"
              >
                <div class="step-title">
                  <strong>{{ step.label || step.name }}</strong>
                  <el-tag size="small" :type="tagType(step.status)">{{ step.status }}</el-tag>
                </div>
                <div class="step-meta">count={{ step.count ?? '-' }}</div>
                <div v-if="step.error" class="error">{{ step.error }}</div>
              </el-timeline-item>
            </el-timeline>
          </section>
        </template>
      </el-card>
    </div>

    <AiTraceDrawer
      v-model:visible="traceDrawerVisible"
      :trace="selectedTracePayload"
      :answer-meta="selectedExecutionSnapshot?.answerMeta || null"
      :tool-results="selectedExecutionSnapshot?.tools || []"
      :sources="selectedExecutionSnapshot?.evidence.sources || []"
      :record="detail"
    />
  </AgentPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import AiTraceDrawer from './components/AiTraceDrawer.vue'
import AnswerSourceAlert from './components/AnswerSourceAlert.vue'
import { toAiExecutionSnapshot } from './components/aiExecution'
import { getAiExecution, listAiExecutions } from '../../api/trace'

const query = reactive({ status: '', keyword: '', projectId: '', routeCode: '', toolName: '', limit: 50 })
const traces = ref<Record<string, any>[]>([])
const tracesLoading = ref(false)
const detailLoading = ref(false)
const selected = ref<Record<string, any> | null>(null)
const detail = ref<Record<string, any> | null>(null)
const traceDrawerVisible = ref(false)
let detailRequestSeq = 0

const detailTraceId = computed(() => traceIdOf(detail.value))
const selectedTracePayload = computed(() => buildTracePayload(detail.value))
const selectedExecutionSnapshot = computed(() => toAiExecutionSnapshot({
  trace: selectedTracePayload.value,
  answerMeta: extractAnswerMeta(detail.value),
  toolResults: extractToolResults(detail.value),
  sources: extractSources(detail.value),
  record: detail.value
}))

const llmStatusLabel = computed(() => {
  const meta = selectedExecutionSnapshot.value?.answerMeta || {}
  const status = meta.llmStatus || meta.llm_status
  if (status) return formatStatus(status)
  if (!Object.keys(meta).length) return '未知'
  return meta.llmSuccess ? '成功' : '未成功'
})

const toolSummaryLabel = computed(() => {
  const summary = selectedExecutionSnapshot.value?.summary
  if (!summary) return '0/0'
  return `${summary.toolSuccessCount || 0}/${summary.toolTotalCount || 0}`
})

const evidenceSummaryLabel = computed(() => {
  const evidence = selectedExecutionSnapshot.value?.evidence
  if (!evidence) return '0'
  const business = evidence.businessCount || 0
  const knowledge = evidence.knowledgeCount || 0
  const outline = evidence.outlineCount || 0
  if (business || knowledge || outline) return `业务 ${business} / 知识 ${knowledge} / Outline ${outline}`
  return String(evidence.sourceCount || 0)
})

const failureReason = computed(() => {
  const meta = selectedExecutionSnapshot.value?.answerMeta || {}
  return stringValue(
    detail.value?.error_message,
    detail.value?.errorMessage,
    meta.fallbackReason,
    meta.fallback_reason,
    meta.errorMessage,
    meta.error_message,
    selectedExecutionSnapshot.value?.steps.find((item) => item.status === 'FAILED' || item.status === 'TIMEOUT')?.error
  )
})

onMounted(loadTraces)

async function loadTraces() {
  tracesLoading.value = true
  try {
    const loadedTraces = await listAiExecutions(query)
    traces.value = loadedTraces
    await selectDefaultTrace(loadedTraces)
  } finally {
    tracesLoading.value = false
  }
}

async function selectDefaultTrace(items: Record<string, any>[]) {
  const currentTraceId = traceIdOf(selected.value)
  const next = items.find((item) => traceIdOf(item) === currentTraceId) || items[0]
  if (!next) {
    detailRequestSeq += 1
    selected.value = null
    detail.value = null
    detailLoading.value = false
    return
  }
  if (traceIdOf(detail.value) === traceIdOf(next)) {
    selected.value = next
    return
  }
  await selectTrace(next)
}

async function selectTrace(item: Record<string, any>) {
  const requestSeq = ++detailRequestSeq
  selected.value = item
  detail.value = null
  detailLoading.value = true
  try {
    const loadedDetail = await getAiExecution(traceIdOf(item))
    if (requestSeq !== detailRequestSeq) return
    detail.value = loadedDetail
  } finally {
    if (requestSeq === detailRequestSeq) {
      detailLoading.value = false
    }
  }
}

async function copyTraceId() {
  await navigator.clipboard.writeText(detailTraceId.value || '')
  ElMessage.success('traceId 已复制')
}

function openTraceDrawer() {
  traceDrawerVisible.value = true
}

function buildTracePayload(value: Record<string, any> | null): Record<string, any> | null {
  if (!value) return null
  return {
    ...value,
    traceId: traceIdOf(value),
    requestType: value.request_type || value.requestType,
    status: value.status,
    mode: value.mode,
    fallback: isFallback(value),
    totalCostMs: value.total_cost_ms ?? value.totalCostMs,
    costMs: value.cost_ms ?? value.costMs,
    error: value.error_message || value.errorMessage,
    steps: Array.isArray(value.steps) ? value.steps : []
  }
}

function extractAnswerMeta(value: Record<string, any> | null): Record<string, any> {
  if (!value) return {}
  const direct = firstRecord(value.answerMeta, value.answer_meta, value.responsePreview?.answerMeta, value.response_preview?.answerMeta)
  if (Object.keys(direct).length) return direct
  for (const step of Array.isArray(value.steps) ? value.steps : []) {
    const data = firstRecord(step.data, step.step_data)
    const meta = firstRecord(data.answerMeta, data.answer_meta)
    if (Object.keys(meta).length) return meta
    if (data.llmStatus || data.llm_status || data.answerSource || data.answer_source || data.llmSuccess !== undefined) {
      return {
        answerSource: data.answerSource || data.answer_source || (data.llmSuccess ? 'LLM' : undefined),
        answerSourceLabel: data.answerSourceLabel || data.answer_source_label,
        llmSuccess: data.llmSuccess ?? data.llm_success,
        llmStatus: data.llmStatus || data.llm_status || step.status,
        llmModel: data.llmModel || data.llm_model || data.model,
        fallbackReason: data.fallbackReason || data.fallback_reason || data.reason || data.errorMessage || data.error_message
      }
    }
  }
  return {}
}

function extractToolResults(value: Record<string, any> | null): any[] {
  if (!value) return []
  const direct = firstArray(value.toolResults, value.tool_results, value.responsePreview?.toolResults, value.response_preview?.toolResults)
  if (direct.length) return direct
  for (const step of Array.isArray(value.steps) ? value.steps : []) {
    const data = firstRecord(step.data, step.step_data)
    const toolSummary = firstArray(data.toolSummary, data.tool_summary)
    if (toolSummary.length) return toolSummary
  }
  return []
}

function extractSources(value: Record<string, any> | null): any[] {
  if (!value) return []
  return firstArray(
    value.sources,
    value.knowledgeSources,
    value.knowledge_sources,
    value.responsePreview?.sources,
    value.response_preview?.sources
  )
}

function traceIdOf(value?: Record<string, any> | null) {
  return String(value?.trace_id || value?.traceId || '')
}

function isFallback(value?: Record<string, any> | null) {
  const raw = value?.fallback ?? value?.fallbackLike ?? value?.fallback_like
  return raw === true || String(raw).toLowerCase() === 'true'
}

function tagType(status?: string) {
  const normalized = String(status || '').toUpperCase()
  if (normalized === 'SUCCESS') return 'success'
  if (normalized === 'FAILED') return 'danger'
  if (normalized === 'TIMEOUT') return 'warning'
  if (normalized === 'SKIPPED') return 'info'
  return 'info'
}

function timelineType(status?: string) {
  const normalized = String(status || '').toUpperCase()
  if (normalized === 'SUCCESS') return 'success'
  if (normalized === 'FAILED' || normalized === 'TIMEOUT') return 'danger'
  return 'info'
}

function formatStatus(status: any) {
  const normalized = String(status || '').toUpperCase()
  if (normalized === 'SUCCESS') return '成功'
  if (normalized === 'FAILED') return '失败'
  if (normalized === 'TIMEOUT') return '超时'
  if (normalized === 'SKIPPED') return '未调用'
  return String(status || '-')
}

function firstRecord(...values: any[]): Record<string, any> {
  for (const value of values) {
    if (value && typeof value === 'object' && !Array.isArray(value) && Object.keys(value).length) return value
  }
  return {}
}

function firstArray(...values: any[]): any[] {
  for (const value of values) {
    if (Array.isArray(value)) return value
  }
  return []
}

function stringValue(...values: any[]) {
  for (const value of values) {
    if (value !== undefined && value !== null && value !== '') return String(value)
  }
  return ''
}

function scopeBrief(scope: Record<string, any>) {
  return [scope.projectId, scope.routeCode, scope.sectionTier, scope.objectType].filter(Boolean).join(' / ') || '已记录业务范围'
}

function stakeRangeLabel(scope: Record<string, any>) {
  const start = scope.startStake ?? scope.start_stake
  const end = scope.endStake ?? scope.end_stake
  if (start === undefined && end === undefined) return '-'
  return `${start ?? '-'} - ${end ?? '-'}`
}

function shouldShowAnswerSourceAlert(value: any) {
  if (value?.answerMeta && Object.keys(value.answerMeta).length) return true
  const status = String(value?.summary?.status || value?.currentStep?.status || '').toUpperCase()
  return !['RUNNING', 'PROCESSING', 'PENDING', 'QUEUED'].includes(status)
}
</script>

<style scoped>
.page-grid {
  display: grid;
  grid-template-columns: 430px minmax(560px, 1fr);
  gap: 16px;
}

.left-card,
.middle-card {
  min-height: calc(100vh - 130px);
}

.card-header,
.row,
.step-title,
.section-title {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.query-form {
  margin-bottom: 12px;
}

.trace-item {
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
  margin-bottom: 10px;
  cursor: pointer;
  font-size: 13px;
}

.trace-item.active {
  background: #dbeafe;
}

.trace-item p {
  color: #475569;
  margin: 6px 0;
  word-break: break-all;
}

.meta-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: #64748b;
  font-size: 12px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.scope-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.scope-grid div {
  min-width: 0;
  padding: 8px;
  border-radius: 6px;
  background: #f8fafc;
}

.scope-grid span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.scope-grid strong {
  display: block;
  margin-top: 4px;
  word-break: break-all;
}

.summary-cell {
  min-width: 0;
  padding: 10px;
  border-radius: 8px;
  background: #f8fafc;
}

.summary-cell span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.summary-cell strong {
  display: block;
  margin-top: 4px;
  font-size: 15px;
  word-break: break-word;
}

.detail-section {
  margin-top: 16px;
}

.detail-section h3 {
  margin: 0 0 10px;
  font-size: 15px;
}

.section-title {
  align-items: center;
}

.section-title span,
.step-meta,
.muted {
  color: #64748b;
  font-size: 12px;
}

.tool-list {
  border-top: 1px solid #eef2f7;
}

.tool-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid #eef2f7;
}

.mb {
  margin-bottom: 12px;
}

pre {
  white-space: pre-wrap;
  word-break: break-word;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 8px;
  padding: 12px;
  line-height: 1.6;
  margin: 0;
}

.error {
  margin-top: 6px;
  color: #dc2626;
  word-break: break-all;
}

@media (max-width: 1280px) {
  .page-grid {
    grid-template-columns: 1fr;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
