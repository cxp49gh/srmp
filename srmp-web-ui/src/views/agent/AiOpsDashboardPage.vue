<template>
  <AgentPageShell
    title="AI 运维总览"
    description="汇总 LLM、Embedding、知识库、Outline 同步和最近 AI 调用状态，作为 AI/RAG/Outline 运维入口。"
  >
    <template #actions>
      <div class="header-actions">
        <el-button :loading="loading" @click="loadAll">刷新总览</el-button>
        <el-button type="warning" plain :loading="probing" @click="probeLlm">LLM 探测</el-button>
        <el-button type="success" plain :loading="vectorizing" @click="vectorizeOutlineNow">补 Outline 向量</el-button>
        <el-button type="primary" plain :loading="scanning" @click="scanDueNow">扫描到期同步</el-button>
      </div>
    </template>

    <div class="ops-page">
      <section class="metric-grid">
        <el-card shadow="never" class="metric-card" :class="healthClass(llmHealthy)">
          <div class="metric-head">
            <span>LLM</span>
            <el-tag size="small" :type="statusType(llmHealthy)">{{ llmStatusText }}</el-tag>
          </div>
          <div class="metric-value">{{ value(llm, ['model']) || '-' }}</div>
          <div class="metric-desc">
            Provider：{{ value(llm, ['provider']) || '-' }}；Read Timeout：{{ value(llm, ['readTimeoutMs']) || '-' }}ms
          </div>
          <div v-if="value(llm, ['errorMessage'])" class="metric-error">{{ value(llm, ['errorMessage']) }}</div>
        </el-card>

        <el-card shadow="never" class="metric-card" :class="healthClass(embeddingHealthy)">
          <div class="metric-head">
            <span>Embedding</span>
            <el-tag size="small" :type="statusType(embeddingHealthy)">{{ embeddingStatusText }}</el-tag>
          </div>
          <div class="metric-value">{{ value(embedding, ['model']) || '-' }}</div>
          <div class="metric-desc">
            Provider：{{ value(embedding, ['provider']) || '-' }}；维度：{{ value(embedding, ['actualDimensions', 'expectedDimensions']) || '-' }}
          </div>
          <div v-if="value(embedding, ['errorMessage'])" class="metric-error">{{ value(embedding, ['errorMessage']) }}</div>
        </el-card>

        <el-card shadow="never" class="metric-card" :class="healthClass(!ragReadiness.requiresAction)">
          <div class="metric-head">
            <span>知识库</span>
            <el-tag size="small" :type="ragReadiness.tagType">{{ ragReadiness.statusLabel }}</el-tag>
          </div>
          <div class="metric-value">{{ value(knowledgeStats, ['documentCount']) || 0 }} 文档</div>
          <div class="metric-desc">
            Chunk：{{ value(knowledgeStats, ['chunkCount']) || 0 }}；已向量：{{ value(knowledgeStats, ['embeddedChunkCount']) || 0 }}
          </div>
          <div v-if="ragReadiness.requiresAction" class="metric-error">{{ ragReadiness.title }}</div>
        </el-card>

        <el-card shadow="never" class="metric-card" :class="Number(value(outlineStats, ['pendingEmbeddingChunkCount']) || 0) > 0 ? 'warn' : 'ok'">
          <div class="metric-head">
            <span>Outline 闭环</span>
            <el-tag size="small" :type="Number(value(outlineStats, ['pendingEmbeddingChunkCount']) || 0) > 0 ? 'warning' : 'success'">
              {{ Number(value(outlineStats, ['pendingEmbeddingChunkCount']) || 0) > 0 ? 'PENDING' : 'READY' }}
            </el-tag>
          </div>
          <div class="metric-value">{{ value(outlineStats, ['documentCount']) || 0 }} 文档</div>
          <div class="metric-desc">
            Chunk：{{ value(outlineStats, ['chunkCount']) || 0 }}；待补向量：{{ value(outlineStats, ['pendingEmbeddingChunkCount']) || 0 }}
          </div>
        </el-card>
      </section>

      <section class="content-grid">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>待处理事项</span>
              <el-button size="small" text @click="copySummary">复制摘要</el-button>
            </div>
          </template>

          <el-alert
            v-if="ragReadiness.requiresAction"
            :type="ragReadiness.tagType === 'danger' ? 'error' : 'warning'"
            show-icon
            class="mb"
            :title="ragReadiness.title"
            :description="ragReadiness.detail"
          >
            <div class="alert-actions">
              <el-button
                v-if="ragReadiness.actions.includes('VECTORIZE_OUTLINE')"
                size="small"
                type="warning"
                plain
                :loading="vectorizing"
                @click="vectorizeOutlineNow"
              >
                补 Outline 向量
              </el-button>
              <el-button v-if="ragReadiness.actions.includes('SYNC_OUTLINE')" size="small" @click="go('/agent/outline/sync')">去同步入库</el-button>
              <el-button v-if="ragReadiness.actions.includes('VERIFY_KNOWLEDGE')" size="small" @click="go('/agent/knowledge-vector')">验证知识库检索</el-button>
            </div>
          </el-alert>
          <el-alert
            v-if="!llmHealthy"
            type="error"
            show-icon
            class="mb"
            title="LLM 当前不可用或未通过健康检查，请优先检查模型配置、网络代理和后端 read-timeout。"
          />
          <el-alert
            v-if="!embeddingHealthy"
            type="warning"
            show-icon
            class="mb"
            title="Embedding 当前不可用或维度异常，RAG 可能退化为关键词检索。"
          />
          <el-alert
            v-if="timeoutWarning"
            type="warning"
            show-icon
            class="mb"
            :title="timeoutWarning"
          />
          <el-empty v-if="todoEmpty" description="当前未发现明显阻塞项" />
        </el-card>

        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>快捷入口</span>
            </div>
          </template>
          <div class="shortcut-grid">
            <el-button @click="go('/agent/ai-health')">AI 健康检查</el-button>
            <el-button @click="go('/agent/ai-traces')">AI 调用监控</el-button>
            <el-button @click="go('/agent/langgraph-ops')">LangGraph 编排</el-button>
            <el-button @click="go('/agent/knowledge-vector')">向量知识库验证</el-button>
            <el-button @click="go('/agent/rag-eval')">RAG 质量评测</el-button>
            <el-button @click="go('/agent/outline/auto-sync')">Outline 自动同步</el-button>
            <el-button @click="go('/agent/solution-tasks')">方案任务闭环</el-button>
            <el-button @click="go('/agent/solution-eval')">方案回归评测</el-button>
          </div>
        </el-card>
      </section>

      <section class="content-grid lower">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>最近 AI Trace</span>
              <el-button size="small" @click="go('/agent/ai-traces')">查看更多</el-button>
            </div>
          </template>
          <el-empty v-if="traces.length === 0" description="暂无 Trace" />
          <div v-for="item in traces" :key="item.trace_id || item.traceId" class="trace-row" @click="go('/agent/ai-traces')">
            <div>
              <strong>{{ value(item, ['trace_id', 'traceId']) || '-' }}</strong>
              <p>{{ value(item, ['user_message', 'userMessage']) || value(item, ['request_type', 'requestType']) || '-' }}</p>
            </div>
            <div class="trace-meta">
              <el-tag size="small" :type="tagType(value(item, ['status']))">{{ value(item, ['status']) || '-' }}</el-tag>
              <span>{{ value(item, ['total_cost_ms', 'totalCostMs']) || 0 }}ms</span>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>Outline 自动同步</span>
              <el-button size="small" @click="go('/agent/outline/auto-sync')">进入配置</el-button>
            </div>
          </template>
          <div class="outline-summary">
            <div>
              <span class="label">配置数</span>
              <strong>{{ configs.length }}</strong>
            </div>
            <div>
              <span class="label">启用数</span>
              <strong>{{ enabledConfigCount }}</strong>
            </div>
            <div>
              <span class="label">最近运行</span>
              <strong>{{ runs.length }}</strong>
            </div>
          </div>
          <el-empty v-if="runs.length === 0" description="暂无运行记录" />
          <div v-for="item in runs" :key="item.id" class="run-row">
            <div>
              <strong>{{ value(item, ['config_name', 'configName', 'config_id', 'configId']) || '-' }}</strong>
              <p>{{ value(item, ['trigger_type', 'triggerType']) || '-' }} / {{ value(item, ['started_at', 'startedAt']) || '-' }}</p>
            </div>
            <el-tag size="small" :type="tagType(value(item, ['status']))">{{ value(item, ['status']) || '-' }}</el-tag>
          </div>
        </el-card>
      </section>

      <el-card shadow="never" class="raw-card">
        <template #header>
          <div class="card-header">
            <span>运行结果</span>
            <el-button size="small" text @click="lastActionResult = ''">清空</el-button>
          </div>
        </template>
        <el-empty v-if="!lastActionResult" description="暂无操作结果" />
        <pre v-else>{{ lastActionResult }}</pre>
      </el-card>
    </div>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import { getEmbeddingHealth, getLlmHealth, getAiKnowledgeStats } from '../../api/agent'
