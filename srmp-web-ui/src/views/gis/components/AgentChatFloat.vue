<template>
  <transition name="chat">
    <div v-if="visible" class="agent-chat-float srmp-card">
      <div class="chat-header compact">
        <div class="title-box">
          <strong>AI 养护助手</strong>
          <p>{{ contextText }}</p>
        </div>
        <button type="button" class="close-btn" @click="emit('update:visible', false)">×</button>
      </div>

      <section class="analysis-workbench" :class="contextMode.toLowerCase()">
        <div class="analysis-title-row">
          <div>
            <strong>一张图分析</strong>
            <span>{{ analysisScopeTitle }}</span>
          </div>
          <el-tag size="small" effect="plain">{{ activeMetricMeta.shortName }}</el-tag>
        </div>

        <div class="analysis-context-line">
          <span v-for="chip in contextChips" :key="chip" class="context-chip">{{ chip }}</span>
        </div>

        <div class="analysis-summary">{{ analysisScopeDescription }}</div>

        <div v-if="analysisMetricItems.length" class="analysis-metrics">
          <span v-for="item in analysisMetricItems" :key="item.key">
            <em>{{ item.label }}</em>
            <strong>{{ item.value }}</strong>
          </span>
        </div>

        <div class="analysis-actions">
          <el-button
            size="small"
            type="primary"
            :disabled="!activeMapObject"
            :loading="loading"
            @click="triggerAnalyzeObject"
          >分析对象</el-button>
          <el-button
            size="small"
            plain
            :disabled="!activeRegionContext"
            :loading="loading"
            @click="triggerAnalyzeRegion"
          >分析区域</el-button>
          <el-button
            size="small"
            plain
            :disabled="!activeRegionContext"
            :loading="loading"
            @click="emit('generate-region')"
          >生成区域建议</el-button>
          <el-button v-if="hasRegionTrace" size="small" plain @click="emit('trace')">Trace</el-button>
          <el-dropdown trigger="click" @command="handleContextCommand">
            <el-button size="small" plain>更多操作</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="activeMapObject" command="suggest-object">生成处置建议</el-dropdown-item>
                <el-dropdown-item v-if="activeRegionContext" command="suggest-region">生成区域追问</el-dropdown-item>
                <el-dropdown-item
                  v-for="action in solutionActions"
                  :key="action.type"
                  :command="`solution:${action.type}`"
                  :disabled="!activeMapObject || solutionLoading || loading"
                >{{ action.label }}</el-dropdown-item>
                <el-dropdown-item command="copy-context" divided>复制上下文</el-dropdown-item>
                <el-dropdown-item v-if="activeMapObject" command="clear-object">取消对象</el-dropdown-item>
                <el-dropdown-item v-if="activeRegionContext" command="clear-region">清除区域</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </section>

      <div class="fold-panel">
        <button type="button" class="fold-trigger" @click="showToolsPanel = !showToolsPanel">
          数据源与工具：{{ optionSummary }}
          <span>{{ showToolsPanel ? '收起' : '展开' }}</span>
        </button>
        <div v-if="showToolsPanel" class="option-row compact-options">
          <el-checkbox v-model="options.useBusinessData">业务数据</el-checkbox>
          <el-checkbox v-model="options.useKnowledge">知识库</el-checkbox>
          <el-checkbox v-model="options.useOutline">Outline</el-checkbox>
          <el-checkbox v-model="useAgentTools">Agent工具</el-checkbox>
        </div>
      </div>

      <div class="fold-panel">
        <button type="button" class="fold-trigger" @click="showQuickPanel = !showQuickPanel">
          快捷提问
          <span>{{ showQuickPanel ? '收起' : '展开' }}</span>
        </button>
        <div v-if="showQuickPanel" class="quick-list compact-quick-list">
          <button type="button" @click="quickAsk('分析当前路线整体路况')">分析路线</button>
          <button type="button" @click="quickAsk('找出次差路段')">次差路段</button>
          <button type="button" @click="quickAsk('解释 PCI 指标')">解释 PCI</button>
          <button type="button" @click="quickAsk('生成评定报告草稿')">报告草稿</button>
        </div>
      </div>

      <div class="message-list">
        <div v-for="(item, index) in messages" :key="index" :class="['message', item.role]">
          <div class="role">{{ item.role === 'user' ? '我' : 'AI' }}</div>
          <div class="content" v-html="renderMarkdown(item.content)" />
          <div v-if="item.meta" class="message-meta">
            <el-tag v-if="item.meta.mapObjectUsed" size="small" type="success">对象上下文</el-tag>
            <el-tag v-if="item.meta.regionUsed || item.meta.mapRegionUsed" size="small" type="success">区域上下文</el-tag>
            <el-tag v-if="item.meta.answerSourceLabel" size="small">{{ item.meta.answerSourceLabel }}</el-tag>
            <el-tag v-if="item.meta.fallback" size="small" type="warning">降级</el-tag>
          </div>
          <AiEvidencePanel
            v-if="item.role === 'assistant'"
            :message="item"
            :map-context="props.context"
            @locate-source="locateEvidenceSource"
            @ask-with-source="askWithSource"
          />
          <div v-if="item.role === 'assistant' && activeMapObject" class="assistant-action-row">
            <el-button
              size="small"
              type="success"
              plain
              :loading="solutionLoading"
              :disabled="loading || solutionLoading"
              @click="generateDefaultSolutionDraft"
            >
              基于本次分析生成方案草稿
            </el-button>
          </div>
          <AiTraceButton v-if="item.role === 'assistant'" :trace="item.trace" class="trace-button" @open="openTrace" />
        </div>
      </div>


      <div class="input-row">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          placeholder="例如：分析当前对象，给出养护建议"
          @keydown.ctrl.enter="send"
        />
        <el-button type="primary" :loading="loading" @click="send">发送</el-button>
      </div>
    </div>
  </transition>
  <SolutionPreviewDialog
    v-model:visible="solutionDialogVisible"
    :solution="solutionResult"
    :trace="solutionResult?.trace || null"
    :save-loading="solutionSaveLoading"
    :saved-task="savedSolutionTask"
    @save="saveSolutionDraft"
  />
  <AiTraceDrawer v-model:visible="traceDrawerVisible" :trace="activeTrace" />
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  chat,
  mapAgentChat,
  generateMapObjectSolution,
  type MapObjectSolutionResponse,
  type MapObjectSolutionType
} from '../../../api/agent'
import { saveMapObjectSolutionDraft, updateSolutionTaskAiContext } from '../../../api/solution'
import SolutionPreviewDialog from './SolutionPreviewDialog.vue'
import AiTraceButton from '../../agent/components/AiTraceButton.vue'
import AiTraceDrawer from '../../agent/components/AiTraceDrawer.vue'
import AiEvidencePanel from './AiEvidencePanel.vue'
import { copyText } from '../../../utils/clipboard'
import { gisContextTypeLabel, sourceToMapTarget, type GisSourceMapTarget } from '../../../utils/gisUnifiedContext'
import { formatMetricValue, getMetricGrade, getMetricMeta, getMetricValue, gradeLabel } from '../../../utils/roadConditionMetrics'

