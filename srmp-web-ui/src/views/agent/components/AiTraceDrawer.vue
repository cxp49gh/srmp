<template>
  <el-drawer
    :model-value="visible"
    title="AI 执行过程"
    size="640px"
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-empty v-if="!snapshot" description="暂无 AI 执行过程" />
    <template v-else>
      <el-descriptions :column="2" border size="small" class="trace-meta">
        <el-descriptions-item label="Provider">{{ snapshot.summary.provider || '-' }}</el-descriptions-item>
        <el-descriptions-item label="意图">{{ snapshot.summary.intent || '-' }}</el-descriptions-item>
        <el-descriptions-item label="能力">{{ capabilityLabel }}</el-descriptions-item>
        <el-descriptions-item label="能力意图">{{ snapshot.summary.capabilityIntent || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ snapshot.summary.status || '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ snapshot.summary.costMs || 0 }} ms</el-descriptions-item>
        <el-descriptions-item label="工具">{{ snapshot.summary.toolSuccessCount || 0 }}/{{ snapshot.summary.toolTotalCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ snapshot.summary.sourceCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="TraceId">{{ snapshot.summary.traceId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="RecordId">{{ snapshot.summary.recordId || '-' }}</el-descriptions-item>
      </el-descriptions>

      <AnswerSourceAlert v-if="shouldShowAnswerSourceAlert(snapshot)" :meta="snapshot.answerMeta" allow-empty />

      <section v-if="snapshot.diagnosis" class="trace-section diagnosis-panel">
        <div class="step-title">
          <h3>诊断结论</h3>
          <el-tag size="small" :type="diagnosisTagType(snapshot.diagnosis.severity)">
            {{ snapshot.diagnosis.title }}
          </el-tag>
        </div>
        <el-alert
          :type="diagnosisAlertType(snapshot.diagnosis.severity)"
          show-icon
          :closable="false"
          :title="snapshot.diagnosis.summary"
          :description="snapshot.diagnosis.cause"
        />
        <div class="diagnosis-tags">
          <el-tag
            v-for="tag in snapshot.diagnosis.tags"
            :key="tag.label"
            size="small"
            :type="diagnosisTagType(tag.type || 'info')"
            effect="plain"
          >
            {{ tag.label }}：{{ tag.value }}
          </el-tag>
        </div>
      </section>

      <section v-if="snapshot.planExecution.available" class="trace-section plan-panel">
        <div class="step-title">
          <h3>计划与实际</h3>
          <el-tag size="small" :type="planExecutionTagType(snapshot.planExecution.status)">{{ snapshot.planExecution.status }}</el-tag>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="计划 action">{{ snapshot.planExecution.plannedAction || '-' }}</el-descriptions-item>
          <el-descriptions-item label="实际 action">{{ snapshot.planExecution.actualAction || '-' }}</el-descriptions-item>
          <el-descriptions-item label="计划 intent">{{ snapshot.planExecution.plannedIntent || '-' }}</el-descriptions-item>
          <el-descriptions-item label="实际 intent">{{ snapshot.planExecution.actualIntent || '-' }}</el-descriptions-item>
          <el-descriptions-item label="计划 trace">{{ snapshot.planExecution.planTraceId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="执行 trace">{{ snapshot.planExecution.runTraceId || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="plan-compare-grid">
          <div>
            <span>计划工具</span>
            <div class="tag-list">
              <el-tag v-for="name in snapshot.planExecution.plannedToolNames" :key="`planned-${name}`" size="small" type="info">{{ name }}</el-tag>
              <span v-if="!snapshot.planExecution.plannedToolNames.length" class="muted">-</span>
            </div>
          </div>
          <div>
            <span>实际工具</span>
            <div class="tag-list">
              <el-tag v-for="name in snapshot.planExecution.actualToolNames" :key="`actual-${name}`" size="small" type="success">{{ name }}</el-tag>
              <span v-if="!snapshot.planExecution.actualToolNames.length" class="muted">-</span>
            </div>
          </div>
          <div>
            <span>缺失工具</span>
            <div class="tag-list">
              <el-tag v-for="name in snapshot.planExecution.missingToolNames" :key="`missing-${name}`" size="small" type="danger">{{ name }}</el-tag>
              <span v-if="!snapshot.planExecution.missingToolNames.length" class="muted">-</span>
            </div>
          </div>
          <div>
            <span>额外工具</span>
            <div class="tag-list">
              <el-tag v-for="name in snapshot.planExecution.extraToolNames" :key="`extra-${name}`" size="small" type="warning">{{ name }}</el-tag>
              <span v-if="!snapshot.planExecution.extraToolNames.length" class="muted">-</span>
            </div>
          </div>
          <div>
            <span>自适应追加</span>
            <div class="tag-list">
              <el-tag v-for="name in snapshot.planExecution.adaptiveExtraToolNames" :key="`adaptive-${name}`" size="small">{{ name }}</el-tag>
              <span v-if="!snapshot.planExecution.adaptiveExtraToolNames.length" class="muted">-</span>
            </div>
          </div>
          <div>
            <span>来源差异</span>
            <div class="tag-list">
              <el-tag v-for="name in snapshot.planExecution.missingSourceTypes" :key="`source-${name}`" size="small" type="warning">{{ name }}</el-tag>
              <span v-if="!snapshot.planExecution.missingSourceTypes.length" class="muted">-</span>
            </div>
          </div>
        </div>
        <el-alert
          v-if="snapshot.planExecution.adaptiveReason"
          class="trace-info"
          type="info"
          show-icon
          :closable="false"
          :title="snapshot.planExecution.adaptiveReason"
        />
        <el-alert
          v-for="warning in snapshot.planExecution.warnings"
          :key="warning.code"
          class="trace-info"
          :type="warning.level === 'ERROR' ? 'error' : warning.level === 'WARN' ? 'warning' : 'info'"
          show-icon
          :closable="false"
          :title="warning.message || warning.code"
        />
      </section>

      <section v-if="snapshot.policyChecks.length" class="trace-section policy-panel">
        <div class="step-title">
          <h3>策略校验</h3>
          <el-tag size="small" :type="policyCheckTagType(snapshot.policyStatus)">{{ snapshot.policyStatus }}</el-tag>
        </div>
        <div class="policy-check-list">
          <div v-for="check in snapshot.policyChecks" :key="check.code + check.message" class="policy-check-row">
            <el-tag size="small" :type="policyCheckTagType(check.status)">{{ check.status }}</el-tag>
            <div>
              <strong>{{ check.code }}</strong>
              <span>{{ check.message || '-' }}</span>
              <div class="tag-list">
                <el-tag v-for="name in check.expectedToolNames" :key="`${check.code}-expected-${name}`" size="small" effect="plain">{{ name }}</el-tag>
                <el-tag v-for="name in check.prohibitedToolNames" :key="`${check.code}-blocked-${name}`" size="small" type="danger">{{ name }}</el-tag>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section v-if="Object.keys(snapshot.businessScope || {}).length" class="trace-section">
        <h3>业务范围</h3>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="项目">{{ snapshot.businessScope.projectId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="路线">{{ snapshot.businessScope.routeCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="年份">{{ snapshot.businessScope.year || '-' }}</el-descriptions-item>
          <el-descriptions-item label="层级">{{ snapshot.businessScope.sectionTier || '-' }}</el-descriptions-item>
          <el-descriptions-item label="对象">{{ snapshot.businessScope.objectType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="对象ID">{{ snapshot.businessScope.objectId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="方向">{{ snapshot.businessScope.direction || '-' }}</el-descriptions-item>
          <el-descriptions-item label="桩号">{{ stakeRangeLabel(snapshot.businessScope) }}</el-descriptions-item>
        </el-descriptions>
      </section>

      <section v-if="snapshot.currentStep" class="trace-section current-step">
        <h3>当前步骤</h3>
        <div class="step-title">
          <strong>{{ snapshot.currentStep.label }}</strong>
          <el-tag size="small" :type="tagType(snapshot.currentStep.status)">{{ snapshot.currentStep.status }}</el-tag>
        </div>
        <div class="step-meta">{{ snapshot.currentStep.costMs || 0 }}ms</div>
        <div v-if="snapshot.currentStep.error" class="error">{{ snapshot.currentStep.error }}</div>
      </section>

      <el-alert
        v-for="warning in snapshot.warnings"
        :key="warning"
        class="trace-warning"
        type="warning"
        show-icon
        :closable="false"
        :title="warning"
      />

      <section v-if="drawerRepairActions.length" class="trace-section repair-panel">
        <h3>建议处理</h3>
        <div class="repair-actions">
          <el-button
            v-for="action in drawerRepairActions"
            :key="action.key"
            size="small"
            :type="action.type || 'primary'"
            @click="go(action.path)"
          >
            {{ action.label }}
          </el-button>
        </div>
        <div class="repair-list">
          <div v-for="action in drawerRepairActions" :key="`${action.key}-desc`" class="repair-item">
            <strong>{{ action.label }}</strong>
            <span>{{ action.description }}</span>
          </div>
        </div>
      </section>

      <section class="trace-section">
        <h3>执行时间线</h3>
        <el-empty v-if="snapshot.steps.length === 0" description="暂无步骤" />
        <el-timeline v-else>
          <el-timeline-item
            v-for="step in snapshot.steps"
            :key="step.key"
            :type="timelineType(step.status)"
            :timestamp="`${step.costMs || 0}ms`"
          >
            <div class="step-title">
              <strong>{{ step.label }}</strong>
              <el-tag size="small" :type="tagType(step.status)">{{ step.status }}</el-tag>
            </div>
            <div class="step-meta">count={{ step.count ?? '-' }}</div>
            <div v-if="step.error" class="error">{{ step.error }}</div>
            <el-collapse v-if="step.data && Object.keys(step.data).length" class="detail-collapse">
              <el-collapse-item title="步骤数据" :name="step.key">
                <pre>{{ formatJson(step.data) }}</pre>
              </el-collapse-item>
            </el-collapse>
          </el-timeline-item>
        </el-timeline>
      </section>

      <section class="trace-section">
        <h3>工具与证据</h3>
        <el-empty v-if="snapshot.tools.length === 0 && snapshot.evidence.sources.length === 0" description="暂无工具或来源" />
        <template v-else>
          <div v-for="tool in snapshot.tools" :key="tool.name" class="tool-row">
            <span>{{ tool.name }}</span>
            <el-tag size="small" :type="tool.success ? 'success' : 'danger'">{{ tool.success ? 'SUCCESS' : 'FAILED' }}</el-tag>
            <span class="muted">count={{ tool.count ?? '-' }}</span>
            <span v-if="tool.costMs !== undefined" class="muted">{{ tool.costMs }}ms</span>
            <span v-if="tool.diagnostic" class="diagnostic">{{ tool.diagnostic }}</span>
            <span v-if="tool.error" class="error">{{ tool.error }}</span>
            <div v-if="tool.retrieval" class="tool-retrieval">
              <span v-if="retrievalFilterText(tool)">过滤：{{ retrievalFilterText(tool) }}</span>
              <span v-if="retrievalQueryText(tool)">检索词：{{ retrievalQueryText(tool) }}</span>
            </div>
          </div>
          <div v-if="snapshot.evidence.sources.length" class="source-summary">
            来源 {{ snapshot.evidence.sources.length }} 条；知识库 {{ snapshot.evidence.knowledgeCount || 0 }}；业务 {{ snapshot.evidence.businessCount || 0 }}；Outline {{ snapshot.evidence.outlineCount || 0 }}
          </div>
          <div v-if="snapshot.evidence.sources.length" class="source-list">
            <div v-for="source in snapshot.evidence.sources" :key="sourceKey(source)" class="source-row">
              <div class="source-main">
                <el-tag size="small" type="info">{{ sourceType(source) }}</el-tag>
                <strong>{{ sourceTitle(source) }}</strong>
              </div>
              <div v-if="sourceMeta(source)" class="source-meta">{{ sourceMeta(source) }}</div>
              <div v-if="sourceExcerpt(source)" class="source-excerpt">{{ sourceExcerpt(source) }}</div>
            </div>
          </div>
        </template>
      </section>

      <section v-if="snapshot.quality && Object.keys(snapshot.quality).length" class="trace-section">
        <h3>质量保护</h3>
        <pre>{{ formatJson(snapshot.quality) }}</pre>
      </section>

      <el-collapse class="trace-section">
        <el-collapse-item title="原始诊断 JSON" name="raw">
          <pre>{{ formatJson(snapshot.raw) }}</pre>
        </el-collapse-item>
      </el-collapse>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import AnswerSourceAlert from './AnswerSourceAlert.vue'
import { toAiExecutionSnapshot } from './aiExecution'

const props = defineProps<{
  visible: boolean
  trace?: Record<string, any> | null
  answerMeta?: Record<string, any> | null
  toolResults?: any[] | null
  sources?: any[] | null
  record?: Record<string, any> | null
  replayResult?: Record<string, any> | null
  solution?: Record<string, any> | null
  repairActions?: Record<string, any>[] | null
}>()

defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const router = useRouter()
const snapshot = computed(() => toAiExecutionSnapshot({
  trace: props.trace,
  answerMeta: props.answerMeta,
  toolResults: props.toolResults,
  sources: props.sources,
  record: props.record,
  replayResult: props.replayResult,
  solution: props.solution
}))
const drawerRepairActions = computed(() => {
  if (props.repairActions?.length) return props.repairActions
  return snapshot.value?.repairActions || []
})
const capabilityLabel = computed(() => {
  const summary = snapshot.value?.summary
  if (!summary) return '-'
  return [summary.capabilityName, summary.capabilityId].filter(Boolean).join(' / ') || '-'
})

function tagType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED' || status === 'TIMEOUT') return 'danger'
  if (status === 'SKIPPED') return 'info'
  return 'warning'
}

function timelineType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED' || status === 'TIMEOUT') return 'danger'
  return 'info'
}

function planExecutionTagType(status?: string) {
  const normalized = String(status || '').toUpperCase()
  if (normalized === 'MATCHED') return 'success'
  if (normalized === 'DIVERGED') return 'danger'
  if (normalized === 'PARTIAL') return 'warning'
  return 'info'
}

function policyCheckTagType(status?: string) {
  const normalized = String(status || '').toUpperCase()
  if (normalized === 'PASS' || normalized === 'SUCCESS') return 'success'
  if (normalized === 'FAIL' || normalized === 'ERROR') return 'danger'
  if (normalized === 'WARN' || normalized === 'WARNING') return 'warning'
  return 'info'
}

function diagnosisAlertType(severity?: string) {
  if (severity === 'success') return 'success'
  if (severity === 'danger') return 'error'
  if (severity === 'warning') return 'warning'
  return 'info'
}

function diagnosisTagType(severity?: string) {
  if (severity === 'success') return 'success'
  if (severity === 'danger') return 'danger'
  if (severity === 'warning') return 'warning'
  return 'info'
}

function formatJson(value: any) {
  return JSON.stringify(value || {}, null, 2)
}

function shouldShowAnswerSourceAlert(value: any) {
  if (value?.answerMeta && Object.keys(value.answerMeta).length) return true
  return !isRunningSnapshot(value)
}

function isRunningSnapshot(value: any) {
  const status = String(value?.summary?.status || value?.currentStep?.status || '').toUpperCase()
  return ['RUNNING', 'PROCESSING', 'PENDING', 'QUEUED'].includes(status)
}

function go(path: string) {
  if (path) router.push(path)
}

function stakeRangeLabel(scope: Record<string, any>) {
  const start = scope?.startStake ?? scope?.start_stake
  const end = scope?.endStake ?? scope?.end_stake
  if (start === undefined && end === undefined) return '-'
  return `${start ?? '-'} - ${end ?? '-'}`
}

function sourceType(source: Record<string, any>) {
  return stringValue(source.sourceType, source.source_type, source.type, source.payload?.sourceType, source.payload?.source_type, '-')
}

function sourceTitle(source: Record<string, any>) {
  return stringValue(
    source.sourceTitle,
    source.source_title,
    source.title,
    source.sourceName,
    source.source_name,
    source.name,
    source.payload?.sourceTitle,
    source.payload?.source_title,
    source.payload?.title,
    sourceType(source)
  )
}

function sourceExcerpt(source: Record<string, any>) {
  return stringValue(
    source.contentExcerpt,
    source.content_excerpt,
    source.excerpt,
    source.summary,
    source.payload?.contentExcerpt,
    source.payload?.content_excerpt,
    source.payload?.excerpt,
    source.payload?.summary
  )
}

function sourceMeta(source: Record<string, any>) {
  return [
    stringValue(source.routeCode, source.route_code, source.payload?.routeCode, source.payload?.route_code),
    stringValue(source.objectType, source.object_type, source.payload?.objectType, source.payload?.object_type),
    stringValue(source.objectId, source.object_id, source.sourceId, source.source_id, source.id, source.payload?.objectId, source.payload?.object_id, source.payload?.sourceId)
  ].filter(Boolean).join(' / ')
}

function sourceKey(source: Record<string, any>) {
  return [sourceType(source), sourceTitle(source), sourceMeta(source), sourceExcerpt(source)].filter(Boolean).join('|')
}

function retrievalFilterText(tool: Record<string, any>) {
  const filters = tool.retrieval?.filters || {}
  const parts = [
    listLabel('能力', filters.capabilityIds || filters.capability_ids),
    listLabel('方案', filters.solutionTypes || filters.solution_types),
    listLabel('对象', filters.objectTypes || filters.object_types),
    listLabel('领域', filters.domains),
    listLabel('来源', tool.retrieval?.sourceTypes || tool.retrieval?.source_types)
  ].filter(Boolean)
  return parts.join('；')
}

function retrievalQueryText(tool: Record<string, any>) {
  return stringValue(tool.retrieval?.rewrittenQuery, tool.retrieval?.rewritten_query, tool.retrieval?.query, tool.retrieval?.originalQuery, tool.retrieval?.original_query)
}

function listLabel(label: string, value: any) {
  const list = Array.isArray(value) ? value : (value ? [value] : [])
  const text = list.map((item) => String(item || '').trim()).filter(Boolean).join(', ')
  return text ? `${label} ${text}` : ''
}

function stringValue(...values: any[]) {
  for (const value of values) {
    if (value !== undefined && value !== null && String(value).trim().length) return String(value)
  }
  return ''
}
</script>

<style scoped>
.trace-meta {
  margin-bottom: 16px;
}

.trace-warning {
  margin-bottom: 10px;
}

.trace-info {
  margin-top: 10px;
}

.trace-section {
  margin-top: 18px;
}

.trace-section h3 {
  margin: 0 0 10px;
  font-size: 14px;
}

.current-step {
  padding: 10px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #eff6ff;
}

.plan-panel {
  padding: 10px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #f8fbff;
}

.policy-panel {
  padding: 10px;
  border: 1px solid #c7d2fe;
  border-radius: 8px;
  background: #f8faff;
}

.diagnosis-panel {
  padding: 10px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #f8fbff;
}

.diagnosis-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.policy-check-list {
  display: grid;
  gap: 8px;
}

.policy-check-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 8px;
  align-items: flex-start;
  padding: 8px;
  border-radius: 6px;
  background: #fff;
}

.policy-check-row strong,
.policy-check-row span {
  display: block;
}

.policy-check-row span {
  color: #475569;
  font-size: 12px;
}

.plan-compare-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 10px;
}

.plan-compare-grid > div {
  min-width: 0;
  padding: 8px;
  border-radius: 6px;
  background: #fff;
}

.plan-compare-grid span:first-child {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.step-title {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.step-meta,
.muted {
  color: #64748b;
  font-size: 12px;
}

.error {
  margin-top: 6px;
  color: #dc2626;
  word-break: break-all;
}

.diagnostic {
  color: #b45309;
  font-size: 12px;
  word-break: break-all;
}

.repair-panel {
  padding: 10px;
  border: 1px solid #fde68a;
  border-radius: 8px;
  background: #fffbeb;
}

.repair-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.repair-list {
  margin-top: 8px;
  display: grid;
  gap: 6px;
}

.repair-item {
  display: grid;
  gap: 2px;
  color: #475569;
  font-size: 12px;
}

.repair-item strong {
  color: #92400e;
}

.detail-collapse {
  margin-top: 6px;
}

.tool-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid #eef2f7;
}

.tool-retrieval {
  flex-basis: 100%;
  display: grid;
  gap: 4px;
  color: #475569;
  font-size: 12px;
  line-height: 1.45;
  word-break: break-word;
}

.source-summary {
  margin-top: 10px;
  color: #475569;
  font-size: 13px;
}

.source-list {
  margin-top: 8px;
  display: grid;
  gap: 8px;
}

.source-row {
  padding: 8px;
  border: 1px solid #eef2f7;
  border-radius: 6px;
  background: #f8fafc;
}

.source-main {
  display: flex;
  align-items: center;
  gap: 8px;
}

.source-main strong {
  min-width: 0;
  word-break: break-all;
}

.source-meta,
.source-excerpt {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
  word-break: break-word;
}

pre {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  font-size: 12px;
}
</style>
