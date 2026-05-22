<template>
  <transition name="chat">
    <div v-if="visible" class="agent-chat-float srmp-card">
      <div class="chat-header compact">
        <div class="title-box">
          <div class="title-main-row">
            <strong>AI 养护助手</strong>
            <button
              type="button"
              class="header-icon-btn analysis-icon-button"
              :class="{ 'is-active': showAnalysisPanel }"
              :aria-expanded="showAnalysisPanel"
              aria-label="一张图分析"
              title="一张图分析"
              @click="toggleAnalysisPanel"
            >
              <el-icon><MapLocation /></el-icon>
            </button>
            <button
              type="button"
              class="header-icon-btn quick-icon-button"
              :class="{ 'is-active': showQuickPanel }"
              :aria-expanded="showQuickPanel"
              aria-label="快捷提问"
              title="快捷提问"
              @click="toggleQuickPanel"
            >
              <el-icon><ChatDotRound /></el-icon>
            </button>
            <button
              type="button"
              class="header-icon-btn settings-icon-button"
              :class="{ 'is-active': showToolsPanel }"
              :aria-expanded="showToolsPanel"
              aria-label="设置"
              title="设置"
              @click="toggleSettingsPanel"
            >
              <el-icon><Setting /></el-icon>
            </button>
            <button
              type="button"
              class="header-icon-btn diagnostics-icon-button"
              :class="{ 'is-active': showDiagnosticsPanel, 'is-loading': diagnosticsLoading }"
              :aria-expanded="showDiagnosticsPanel"
              :aria-busy="diagnosticsLoading"
              aria-label="状态诊断"
              title="状态诊断"
              @click="toggleDiagnosticsPanel"
            >
              <el-icon><Monitor /></el-icon>
            </button>
          </div>
          <span class="header-subtitle">一张图分析、养护建议与依据追踪</span>
        </div>
        <button type="button" class="close-btn" @click="emit('update:visible', false)">×</button>
      </div>

      <section v-if="showToolsPanel" class="settings-panel header-settings-panel">
        <div class="settings-head">
          <strong>设置</strong>
          <span>当前依据：{{ optionSummary }}</span>
        </div>
        <div class="settings-grid">
          <el-checkbox v-model="options.useBusinessData">业务数据</el-checkbox>
          <el-checkbox v-model="options.useKnowledge">知识库</el-checkbox>
          <el-checkbox v-model="options.useOutline">Outline</el-checkbox>
          <el-checkbox v-model="useAgentTools">Agent工具</el-checkbox>
        </div>
      </section>
      <section v-if="showDiagnosticsPanel" class="diagnostics-panel header-diagnostics-panel">
        <div class="diagnostics-head">
          <strong>系统状态</strong>
          <div class="diagnostics-head-actions">
            <el-tag v-if="quickDiagnostics" size="small" :type="quickDiagnostics.runtimeOk ? 'success' : 'danger'">
              {{ quickDiagnostics.status }}
            </el-tag>
            <el-button size="small" text :loading="diagnosticsLoading" @click="loadQuickDiagnostics">刷新</el-button>
          </div>
        </div>
        <p v-if="diagnosticsLoading && !quickDiagnostics" class="diagnostics-empty">正在诊断...</p>
        <p v-if="diagnosticsError" class="diagnostics-error">{{ diagnosticsError }}</p>
        <div v-if="quickDiagnostics" class="diagnostics-summary">
          <span>{{ diagnosticsSummary }}</span>
        </div>
        <div v-if="quickDiagnostics" class="diagnostics-grid">
          <span><em>运行服务</em><strong>{{ quickDiagnostics.runtimeOk ? '正常' : '异常' }}</strong></span>
          <span><em>工具网关</em><strong>{{ quickDiagnostics.toolGatewayOk ? '正常' : '异常' }}</strong></span>
          <span><em>契约</em><strong>{{ quickDiagnostics.contractOk ? 'OK' : '异常' }}</strong></span>
          <span><em>模型</em><strong>{{ quickDiagnostics.llmEnabled ? quickDiagnostics.llmModel : '关闭' }}</strong></span>
          <span><em>成功率</em><strong>{{ quickDiagnostics.successRateLabel }}</strong></span>
          <span><em>平均耗时</em><strong>{{ quickDiagnostics.avgCostLabel }}</strong></span>
        </div>
      </section>

      <section v-if="showQuickPanel" class="quick-panel header-quick-panel">
        <div class="quick-panel-head">
          <strong>快捷提问</strong>
          <span>基于当前地图上下文</span>
        </div>
        <div class="quick-list compact-quick-list">
          <button type="button" @click="quickAsk('分析当前路线整体路况')">分析路线</button>
          <button type="button" @click="quickAsk('找出次差路段')">次差路段</button>
          <button type="button" @click="quickAsk('解释 PCI 指标')">解释 PCI</button>
        </div>
      </section>

      <section v-if="showAnalysisPanel" class="analysis-workbench" :class="contextMode.toLowerCase()">
        <div class="analysis-title-row">
          <div class="analysis-heading">
            <strong>一张图分析</strong>
            <span class="analysis-compact-scope">{{ analysisCompactScope }}</span>
          </div>
        </div>

        <div class="analysis-flow">
          <div class="analysis-flow-group primary">
            <span class="flow-group-label">先分析</span>
            <el-button
              size="small"
              type="primary"
              :disabled="primaryAnalyzeDisabled"
              :loading="primaryAnalyzeLoading"
              @click="runPrimaryAnalysis"
            >{{ primaryAnalyzeLabel }}</el-button>
          </div>

          <div class="analysis-flow-group produce">
            <span class="flow-group-label">生成成果</span>
            <el-button
              v-for="action in resultActions"
              :key="action.type"
              size="small"
              :type="action.buttonType || 'success'"
              plain
              :disabled="action.disabled"
              :loading="solutionLoading && activeSolutionType === action.type"
              @click="runResultAction(action)"
            >{{ action.label }}</el-button>
          </div>

          <div class="analysis-flow-group utility">
            <span class="flow-group-label">辅助</span>
            <el-button size="small" plain @click="previewCurrentPlan">查看执行计划</el-button>
            <el-button size="small" plain @click="copyCurrentContext">复制上下文</el-button>
            <el-button v-if="contextMode === 'OBJECT'" size="small" plain @click="emit('close-detail')">取消对象</el-button>
            <el-button v-if="contextMode === 'REGION'" size="small" plain @click="emit('clear-region')">清除区域</el-button>
          </div>
        </div>
        <div class="analysis-summary">{{ analysisScopeDescription }}</div>
        <div v-if="analysisMetricItems.length" class="analysis-metrics">
          <span v-for="item in analysisMetricItems" :key="item.key">
            <em>{{ item.label }}</em>
            <strong>{{ item.value }}</strong>
          </span>
        </div>
        <div class="analysis-action-hint">{{ operationHint }}</div>
      </section>

      <MapAiWorkbench
        v-model:input="input"
        :context-scope="contextMode"
        :context="props.context"
        :map-object="activeMapObject"
        :messages="messages"
        :loading="loading"
        :solution-loading="solutionLoading"
        :latest-action-result="latestActionResult"
        @send="sendText"
        @open-trace="openTrace"
        @locate-source="locateEvidenceSource"
        @ask-with-source="askWithSource"
      >
        <template #message-tail>
          <section v-if="aiBusy" class="ai-wait-panel" :class="{ slow: waitFeedback.longWait }">
            <div class="wait-head">
              <strong>{{ waitFeedback.title }}</strong>
              <span>已耗时 {{ waitFeedback.elapsedLabel }}</span>
            </div>
            <p>{{ waitFeedback.message }}</p>
            <div v-if="liveTraceSummary" class="live-trace-panel">
              <div class="live-current">
                <span>当前步骤</span>
                <strong>{{ liveTraceSummary.currentLabel || formatLiveStatus(liveTraceSummary.currentStatus || activeLiveTrace?.status) }}</strong>
              </div>
              <div class="live-meta">
                <span v-if="liveTraceSummary.toolLabel">{{ liveTraceSummary.toolLabel }}</span>
                <span v-if="liveTraceSummary.sourceLabel">{{ liveTraceSummary.sourceLabel }}</span>
              </div>
              <div v-if="liveTraceSummary.recentSteps.length" class="live-steps">
                <span v-for="step in liveTraceSummary.recentSteps" :key="step.name || step.label">
                  {{ step.label || step.name }} · {{ formatLiveStatus(step.status) }}
                </span>
              </div>
              <el-button size="small" text @click="openLiveTrace">查看执行过程</el-button>
            </div>
            <div v-else-if="liveTraceError" class="live-trace-error">
              {{ liveTraceError }}，最终结果仍在等待。
            </div>
          </section>
        </template>
      </MapAiWorkbench>

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
  <AiTraceDrawer
    v-model:visible="traceDrawerVisible"
    :trace="activeExecution?.trace || null"
    :answer-meta="activeExecution?.answerMeta || null"
    :tool-results="activeExecution?.toolResults || []"
    :sources="activeExecution?.sources || []"
  />
  <MapAiPlanPreviewDrawer
    v-model:visible="planDrawerVisible"
    :loading="planLoading"
    :plan="planPreview"
    :plan-execution="lastPlanExecution"
    :error="planError"
    @refresh="refreshPlanPreview"
    @execute="executePlanPreview"
  />