interface MessageItem {
  role: 'user' | 'assistant'
  content: string
  meta?: Record<string, any>
  trace?: Record<string, any> | null
  sources?: any[]
  toolResults?: any[]
}

const props = defineProps<{
  visible: boolean
  context: Record<string, any>
  mapObject?: Record<string, any> | null
  autoQuestion?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'auto-question-consumed'): void
  (e: 'locate-source', value: GisSourceMapTarget): void
  (e: 'ask-with-source', value: Record<string, any>): void
  (e: 'ai-analyze-object'): void
  (e: 'ai-analyze-region'): void
  (e: 'generate-region'): void
  (e: 'trace'): void
  (e: 'clear-region'): void
  (e: 'close-detail'): void
}>()

const input = ref('')
const loading = ref(false)
const messages = ref<MessageItem[]>([])
const sourceSummary = ref('')
const solutionLoading = ref(false)
const activeSolutionType = ref<MapObjectSolutionType | ''>('')
const solutionDialogVisible = ref(false)
const solutionResult = ref<MapObjectSolutionResponse | null>(null)
const solutionSaveLoading = ref(false)
const savedSolutionTask = ref<Record<string, any> | null>(null)
const traceDrawerVisible = ref(false)
const activeTrace = ref<Record<string, any> | null>(null)
const useAgentTools = ref(true)
const showToolsPanel = ref(false)
const showQuickPanel = ref(false)

const options = reactive({
  useBusinessData: true,
  useKnowledge: true,
  useOutline: false,
  topK: 5
})

const optionSummary = computed(() => {
  const parts: string[] = []
  if (options.useBusinessData) parts.push('业务数据')
  if (options.useKnowledge) parts.push('知识库')
  if (options.useOutline) parts.push('Outline')
  if (useAgentTools.value) parts.push('Agent工具')
  return parts.length ? parts.join('、') : '未启用'
})

const activeMetricMeta = computed(() => getMetricMeta(props.context?.query?.indexCode || props.context?.indexCode || 'MQI'))

