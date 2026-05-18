<template>
  <div v-if="hasEvidence" class="ai-evidence-panel" :class="{ embedded }">
    <div v-if="!embedded" class="evidence-header" @click="expanded = !expanded">
      <span>依据与调用</span>
      <el-tag v-if="sourceCount" size="small" type="info" effect="plain">来源 {{ sourceCount }}</el-tag>
      <el-tag v-if="locatableSourceCount" size="small" type="success" effect="plain">地图 {{ locatableSourceCount }}</el-tag>
      <el-tag v-if="toolCount" size="small" :type="knowledgeTool?.fallback ? 'warning' : 'success'" effect="plain">工具 {{ toolCount }}</el-tag>
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
            <span v-if="source.score !== undefined && source.score !== null" class="source-score">{{ formatScore(source.score) }}</span>
          </div>
          <div class="map-target" :class="{ empty: !canLocateSource(source) }">
            {{ targetLabel(source) }}
          </div>
          <div class="source-actions">
            <el-button size="small" link :disabled="!canLocateSource(source)" @click="locateSource(source)">地图定位</el-button>
            <el-button size="small" link @click="askWithSource(source)">围绕来源追问</el-button>
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

      <div v-if="knowledgeTool?.fallbackReason" class="fallback">
        降级原因：{{ knowledgeTool.fallbackReason }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { copyText } from '../../../utils/clipboard'
import { hasLocatableTarget, mapTargetLabel, sourceToMapTarget, type GisSourceMapTarget } from '../../../utils/gisUnifiedContext'

const props = defineProps<{
  message: Record<string, any>
  mapContext?: Record<string, any>
  embedded?: boolean
  defaultExpanded?: boolean
}>()

const emit = defineEmits<{
  (e: 'locate-source', value: GisSourceMapTarget): void
  (e: 'ask-with-source', value: Record<string, any>): void
}>()

const expanded = ref(props.defaultExpanded === true)

const sources = computed(() => Array.isArray(props.message.sources) ? props.message.sources : [])
const toolResults = computed(() => Array.isArray(props.message.toolResults) ? props.message.toolResults : [])
const sourceCount = computed(() => sources.value.length)
const toolCount = computed(() => toolResults.value.length)
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
    ...data
  }
})

const contextLabel = computed(() => {
  const ctx: any = props.mapContext || {}
  const obj = ctx.mapObject || ctx.selectedMapObject || ctx.selected || {}
  const region = ctx.regionContext || ctx.region || {}
  const route = obj.routeCode || obj.route_code || region.routeCode || ctx.routeCode || ctx.query?.routeCode || '-'
  const disease = obj.diseaseName || obj.disease_name || obj.diseaseType || obj.disease_type || ''
  const mode = ctx.mode || ctx.contextScope || (region.objectType ? 'REGION' : (obj.objectType ? 'OBJECT' : '-'))
  return `${mode}｜${route}${disease ? `｜${disease}` : ''}`
})

function sourceTitle(source: any) {
  const title = source.title || source.docTitle || source.documentTitle || source.sourceTitle || '知识片段'
  const section = source.sectionTitle || source.section || source.heading
  return section ? `${title} / ${section}` : title
}

function targetLabel(source: any) {
  return mapTargetLabel(sourceToMapTarget(source))
}

function canLocateSource(source: any) {
  return hasLocatableTarget(sourceToMapTarget(source))
}

function locateSource(source: any) {
  emit('locate-source', sourceToMapTarget(source))
}

function askWithSource(source: any) {
  emit('ask-with-source', source)
}

async function copySource(source: any) {
  try {
    await copyText(JSON.stringify({
      title: sourceTitle(source),
      mapTarget: sourceToMapTarget(source),
      raw: source
    }, null, 2))
    ElMessage.success('来源与地图关联信息已复制')
  } catch (error: any) {
    ElMessage.error(error?.message || '复制失败')
  }
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
  gap: 8px;
  margin-left: 20px;
  flex-wrap: wrap;
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
</style>