import {
  getOutlineAutoSyncConfigs,
  getOutlineAutoSyncRuns,
  getOutlineKnowledgeStats,
  scanOutlineAutoSyncDue,
  vectorizeOutline
} from '../../api/outline'
import { listAiTraces } from '../../api/trace'
import { copyToClipboard } from '../../utils/clipboard'
import { buildKnowledgeReadiness } from '../../utils/aiKnowledgeReadiness'

const router = useRouter()
const loading = ref(false)
const probing = ref(false)
const vectorizing = ref(false)
const scanning = ref(false)
const lastActionResult = ref('')

const llm = reactive<Record<string, any>>({})
const embedding = reactive<Record<string, any>>({})
const knowledgeStats = reactive<Record<string, any>>({})
const outlineStats = reactive<Record<string, any>>({})
const configs = ref<Record<string, any>[]>([])
const runs = ref<Record<string, any>[]>([])
const traces = ref<Record<string, any>[]>([])

const llmHealthy = computed(() => Boolean(value(llm, ['available', 'success', 'enabled'])) && !value(llm, ['errorMessage', 'error_message']))
const embeddingHealthy = computed(() => Boolean(value(embedding, ['available'])) && !value(embedding, ['errorMessage', 'error_message']))
const ragReadiness = computed(() => buildKnowledgeReadiness({ knowledgeStats, outlineStats, embedding }))
const enabledConfigCount = computed(() => configs.value.filter((item) => truthy(value(item, ['enabled']))).length)
const llmStatusText = computed(() => llmHealthy.value ? 'OK' : (value(llm, ['status']) || 'CHECK'))
const embeddingStatusText = computed(() => embeddingHealthy.value ? 'OK' : (value(embedding, ['errorType']) || 'CHECK'))
const timeoutWarning = computed(() => {
  const frontendTimeout = Number(import.meta.env.VITE_AI_TIMEOUT || 300000)
  const backendReadTimeout = Number(value(llm, ['readTimeoutMs']) || 0)
  if (backendReadTimeout > 0 && frontendTimeout <= backendReadTimeout) {
    return `前端 VITE_AI_TIMEOUT=${frontendTimeout}ms 不大于后端 read-timeout=${backendReadTimeout}ms，浏览器可能先超时。`
  }
  return ''
})
const todoEmpty = computed(() => llmHealthy.value && embeddingHealthy.value && !ragReadiness.value.requiresAction && !timeoutWarning.value && Number(value(outlineStats, ['pendingEmbeddingChunkCount']) || 0) <= 0)

