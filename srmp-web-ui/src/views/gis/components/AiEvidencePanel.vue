<template>
  <div v-if="hasEvidence" class="ai-evidence-panel" :class="{ embedded }">
    <div v-if="!embedded" class="evidence-header" @click="expanded = !expanded">
      <span>依据与调用</span>
      <el-tag v-if="sourceCount" size="small" type="info" effect="plain">来源 {{ sourceCount }}</el-tag>
      <el-tag v-if="locatableSourceCount" size="small" type="success" effect="plain">地图 {{ locatableSourceCount }}</el-tag>
      <el-tag v-if="toolCount" size="small" :type="toolStatusTagType" effect="plain">工具 {{ toolCount }}</el-tag>
      <span class="toggle">{{ expanded ? '收起' : '展开' }}</span>
    </div>

    <div v-if="expanded || embedded" class="evidence-body">
      <div class="evidence-grid">
        <div>
          <span class="label">地图上下文</span>
          <strong>{{ contextLabel }}</strong>
        </div>
        <div>
          <span class="label">检索模式</span>
          <strong>{{ knowledgeTool?.searchMode || knowledgeTool?.retrievalStrategy || '-' }}</strong>
        </div>
        <div>
          <span class="label">向量检索</span>
          <strong>{{ knowledgeTool ? (knowledgeTool.vectorUsed ? '是' : '否') : '-' }}</strong>
        </div>
        <div>
          <span class="label">Top Score</span>
          <strong>{{ formatScore(knowledgeTool?.topScore) }}</strong>
        </div>
      </div>

      <div v-if="knowledgeTool?.rewrittenQuery" class="evidence-block compact-block">
        <div class="label">检索 Query</div>
        <div class="query">{{ knowledgeTool.rewrittenQuery }}</div>
      </div>

      <div v-if="sources.length" class="evidence-block">
        <div class="label">参考资料与地图关联</div>
        <div v-for="(source, index) in sources" :key="index" class="evidence-source-card">
          <div class="source-row">
            <span class="source-index">{{ index + 1 }}</span>
            <strong class="source-title">{{ sourceTitle(source) }}</strong>
            <span class="binding-status" :class="bindingStatusClass(source)">
              {{ bindingStatusLabel(source) }}
            </span>
            <span v-if="source.score !== undefined && source.score !== null" class="source-score">{{ formatScore(source.score) }}</span>
          </div>
          <div class="map-target" :class="{ empty: !canLocateSource(source) }">
            {{ targetLabel(source) }}
          </div>
          <div class="source-actions">
            <template v-if="canLocateSource(source)">
              <el-button size="small" link @click="locateSource(source)">地图定位</el-button>
            </template>
            <span v-else class="source-unlocated">{{ bindingStatusLabel(source) }}</span>
            <el-button size="small" link @click="askWithSource(source)">{{ sourceAskLabel(source) }}</el-button>
            <el-button size="small" link @click="copySource(source)">复制来源</el-button>
          </div>
        </div>
      </div>

      <div v-if="toolResults.length" class="evidence-block">
        <div class="label">工具调用</div>
        <div v-for="(tool, index) in toolResults" :key="index" class="tool-item">
          <span class="tool-name">{{ tool.toolName || tool.name || 'tool' }}</span>
          <el-tag size="small" :type="tool.success === false ? 'danger' : 'success'" effect="plain">
            {{ tool.success === false ? '失败' : '成功' }}
          </el-tag>
          <span class="tool-summary">
            {{ tool.summary || '-' }}
            <template v-if="tool.count !== undefined && tool.count !== null">（{{ tool.count }}条）</template>
          </span>
        </div>
      </div>

      <div v-if="knowledgeFallbackNotice" class="fallback">
        知识库状态：{{ knowledgeFallbackNotice }}
      </div>

      <div v-if="enableFeedback" class="feedback-bar">
        <el-button size="small" @click="openFeedback('MISSING_KNOWLEDGE')">知识缺失反馈</el-button>
        <el-button size="small" type="warning" plain @click="openFeedback('SOURCE_INACCURATE')">来源不准确反馈</el-button>
      </div>
    </div>

    <el-dialog v-model="feedbackVisible" :title="feedbackTitle" width="520px" destroy-on-close>
      <el-form label-width="88px">
        <el-form-item label="用户问题">
          <el-input :model-value="question" type="textarea" :rows="2" readonly />
        </el-form-item>
        <el-form-item label="补充说明">
          <el-input v-model="feedbackRemark" type="textarea" :rows="3" placeholder="请描述缺失的资料或来源为何不准确" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feedbackVisible = false">取消</el-button>
        <el-button type="primary" :loading="feedbackSubmitting" @click="submitFeedback">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createAiKnowledgeFeedback, type AiFeedbackType } from '../../../api/knowledgeFeedback'