</template>

<script setup lang="ts">
import { computed, nextTick, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, MapLocation, Monitor, Setting } from '@element-plus/icons-vue'
import { mapAgentRun, type MapAgentAction, type MapAgentActionResult, type MapAgentRunRequest, type MapAgentRunResponse } from '../../../api/agent'
import { saveMapObjectSolutionDraft, updateSolutionTaskAiContext } from '../../../api/solution'
import { getOrchestratorLiveTrace, getOrchestratorQuickDiagnostics, runOrchestratorPlan } from '../../../api/orchestrator'
import SolutionPreviewDialog from './SolutionPreviewDialog.vue'
import AiTraceDrawer from '../../agent/components/AiTraceDrawer.vue'
import MapAiPlanPreviewDrawer from './MapAiPlanPreviewDrawer.vue'
import MapAiWorkbench from './map-ai/MapAiWorkbench.vue'
import { copyText } from '../../../utils/clipboard'
import { gisContextTypeLabel, sourceToMapTarget, type GisSourceMapTarget } from '../../../utils/gisUnifiedContext'
import { formatMetricValue, getMetricGrade, getMetricMeta, getMetricValue, gradeLabel } from '../../../utils/roadConditionMetrics'
import { assessmentAnalyzeLabel, assessmentOperationHint, assessmentSolutionAction } from '../../../utils/mapAssessmentSemantics'
import { buildWaitFeedback, formatElapsedMs, normalizeLangGraphDiagnostics, summarizeRunTiming, type LangGraphDiagnostics } from '../../../utils/aiRunFeedback'
import { buildPlanPreviewMeta, normalizeMapAiPlanResponse, normalizePlanExecution, type MapAiPlanExecution, type MapAiPlanPreview } from '../../../utils/mapAiPlanPreview'
import { buildMapAiContextPayload } from '../../../utils/mapAiContext'
import {
  buildLiveTraceSummary,
  createWebTraceId,
  normalizeLiveTraceSnapshot,
  shouldPauseLiveTracePolling,
  type LiveTraceSnapshot
} from '../../../utils/liveTrace'

interface MessageItem {
  role: 'user' | 'assistant'
  content: string
  meta?: Record<string, any>
  trace?: Record<string, any> | null
  sources?: any[]
  toolResults?: any[]
  actionResult?: MapAgentActionResult | null
}

type MapObjectSolutionType =
  | 'DISEASE_REVIEW'
  | 'DISEASE_TREATMENT'
  | 'LOW_SCORE_TREATMENT'
  | 'EVALUATION_UNIT_ADVICE'
  | 'SECTION_PLAN'
  | 'ROUTE_REPORT'
  | 'GENERAL_ADVICE'

type AnalysisResultAction = {
  type: MapObjectSolutionType | 'REGION_SOLUTION' | 'WEAK_SECTIONS'
  label: string
  buttonType?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  disabled?: boolean
}

type PreviewSolution = MapAgentRunResponse & {
  solutionType?: string
  status?: string
  title?: string
  markdown?: string
  errorMessage?: string
  objectSummary?: Record<string, any>
  regionSummary?: Record<string, any>
  qualityCheck?: Record<string, any>
  templateMeta?: Record<string, any>
  sourceSummaries?: Record<string, any>[]
}