async function loadAll() {
  loading.value = true
  try {
    const [llmData, embeddingData, knowledgeData, outlineData, configData, runData, traceData] = await Promise.allSettled([
      getLlmHealth(false),
      getEmbeddingHealth(),
      getAiKnowledgeStats(),
      getOutlineKnowledgeStats(),
      getOutlineAutoSyncConfigs(),
      getOutlineAutoSyncRuns({ limit: 8 }),
      listAiTraces({ limit: 8 })
    ])
    assignIfFulfilled(llm, llmData)
    assignIfFulfilled(embedding, embeddingData)
    assignIfFulfilled(knowledgeStats, knowledgeData)
    assignIfFulfilled(outlineStats, outlineData)
    configs.value = arrayIfFulfilled(configData)
    runs.value = arrayIfFulfilled(runData)
    traces.value = arrayIfFulfilled(traceData)
  } finally {
    loading.value = false
  }
}

async function probeLlm() {
  probing.value = true
  try {
    const data = await getLlmHealth(true)
    Object.assign(llm, data || {})
    lastActionResult.value = JSON.stringify(data, null, 2)
    ElMessage.success('LLM 探测完成')
  } finally {
    probing.value = false
  }
}

async function vectorizeOutlineNow() {
  vectorizing.value = true
  try {
    const data = await vectorizeOutline({ force: false, limit: 200 })
    lastActionResult.value = JSON.stringify(data, null, 2)
    ElMessage.success('Outline 补向量已完成')
    await loadAll()
  } finally {
    vectorizing.value = false
  }
}

async function scanDueNow() {
  scanning.value = true
  try {
    const data = await scanOutlineAutoSyncDue()
    lastActionResult.value = JSON.stringify(data, null, 2)
    ElMessage.success('到期同步扫描已触发')
    await loadAll()
  } finally {
    scanning.value = false
  }
}