const activeMetricDisplay = computed(() => {
  const obj: any = activeMapObject.value || {}
  const value = getMetricValue(obj, activeMetricMeta.value.code)
  if (value === undefined || value === null || value === '') return ''
  const gradeCode = getMetricGrade(obj, activeMetricMeta.value.code)
  const grade = gradeCode ? gradeLabel(gradeCode) : ''
  return `${formatMetricValue(value)}${grade ? ` ${grade}` : ''}`
})

const hasRegionTrace = computed(() => Boolean(props.context?.regionTrace?.traceId || props.context?.regionTrace?.trace_id))

const analysisScopeTitle = computed(() => {
  if (activeMapObject.value) return '当前对象'
  if (activeRegionContext.value) return activeRegionContext.value.geometryType === 'POLYGON' ? '多边形区域' : '矩形区域'
  return '当前路线范围'
})

const analysisScopeDescription = computed(() => {
  if (activeMapObject.value) return mapContextLabel.value
  if (activeRegionContext.value) {
    const route = props.context?.query?.routeCode || activeRegionContext.value.routeCode || '当前路线'
    const targets = analysisTargetText.value || '线路、路段、病害、评定结果'
    return `${route} 区域范围，已统一关联 ${targets}`
  }
  const query = props.context?.query || {}
  return `${query.routeCode || '全部路线'} / ${query.year || '全部年度'}，可选择对象或框选区域后继续分析`
})

const analysisMetricItems = computed(() => {
  const items: Array<{ key: string; label: string; value: string }> = []
  const stats: any = props.context?.statistics || {}
  if (activeMetricDisplay.value) items.push({ key: 'activeMetric', label: activeMetricMeta.value.code, value: activeMetricDisplay.value })
  const routeCount = firstDisplayValue(stats.routeCount, stats.route_count, stats.routes, stats.totalRouteCount)
  const sectionCount = firstDisplayValue(stats.sectionCount, stats.section_count, stats.sections, stats.totalSectionCount)
  const diseaseCount = firstDisplayValue(
    activeRegionSummary.value?.diseaseSummary?.disease_count,
    activeRegionSummary.value?.diseaseSummary?.diseaseCount,
    activeRegionSummary.value?.disease_count,
    activeRegionSummary.value?.diseaseCount,
    stats.diseaseCount,
    stats.disease_count
  )
  const assessmentCount = firstDisplayValue(stats.assessmentCount, stats.assessment_count, stats.evaluationCount, stats.evaluation_count)
  const avgMetric = firstDisplayValue(stats[`avg${activeMetricMeta.value.code}`], stats[`avg_${String(activeMetricMeta.value.code).toLowerCase()}`], stats.avgMqi, stats.avg_mqi)
  if (routeCount !== '') items.push({ key: 'routeCount', label: '路线', value: String(routeCount) })
  if (sectionCount !== '') items.push({ key: 'sectionCount', label: '路段', value: String(sectionCount) })
  if (diseaseCount !== '') items.push({ key: 'diseaseCount', label: '病害', value: String(diseaseCount) })
  if (assessmentCount !== '') items.push({ key: 'assessmentCount', label: '评定', value: String(assessmentCount) })
  if (!activeMetricDisplay.value && avgMetric !== '') items.push({ key: 'avgMetric', label: `均值${activeMetricMeta.value.code}`, value: formatMetricValue(avgMetric) })
  return items.slice(0, 5)
})

function firstDisplayValue(...values: any[]) {
  const value = values.find((it) => it !== undefined && it !== null && it !== '')
  return value === undefined || value === null ? '' : value
}

const contextChips = computed(() => {
  if (activeRegionContext.value) {
    const summary = activeRegionSummary.value || {}
    const chips = ['区域']
    const label = activeRegionContext.value.label || ''
    if (label) chips.push(label)
    const query = props.context?.query || {}
    if (query.routeCode) chips.push(String(query.routeCode))
    const targets = analysisTargets.value.length ? `对象 ${analysisTargets.value.length} 类` : ''
    if (targets) chips.push(targets)
    const count = summary.diseaseSummary?.disease_count || summary.diseaseSummary?.diseaseCount || summary.disease_count || summary.diseaseCount
    if (count !== undefined && count !== null && count !== '') chips.push(`病害 ${count}`)
    return chips.slice(0, 6)
  }

  const obj: any = activeMapObject.value || {}
  const type = normalizeObjectType(obj)
  const chips = [mapObjectTypeLabel(type)]
  const disease = obj.diseaseName || obj.disease_name || obj.diseaseType || obj.disease_type
  const route = obj.routeCode || obj.route_code
  const severity = obj.severity
  const stake = formatStake(obj.startStake ?? obj.start_stake, obj.endStake ?? obj.end_stake)
  const score = obj.mqi !== undefined && obj.mqi !== null ? `MQI ${obj.mqi}` : (obj.pci !== undefined && obj.pci !== null ? `PCI ${obj.pci}` : '')
  ;[disease, severity, route, stake, score].forEach((it) => {
    if (it !== undefined && it !== null && it !== '') chips.push(String(it))
  })
  return chips.slice(0, 6)
})

