<template>
  <el-dialog
    :model-value="visible"
    :title="solution?.title || '方案草稿'"
    width="760px"
    class="solution-preview-dialog"
    append-to-body
    @update:model-value="updateVisible"
  >
    <template v-if="solution">
      <section v-if="summaryItems.length" class="solution-summary">
        <div v-for="item in summaryItems" :key="item.key" class="summary-item">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </section>

      <TemplateMetaCard :meta="(solution as any)?.templateMeta || (solution as any)?.template_meta || null" show-empty />

      <section v-if="solution.qualityCheck" class="quality-check">
        <div class="quality-header">
          <strong>质量检查</strong>
          <el-tag :type="solution.qualityCheck.passed ? 'success' : 'warning'" size="small">
            {{ solution.qualityCheck.passed ? '通过' : '需复核' }}
          </el-tag>
        </div>
        <div v-if="qualityItems.length" class="quality-items">
          <span
            v-for="item in qualityItems"
            :key="item.key"
            :class="['quality-item', { passed: item.passed }]"
          >
            {{ item.label }}
          </span>
        </div>
        <p v-if="solution.qualityCheck.warnings?.length" class="quality-warning">
          {{ solution.qualityCheck.warnings.join('；') }}
        </p>
      </section>

      <el-alert
        v-if="savedTask?.id"
        class="saved-task"
        type="success"
        show-icon
        :closable="false"
        :title="`已保存：${savedTask.id} / ${savedTask.draft_status || 'DRAFT'}`"
      />

      <article class="markdown-preview" v-html="renderedMarkdown" />
    </template>
    <el-empty v-else description="暂无方案草稿" />

    <template #footer>
      <AiTraceButton :trace="activeTrace" @open="traceDrawerVisible = true" />
      <el-button :disabled="!solution?.markdown" @click="copyMarkdown">复制</el-button>
      <el-button :disabled="!solution?.markdown" @click="downloadMarkdown">下载 Markdown</el-button>
      <el-button
        type="primary"
        plain
        :loading="saveLoading"
        :disabled="!solution?.markdown"
        @click="emit('save')"
      >
        保存草稿
      </el-button>
    </template>
  </el-dialog>
  <AiTraceDrawer v-model:visible="traceDrawerVisible" :trace="activeTrace" />
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { MapObjectSolutionResponse } from '../../../api/agent'
import type { MapRegionSolutionResponse } from '../../../api/gis'
import AiTraceButton from '../../agent/components/AiTraceButton.vue'
import AiTraceDrawer from '../../agent/components/AiTraceDrawer.vue'
import TemplateMetaCard from '../../agent/components/TemplateMetaCard.vue'

type PreviewSolution = MapObjectSolutionResponse | MapRegionSolutionResponse

const props = defineProps<{
  visible: boolean
  solution: PreviewSolution | null
  trace?: Record<string, any> | null
  saveLoading?: boolean
  savedTask?: Record<string, any> | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'save'): void
}>()

const summaryLabels: Record<string, string> = {
  objectType: '对象类型',
  objectId: '对象编号',
  routeCode: '路线',
  routeName: '路线名称',
  sectionName: '路段',
  sectionCode: '路段编号',
  unitCode: '单元编号',
  year: '年度',
  stakeRange: '桩号',
  diseaseName: '病害',
  severity: '严重程度',
  quantity: '工程量',
  measureUnit: '单位',
  mqi: 'MQI',
  pqi: 'PQI',
  pci: 'PCI',
  grade: '等级',
  areaKm2: '面积 km2',
  routeCount: '路线',
  sectionCount: '路段',
  unitCount: '评定单元',
  diseaseCount: '病害',
  heavyCount: '重度病害',
  avgMqi: '平均 MQI',
  avgPci: '平均 PCI'
}

const summaryItems = computed(() => {
  const summary = (props.solution as any)?.objectSummary || (props.solution as any)?.regionSummary || {}
  const disease = summary.diseaseSummary || {}
  const assessment = summary.assessmentSummary || {}
  const flat = {
    ...summary,
    diseaseCount: disease.disease_count || disease.diseaseCount,
    heavyCount: disease.heavy_count || disease.heavyCount,
    avgMqi: assessment.avg_mqi || assessment.avgMqi,
    avgPci: assessment.avg_pci || assessment.avgPci
  }
  return Object.keys(summaryLabels)
    .map((key) => ({ key, label: summaryLabels[key], value: formatValue((flat as any)[key]) }))
    .filter((item) => item.value)
})

