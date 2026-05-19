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
        <el-descriptions-item label="状态">{{ snapshot.summary.status || '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ snapshot.summary.costMs || 0 }} ms</el-descriptions-item>
        <el-descriptions-item label="工具">{{ snapshot.summary.toolSuccessCount || 0 }}/{{ snapshot.summary.toolTotalCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ snapshot.summary.sourceCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="TraceId">{{ snapshot.summary.traceId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="RecordId">{{ snapshot.summary.recordId || '-' }}</el-descriptions-item>
      </el-descriptions>

      <AnswerSourceAlert v-if="shouldShowAnswerSourceAlert(snapshot)" :meta="snapshot.answerMeta" allow-empty />

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
          </div>
          <div v-if="snapshot.evidence.sources.length" class="source-summary">
            来源 {{ snapshot.evidence.sources.length }} 条；知识库 {{ snapshot.evidence.knowledgeCount || 0 }}；业务 {{ snapshot.evidence.businessCount || 0 }}；Outline {{ snapshot.evidence.outlineCount || 0 }}
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
}>()

defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const snapshot = computed(() => toAiExecutionSnapshot({
  trace: props.trace,
  answerMeta: props.answerMeta,
  toolResults: props.toolResults,
  sources: props.sources,
  record: props.record,
  replayResult: props.replayResult,
  solution: props.solution
}))

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
</script>

<style scoped>
.trace-meta {
  margin-bottom: 16px;
}

.trace-warning {
  margin-bottom: 10px;
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

.source-summary {
  margin-top: 10px;
  color: #475569;
  font-size: 13px;
}

pre {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  font-size: 12px;
}
</style>