async function copySummary() {
  const summary = [
    `LLM: ${llmStatusText.value} ${value(llm, ['provider']) || ''}/${value(llm, ['model']) || ''}`,
    `Embedding: ${embeddingStatusText.value} ${value(embedding, ['provider']) || ''}/${value(embedding, ['model']) || ''}`,
    `RAG: ${ragReadiness.value.status} - ${ragReadiness.value.title}`,
    `Knowledge: documents=${value(knowledgeStats, ['documentCount']) || 0}, chunks=${value(knowledgeStats, ['chunkCount']) || 0}, embedded=${value(knowledgeStats, ['embeddedChunkCount']) || 0}`,
    `Outline: documents=${value(outlineStats, ['documentCount']) || 0}, chunks=${value(outlineStats, ['chunkCount']) || 0}, pendingEmbedding=${value(outlineStats, ['pendingEmbeddingChunkCount']) || 0}`,
    timeoutWarning.value ? `Warning: ${timeoutWarning.value}` : ''
  ].filter(Boolean).join('\n')
  await copyToClipboard(summary)
  ElMessage.success('运维摘要已复制')
}

function assignIfFulfilled(target: Record<string, any>, result: PromiseSettledResult<any>) {
  if (result.status === 'fulfilled') {
    Object.keys(target).forEach((key) => delete target[key])
    Object.assign(target, result.value || {})
  }
}

function arrayIfFulfilled(result: PromiseSettledResult<any>): Record<string, any>[] {
  return result.status === 'fulfilled' && Array.isArray(result.value) ? result.value : []
}

function value(obj: Record<string, any> | null | undefined, keys: string[]) {
  if (!obj) return undefined
  for (const key of keys) {
    const current = obj[key]
    if (current !== undefined && current !== null && current !== '') return current
  }
  return undefined
}

function truthy(val: any) {
  return val === true || val === 'true' || val === 1 || val === '1' || val === 'Y'
}

function statusType(ok: boolean) {
  return ok ? 'success' : 'warning'
}

function healthClass(ok: boolean) {
  return ok ? 'ok' : 'warn'
}

function tagType(status: any) {
  const val = String(status || '').toUpperCase()
  if (['SUCCESS', 'OK', 'READY', 'COMPLETED'].includes(val)) return 'success'
  if (['FAILED', 'ERROR'].includes(val)) return 'danger'
  if (['TIMEOUT', 'RUNNING', 'PROCESSING', 'PENDING'].includes(val)) return 'warning'
  return 'info'
}

function go(path: string) {
  router.push(path)
}

onMounted(loadAll)
</script>

<style scoped>
.ops-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.header-actions,
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.metric-card {
  border-left: 4px solid #94a3b8;
}

.metric-card.ok {
  border-left-color: #16a34a;
}

.metric-card.warn {
  border-left-color: #f59e0b;
}

.metric-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 13px;
}

.metric-value {
  margin-top: 10px;
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
  word-break: break-word;
}

.metric-desc,
.metric-error {
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
  word-break: break-all;
}

.metric-error {
  color: #dc2626;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(420px, 1fr) minmax(420px, 1fr);
  gap: 16px;
}

.panel-card {
  min-height: 220px;
}

.mb {
  margin-bottom: 10px;
}

.alert-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.trace-row,
.run-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #e2e8f0;
}

.trace-row {
  cursor: pointer;
}

.trace-row:hover {
  background: #f8fafc;
}

.trace-row strong,
.run-row strong {
  font-size: 13px;
  color: #0f172a;
  word-break: break-all;
}

.trace-row p,
.run-row p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 12px;
  max-width: 520px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.trace-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 12px;
  flex: 0 0 auto;
}

.outline-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}

.outline-summary > div {
  background: #f8fafc;
  border-radius: 10px;
  padding: 12px;
}

.outline-summary .label {
  display: block;
  color: #64748b;
  font-size: 12px;
  margin-bottom: 6px;
}

.outline-summary strong {
  font-size: 22px;
}

.raw-card pre {
  white-space: pre-wrap;
  word-break: break-all;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 12px;
  padding: 14px;
  max-height: 360px;
  overflow: auto;
}

@media (max-width: 1200px) {
  .metric-grid,
  .content-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .metric-grid,
  .content-grid,
  .shortcut-grid,
  .outline-summary {
    grid-template-columns: 1fr;
  }
}
</style>