const activeMapObject = computed(() => {
  return props.mapObject || props.context?.mapObject || props.context?.selectedMapObject || props.context?.selected || null
})

const activeRegionContext = computed(() => {
  return props.context?.regionContext || props.context?.region || null
})

const activeRegionSummary = computed(() => {
  return props.context?.regionSummary || activeRegionContext.value?.summary || activeRegionContext.value || null
})

const hasStructuredContext = computed(() => Boolean(activeMapObject.value || activeRegionContext.value))

const analysisTargets = computed(() => {
  const list = props.context?.analysisTargets
  return Array.isArray(list) ? list : []
})

const analysisTargetText = computed(() => {
  if (!analysisTargets.value.length) return ''
  return analysisTargets.value
    .slice(0, 6)
    .map((it: any) => {
      const suffix = it.count !== undefined ? ` ${it.count}` : (it.score !== undefined ? ` ${it.score}` : '')
      return `${gisContextTypeLabel(it.type)}${suffix}`
    })
    .join(' / ')
})

const contextMode = computed(() => {
  if (activeMapObject.value) return 'OBJECT'
  if (activeRegionContext.value || activeRegionSummary.value) return 'REGION'
  if (props.context?.viewport || props.context?.bounds) return 'VIEWPORT'
  if (props.context?.query?.routeCode || props.context?.routeCode) return 'ROUTE'
  return 'FREE'
})

const solutionActions = computed(() => {
  const type = normalizeObjectType(activeMapObject.value)
  if (type === 'DISEASE' || type === 'DISEASE_RECORD') {
    return [
      { type: 'DISEASE_REVIEW' as MapObjectSolutionType, label: '生成复核意见', primary: false },
      { type: 'DISEASE_TREATMENT' as MapObjectSolutionType, label: '生成处置建议', primary: true }
    ]
  }
  if (type === 'ASSESSMENT' || type === 'ASSESSMENT_RESULT') {
    return [{ type: 'LOW_SCORE_TREATMENT' as MapObjectSolutionType, label: '生成低分处置', primary: true }]
  }
  if (type === 'EVALUATION_UNIT') {
    return [{ type: 'EVALUATION_UNIT_ADVICE' as MapObjectSolutionType, label: '生成单元建议', primary: true }]
  }
  if (type === 'ROAD_SECTION') {
    return [{ type: 'SECTION_PLAN' as MapObjectSolutionType, label: '生成路段计划', primary: true }]
  }
  if (type === 'ROAD_ROUTE') {
    return [{ type: 'ROUTE_REPORT' as MapObjectSolutionType, label: '生成路线报告', primary: true }]
  }
  return [{ type: 'GENERAL_ADVICE' as MapObjectSolutionType, label: '生成方案草稿', primary: true }]
})

function normalizeObjectType(obj: any) {
  return String(obj?.objectType || obj?.object_type || obj?.type || obj?.layerType || '').toUpperCase()
}

function mapObjectTypeLabel(type: any) {
  const value = String(type || '').toUpperCase()
  const map: Record<string, string> = {
    ROAD_ROUTE: '路线',
    ROAD_SECTION: '路段',
    EVALUATION_UNIT: '评定单元',
    ASSESSMENT: '评定结果',
    ASSESSMENT_RESULT: '评定结果',
    DISEASE: '病害',
    DISEASE_RECORD: '病害'
  }
  return map[value] || value || '地图对象'
}

function formatStake(start: any, end?: any) {
  if (start === undefined || start === null || start === '') return ''
  const s = `K${start}`
  return end !== undefined && end !== null && end !== '' ? `${s}—K${end}` : s
}

