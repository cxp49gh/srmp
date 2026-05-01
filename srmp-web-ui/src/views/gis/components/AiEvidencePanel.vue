<template>
  <div v-if="hasEvidence" class="ai-evidence-panel">
    <div class="evidence-header" @click="expanded = !expanded">
      <span>回答依据</span>
      <el-tag v-if="knowledgeTool" size="small" :type="knowledgeTool.vectorUsed ? 'success' : 'warning'" effect="plain">
        {{ knowledgeTool.searchMode || knowledgeTool.retrievalStrategy || 'RAG' }}
      </el-tag>
      <el-tag v-if="knowledgeTool?.fallback" size="small" type="warning" effect="plain">降级</el-tag>
      <span class="toggle">{{ expanded ? '收起' : '展开' }}</span>
    </div>

    <div v-if="expanded" class="evidence-body">
      <div class="evidence-block">
        <div class="label">地图上下文</div>
        <div class="value">{{ contextLabel }}</div>
      </div>

      <div v-if="knowledgeTool" class="evidence-grid">
        <div>
          <span class="label">检索策略</span>
          <strong>{{ knowledgeTool.retrievalStrategy || '-' }}</strong>
        </div>
        <div>
          <span class="label">检索模式</span>
          <strong>{{ knowledgeTool.searchMode || '-' }}</strong>
        </div>
        <div>
          <span class="label">向量检索</span>
          <strong>{{ knowledgeTool.vectorUsed ? '是' : '否' }}</strong>
        </div>
        <div>
          <span class="label">Top Score</span>
          <strong>{{ formatScore(knowledgeTool.topScore) }}</strong>
        </div>
      </div>

      <div v-if="knowledgeTool?.rewrittenQuery" class="evidence-block">
        <div class="label">改写后的检索 Query</div>
        <div class="query">{{ knowledgeTool.rewrittenQuery }}</div>
      </div>

      <div v-if="message.sources && message.sources.length" class="evidence-block">
        <div class="label">知识来源</div>
        <ol>
          <li v-for="(source, index) in message.sources" :key="index">
            {{ source.title || source.docTitle || '知识片段' }}
            <template v-if="source.sectionTitle || source.section"> / {{ source.sectionTitle || source.section }}</template>
            <span v-if="source.score !== undefined">（score={{ formatScore(source.score) }}）</span>
          </li>
        </ol>
      </div>

      <div v-if="message.toolResults && message.toolResults.length" class="evidence-block">
        <div class="label">工具调用</div>
        <ul>
          <li v-for="(tool, index) in message.toolResults" :key="index">
            {{ tool.toolName || tool.name }}：{{ tool.summary || '-' }}
          </li>
        </ul>
      </div>

      <div v-if="knowledgeTool?.fallbackReason" class="fallback">
        降级原因：{{ knowledgeTool.fallbackReason }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

const props = defineProps<{
  message: Record<string, any>
  mapContext?: Record<string, any>
}>()

const expanded = ref(false)

const hasEvidence = computed(() => {
  return Boolean((props.message.sources && props.message.sources.length) || (props.message.toolResults && props.message.toolResults.length))
})

const knowledgeTool = computed(() => {
  const tools = props.message.toolResults || []
  const tool = tools.find((it: any) => (it.toolName || it.name) === 'knowledge.retrieve')
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
  const route = obj.routeCode || obj.route_code || ctx.routeCode || ctx.query?.routeCode || '-'
  const disease = obj.diseaseName || obj.disease_name || obj.diseaseType || obj.disease_type || ''
  const mode = ctx.mode || (obj.objectType ? 'OBJECT' : '-')
  return `${mode}｜${route}${disease ? `｜${disease}` : ''}`
})

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

.evidence-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 8px;
}

.label {
  display: block;
  color: #64748b;
  margin-bottom: 3px;
  font-weight: 600;
}

.value,
.query {
  color: #0f172a;
  line-height: 1.5;
}

.query {
  background: #fff;
  border-radius: 8px;
  padding: 6px;
  word-break: break-word;
}

.evidence-block {
  margin-top: 8px;
}

ol,
ul {
  margin: 4px 0 0 18px;
  padding: 0;
}

li {
  margin: 3px 0;
  color: #334155;
}

.fallback {
  margin-top: 8px;
  color: #b45309;
}
</style>