import { copyText } from '../../../utils/clipboard'
import {
  categoryLabel,
  classifySource,
  mergeAiSources
} from '../../../utils/aiSourceDisplay'
import { hasLocatableTarget, mapTargetLabel, sourceToMapTarget, type GisSourceMapTarget } from '../../../utils/gisUnifiedContext'

const props = withDefaults(
  defineProps<{
    message: Record<string, any>
    mapContext?: Record<string, any>
    embedded?: boolean
    defaultExpanded?: boolean
    question?: string
    enableFeedback?: boolean
  }>(),
  {
    question: '',
    enableFeedback: true
  }
)

const emit = defineEmits<{
  (e: 'locate-source', value: GisSourceMapTarget): void
  (e: 'ask-with-source', value: Record<string, any>): void
}>()

const expanded = ref(props.defaultExpanded === true)

const sources = computed(() => Array.isArray(props.message.sources) ? props.message.sources : [])
const toolResults = computed(() => Array.isArray(props.message.toolResults) ? props.message.toolResults : [])
const displaySources = computed(() => mergeAiSources(sources.value, toolResults.value))

const feedbackVisible = ref(false)
const feedbackType = ref<AiFeedbackType>('MISSING_KNOWLEDGE')
const feedbackRemark = ref('')
const feedbackSubmitting = ref(false)
const feedbackTitle = computed(() =>
  feedbackType.value === 'MISSING_KNOWLEDGE' ? '知识缺失反馈' : '答案来源不准确反馈'
)
const sourceCount = computed(() => sources.value.length)
const toolCount = computed(() => toolResults.value.length)
const failedToolCount = computed(() => toolResults.value.filter((tool: any) => isFailedTool(tool)).length)
const toolStatusTagType = computed(() => failedToolCount.value ? 'warning' : 'success')
const locatableSourceCount = computed(() => sources.value.filter((source: any) => hasLocatableTarget(sourceToMapTarget(source))).length)

const hasEvidence = computed(() => {
  return Boolean(sourceCount.value || toolCount.value)
})

const knowledgeTool = computed(() => {
  const tool = toolResults.value.find((it: any) => (it.toolName || it.name) === 'knowledge.retrieve')
  if (!tool) return null
  const data = tool.data || {}
  return {
    ...tool,
    ...data,
    fallbackReason: firstNonEmpty(data.fallbackReason, data.fallback_reason, tool.fallbackReason, tool.fallback_reason, data.reason, tool.reason)
  }
})

const knowledgeFallbackNotice = computed(() => knowledgeFallbackText(knowledgeTool.value?.fallbackReason))

const contextLabel = computed(() => {
  const ctx: any = props.mapContext || {}
  const obj = ctx.mapObject || ctx.selectedMapObject || ctx.selected || {}
  const region = ctx.regionContext || ctx.region || {}
  const route = obj.routeCode || obj.route_code || region.routeCode || ctx.routeCode || ctx.query?.routeCode || '-'
  const disease = obj.diseaseName || obj.disease_name || obj.diseaseType || obj.disease_type || ''
  const mode = ctx.mode || ctx.contextScope || (region.objectType ? 'REGION' : (obj.objectType ? 'OBJECT' : '-'))
  return `${mode}｜${route}${disease ? `｜${disease}` : ''}`
})