const mapContextLabel = computed(() => {
  if (activeRegionContext.value) {
    return activeRegionContext.value.label || '地图区域｜区域养护分析'
  }

  const obj: any = activeMapObject.value || {}
  const type = String(obj.objectType || obj.object_type || '').toUpperCase()
  const typeLabel = mapObjectTypeLabel(type)
  const route = obj.routeCode || obj.route_code || ''
  const stake = formatStake(obj.startStake ?? obj.start_stake, obj.endStake ?? obj.end_stake)

  if (type === 'DISEASE' || type === 'DISEASE_RECORD') {
    const name = obj.diseaseName || obj.disease_name || obj.diseaseType || obj.disease_type || '病害'
    const sev = obj.severity ? `｜${obj.severity}` : ''
    return `${typeLabel}｜${name}${sev}${route ? `｜${route}` : ''}${stake ? `｜${stake}` : ''}`
  }

  if (type === 'ASSESSMENT' || type === 'ASSESSMENT_RESULT') {
    const score = obj.mqi !== undefined && obj.mqi !== null
      ? `｜MQI ${obj.mqi}`
      : (obj.pci !== undefined && obj.pci !== null ? `｜PCI ${obj.pci}` : '')
    return `${typeLabel}${route ? `｜${route}` : ''}${stake ? `｜${stake}` : ''}${score}`
  }

  if (type === 'EVALUATION_UNIT') {
    const unit = obj.unitCode || obj.unit_code || ''
    return `${typeLabel}${route ? `｜${route}` : ''}${stake ? `｜${stake}` : ''}${unit ? `｜${unit}` : ''}`
  }

  if (type === 'ROAD_SECTION') {
    const section = obj.sectionName || obj.section_name || obj.sectionCode || obj.section_code || ''
    return `${typeLabel}${section ? `｜${section}` : ''}${route ? `｜${route}` : ''}${stake ? `｜${stake}` : ''}`
  }

  if (type === 'ROAD_ROUTE') {
    const name = obj.routeName || obj.route_name || route || '路线'
    return `${typeLabel}｜${name}${route && name !== route ? `｜${route}` : ''}`
  }

  return `${typeLabel}${route ? `｜${route}` : ''}${stake ? `｜${stake}` : ''}`
})

const contextText = computed(() => {
  if (activeMapObject.value || activeRegionContext.value) return mapContextLabel.value
  if (activeRegionSummary.value) return '框选区域｜区域养护分析'
  const query = props.context?.query || {}
  const route = query.routeCode || '全部路线'
  const year = query.year || '全部年度'
  return `${route}｜${year}`
})

watch(
  () => props.autoQuestion,
  async (question) => {
    const text = String(question || '').trim()
    if (!props.visible || !text || loading.value) return
    input.value = text
    await nextTick()
    await send()
    emit('auto-question-consumed')
  },
  { immediate: true }
)

function quickAsk(text: string) {
  input.value = text
  send()
}

function analyzeCurrentObject() {
  if (!activeMapObject.value) return
  quickAsk('分析当前地图选中对象，说明主要问题、成因判断，并给出养护处置建议')
}

function triggerAnalyzeObject() {
  analyzeCurrentObject()
}

function suggestForCurrentObject() {
  if (!activeMapObject.value) return
  quickAsk('基于当前地图选中对象，生成养护处置建议和优先级判断')
}

function analyzeCurrentRegion() {
  if (!activeRegionContext.value) return
  quickAsk('综合分析当前区域内线路、路段、评定单元、病害和评定结果，说明区域养护重点和风险成因')
}

function triggerAnalyzeRegion() {
  analyzeCurrentRegion()
}

function suggestForCurrentRegion() {
  if (!activeRegionContext.value) return
  quickAsk('基于当前区域分析结果，生成区域养护处置建议、优先级和可落地实施步骤')
}

async function handleContextCommand(command: string) {
  if (command === 'suggest-object') {
    suggestForCurrentObject()
    return
  }
  if (command === 'suggest-region') {
    suggestForCurrentRegion()
    return
  }
  if (command === 'copy-context') {
    await copyCurrentContext()
    return
  }
  if (command === 'clear-object') {
    emit('close-detail')
    return
  }
  if (command === 'clear-region') {
    emit('clear-region')
    return
  }
  if (command.startsWith('solution:')) {
    generateSolutionDraft(command.replace('solution:', '') as MapObjectSolutionType)
  }
}

async function copyCurrentContext() {
  try {
    await copyText(JSON.stringify(buildMapAiContext(''), null, 2))
    ElMessage.success('当前地图上下文已复制')
  } catch (error: any) {
    ElMessage.error(error?.message || '复制失败')
  }
}

/**
 * Phase36 关键方法：buildMapAiContext
 * 构建一张图 AI Agent 使用的地图上下文包。
 */
function buildMapAiContext(message: string) {
  const query = props.context?.query || props.context || {}
  return {
    mode: contextMode.value,
    routeCode: query.routeCode || activeMapObject.value?.routeCode || activeMapObject.value?.route_code,
    year: Number(query.year || activeMapObject.value?.year || 2026),
    mapObject: activeMapObject.value,
    region: activeRegionContext.value,
    regionSummary: activeRegionSummary.value,
    regionGeometry: props.context?.regionGeometry || activeRegionContext.value?.geometry || null,
    viewport: props.context?.viewport || props.context?.bounds || null,
    selectedLayers: props.context?.selectedLayers || [],
    analysisTargets: analysisTargets.value,
    nearbyObjects: props.context?.nearbyObjects || [],
    userQuestion: message,
    extra: {
      rawContext: props.context || {},
      contextScope: props.context?.contextScope || contextMode.value,
      unifiedContextVersion: 'phase48-v2'
    }
  }
}