const props = defineProps<{
  visible: boolean
  context: Record<string, any>
  mapObject?: Record<string, any> | null
  autoQuestion?: string
  regionLoading?: boolean
  regionSummaryLoading?: boolean
  contextScope?: 'ROUTE' | 'OBJECT' | 'REGION' | 'VIEWPORT' | 'FREE'
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
const solutionResult = ref<PreviewSolution | null>(null)
const solutionSaveLoading = ref(false)
const savedSolutionTask = ref<Record<string, any> | null>(null)
const traceDrawerVisible = ref(false)
const activeExecution = ref<Record<string, any> | null>(null)
const autoQuestionInFlight = ref(false)
const useAgentTools = ref(true)
const showAnalysisPanel = ref(true)
const showToolsPanel = ref(false)
const showDiagnosticsPanel = ref(false)
const showQuickPanel = ref(false)
const diagnosticsLoading = ref(false)
const diagnosticsError = ref('')
const quickDiagnostics = ref<LangGraphDiagnostics | null>(null)
const aiRunStartedAt = ref<number | null>(null)
const aiElapsedMs = ref(0)
let aiElapsedTimer: ReturnType<typeof window.setInterval> | null = null
const activeTraceId = ref('')
const liveTrace = ref<LiveTraceSnapshot | null>(null)
const liveTraceError = ref('')
const liveTraceFailureCount = ref(0)
let liveTraceTimer: ReturnType<typeof window.setInterval> | null = null

type PlanExecutionKind = 'SEND'

interface PlanExecutionSnapshot {
  kind: PlanExecutionKind
  action: MapAgentAction
  message: string
  request: MapAgentRunRequest
}

const planDrawerVisible = ref(false)
const planLoading = ref(false)
const planError = ref('')
const planPreview = ref<MapAiPlanPreview | null>(null)
const planExecutionSnapshot = ref<PlanExecutionSnapshot | null>(null)
const lastPlanExecution = ref<MapAiPlanExecution | null>(null)

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

const diagnosticsSummary = computed(() => {
  const diagnostics = quickDiagnostics.value
  if (!diagnostics) return ''
  const runtime = diagnostics.runtimeOk ? '服务正常' : '服务异常'
  const tool = diagnostics.toolGatewayOk ? '工具可用' : '工具异常'
  const contract = diagnostics.contractOk ? '契约正常' : '契约异常'
  const model = diagnostics.llmEnabled ? `模型 ${diagnostics.llmModel}` : '模型关闭'
  return `${runtime} / ${tool} / ${contract} / ${model} / 成功率 ${diagnostics.successRateLabel}`
})

const latestAssistant = computed(() => {
  const list = messages.value.filter((it) => it.role === 'assistant')
  return list.length ? list[list.length - 1] : null
})

const latestActionResult = computed(() => latestAssistant.value?.actionResult || null)
const aiBusy = computed(() => Boolean(loading.value || solutionLoading.value || props.regionLoading))
const waitFeedback = computed(() => buildWaitFeedback(aiElapsedMs.value))
const activeLiveTrace = computed(() => liveTrace.value || props.context?.regionLiveTrace || null)
const liveTraceSummary = computed(() => activeLiveTrace.value ? buildLiveTraceSummary(activeLiveTrace.value) : null)

const activeMetricMeta = computed(() => getMetricMeta(props.context?.query?.indexCode || props.context?.indexCode || 'MQI'))

const activeMetricDisplay = computed(() => {
  const obj: any = activeMapObject.value || {}
  const value = getMetricValue(obj, activeMetricMeta.value.code)
  if (value === undefined || value === null || value === '') return ''
  const gradeCode = getMetricGrade(obj, activeMetricMeta.value.code)
  const grade = gradeCode ? gradeLabel(gradeCode) : ''
  return `${formatMetricValue(value)}${grade ? ` ${grade}` : ''}`
})

const analysisScopeTitle = computed(() => {
  if (activeMapObject.value) return '当前对象'
  if (activeRegionContext.value) return activeRegionContext.value.geometryType === 'POLYGON' ? '多边形区域' : '矩形区域'
  return '当前路线范围'
})

const analysisCompactScope = computed(() => {
  const scope = analysisScopeTitle.value
  const chips = contextChips.value
    .filter((chip) => !(scope.includes('区域') && chip === '区域'))
    .slice(0, 3)
  return [scope, ...chips].filter(Boolean).join(' · ')
})

const analysisScopeDescription = computed(() => {
  if (activeMapObject.value) {
    const metric = activeMetricDisplay.value ? `当前${activeMetricMeta.value.code} ${activeMetricDisplay.value}，` : ''
    return `${metric}已接入指标专题、图层统计与 AI 上下文，可直接分析或生成养护建议。`
  }
  if (activeRegionContext.value) {
    const route = props.context?.query?.routeCode || activeRegionContext.value.routeCode || '当前路线'
    const targets = analysisTargetText.value || '线路、路段、病害、评定结果'
    return `${route} 区域范围，已统一关联 ${targets}`
  }
  const query = props.context?.query || {}
  return `${query.routeCode || '全部路线'} / ${query.year || '全部年度'}，可选择对象或框选区域后继续分析`
})

const analysisMetricItems = computed(() => {
  if (activeMapObject.value) return objectScopeMetricItems(activeMapObject.value)

  const items: Array<{ key: string; label: string; value: string }> = []
  const stats: any = props.context?.statistics || {}
  const summary: any = activeRegionSummary.value || {}
  if (activeMetricDisplay.value) items.push({ key: 'activeMetric', label: activeMetricMeta.value.code, value: activeMetricDisplay.value })
  const routeCount = firstDisplayValue(summary.routeCount, summary.route_count, summary.roadRouteCount, summary.road_route_count, stats.routeCount, stats.route_count, stats.routes, stats.totalRouteCount)
  const sectionCount = firstDisplayValue(summary.sectionCount, summary.section_count, summary.roadSectionCount, summary.road_section_count, stats.sectionCount, stats.section_count, stats.sections, stats.totalSectionCount)
  const diseaseCount = firstDisplayValue(
    summary.diseaseSummary?.disease_count,
    summary.diseaseSummary?.diseaseCount,
    summary.disease_count,
    summary.diseaseCount,
    stats.diseaseCount,
    stats.disease_count
  )
  const assessmentCount = firstDisplayValue(summary.assessmentCount, summary.assessment_count, summary.evaluationCount, summary.evaluation_count, stats.assessmentCount, stats.assessment_count, stats.evaluationCount, stats.evaluation_count)
  const avgMetric = firstDisplayValue(summary[`avg${activeMetricMeta.value.code}`], summary[`avg_${String(activeMetricMeta.value.code).toLowerCase()}`], stats[`avg${activeMetricMeta.value.code}`], stats[`avg_${String(activeMetricMeta.value.code).toLowerCase()}`], stats.avgMqi, stats.avg_mqi)
  if (routeCount !== '') items.push({ key: 'routeCount', label: '路线', value: String(routeCount) })
  if (sectionCount !== '') items.push({ key: 'sectionCount', label: '路段', value: String(sectionCount) })
  if (diseaseCount !== '') items.push({ key: 'diseaseCount', label: '病害', value: String(diseaseCount) })
  if (assessmentCount !== '') items.push({ key: 'assessmentCount', label: '评定', value: String(assessmentCount) })
  if (!activeMetricDisplay.value && avgMetric !== '') items.push({ key: 'avgMetric', label: `均值${activeMetricMeta.value.code}`, value: formatMetricValue(avgMetric) })
  return items.slice(0, 5)
})

function objectScopeMetricItems(mapObject: Record<string, any>) {
  const items: Array<{ key: string; label: string; value: string }> = []
  if (activeMetricDisplay.value) items.push({ key: 'activeMetric', label: activeMetricMeta.value.code, value: activeMetricDisplay.value })

  const type = normalizeObjectType(mapObject)
  const routeCount = hasRouteContext(mapObject) || ['ROAD_ROUTE', 'ROAD_SECTION', 'EVALUATION_UNIT', 'DISEASE', 'ASSESSMENT_RESULT'].includes(type) ? '1' : ''
  const sectionCount = firstDisplayValue(
    mapObject.relatedSectionCount,
    mapObject.related_section_count,
    mapObject.sectionCount,
    mapObject.section_count,
    type === 'ROAD_ROUTE' ? '' : (hasSectionContext(mapObject) || ['ROAD_SECTION', 'EVALUATION_UNIT', 'DISEASE', 'ASSESSMENT_RESULT'].includes(type) ? '1' : '')
  )
  const diseaseCount = firstDisplayValue(
    mapObject.relatedDiseaseCount,
    mapObject.related_disease_count,
    mapObject.diseaseCount,
    mapObject.disease_count,
    type === 'DISEASE' ? '1' : ''
  )
  const evaluationUnitCount = firstDisplayValue(
    mapObject.relatedEvaluationUnitCount,
    mapObject.related_evaluation_unit_count,
    mapObject.evaluationUnitCount,
    mapObject.evaluation_unit_count,
    type === 'EVALUATION_UNIT' ? '1' : ''
  )
  const assessmentCount = firstDisplayValue(
    mapObject.relatedAssessmentCount,
    mapObject.related_assessment_count,
    mapObject.assessmentCount,
    mapObject.assessment_count,
    type === 'ASSESSMENT_RESULT' ? '1' : ''
  )

  pushMeaningfulCountMetric(items, 'routeCount', '路线', routeCount)
  pushMeaningfulCountMetric(items, 'sectionCount', '路段', sectionCount)
  pushMeaningfulCountMetric(items, 'diseaseCount', '病害', diseaseCount)
  pushMeaningfulCountMetric(items, 'evaluationUnitCount', '评定单元', evaluationUnitCount)
  pushMeaningfulCountMetric(items, 'assessmentCount', '评定', assessmentCount)

  return items.slice(0, 5)
}

function pushMeaningfulCountMetric(items: Array<{ key: string; label: string; value: string }>, key: string, label: string, rawValue: any) {
  if (rawValue === undefined || rawValue === null || rawValue === '') return
  const value = String(rawValue)
  const numeric = Number(value)
  if (Number.isFinite(numeric) && Number(value) <= 1) return
  items.push({ key, label, value })
}

function hasRouteContext(mapObject: Record<string, any>) {
  return Boolean(mapObject.routeCode || mapObject.route_code || mapObject.routeName || mapObject.route_name)
}

function hasSectionContext(mapObject: Record<string, any>) {
  return Boolean(
    mapObject.sectionId ||
    mapObject.section_id ||
    mapObject.sectionCode ||
    mapObject.section_code ||
    mapObject.startStake ||
    mapObject.start_stake ||
    mapObject.endStake ||
    mapObject.end_stake
  )
}

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

  if (!activeMapObject.value) {
    const query = props.context?.query || {}
    return [query.routeCode || '当前路线', query.year || '当前年度', activeMetricMeta.value.code].filter(Boolean).slice(0, 6)
  }

  const obj: any = activeMapObject.value || {}
  const type = normalizeObjectType(obj)
  const chips = [mapObjectTypeLabel(type)]
  const disease = obj.diseaseName || obj.disease_name || obj.diseaseType || obj.disease_type
  const route = obj.routeCode || obj.route_code
  const severity = obj.severity
  const stake = formatStake(obj.startStake ?? obj.start_stake, obj.endStake ?? obj.end_stake)
  ;[disease, severity, route, stake].forEach((it) => {
    if (it !== undefined && it !== null && it !== '') chips.push(String(it))
  })
  return chips.slice(0, 6)
})