function isFailedTool(tool: any) {
  const status = String(tool?.status || tool?.state || '').toUpperCase()
  return tool?.success === false || ['FAILED', 'ERROR', 'TIMEOUT'].includes(status)
}

function firstNonEmpty(...values: any[]) {
  return values.find((value) => String(value ?? '').trim())
}

function knowledgeFallbackText(value: any) {
  const reason = String(value ?? '').trim()
  if (!reason) return ''
  const normalized = reason.toLowerCase()
  if (normalized === 'no knowledge chunks') return '本地知识库暂无切片，当前回答只依赖业务数据；请先同步 Outline 或导入知识文档。'
  if (normalized === 'no embedded chunks') return '暂无可用向量切片，已尝试关键词检索；不影响业务数据分析。'
  if (normalized === 'query is empty') return '本次问题未形成有效知识库检索词，知识库未参与。'
  if (normalized.includes('pgvector')) return '向量检索扩展不可用，已尝试关键词检索；不影响业务数据分析。'
  return `知识检索已切换兜底策略：${reason}`
}

function sourceTitle(source: any) {
  const title = source.title || source.docTitle || source.documentTitle || source.sourceTitle || '知识片段'
  const section = source.sectionTitle || source.section || source.heading
  return section ? `${title} / ${section}` : title
}

function openFeedback(type: AiFeedbackType) {
  feedbackType.value = type
  feedbackRemark.value = ''
  feedbackVisible.value = true
}

async function submitFeedback() {
  feedbackSubmitting.value = true
  try {
    await createAiKnowledgeFeedback({
      feedbackType: feedbackType.value,
      question: props.question,
      remark: feedbackRemark.value.trim(),
      businessContext: props.mapContext,
      citedSources: displaySources.value.map((item) => item.raw)
    })
    ElMessage.success('反馈已提交')
    feedbackVisible.value = false
  } finally {
    feedbackSubmitting.value = false
  }
}

function targetLabel(source: any) {
  const target = sourceToMapTarget(source)
  if (!hasLocatableTarget(target)) return referenceTargetLabel(source)
  return mapTargetLabel(target)
}

function canLocateSource(source: any) {
  return hasLocatableTarget(sourceToMapTarget(source))
}

function bindingStatusLabel(source: any) {
  const type = String(source?.bindingType || source?.binding_type || 'NONE').toUpperCase()
  const status = String(source?.bindingStatus || source?.binding_status || 'UNVERIFIED').toUpperCase()
  if (status === 'NOT_FOUND') return '对象已变更或不存在'
  if (status === 'INVALID') return '定位信息异常'
  if (type === 'OBJECT') return status === 'VALID' ? '地图对象' : '地图对象（待验证）'
  if (type === 'RANGE') return status === 'VALID' ? '地图范围' : '地图范围（待验证）'
  return '仅参考资料'
}

function bindingStatusClass(source: any) {
  const type = String(source?.bindingType || source?.binding_type || 'NONE').toUpperCase()
  const status = String(source?.bindingStatus || source?.binding_status || 'UNVERIFIED').toUpperCase()
  if (status === 'NOT_FOUND') return 'not-found'
  if (status === 'INVALID') return 'invalid'
  if (type === 'NONE') return 'reference'
  return status === 'VALID' ? 'verified' : 'unverified'
}

function referenceTargetLabel(source: any) {
  const reason = source?.bindingReason || source?.binding_reason
  if (reason) return `${bindingStatusLabel(source)}｜${reason}`
  const kind = categoryLabel(classifySource(source))
  return `${kind}｜暂无地图位置`
}

function sourceAskLabel(source: any) {
  return canLocateSource(source) ? '追问来源' : '追问资料'
}