function generateDefaultSolutionDraft() {
  const primary = solutionActions.value.find((it: any) => it.primary) || solutionActions.value[0]
  if (!primary) {
    ElMessage.warning('当前对象暂不支持生成方案草稿')
    return
  }
  generateSolutionDraft(primary.type)
}

async function generateSolutionDraft(solutionType: MapObjectSolutionType) {
  const obj: any = activeMapObject.value
  if (!obj) {
    ElMessage.warning('请先在地图上选择一个对象')
    return
  }
  if (solutionLoading.value) return

  const query = props.context?.query || {}
  solutionLoading.value = true
  activeSolutionType.value = solutionType

  try {
    savedSolutionTask.value = null
    const res = await generateMapObjectSolution({
      objectType: normalizeObjectType(obj),
      objectId: String(obj.objectId || obj.object_id || obj.id || obj.featureId || ''),
      routeCode: String(obj.routeCode || obj.route_code || query.routeCode || ''),
      year: normalizeYear(obj.year || query.year),
      solutionType,
      mapObject: obj,
      options: { ...options }
    })
    solutionResult.value = normalizeSolutionResponse(res)
    solutionDialogVisible.value = true
  } catch (error: any) {
    ElMessage.error(error?.message || '生成方案草稿失败')
  } finally {
    solutionLoading.value = false
    activeSolutionType.value = ''
  }
}

function latestAssistantMessage() {
  const list = messages.value.filter((it: any) => it.role === 'assistant')
  return list.length ? list[list.length - 1] : null
}

function buildAiEvidenceFromMessage(message: any) {
  const toolResults = message?.toolResults || []
  const knowledgeTool = toolResults.find((it: any) => (it.toolName || it.name) === 'knowledge.retrieve')
  const data = knowledgeTool?.data || {}
  return {
    traceId: message?.trace?.id || message?.trace?.traceId || '',
    retrievalStrategy: data.retrievalStrategy || '',
    searchMode: data.searchMode || '',
    vectorUsed: data.vectorUsed === true,
    fallback: data.fallback === true,
    rewrittenQuery: data.rewrittenQuery || '',
    topScore: data.topScore,
    sourceCount: (message?.sources || []).length,
    mapContext: props.context || {}
  }
}

async function saveSolutionDraft() {
  const solution = solutionResult.value
  const obj: any = activeMapObject.value
  if (!solution?.markdown || !obj) {
    ElMessage.warning('暂无可保存的方案草稿')
    return
  }

  const query = props.context?.query || {}
  solutionSaveLoading.value = true
  try {
    const templateMeta = solution.templateMeta || {}
    const objectSource = {
      sourceType: 'MAP_OBJECT',
      sourceTitle: mapContextLabel.value,
      sourceId: String(obj.objectId || obj.object_id || obj.id || obj.featureId || ''),
      contentExcerpt: mapContextLabel.value
    }
    const assistantMessage = latestAssistantMessage()
    savedSolutionTask.value = await saveMapObjectSolutionDraft({
      solutionType: solution.solutionType,
      title: solution.title,
      markdown: solution.markdown,
      routeCode: String(obj.routeCode || obj.route_code || query.routeCode || ''),
      year: normalizeYear(obj.year || query.year),
      mapObject: obj,
      objectSummary: solution.objectSummary || {},
      qualityCheck: solution.qualityCheck || {},
      trace: solution.trace || {},
      templateId: templateMeta.templateId || templateMeta.template_id || '',
      templateVersion: templateMeta.templateVersion || templateMeta.template_version || '',
      templateName: templateMeta.templateName || templateMeta.template_name || '',
      templateMeta,
      sourceSummaries: [...(solution.sourceSummaries || []), objectSource],
      options: { ...options },
      requestContext: props.context || {}
    })
    if (savedSolutionTask.value?.id && assistantMessage) {
      await updateSolutionTaskAiContext(savedSolutionTask.value.id, {
        aiTraceId: String(assistantMessage.trace?.id || assistantMessage.trace?.traceId || ''),
        aiAnswer: assistantMessage.content || '',
        aiSources: assistantMessage.sources || [],
        aiToolResults: assistantMessage.toolResults || [],
        aiEvidence: buildAiEvidenceFromMessage(assistantMessage),
        aiContext: {
          mapContext: props.context || {},
          mapObject: obj,
          solutionType: solution.solutionType,
          solutionTitle: solution.title
        },
        generationMode: 'MAP_AI_ANALYSIS_TO_SOLUTION_DRAFT'
      })
    }
    ElMessage.success('方案草稿已保存')
  } catch (error: any) {
    ElMessage.error(error?.message || '保存方案草稿失败')
  } finally {
    solutionSaveLoading.value = false
  }
}