const preferredContextScope = computed(() => String(props.contextScope || props.context?.contextScope || '').toUpperCase())

const activeMapObject = computed(() => {
  if (preferredContextScope.value === 'REGION') return null
  return props.mapObject || props.context?.mapObject || props.context?.selectedMapObject || props.context?.selected || null
})

const activeRegionContext = computed(() => {
  if (preferredContextScope.value === 'OBJECT') return null
  return props.context?.regionContext || props.context?.region || null
})

const activeRegionSummary = computed(() => {
  return props.context?.regionSummary || activeRegionContext.value?.summary || activeRegionContext.value || null
})

const hasStructuredContext = computed(() => Boolean(activeMapObject.value || activeRegionContext.value))
const regionBusy = computed(() => Boolean(props.regionLoading || props.regionSummaryLoading || loading.value))

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
  if (preferredContextScope.value === 'OBJECT' && activeMapObject.value) return 'OBJECT'
  if (preferredContextScope.value === 'REGION' && (activeRegionContext.value || activeRegionSummary.value)) return 'REGION'
  if (preferredContextScope.value === 'ROUTE') return 'ROUTE'
  if (preferredContextScope.value === 'VIEWPORT') return 'VIEWPORT'
  if (preferredContextScope.value === 'FREE') return 'FREE'
  if (activeMapObject.value) return 'OBJECT'
  if (activeRegionContext.value || activeRegionSummary.value) return 'REGION'
  if (props.context?.query?.routeCode || props.context?.routeCode) return 'ROUTE'
  if (props.context?.viewport || props.context?.bounds) return 'VIEWPORT'
  return 'FREE'
})

const analyzeObjectLabel = computed(() => {
  const type = normalizeObjectType(activeMapObject.value)
  if (type === 'DISEASE' || type === 'DISEASE_RECORD') return '分析病害'
  if (type === 'ASSESSMENT' || type === 'ASSESSMENT_RESULT') {
    return assessmentAnalyzeLabel(activeMapObject.value, activeMetricMeta.value.code)
  }
  if (type === 'EVALUATION_UNIT') return '分析评定单元'
  if (type === 'ROAD_SECTION') return '分析路段'
  if (type === 'ROAD_ROUTE') return '分析路线'
  return '分析对象'
})

const solutionActions = computed(() => {
  const type = normalizeObjectType(activeMapObject.value)
  if (type === 'DISEASE' || type === 'DISEASE_RECORD') {
    return [
      { type: 'DISEASE_TREATMENT' as MapObjectSolutionType, label: '生成处置建议', primary: true },
      { type: 'DISEASE_REVIEW' as MapObjectSolutionType, label: '生成复核意见', primary: false }
    ]
  }
  if (type === 'ASSESSMENT' || type === 'ASSESSMENT_RESULT') {
    const action = assessmentSolutionAction(activeMapObject.value, activeMetricMeta.value.code)
    return [{ type: action.type as MapObjectSolutionType, label: action.label, primary: true }]
  }
  if (type === 'EVALUATION_UNIT') {
    return [{ type: 'EVALUATION_UNIT_ADVICE' as MapObjectSolutionType, label: '生成单元养护建议', primary: true }]
  }
  if (type === 'ROAD_SECTION') {
    return [{ type: 'SECTION_PLAN' as MapObjectSolutionType, label: '生成路段养护计划', primary: true }]
  }
  if (type === 'ROAD_ROUTE') {
    return [{ type: 'ROUTE_REPORT' as MapObjectSolutionType, label: '生成路线养护报告', primary: true }]
  }
  return [{ type: 'GENERAL_ADVICE' as MapObjectSolutionType, label: '生成通用建议', primary: true }]
})