function locateSource(source: any) {
  emit('locate-source', sourceToMapTarget(source))
}

function askWithSource(source: any) {
  emit('ask-with-source', source.followupContext || source.followup_context || {
    sourceId: source.sourceId || source.source_id,
    sourceType: source.sourceType || source.source_type,
    sourceTitle: sourceTitle(source),
    contentExcerpt: sourceExcerptText(source),
    bindingType: 'NONE',
    bindingStatus: 'UNVERIFIED'
  })
}

async function copySource(source: any) {
  try {
    const title = source.title || source.docTitle || source.documentTitle || '知识片段'
    await copyText(JSON.stringify({
      title,
      mapTarget: sourceToMapTarget(source),
      raw: source
    }, null, 2))
    ElMessage.success('来源与地图关联信息已复制')
  } catch (error: any) {
    ElMessage.error(error?.message || '复制失败')
  }
}

function sourceExcerptText(source: any) {
  const value = firstNonEmpty(
    source?.content,
    source?.text,
    source?.contentExcerpt,
    source?.content_excerpt,
    source?.excerpt,
    source?.summary,
    source?.snippet
  )
  return String(value || '').replace(/\s+/g, ' ').trim().slice(0, 260)
}

function formatScore(value: any) {
  const num = Number(value)
  return Number.isFinite(num) ? num.toFixed(3) : '-'
}
</script>

<style scoped>
.ai-evidence-panel {
  margin-top: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
  font-size: 12px;
  overflow: hidden;
}

.ai-evidence-panel.embedded {
  margin-top: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.evidence-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 9px;
  cursor: pointer;
  color: #334155;
  font-weight: 700;
}

.toggle {
  margin-left: auto;
  color: #2563eb;
  font-weight: 500;
}

.evidence-body {
  padding: 8px 10px 10px;
  border-top: 1px solid #e2e8f0;
}

.embedded .evidence-body {
  padding: 0;
  border-top: 0;
}

.evidence-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 8px;
}

.evidence-grid > div {
  min-width: 0;
  padding: 7px 8px;
  border-radius: 8px;
  background: #fff;
}

.evidence-grid strong {
  display: block;
  min-width: 0;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.label {
  display: block;
  color: #64748b;
  margin-bottom: 4px;
  font-weight: 600;
}

.query {
  background: #fff;
  border-radius: 8px;
  padding: 6px;
  color: #0f172a;
  line-height: 1.5;
  word-break: break-word;
}

.evidence-block {
  margin-top: 8px;
}

.compact-block {
  margin-bottom: 4px;
}

.evidence-source-card {
  margin: 6px 0;
  padding: 8px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #e5e7eb;
}

.source-row,
.tool-item {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
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

.source-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #334155;
}

.source-score {
  color: #64748b;
}

.binding-status {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 11px;
  font-weight: 600;
}

.binding-status.verified {
  background: #dcfce7;
  color: #15803d;
}

.binding-status.unverified {
  background: #fef3c7;
  color: #b45309;
}

.binding-status.not-found,
.binding-status.invalid {
  background: #fee2e2;
  color: #b91c1c;
}

.map-target {
  margin: 5px 0 0 24px;
  color: #475569;
  line-height: 1.45;
}

.map-target.empty {
  color: #b45309;
}

.source-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 20px;
  flex-wrap: wrap;
}

.source-unlocated {
  color: #94a3b8;
  font-size: 12px;
  line-height: 24px;
}

.tool-name {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  color: #0f766e;
}

.tool-summary {
  flex: 1;
  min-width: 0;
  color: #64748b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fallback {
  margin-top: 8px;
  color: #b45309;
}

.source-meta-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 4px 0 0 24px;
  color: #64748b;
  font-size: 11px;
}

.source-excerpt {
  margin: 6px 0 0 24px;
  color: #475569;
  line-height: 1.45;
}

.feedback-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #e2e8f0;
}
</style>
