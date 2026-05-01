<template>
  <transition name="chat">
    <div v-if="visible" class="agent-chat-float srmp-card">
      <div class="chat-header">
        <div class="title-box">
          <strong>AI 养护助手</strong>
          <p>{{ contextText }}</p>
        </div>
        <button type="button" class="close-btn" @click="emit('update:visible', false)">×</button>
      </div>

      <div v-if="activeMapObject" class="map-context-banner">
        <div class="map-context-main">
          <strong>当前地图上下文</strong>
          <span>{{ mapContextLabel }}</span>
        </div>
        <div class="map-context-actions">
          <el-button size="small" type="primary" plain :loading="loading" @click="analyzeCurrentObject">
            重新分析
          </el-button>
          <el-button size="small" plain :loading="loading" @click="suggestForCurrentObject">
            处置建议
          </el-button>
          <el-button
            v-for="action in solutionActions"
            :key="action.type"
            size="small"
            plain
            :type="action.primary ? 'success' : undefined"
            :loading="solutionLoading && activeSolutionType === action.type"
            :disabled="solutionLoading || loading"
            @click="generateSolutionDraft(action.type)"
          >
            {{ action.label }}
          </el-button>
        </div>
      </div>

      <div class="option-row">
        <el-checkbox v-model="options.useBusinessData">业务数据</el-checkbox>
        <el-checkbox v-model="options.useKnowledge">知识库</el-checkbox>
        <el-checkbox v-model="options.useOutline">Outline</el-checkbox>
        <el-checkbox v-model="useAgentTools">Agent工具</el-checkbox>
      </div>

      <div class="quick-list">
        <button type="button" @click="quickAsk('分析当前路线整体路况')">分析路线</button>
        <button type="button" @click="quickAsk('找出次差路段')">次差路段</button>
        <button type="button" @click="quickAsk('解释 PCI 指标')">解释 PCI</button>
        <button type="button" @click="quickAsk('生成评定报告草稿')">报告草稿</button>
      </div>

      <div class="message-list">
        <div v-for="(item, index) in messages" :key="index" :class="['message', item.role]">
          <div class="role">{{ item.role === 'user' ? '我' : 'AI' }}</div>
          <div class="content" v-html="renderMarkdown(item.content)" />
          <div v-if="item.meta" class="message-meta">
            <el-tag v-if="item.meta.mapObjectUsed" size="small" type="success">地图上下文</el-tag>
            <el-tag v-if="item.meta.answerSourceLabel" size="small">{{ item.meta.answerSourceLabel }}</el-tag>
            <el-tag v-if="item.meta.fallback" size="small" type="warning">降级</el-tag>
          </div>
          <div v-if="item.role === 'assistant' && item.sources && item.sources.length" class="source-panel">
            <div class="source-title">参考资料</div>
            <div v-for="(source, sIdx) in item.sources" :key="sIdx" class="source-item">
              <span class="source-index">{{ sIdx + 1 }}</span>
              <span class="source-main">
                {{ source.title || source.docTitle || source.documentTitle || '知识片段' }}
                <template v-if="source.sectionTitle || source.section"> / {{ source.sectionTitle || source.section }}</template>
              </span>
              <span v-if="source.score !== undefined && source.score !== null" class="source-score">{{ formatScore(source.score) }}</span>
            </div>
          </div>
          <div v-if="item.role === 'assistant' && item.toolResults && item.toolResults.length" class="tool-panel">
            <div class="source-title">工具调用</div>
            <div v-for="(tool, tIdx) in item.toolResults" :key="tIdx" class="tool-item">
              <span class="tool-name">{{ tool.toolName || tool.name || 'tool' }}</span>
              <el-tag size="small" :type="tool.success === false ? 'danger' : 'success'" effect="plain">
                {{ tool.success === false ? '失败' : '成功' }}
              </el-tag>
              <span class="tool-summary">
                {{ tool.summary || '' }}
                <template v-if="tool.count !== undefined && tool.count !== null">（{{ tool.count }}条）</template>
              </span>
            </div>
          </div>
          <AiEvidencePanel v-if="item.role === 'assistant'" :message="item" :map-context="props.context" />
          <AiTraceButton v-if="item.role === 'assistant'" :trace="item.trace" class="trace-button" @open="openTrace" />
        </div>
      </div>

      <div v-if="sourceSummary" class="source-summary">{{ sourceSummary }}</div>

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
import { saveMapObjectSolutionDraft } from '../../../api/solution'
import SolutionPreviewDialog from './SolutionPreviewDialog.vue'
import AiTraceButton from '../../agent/components/AiTraceButton.vue'
import AiTraceDrawer from '../../agent/components/AiTraceDrawer.vue'
import AiEvidencePanel from './AiEvidencePanel.vue'

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

const options = reactive({
  useBusinessData: true,
  useKnowledge: true,
  useOutline: false,
  topK: 5
})

const activeMapObject = computed(() => {
  return props.mapObject || props.context?.mapObject || props.context?.selectedMapObject || props.context?.selected || null
})

const activeRegionSummary = computed(() => {
  return props.context?.regionSummary || props.context?.region || null
})

const contextMode = computed(() => {
  if (activeMapObject.value) return 'OBJECT'
  if (activeRegionSummary.value) return 'REGION'
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
  if (activeMapObject.value) return mapContextLabel.value
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

function suggestForCurrentObject() {
  if (!activeMapObject.value) return
  quickAsk('基于当前地图选中对象，生成养护处置建议和优先级判断')
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
    regionSummary: activeRegionSummary.value,
    viewport: props.context?.viewport || props.context?.bounds || null,
    selectedLayers: props.context?.selectedLayers || [],
    nearbyObjects: props.context?.nearbyObjects || [],
    userQuestion: message,
    extra: { rawContext: props.context || {} }
  }
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
        mapObjectUsed: payload.data?.mapObjectUsed || payload.mapObjectUsed || meta?.mapObjectUsed
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

function openTrace(trace: Record<string, any>) {
  activeTrace.value = trace
  traceDrawerVisible.value = true
}
</script>

<style scoped>
.agent-chat-float {
  position: absolute;
  top: 96px;
  right: 18px;
  z-index: 950;
  display: flex;
  flex-direction: column;
  width: min(430px, calc(100vw - 36px));
  max-height: calc(100vh - 124px);
  padding: 14px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 24px 50px rgba(15, 23, 42, 0.16);
}

.chat-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
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
  min-height: 180px;
  max-height: min(52vh, 480px);
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

.source-item,
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