const primaryAnalyzeLabel = computed(() => {
  if (contextMode.value === 'OBJECT') return analyzeObjectLabel.value
  if (contextMode.value === 'REGION') return '分析区域'
  if (contextMode.value === 'ROUTE') return '分析路线'
  if (contextMode.value === 'VIEWPORT') return '分析视野'
  return '分析当前范围'
})

const primaryAnalyzeDisabled = computed(() => {
  if (contextMode.value === 'OBJECT') return !activeMapObject.value
  if (contextMode.value === 'REGION') return !activeRegionContext.value || regionBusy.value
  return false
})

const primaryAnalyzeLoading = computed(() => {
  if (contextMode.value === 'REGION') return Boolean(props.regionSummaryLoading || loading.value)
  return loading.value
})

const resultActions = computed<AnalysisResultAction[]>(() => {
  if (contextMode.value === 'OBJECT') {
    return solutionActions.value.map((action: any) => ({
      type: action.type,
      label: action.label,
      buttonType: action.primary ? 'success' : 'info',
      disabled: !activeMapObject.value || solutionLoading.value || loading.value
    }))
  }
  if (contextMode.value === 'REGION') {
    return [{
      type: 'REGION_SOLUTION',
      label: '生成区域建议',
      buttonType: 'success',
      disabled: !activeRegionContext.value || regionBusy.value
    }]
  }
  return [{
    type: 'WEAK_SECTIONS',
    label: '查找次差路段',
    buttonType: 'info',
    disabled: loading.value
  }]
})