async function send() {
  const text = input.value.trim()
  if (!text || loading.value) return

  messages.value.push({ role: 'user', content: text })
  input.value = ''
  loading.value = true

  try {
    const requestPayload = {
      message: text,
      context: props.context,
      mapObject: activeMapObject.value,
      options: { ...options, useTools: useAgentTools.value }
    }

    const res: any = useAgentTools.value
      ? await mapAgentChat({
          ...requestPayload,
          mapContext: buildMapAiContext(text)
        })
      : await chat(requestPayload)

    const payload = normalizeResponse(res)
    const answer = String(payload.answer || payload.data?.answer || '未返回内容')
    const meta = payload.data?.answerMeta || {
      answerSourceLabel: payload.data?.answerSourceLabel,
      fallback: payload.data?.fallback,
      mapObjectUsed: payload.data?.mapObjectUsed
    }

    messages.value.push({
      role: 'assistant',
      content: answer,
      trace: payload.data?.trace || payload.trace || null,
      sources: payload.data?.sources || payload.sources || payload.data?.knowledgeHits || [],
      toolResults: payload.data?.toolResults || payload.toolResults || payload.data?.tools || [],
      meta: {
        ...meta,
        mapObjectUsed: payload.data?.mapObjectUsed || payload.mapObjectUsed || meta?.mapObjectUsed,
        regionUsed: payload.data?.regionUsed || payload.data?.mapRegionUsed || payload.mapRegionUsed || meta?.regionUsed
      }
    })

    sourceSummary.value = buildSourceSummary(payload.data || {})
  } catch (error: any) {
    ElMessage.error(error?.message || 'AI 问答失败')
    messages.value.push({
      role: 'assistant',
      content: `AI 问答失败：${error?.message || '未知错误'}`,
      meta: { fallback: true, answerSourceLabel: '请求失败' }
    })
  } finally {
    loading.value = false
  }
}

function normalizeResponse(res: any) {
  if (res?.answer || res?.data?.answerMeta || res?.data?.mapObjectUsed) return res
  if (res?.data?.answer || res?.data?.data) return res.data
  return res || {}
}

function normalizeSolutionResponse(res: any): MapObjectSolutionResponse {
  if (res?.data?.markdown) return res.data
  return res
}

function normalizeYear(value: any) {
  const year = Number(value)
  return Number.isFinite(year) ? year : undefined
}

function buildSourceSummary(data: Record<string, any>) {
  const parts: string[] = []
  if (data.usedKnowledge) parts.push('已使用知识库')
  if (data.usedOutline) parts.push('已使用 Outline')
  if (data.mapObjectUsed) parts.push('已使用地图对象')
  if (data.regionUsed || data.mapRegionUsed) parts.push('已使用区域分析')
  return parts.join('｜')
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
    .replace(/^\- (.*)$/gm, '<div class="md-list">• $1</div>')
    .replace(/\n/g, '<br />')
}

function formatScore(score: any) {
  const num = Number(score)
  return Number.isFinite(num) ? num.toFixed(3) : String(score)
}

function locateEvidenceSource(target: GisSourceMapTarget) {
  emit('locate-source', target)
}

function askWithSource(source: any) {
  emit('ask-with-source', source)
}

function openTrace(trace: Record<string, any>) {
  activeTrace.value = trace
  traceDrawerVisible.value = true
}
</script>

<style scoped>
.agent-chat-float {
  position: absolute;
  top: 112px;
  right: 18px;
  bottom: 18px;
  z-index: 950;
  display: flex;
  flex-direction: column;
  width: min(512px, calc(100vw - 36px));
  max-height: none;
  padding: 12px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 24px 50px rgba(15, 23, 42, 0.16);
}

.chat-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.chat-header.compact {
  align-items: center;
}

.title-box {
  min-width: 0;
}

.chat-header strong {
  color: #0f172a;
  font-size: 16px;
}

.chat-header p {
  margin: 2px 0 0;
  color: #64748b;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.close-btn {
  flex-shrink: 0;
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 22px;
  cursor: pointer;
}

