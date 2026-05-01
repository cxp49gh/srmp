<template>
  <AgentPageShell title="方案任务" description="查看 AI 方案生成历史、质量校验、结果内容和引用来源。">
    <div class="page-grid">
      <el-card class="left-card">
        <template #header>
          <div class="card-header">
            <span>任务列表</span>
            <el-button size="small" @click="loadTasks">刷新</el-button>
          </div>
        </template>

        <el-form :inline="true" class="query-form">
          <el-form-item>
            <el-input v-model="query.routeCode" clearable placeholder="路线，如 G210" style="width: 150px" />
          </el-form-item>
          <el-form-item>
            <el-input-number v-model="query.year" :min="2000" :max="2100" />
          </el-form-item>
          <el-form-item>
            <el-select v-model="query.draftStatus" clearable placeholder="草稿状态" style="width: 120px">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="已确认" value="CONFIRMED" />
              <el-option label="已归档" value="ARCHIVED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadTasks">查询</el-button>
          </el-form-item>
        </el-form>

        <el-empty v-if="tasks.length === 0" description="暂无任务" />
        <div
          v-for="item in tasks"
          :key="item.id"
          :class="['task-item', selected?.id === item.id ? 'active' : '']"
          @click="selectTask(item)"
        >
          <div class="row">
            <strong>{{ item.title }}</strong>
            <el-tag v-if="item.draft_status" size="small" type="warning">{{ item.draft_status }}</el-tag>
            <el-tag v-else size="small">{{ item.status }}</el-tag>
          </div>
          <p>{{ item.id }}</p>
          <div class="meta">
            {{ item.route_code || item.object_type || '-' }} / {{ item.year || item.object_id || '-' }} / {{ item.solution_type }}
          </div>
        </div>
      </el-card>

      <el-card class="middle-card">
        <template #header>
          <div class="card-header">
            <span>方案结果</span>
            <div class="card-actions">
              <el-button v-if="detail?.id" size="small" @click="openVersions">版本历史</el-button>
              <el-button
                v-if="detail?.draft_status === 'DRAFT'"
                size="small"
                :loading="statusUpdating"
                @click="changeDraftStatus('CONFIRMED')"
              >
                确认
              </el-button>
              <el-button
                v-if="detail?.draft_status === 'DRAFT' || detail?.draft_status === 'CONFIRMED'"
                size="small"
                :loading="statusUpdating"
                @click="changeDraftStatus('ARCHIVED')"
              >
                归档
              </el-button>
              <el-button v-if="detail?.id" size="small" :loading="checking" @click="runQualityCheck">质量校验</el-button>
              <el-button v-if="detail?.id" size="small" @click="exportMarkdown">导出 Markdown</el-button>
              <el-button v-if="displayResultContent(detail)" size="small" @click="copyResult">复制</el-button>
            </div>
          </div>
        </template>
        <el-empty v-if="!detail" description="请选择任务" />
        <template v-else>
          <el-descriptions :column="2" border size="small" class="mb">
            <el-descriptions-item label="任务ID">{{ detail.id }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
            <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
            <el-descriptions-item label="模板版本">{{ detail.template_version }}</el-descriptions-item>
            <el-descriptions-item label="草稿状态">{{ detail.draft_status || '-' }}</el-descriptions-item>
            <el-descriptions-item label="对象类型">{{ detail.object_type || '-' }}</el-descriptions-item>
            <el-descriptions-item label="对象ID">{{ detail.object_id || '-' }}</el-descriptions-item>
            <el-descriptions-item label="当前版本">{{ detail.current_version_no || '-' }}</el-descriptions-item>
          </el-descriptions>

          <TemplateMetaCard :meta="detail.template_meta || detail.templateMeta || null" show-empty />

          <SolutionQualityPanel :quality="quality" />

          <el-card v-if="aiContext && (aiContext.aiAnswer || aiContext.generationMode)" shadow="never" class="mb">
            <template #header>AI 分析依据</template>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="生成模式">{{ aiContext.generationMode || '-' }}</el-descriptions-item>
              <el-descriptions-item label="Trace">{{ aiContext.aiTraceId || '-' }}</el-descriptions-item>
            </el-descriptions>
            <div v-if="aiContext.aiAnswer" class="ai-answer-preview">{{ aiContext.aiAnswer }}</div>
          </el-card>

          <pre>{{ displayResultContent(detail) }}</pre>
        </template>
      </el-card>

      <el-card class="right-card">
        <template #header>引用来源</template>
        <div v-if="statusTimeline.length" class="timeline-box">
          <strong>状态时间线</strong>
          <div v-for="item in statusTimeline" :key="item.id" class="timeline-item">
            {{ item.from_status || '-' }} -> {{ item.to_status }} | {{ item.action || '-' }}
          </div>
        </div>
        <el-empty v-if="sources.length === 0" description="暂无来源" />
        <div v-for="item in sources" :key="item.id" class="source-item">
          <div class="source-title">
            <strong>{{ item.source_title }}</strong>
            <el-tag size="small">{{ item.source_type }}</el-tag>
          </div>
          <p>{{ item.source_url || item.source_id }}</p>
          <div>{{ item.content_excerpt }}</div>
        </div>
      </el-card>
    </div>

    <el-drawer v-model="versionDrawerVisible" title="版本历史" size="520px">
      <el-empty v-if="versions.length === 0" description="暂无版本" />
      <div v-for="item in versions" :key="item.id" class="version-item">
        <strong>v{{ item.version_no }} {{ item.title }}</strong>
        <p>{{ item.change_note || '版本快照' }}</p>
        <el-button v-if="detail?.draft_status === 'DRAFT'" size="small" @click="restoreVersion(item.version_no)">恢复到该版本</el-button>
        <div class="meta">{{ item.created_at }}</div>
      </div>
    </el-drawer>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import SolutionQualityPanel from './components/SolutionQualityPanel.vue'
import TemplateMetaCard from './components/TemplateMetaCard.vue'
import {
  checkSolutionQuality,
  getSolutionMarkdownExportUrl,
  getSolutionMarkdownV2ExportUrl,
  getSolutionTaskAiContext,
  getSolutionTaskStatusTimeline,
  restoreSolutionTaskVersion,
  getSolutionQualityResult,
  getSolutionTask,
  getSolutionTaskSources,
  getSolutionTaskVersions,
  listSolutionTasks,
  updateSolutionTaskDraftStatus
} from '../../api/solution'

const query = reactive({
  routeCode: 'G210',
  year: 2026,
  draftStatus: '',
  limit: 50
})

const tasks = ref<Record<string, any>[]>([])
const selected = ref<Record<string, any> | null>(null)
const detail = ref<Record<string, any> | null>(null)
const sources = ref<Record<string, any>[]>([])
const quality = ref<Record<string, any> | null>(null)
const versions = ref<Record<string, any>[]>([])
const aiContext = ref<Record<string, any> | null>(null)
const statusTimeline = ref<Record<string, any>[]>([])
const checking = ref(false)
const versionDrawerVisible = ref(false)
const statusUpdating = ref(false)

onMounted(loadTasks)

async function loadTasks() {
  tasks.value = await listSolutionTasks(query)
}

async function selectTask(item: Record<string, any>) {
  selected.value = item
  detail.value = await getSolutionTask(item.id)
  sources.value = await getSolutionTaskSources(item.id)
  quality.value = await getSolutionQualityResult(item.id)
  aiContext.value = await getSolutionTaskAiContext(item.id)
  statusTimeline.value = await getSolutionTaskStatusTimeline(item.id)
  versions.value = []
}

async function runQualityCheck() {
  if (!detail.value?.id) return
  checking.value = true
  try {
    quality.value = await checkSolutionQuality(detail.value.id)
    detail.value = await getSolutionTask(detail.value.id)
    ElMessage.success('质量校验完成')
  } finally {
    checking.value = false
  }
}

async function copyResult() {
  await navigator.clipboard.writeText(detail.value?.result_content || '')
  ElMessage.success('已复制')
}


function valueOfAny(obj: any, keys: string[]) {
  if (!obj) return ''
  for (const key of keys) {
    const value = obj[key]
    if (value !== undefined && value !== null && String(value).trim() !== '') return value
  }
  if (obj.raw) {
    for (const key of keys) {
      const value = obj.raw[key]
      if (value !== undefined && value !== null && String(value).trim() !== '') return value
    }
  }
  return ''
}

function formatStakeValue(value: any) {
  if (value === undefined || value === null || String(value).trim() === '') return ''
  return `K${String(value).replace(/\.0+$/, '')}`
}

function buildFallbackMarkdownForDisplay(task: any) {
  const mapObject = task?.map_object || task?.mapObject || task?.object_summary?.mapObject || task?.objectSummary?.mapObject || {}
  const aiRaw = aiContext.value?.aiContext?.raw || aiContext.value?.aiContext || {}
  const aiMapObject = aiRaw?.mapObject || aiRaw?.map_context?.mapObject || aiRaw?.mapContext?.mapObject || {}

  const merged = {
    ...(task || {}),
    ...(task?.object_summary || task?.objectSummary || {}),
    ...(mapObject || {}),
    ...(mapObject?.raw || {}),
    ...(aiRaw || {}),
    ...(aiMapObject || {}),
    ...(aiMapObject?.raw || {})
  }

  const solutionType = valueOfAny(merged, ['solutionType', 'solution_type']) || '-'
  const routeCode = valueOfAny(merged, ['routeCode', 'route_code']) || '-'
  const year = valueOfAny(merged, ['year']) || '-'
  const objectType = valueOfAny(merged, ['objectType', 'object_type', 'type', 'layerType']) || '-'
  const startStake = valueOfAny(merged, ['startStake', 'start_stake', 'startMileage', 'start_mileage'])
  const endStake = valueOfAny(merged, ['endStake', 'end_stake', 'endMileage', 'end_mileage'])
  const diseaseName = valueOfAny(merged, ['diseaseName', 'disease_name', 'diseaseType', 'disease_type'])
  const severity = valueOfAny(merged, ['severity', 'grade', 'level'])
  const quantity = valueOfAny(merged, ['quantity', 'area', 'length'])
  const unit = valueOfAny(merged, ['measureUnit', 'measure_unit', 'unit'])
  const aiAnswer = aiContext.value?.aiAnswer || ''

  let md = '# AI 方案草稿（系统兜底模板）\n\n'
  md += '> 未匹配到可用方案模板，或模板渲染结果为空；系统已使用内置兜底模板生成草稿，需人工复核后使用。\n\n'
  md += '## 一、基础信息\n\n'
  md += '| 字段 | 内容 |\n|---|---|\n'
  md += `| 方案类型 | ${solutionType} |\n`
  md += `| 路线编号 | ${routeCode} |\n`
  md += `| 年度 | ${year} |\n`
  md += `| 对象类型 | ${objectType} |\n`
  if (startStake || endStake) md += `| 桩号范围 | ${formatStakeValue(startStake)}${endStake ? '-' + formatStakeValue(endStake) : ''} |\n`
  if (diseaseName) md += `| 病害类型 | ${diseaseName} |\n`
  if (severity) md += `| 严重程度/等级 | ${severity} |\n`
  if (quantity) md += `| 数量 | ${quantity}${unit || ''} |\n`
  md += '\n## 二、AI 分析摘要\n\n'
  md += aiAnswer || '暂无 AI 分析摘要。建议结合当前地图对象、知识库资料和现场复核结果完善方案。'
  md += '\n\n## 三、主要问题\n\n'
  md += diseaseName
    ? `- 当前对象涉及 ${diseaseName}，需结合严重程度、影响范围、周边病害和评定结果综合判断。\n`
    : `- 当前对象类型为 ${objectType}，需结合地图上下文、评定指标、病害分布和养护规则综合判断。\n`
  md += '- 若存在连续病害、低分单元或重度病害，应优先安排现场复核。\n\n'
  md += '## 四、处置建议\n\n'
  md += '- 点状病害可采用局部修补、裂缝处置、坑槽修补等措施。\n'
  md += '- 连续病害或低分区间可考虑封层、薄层罩面、局部铣刨重铺或中修。\n'
  md += '- 重度或影响安全的对象应优先纳入近期处置计划。\n\n'
  md += '## 五、实施与复核要求\n\n'
  md += '- 复核病害位置、范围、面积/长度、严重程度和发展趋势。\n'
  md += '- 核查排水、基层、路基、重复修补区域和交通安全风险。\n'
  md += '- 根据现场复核结果调整工程量、工艺和处置边界。\n'
  return md
}

function displayResultContent(task: any) {
  const content = task?.result_content || task?.resultContent || ''
  if (!content) return buildFallbackMarkdownForDisplay(task)
  const hasEmptyFallbackField = content.includes('系统兜底模板') && (
    content.includes('| 路线编号 | - |') ||
    content.includes('| 年度 | - |') ||
    content.includes('| 对象类型 | - |') ||
    content.includes('| 方案类型 | - |')
  )
  return hasEmptyFallbackField ? buildFallbackMarkdownForDisplay(task) : content
}


function exportMarkdown() {
  if (!detail.value?.id) return
  window.open(getSolutionMarkdownV2ExportUrl(detail.value.id), '_blank')
}

async function restoreVersion(versionNo: number) {
  if (!detail.value?.id) return
  detail.value = await restoreSolutionTaskVersion(detail.value.id, versionNo, `恢复到 v${versionNo}`)
  versions.value = await getSolutionTaskVersions(detail.value.id)
  ElMessage.success('已恢复历史版本')
}

async function openVersions() {
  if (!detail.value?.id) return
  versions.value = await getSolutionTaskVersions(detail.value.id)
  versionDrawerVisible.value = true
}

async function changeDraftStatus(next: string) {
  if (!detail.value?.id) return
  statusUpdating.value = true
  try {
    detail.value = await updateSolutionTaskDraftStatus(detail.value.id, next)
    await loadTasks()
    ElMessage.success('草稿状态已更新')
  } finally {
    statusUpdating.value = false
  }
}
</script>

<style scoped>
.page-grid {
  display: grid;
  grid-template-columns: 360px minmax(520px, 1fr) 360px;
  gap: 16px;
}

.left-card,
.middle-card,
.right-card {
  min-height: calc(100vh - 130px);
}

.card-header,
.row,
.source-title {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.query-form {
  margin-bottom: 12px;
}

.task-item,
.source-item {
  padding: 12px;
  border-radius: 10px;
  background: #f8fafc;
  margin-bottom: 10px;
  cursor: pointer;
  font-size: 13px;
}

.task-item.active {
  background: #dbeafe;
}

.task-item p,
.source-item p {
  color: #64748b;
  margin: 4px 0;
  word-break: break-all;
}

.meta {
  color: #64748b;
  font-size: 12px;
}

.version-item {
  padding: 12px;
  margin-bottom: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.version-item p {
  margin: 6px 0;
  color: #475569;
}

.mb {
  margin-bottom: 12px;
}

pre {
  white-space: pre-wrap;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 12px;
  padding: 14px;
  line-height: 1.6;
  max-height: calc(100vh - 360px);
  overflow: auto;
}

.ai-answer-preview {
  margin-top: 10px;
  max-height: 160px;
  overflow: auto;
  white-space: pre-wrap;
  color: #334155;
  font-size: 13px;
  line-height: 1.6;
}

.timeline-box {
  margin-bottom: 12px;
  padding: 10px;
  border-radius: 10px;
  background: #f8fafc;
  font-size: 13px;
}

.timeline-item {
  margin-top: 6px;
  color: #475569;
}
</style>