const operationHint = computed(() => {
  if (activeMapObject.value) {
    const type = normalizeObjectType(activeMapObject.value)
    if (type === 'DISEASE' || type === 'DISEASE_RECORD') {
      return '分析病害用于解释成因和风险；生成成果会按对象类型生成处置建议或复核意见。'
    }
    if (type === 'ASSESSMENT' || type === 'ASSESSMENT_RESULT') {
      return assessmentOperationHint(activeMapObject.value, activeMetricMeta.value.code)
    }
    return '分析对象用于 AI 解释；生成建议会生成结构化结果，预览后可保存为方案草稿。'
  }
  if (activeRegionContext.value) return '当前为框选区域模式，只展示区域分析和区域方案动作；点击地图对象后会自动切换为对象模式。'
  return '当前为路线范围模式，页面只展示路线分析动作；点击对象或框选区域后，操作区会自动切换。'
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
  [() => props.autoQuestion, () => props.visible, loading],
  () => {
    void consumeAutoQuestionWhenReady()
  },
  { immediate: true }
)

watch(
  aiBusy,
  (busy) => {
    if (busy) beginAiRun()
    else endAiRun()
  },
  { immediate: true }
)

onUnmounted(() => {
  stopAiElapsedTimer()
  stopLiveTracePolling()
})

function beginLiveTrace(traceId: string) {
  activeTraceId.value = traceId
  liveTrace.value = null
  liveTraceError.value = ''
  liveTraceFailureCount.value = 0
  stopLiveTracePolling()
  window.setTimeout(() => {
    if (activeTraceId.value === traceId) {
      void pollLiveTraceOnce(traceId)
      liveTraceTimer = window.setInterval(() => {
        void pollLiveTraceOnce(traceId)
      }, 1500)
    }
  }, 500)
}

async function pollLiveTraceOnce(traceId = activeTraceId.value) {
  if (!traceId) return
  try {
    const res = await getOrchestratorLiveTrace(traceId)
    const snapshot = normalizeLiveTraceSnapshot(res)
    if (activeTraceId.value !== traceId) return
    liveTrace.value = snapshot
    liveTraceError.value = ''
    liveTraceFailureCount.value = 0
    if (['SUCCESS', 'FAILED', 'TIMEOUT'].includes(snapshot.status)) {
      stopLiveTracePolling()
    }
  } catch (error: any) {
    liveTraceFailureCount.value += 1
    liveTraceError.value = error?.message || '实时过程暂不可用'
    if (shouldPauseLiveTracePolling(liveTraceFailureCount.value)) {
      stopLiveTracePolling()
    }
  }
}

function stopLiveTracePolling() {
  if (!liveTraceTimer) return
  window.clearInterval(liveTraceTimer)
  liveTraceTimer = null
}

function openLiveTrace() {
  if (!activeLiveTrace.value) return
  openTrace({ trace: activeLiveTrace.value, answerMeta: activeLiveTrace.value.answerMeta || {} })
}

function formatLiveStatus(status?: string) {
  const normalized = String(status || '').toUpperCase()
  if (normalized === 'RUNNING') return '正在执行'
  if (normalized === 'SUCCESS' || normalized === 'COMPLETED') return '已完成'
  if (normalized === 'FAILED' || normalized === 'ERROR') return '执行失败'
  if (normalized === 'PENDING') return '等待中'
  return '等待执行状态'
}

function beginAiRun(startedAt = Date.now()) {
  if (!aiRunStartedAt.value) {
    aiRunStartedAt.value = startedAt
    aiElapsedMs.value = 0
  }
  if (aiElapsedTimer) return
  aiElapsedTimer = window.setInterval(() => {
    if (aiRunStartedAt.value) {
      aiElapsedMs.value = Date.now() - aiRunStartedAt.value
    }
  }, 500)
}

function endAiRun() {
  stopAiElapsedTimer()
  aiRunStartedAt.value = null
  aiElapsedMs.value = 0
}

function stopAiElapsedTimer() {
  if (!aiElapsedTimer) return
  window.clearInterval(aiElapsedTimer)
  aiElapsedTimer = null
}

async function consumeAutoQuestionWhenReady() {
  const text = String(props.autoQuestion || '').trim()
  if (autoQuestionInFlight.value || !props.visible || !text || loading.value) return
  autoQuestionInFlight.value = true
  try {
    input.value = text
    await nextTick()
    await send()
    emit('auto-question-consumed')
  } finally {
    autoQuestionInFlight.value = false
  }
}

function quickAsk(text: string) {
  input.value = text
  showQuickPanel.value = false
  send()
}

function analyzeCurrentObject() {
  if (!activeMapObject.value) return
  quickAsk('分析当前地图选中对象，说明主要问题、成因判断，并给出养护处置建议')
}

function triggerAnalyzeObject() {
  analyzeCurrentObject()
}

function analyzeCurrentRegion() {
  if (!activeRegionContext.value) return
  quickAsk('综合分析当前区域内线路、路段、评定单元、病害和评定结果，说明区域养护重点和风险成因')
}

function triggerAnalyzeRegion() {
  analyzeCurrentRegion()
}

function runPrimaryAnalysis() {
  if (contextMode.value === 'OBJECT') {
    triggerAnalyzeObject()
    return
  }
  if (contextMode.value === 'REGION') {
    triggerAnalyzeRegion()
    return
  }
  analyzeCurrentRoute()
}

function runResultAction(action: AnalysisResultAction) {
  if (action.type === 'REGION_SOLUTION') {
    emit('generate-region')
    return
  }
  if (action.type === 'WEAK_SECTIONS') {
    findWeakSections()
    return
  }
  generateSolutionDraft(action.type)
}

function analyzeCurrentRoute() {
  const query = props.context?.query || {}
  const route = query.routeCode ? `路线 ${query.routeCode}` : '当前路线范围'
  quickAsk(`分析${route}整体路况，结合当前指标、等级过滤、图层统计和地图视野，指出主要风险和养护重点`)
}

function findWeakSections() {
  quickAsk('结合当前查询条件、地图视野和启用图层，找出次差路段、低分单元或病害集中区域，并说明排序依据')
}

function defaultPlanMessage() {
  const text = input.value.trim()
  if (text) return text
  if (contextMode.value === 'REGION') return '综合分析当前区域内线路、路段、评定单元、病害和评定结果'
  if (contextMode.value === 'OBJECT') return '分析当前地图选中对象，说明主要问题、成因判断和养护建议'
  if (contextMode.value === 'ROUTE') return '分析当前路线整体路况和养护重点'
  return '基于当前地图上下文回答问题'
}

function buildPlanRequest(action: MapAgentAction, message: string, actionInput: Record<string, any> = {}): MapAgentRunRequest {
  const mapContext: any = buildMapAiContext(message)
  if (mapContext.mode === 'REGION') {
    mapContext.geometry = mapContext.geometry || mapContext.regionGeometry || mapContext.region?.geometry || props.context?.regionGeometry || null
  }
  return {
    action,
    message,
    mapContext,
    actionInput,
    options: { ...options, useTools: useAgentTools.value, traceId: createWebTraceId() }
  }
}

function buildCurrentPlanSnapshot(): PlanExecutionSnapshot {
  const action: MapAgentAction = contextMode.value === 'REGION'
    ? 'ANALYZE_REGION'
    : contextMode.value === 'OBJECT'
      ? 'ANALYZE_OBJECT'
      : contextMode.value === 'ROUTE'
        ? 'ANALYZE_ROUTE'
        : 'CHAT'
  const message = defaultPlanMessage()
  return {
    kind: 'SEND',
    action,
    message,
    request: buildPlanRequest(action, message, { mapObject: activeMapObject.value })
  }
}

async function openPlanPreview(snapshot: PlanExecutionSnapshot) {
  planExecutionSnapshot.value = snapshot
  planDrawerVisible.value = true
  planLoading.value = true
  planError.value = ''
  try {
    const result = await runOrchestratorPlan(snapshot.request as any)
    planPreview.value = normalizeMapAiPlanResponse(result)
  } catch (error: any) {
    planPreview.value = null
    planError.value = error?.message || '执行计划生成失败'
  } finally {
    planLoading.value = false
  }
}

function previewCurrentPlan() {
  openPlanPreview(buildCurrentPlanSnapshot())
}

function refreshPlanPreview() {
  if (planExecutionSnapshot.value) openPlanPreview(planExecutionSnapshot.value)
}

async function executePlanPreview() {
  const snapshot = planExecutionSnapshot.value
  if (!snapshot) return
  planDrawerVisible.value = false
  if (input.value.trim() === snapshot.message) input.value = ''
  const traceId = createWebTraceId()
  const request: MapAgentRunRequest = {
    ...snapshot.request,
    action: snapshot.request.action || snapshot.action,
    message: snapshot.request.message || snapshot.message,
    mapContext: snapshot.request.mapContext,
    actionInput: { ...(snapshot.request.actionInput || {}) },
    options: {
      ...(snapshot.request.options || {}),
      traceId,
      planPreview: buildPlanPreviewMeta(planPreview.value)
    }
  }
  await runMapAgentRequest(request, snapshot.message)
}

async function loadQuickDiagnostics() {
  showDiagnosticsPanel.value = true
  showQuickPanel.value = false
  showAnalysisPanel.value = false
  showToolsPanel.value = false
  diagnosticsLoading.value = true
  diagnosticsError.value = ''
  try {
    const result = await getOrchestratorQuickDiagnostics()
    quickDiagnostics.value = normalizeLangGraphDiagnostics(result)
  } catch (error: any) {
    diagnosticsError.value = error?.message || '诊断失败'
    ElMessage.error(diagnosticsError.value)
  } finally {
    diagnosticsLoading.value = false
  }
}

function toggleAnalysisPanel() {
  showAnalysisPanel.value = !showAnalysisPanel.value
  if (showAnalysisPanel.value) {
    showQuickPanel.value = false
    showToolsPanel.value = false
    showDiagnosticsPanel.value = false
  }
}

function toggleQuickPanel() {
  showQuickPanel.value = !showQuickPanel.value
  if (showQuickPanel.value) {
    showAnalysisPanel.value = false
    showToolsPanel.value = false
    showDiagnosticsPanel.value = false
  }
}

function toggleSettingsPanel() {
  showToolsPanel.value = !showToolsPanel.value
  if (showToolsPanel.value) {
    showQuickPanel.value = false
    showAnalysisPanel.value = false
    showDiagnosticsPanel.value = false
  }
}

async function toggleDiagnosticsPanel() {
  showDiagnosticsPanel.value = !showDiagnosticsPanel.value
  if (showDiagnosticsPanel.value) {
    showQuickPanel.value = false
    showAnalysisPanel.value = false
    showToolsPanel.value = false
    if (!quickDiagnostics.value && !diagnosticsError.value && !diagnosticsLoading.value) {
      await loadQuickDiagnostics()
    }
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
  return buildMapAiContextPayload({
    mode: contextMode.value,
    message,
    context: props.context || {},
    mapObject: activeMapObject.value,
    region: activeRegionContext.value,
    regionSummary: activeRegionSummary.value,
    analysisTargets: analysisTargets.value,
    nearbyObjects: props.context?.nearbyObjects || []
  })
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
  const traceId = createWebTraceId()
  beginLiveTrace(traceId)

  try {
    savedSolutionTask.value = null
    const res = await mapAgentRun({
      action: solutionType === 'ROUTE_REPORT' ? 'GENERATE_ROUTE_REPORT' : 'GENERATE_OBJECT_SOLUTION',
      message: '生成当前对象的结构化养护建议',
      mapContext: buildMapAiContext('生成当前对象的结构化养护建议'),
      actionInput: {
        objectType: normalizeObjectType(obj),
        objectId: String(obj.objectId || obj.object_id || obj.id || obj.featureId || ''),
        routeCode: String(obj.routeCode || obj.route_code || query.routeCode || ''),
        year: normalizeYear(obj.year || query.year),
        solutionType,
        mapObject: obj
      },
      options: { ...options, requireAi: true, traceId }
    })
    const normalized = normalizeSolutionResponse(res)
    if (isFailedSolutionResponse(normalized)) {
      solutionResult.value = normalized
      activeExecution.value = {
        trace: normalized.trace || liveTrace.value || null,
        answerMeta: normalized.answerMeta || null,
        toolResults: normalized.toolResults || [],
        sources: normalized.sources || [],
        solution: normalized
      }
      ElMessage.error(solutionFailureMessage(normalized))
      return
    }
    solutionResult.value = normalized
    solutionDialogVisible.value = true
  } catch (error: any) {
    ElMessage.error(error?.message || '生成结构化建议失败')
  } finally {
    await pollLiveTraceOnce(traceId)
    stopLiveTracePolling()
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
      solutionType: solution.solutionType || activeSolutionType.value || 'GENERAL_ADVICE',
      title: solution.title || '方案草稿',
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

function chatActionForCurrentContext(): MapAgentAction {
  return contextMode.value === 'REGION'
    ? 'ANALYZE_REGION'
    : contextMode.value === 'OBJECT'
      ? 'ANALYZE_OBJECT'
      : contextMode.value === 'ROUTE'
        ? 'ANALYZE_ROUTE'
        : 'CHAT'
}

function buildChatRunRequest(message: string, traceId: string): MapAgentRunRequest {
  return {
    action: chatActionForCurrentContext(),
    message,
    mapContext: buildMapAiContext(message),
    actionInput: {
      mapObject: activeMapObject.value
    },
    options: { ...options, useTools: useAgentTools.value, traceId }
  }
}

async function runMapAgentRequest(request: MapAgentRunRequest, userMessage: string) {
  const requestStartedAt = Date.now()
  const traceId = String(request.options?.traceId || createWebTraceId())
  request.options = { ...(request.options || {}), traceId }
  beginAiRun(requestStartedAt)
  beginLiveTrace(traceId)
  messages.value.push({ role: 'user', content: userMessage })
  loading.value = true

  try {
    const res: any = await mapAgentRun(request)
    const payload = normalizeResponse(res)
    const answer = String(payload.answer || payload.data?.answer || '未返回内容')
    const timing = summarizeRunTiming(payload)
    const localElapsedMs = Date.now() - requestStartedAt
    const runElapsed = timing.costMs > 0 ? timing.elapsedLabel : formatElapsedMs(localElapsedMs)
    const planExecution = normalizePlanExecution(payload)
    const hasPlanExecution = planExecution.status !== 'NO_PLAN'
    if (hasPlanExecution) lastPlanExecution.value = planExecution
    const meta = payload.data?.answerMeta || {
      answerSourceLabel: payload.data?.answerSourceLabel,
      fallback: payload.data?.fallback,
      mapObjectUsed: payload.data?.mapObjectUsed
    }

    messages.value.push({
      role: 'assistant',
      content: answer,
      trace: payload.data?.trace || payload.trace || liveTrace.value || null,
      sources: payload.data?.sources || payload.sources || payload.data?.knowledgeHits || [],
      toolResults: payload.data?.toolResults || payload.toolResults || payload.data?.tools || [],
      actionResult: payload.actionResult || null,
      meta: {
        ...meta,
        intent: payload.data?.intent || payload.intent,
        retriedWithCompactPrompt: meta?.retriedWithCompactPrompt || payload.data?.retriedWithCompactPrompt,
        mapObjectUsed: payload.data?.mapObjectUsed || payload.mapObjectUsed || meta?.mapObjectUsed,
        regionUsed: payload.data?.regionUsed || payload.data?.mapRegionUsed || payload.mapRegionUsed || meta?.regionUsed,
        runElapsed,
        traceId: timing.traceId,
        planExecutionStatus: hasPlanExecution ? planExecution.status : undefined,
        planExecution: hasPlanExecution ? planExecution : undefined,
        llmStatus: timing.llmStatus || meta?.llmStatus || payload.data?.llmStatus,
        llmModel: timing.llmModel || meta?.llmModel || payload.data?.llmModel
      }
    })

    sourceSummary.value = buildSourceSummary(payload.data || {})
  } catch (error: any) {
    ElMessage.error(error?.message || 'AI 问答失败')
    messages.value.push({
      role: 'assistant',
      content: `AI 问答失败：${error?.message || '未知错误'}`,
      trace: liveTrace.value || null,
      meta: { fallback: true, answerSourceLabel: '请求失败' }
    })
  } finally {
    await pollLiveTraceOnce(traceId)
    stopLiveTracePolling()
    loading.value = false
  }
}

async function send() {
  const text = input.value.trim()
  if (!text || loading.value) return
  input.value = ''
  await runMapAgentRequest(buildChatRunRequest(text, createWebTraceId()), text)
}

async function sendText(text: string) {
  input.value = text
  await send()
}

function normalizeResponse(res: any) {
  if (res?.answer || res?.answerMeta || res?.actionResult) {
    return { ...res, data: { ...(res.data || {}), answerMeta: res.answerMeta, toolResults: res.toolResults, sources: res.sources, trace: res.trace, intent: res.intent } }
  }
  if (res?.data?.answer || res?.data?.data) return res.data
  return res || {}
}

function normalizeSolutionResponse(res: MapAgentRunResponse): PreviewSolution {
  const responseData = ((res as any).data || {}) as Record<string, any>
  const actionResult = ((res.actionResult || responseData.actionResult || {}) as Record<string, any>)
  const toolResults = Array.isArray(res.toolResults)
    ? res.toolResults
    : Array.isArray(responseData.toolResults)
      ? responseData.toolResults
      : []
  const toolData = (toolResults.find((item: any) => item?.data && typeof item.data === 'object') as any)?.data || {}
  const sources = Array.isArray(res.sources)
    ? res.sources
    : Array.isArray(res.knowledgeSources)
      ? res.knowledgeSources
      : Array.isArray(responseData.sources)
        ? responseData.sources
        : Array.isArray(responseData.knowledgeSources)
          ? responseData.knowledgeSources
          : []
  return {
    ...res,
    solutionType: String((res.action === 'GENERATE_ROUTE_REPORT' ? 'ROUTE_REPORT' : (res as any).solutionType) || activeSolutionType.value || res.action || ''),
    status: actionResult.status || toolData.status || responseData.actionStatus || (res as any).actionStatus || '',
    title: actionResult.title || toolData.title || '方案草稿',
    markdown: actionResult.markdown || toolData.markdown || res.answer || responseData.answer || '',
    errorMessage: actionResult.errorMessage || toolData.errorMessage || responseData.errorMessage || (res as any).errorMessage || '',
    objectSummary: actionResult.objectSummary || toolData.objectSummary || {},
    regionSummary: actionResult.regionSummary || toolData.regionSummary || {},
    qualityCheck: actionResult.qualityCheck || toolData.qualityCheck || {},
    templateMeta: actionResult.templateMeta || toolData.templateMeta || {},
    sourceSummaries: sources,
    trace: res.trace || responseData.trace || toolData.trace,
    answerMeta: pickSolutionAnswerMeta(res, actionResult),
    toolResults,
    sources
  } as PreviewSolution
}

function isFailedSolutionResponse(solution: PreviewSolution | null) {
  if (!solution) return true
  const status = String((solution as any).status || (solution as any).actionResult?.status || (solution as any).data?.actionStatus || '').toUpperCase()
  if (['FAILED', 'ERROR', 'TIMEOUT'].includes(status)) return true
  return !solution.markdown && Boolean(solutionFailureMessage(solution))
}

function solutionFailureMessage(solution: PreviewSolution | null) {
  const value = (solution as any)?.errorMessage
    || (solution as any)?.actionResult?.errorMessage
    || (solution as any)?.data?.errorMessage
    || ''
  return value ? `生成结构化建议失败：${value}` : '生成结构化建议失败，请查看 AI 执行过程。'
}

function pickSolutionAnswerMeta(res: any, actionResult: Record<string, any>) {
  const toolResults = Array.isArray(res?.toolResults)
    ? res.toolResults
    : Array.isArray(res?.data?.toolResults)
      ? res.data.toolResults
      : []
  const toolMeta = toolResults?.find((item: any) => item?.data?.answerMeta || item?.data?.answer_meta)?.data?.answerMeta
    || toolResults?.find((item: any) => item?.data?.answerMeta || item?.data?.answer_meta)?.data?.answer_meta
  return firstSolutionRecord(
    res?.answerMeta,
    res?.answer_meta,
    res.data?.answerMeta,
    res.data?.answer_meta,
    actionResult.answerMeta,
    actionResult.answer_meta,
    actionResult.data?.answerMeta,
    actionResult.data?.answer_meta,
    toolMeta
  ) || {}
}

function firstSolutionRecord(...values: any[]) {
  for (const value of values) {
    if (value && typeof value === 'object' && !Array.isArray(value) && Object.keys(value).length) {
      return value
    }
  }
  return null
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

function locateEvidenceSource(target: GisSourceMapTarget) {
  emit('locate-source', target)
}

function askWithSource(source: any) {
  emit('ask-with-source', source)
}

function openTrace(execution: Record<string, any>) {
  activeExecution.value = execution
  traceDrawerVisible.value = true
}
</script>

<style scoped>
.agent-chat-float {
  position: fixed;
  top: 16px;
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
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
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
  flex: 1;
  min-width: 0;
}

.title-main-row {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  max-width: 100%;
}

.chat-header strong {
  color: #0f172a;
  font-size: 16px;
}

.header-subtitle {
  display: block;
  margin-top: 2px;
  color: #64748b;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-icon-btn {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
}

.header-icon-btn:hover,
.header-icon-btn.is-active {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #2563eb;
}

.diagnostics-icon-button.is-loading {
  color: #2563eb;
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
  flex-shrink: 0;
  margin-bottom: 8px;
  padding: 9px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: rgba(248, 251, 255, 0.88);
  color: #1e3a8a;
}

.analysis-workbench.region {
  border-color: #bbf7d0;
  background: rgba(236, 253, 245, 0.68);
  color: #047857;
}

.analysis-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.analysis-heading {
  min-width: 0;
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

.analysis-compact-scope {
  max-width: 330px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.analysis-context-line {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  min-width: 0;
}

.detail-context-line {
  margin-top: 7px;
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

.analysis-flow {
  display: grid;
  grid-template-columns: minmax(120px, 0.9fr) minmax(160px, 1.3fr) minmax(170px, 1.2fr);
  gap: 8px;
}

.analysis-flow-group {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  padding: 7px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.66);
}

.analysis-flow-group.primary {
  background: rgba(219, 234, 254, 0.78);
}

.flow-group-label {
  flex: 0 0 100%;
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
}

.analysis-action-hint {
  margin-top: 6px;
  padding: 6px 8px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.62);
  color: #64748b;
  font-size: 11px;
  line-height: 1.45;
}

.compact-quick-list {
  margin-bottom: 8px;
}

.header-settings-panel {
  margin: 0 0 8px;
}

.header-diagnostics-panel {
  margin: 0 0 8px;
}

.header-quick-panel {
  margin: 0 0 8px;
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
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.settings-summary {
  flex-basis: 100%;
  color: #64748b;
  font-size: 12px;
}

.quick-panel,
.settings-panel,
.diagnostics-panel,
.ai-wait-panel {
  flex-shrink: 0;
  margin-bottom: 8px;
  padding: 9px 10px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #f8fbff;
  font-size: 12px;
}

.quick-panel-head,
.settings-head,
.diagnostics-head,
.wait-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.quick-panel-head strong,
.settings-head strong,
.diagnostics-head strong,
.wait-head strong {
  color: #0f172a;
}

.quick-panel-head span,
.settings-head span {
  min-width: 0;
  color: #64748b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 10px;
  margin-top: 8px;
}

.settings-grid :deep(.el-checkbox) {
  height: 24px;
  margin-right: 0;
  overflow: hidden;
}

.diagnostics-head-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.diagnostics-summary {
  display: flex;
  align-items: center;
  margin-top: 7px;
  color: #475569;
  line-height: 1.45;
}

.diagnostics-summary span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wait-head span {
  color: #2563eb;
  font-weight: 700;
}

.ai-wait-panel p {
  margin: 5px 0 0;
  color: #475569;
  line-height: 1.45;
}

.ai-wait-panel.slow {
  border-color: #fde68a;
  background: #fffbeb;
}

.live-trace-panel {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid rgba(148, 163, 184, 0.32);
}

.live-current {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  color: #334155;
  font-size: 12px;
}

.live-current strong {
  color: #0f172a;
}

.live-meta,
.live-steps {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
  color: #64748b;
  font-size: 11px;
}

.live-trace-error {
  margin-top: 8px;
  color: #b45309;
  font-size: 12px;
}

.diagnostics-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  margin-top: 8px;
}

.diagnostics-grid span {
  min-width: 0;
  padding: 6px;
  border-radius: 8px;
  background: #fff;
}

.diagnostics-grid em {
  display: block;
  color: #64748b;
  font-size: 11px;
  font-style: normal;
}

.diagnostics-grid strong {
  display: block;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.diagnostics-error {
  margin: 6px 0 0;
  color: #dc2626;
}

.diagnostics-empty {
  margin: 7px 0 0;
  color: #64748b;
}

.compact-diagnostics {
  flex-basis: 100%;
  margin: 2px 0 0;
}

.quick-list {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.quick-panel .quick-list {
  margin: 8px 0 0;
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

@media (max-height: 700px) {
  .agent-chat-float {
    overflow-y: hidden;
  }

  .chat-header {
    margin-bottom: 6px;
  }

  .analysis-workbench {
    margin-bottom: 6px;
    padding: 8px;
    border-radius: 10px;
  }

  .analysis-title-row {
    margin-bottom: 5px;
  }

  .analysis-summary,
  .analysis-action-hint {
    display: none;
  }

  .analysis-metrics {
    display: none;
  }

  .analysis-flow {
    grid-template-columns: 1fr;
    gap: 6px;
  }

  .analysis-flow-group {
    padding: 6px;
  }

  .map-context-banner {
    margin-bottom: 6px;
    padding: 6px 8px;
  }
}

@media (max-width: 960px) {
  .agent-chat-float {
    top: 12px;
    left: 12px;
    right: 12px;
    bottom: 12px;
    width: auto;
    max-height: none;
  }

  .map-context-banner {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