.analysis-workbench {
  margin-bottom: 8px;
  padding: 10px;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: linear-gradient(180deg, #eff6ff 0%, rgba(239, 246, 255, 0.52) 100%);
  color: #1e3a8a;
}

.analysis-workbench.region {
  border-color: #bbf7d0;
  background: linear-gradient(180deg, #ecfdf5 0%, rgba(236, 253, 245, 0.52) 100%);
  color: #047857;
}

.analysis-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 7px;
}

.analysis-title-row strong {
  display: block;
  color: #0f172a;
  font-size: 14px;
}

.analysis-title-row span {
  display: block;
  margin-top: 1px;
  color: #64748b;
  font-size: 12px;
}

.analysis-context-line {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  min-width: 0;
}

.context-chip {
  max-width: 128px;
  padding: 3px 7px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  color: inherit;
  font-size: 12px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.analysis-summary {
  margin-top: 6px;
  color: #475569;
  font-size: 12px;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.analysis-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  margin-top: 8px;
}

.analysis-metrics span {
  min-width: 0;
  padding: 6px 7px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.76);
}

.analysis-metrics em {
  display: block;
  color: #64748b;
  font-size: 11px;
  font-style: normal;
}

.analysis-metrics strong {
  display: block;
  color: #0f172a;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.analysis-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.fold-panel {
  border-top: 1px solid #eef2f7;
}

.fold-trigger {
  width: 100%;
  padding: 7px 0;
  border: none;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #475569;
  font-size: 12px;
  cursor: pointer;
}

.fold-trigger span {
  color: #2563eb;
}

.compact-options,
.compact-quick-list {
  margin-bottom: 8px;
}

.map-context-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
  padding: 8px 10px;
  border-radius: 10px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
}

.map-context-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.map-context-main span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.map-context-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.option-row {
  display: flex;
  gap: 12px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.quick-list {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.quick-list button {
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  padding: 5px 10px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  cursor: pointer;
}

.message-list {
  flex: 1;
  min-height: 220px;
  max-height: none;
  overflow-y: auto;
  padding-right: 4px;
}

.message {
  margin-bottom: 12px;
}

.role {
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
}

.content {
  border-radius: 12px;
  padding: 10px 12px;
  line-height: 1.65;
  font-size: 13px;
  white-space: normal;
  word-break: break-word;
}

.message.user .content {
  background: #eff6ff;
  color: #1e3a8a;
}

.message.assistant .content {
  background: #f8fafc;
  color: #0f172a;
}

.content :deep(h2),
.content :deep(h3),
.content :deep(h4) {
  margin: 8px 0 4px;
  line-height: 1.35;
}

.content :deep(code) {
  padding: 1px 4px;
  border-radius: 4px;
  background: #e2e8f0;
}

.content :deep(.md-list) {
  margin-left: 4px;
}

.message-meta {
  display: flex;
  gap: 6px;
  margin-top: 6px;
  flex-wrap: wrap;
}

.assistant-action-row {
  margin-top: 8px;
  display: flex;
  justify-content: flex-start;
}

.trace-button {
  margin-top: 6px;
}

.source-panel,
.tool-panel {
  margin-top: 6px;
  padding: 8px;
  background: #f9fafb;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  font-size: 12px;
}

.source-title {
  margin-bottom: 6px;
  font-weight: 700;
  color: #334155;
}

.source-card-main,
.tool-item {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 4px 0;
  color: #475569;
}

.source-index {
  width: 18px;
  height: 18px;
  border-radius: 999px;
  background: #e0f2fe;
  color: #0369a1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.source-main {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-score {
  color: #64748b;
}

.source-card {
  margin: 6px 0;
  padding: 8px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #eef2f7;
}

.source-map-target {
  margin: 4px 0 0 24px;
  color: #64748b;
  font-size: 12px;
}

.source-card-actions {
  display: flex;
  gap: 8px;
  margin-left: 20px;
  flex-wrap: wrap;
}

.map-context-main em {
  color: #64748b;
  font-size: 11px;
  font-style: normal;
}

.map-context-banner.region {
  background: #ecfdf5;
  color: #047857;
}

.tool-name {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  color: #0f766e;
}

.tool-summary {
  flex: 1;
  color: #64748b;
}


.source-summary {
  margin: 6px 0;
  color: #64748b;
  font-size: 12px;
}

.input-row {
  display: grid;
  grid-template-columns: 1fr 70px;
  gap: 8px;
  margin-top: 8px;
}

.chat-enter-active,
.chat-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.chat-enter-from,
.chat-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 960px) {
  .agent-chat-float {
    top: auto;
    left: 12px;
    right: 12px;
    bottom: 16px;
    width: auto;
    max-height: 70vh;
  }

  .map-context-banner {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