const renderedMarkdown = computed(() => renderMarkdown(props.solution?.markdown || ''))
const traceDrawerVisible = ref(false)
const activeTrace = computed(() => props.trace || (props.solution as any)?.trace || null)

const qualityItems = computed(() => {
  const items = (props.solution as any)?.qualityCheck?.items
  return Array.isArray(items)
    ? items.map((item: any, index: number) => ({
        key: item.name || item.code || index,
        label: item.name || item.code || item.message || '检查项',
        passed: item.passed === true || item.level === 'OK'
      }))
    : []
})

function updateVisible(value: boolean) {
  emit('update:visible', value)
}

async function copyMarkdown() {
  const text = props.solution?.markdown || ''
  if (!text) return
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      fallbackCopy(text)
    }
    ElMessage.success('已复制方案草稿')
  } catch (error) {
    fallbackCopy(text)
    ElMessage.success('已复制方案草稿')
  }
}

function downloadMarkdown() {
  const text = props.solution?.markdown || ''
  if (!text) return
  const blob = new Blob([text], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${sanitizeFileName(props.solution?.title || '方案草稿')}.md`
  link.click()
  URL.revokeObjectURL(url)
}

function fallbackCopy(text: string) {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  document.execCommand('copy')
  document.body.removeChild(textarea)
}

function sanitizeFileName(value: string) {
  return value.replace(/[\\/:*?"<>|]/g, '_').slice(0, 80)
}

function formatValue(value: any) {
  if (value === undefined || value === null || value === '') return ''
  return String(value)
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function renderMarkdown(value: string) {
  const escaped = escapeHtml(value || '')
  return escaped
    .replace(/^### (.*)$/gm, '<h4>$1</h4>')
    .replace(/^## (.*)$/gm, '<h3>$1</h3>')
    .replace(/^# (.*)$/gm, '<h2>$1</h2>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/^\d+\. (.*)$/gm, '<div class="md-list">$&</div>')
    .replace(/^\- (.*)$/gm, '<div class="md-list">• $1</div>')
    .replace(/\n/g, '<br />')
}
</script>

<style scoped>
.solution-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}

.summary-item {
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.summary-item span {
  display: block;
  margin-bottom: 3px;
  color: #64748b;
  font-size: 12px;
}

.summary-item strong {
  display: block;
  overflow: hidden;
  color: #0f172a;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quality-check {
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #eff6ff;
}

.saved-task {
  margin-bottom: 12px;
}

.quality-header,
.quality-items {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.quality-header {
  justify-content: space-between;
  margin-bottom: 8px;
  color: #1e3a8a;
}

.quality-item {
  padding: 3px 8px;
  border-radius: 999px;
  background: #fef3c7;
  color: #92400e;
  font-size: 12px;
}

.quality-item.passed {
  background: #dcfce7;
  color: #166534;
}

.quality-warning {
  margin: 8px 0 0;
  color: #92400e;
  font-size: 12px;
  line-height: 1.6;
}

.markdown-preview {
  max-height: min(58vh, 560px);
  overflow-y: auto;
  padding: 14px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  color: #0f172a;
  font-size: 14px;
  line-height: 1.75;
}

.markdown-preview :deep(h2),
.markdown-preview :deep(h3),
.markdown-preview :deep(h4) {
  margin: 12px 0 6px;
  line-height: 1.35;
}

.markdown-preview :deep(h2:first-child),
.markdown-preview :deep(h3:first-child) {
  margin-top: 0;
}

.markdown-preview :deep(code) {
  padding: 1px 4px;
  border-radius: 4px;
  background: #e2e8f0;
}

.markdown-preview :deep(.md-list) {
  margin: 2px 0;
}

@media (max-width: 640px) {
  .solution-summary {
    grid-template-columns: 1fr;
  }
}
</style>
