<template>
  <AgentPageShell
    title="AI 健康检查"
    description="统一查看 LLM 超时配置、Embedding、知识库和向量能力状态，辅助定位 AI 超时、RAG 降级和 reindex 失败。"
  >
    <template #actions>
      <el-button :loading="probingLlm" type="warning" plain @click="probeLlm">LLM 探测</el-button>
      <el-button :loading="loading" type="primary" @click="loadAll">刷新</el-button>
    </template>

    <el-row :gutter="16" class="summary-row">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="metric-label">LLM</div>
          <div class="metric-value">
            <el-tag :type="llm.enabled && (llm.available !== false) ? 'success' : 'danger'">
              {{ llm.enabled ? (llm.available === false ? '不可用' : '已启用') : '未启用' }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="metric-label">Embedding</div>
          <div class="metric-value">
            <el-tag :type="embedding.available ? 'success' : 'danger'">
              {{ embedding.available ? '可用' : '不可用' }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="metric-label">知识切片</div>
          <div class="metric-value">{{ knowledge.chunkCount ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="metric-label">向量能力</div>
          <div class="metric-value">
            <el-tag :type="knowledge.vectorEnabled ? 'success' : 'danger'">
              {{ knowledge.vectorEnabled ? 'pgvector 已启用' : 'pgvector 未启用' }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="block-card">
      <template #header>
        <div class="card-header">
          <span>LLM Provider / Timeout</span>
          <el-tag :type="llmTagType">{{ llm.status || (llm.enabled ? 'READY' : 'DISABLED') }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="Provider">{{ llm.provider || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Model">{{ llm.model || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Enabled">{{ llm.enabled }}</el-descriptions-item>
        <el-descriptions-item label="Base URL" :span="3">{{ llm.baseUrl || '-' }}</el-descriptions-item>
        <el-descriptions-item label="连接超时">{{ llm.connectTimeoutMs ?? '-' }} ms</el-descriptions-item>
        <el-descriptions-item label="读取超时">{{ llm.readTimeoutMs ?? '-' }} ms</el-descriptions-item>
        <el-descriptions-item label="curl max-time">{{ llm.curlMaxTimeSeconds ?? '-' }} s</el-descriptions-item>
        <el-descriptions-item label="maxTokens">{{ llm.maxTokens ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="temperature">{{ llm.temperature ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="最近耗时">{{ llm.probeCostMs ?? llm.costMs ?? '-' }} ms</el-descriptions-item>
        <el-descriptions-item label="错误类型">{{ llm.errorType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="错误信息" :span="2">{{ llm.errorMessage || '-' }}</el-descriptions-item>
        <el-descriptions-item label="探测响应" :span="3">{{ llm.probeAnswerPreview || llm.rawResponsePreview || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="llm.readTimeoutMs && llm.readTimeoutMs < 180000"
        class="tip"
        type="warning"
        show-icon
        title="区域养护方案 Prompt 较长，建议 srmp.llm.read-timeout-ms 与 VITE_AI_TIMEOUT 均设置为 180000~300000。"
      />
    </el-card>

    <el-card class="block-card">
      <template #header>Embedding Provider</template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="Provider">{{ embedding.provider || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Model">{{ embedding.model || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Endpoint">{{ embedding.endpoint || '-' }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ embedding.costMs ?? '-' }} ms</el-descriptions-item>
        <el-descriptions-item label="期望维度">{{ embedding.expectedDimensions ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="实际维度">{{ embedding.actualDimensions ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="错误类型">{{ embedding.errorType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="错误信息">{{ embedding.errorMessage || '-' }}</el-descriptions-item>
        <el-descriptions-item label="建议" :span="2">{{ embedding.suggestion || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card class="block-card">
      <template #header>知识库状态</template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="文档数">{{ knowledge.documentCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="切片数">{{ knowledge.chunkCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="已向量化">{{ knowledge.embeddedChunkCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="Embedding Provider">{{ knowledge.embeddingProvider || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Embedding Model">{{ knowledge.embeddingModel || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Embedding 维度">{{ knowledge.embeddingDimensions || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ knowledge.status || '-' }}</el-descriptions-item>
        <el-descriptions-item label="说明" :span="2">{{ knowledge.message || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="knowledge.chunkEmbeddingProviders" class="tag-section">
        <div class="tag-title">Chunk Embedding 来源分布</div>
        <el-tag
          v-for="(count, key) in knowledge.chunkEmbeddingProviders"
          :key="key"
          class="tag-item"
          :type="String(key).toLowerCase().includes('mock') ? 'warning' : 'success'"
          effect="plain"
        >
          {{ key }}: {{ count }}
        </el-tag>
      </div>
    </el-card>

    <el-alert
      v-if="!embedding.available"
      type="warning"
      show-icon
      title="Embedding 不可用时，RAG 检索会降级为 KEYWORD_FALLBACK，质量评测结果不具备真实向量检索参考价值。"
    />
  </AgentPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import { getAiKnowledgeStats, getEmbeddingHealth, getLlmHealth } from '../../api/agent'

const loading = ref(false)
const probingLlm = ref(false)
const llm = reactive<Record<string, any>>({})
const embedding = reactive<Record<string, any>>({})
const knowledge = reactive<Record<string, any>>({})

const llmTagType = computed(() => {
  const status = String(llm.status || '')
  if (status === 'SUCCESS' || (llm.enabled && llm.available)) return 'success'
  if (status === 'DISABLED' || llm.enabled === false) return 'info'
  if (status === 'ERROR' || status.includes('TIMEOUT') || llm.available === false) return 'danger'
  return 'warning'
})

async function loadAll() {
  loading.value = true
  try {
    const [llmResp, embeddingResp, knowledgeResp] = await Promise.all([
      getLlmHealth(false),
      getEmbeddingHealth(),
      getAiKnowledgeStats()
    ])
    Object.assign(llm, llmResp || {})
    Object.assign(embedding, embeddingResp || {})
    Object.assign(knowledge, knowledgeResp || {})
  } finally {
    loading.value = false
  }
}

async function probeLlm() {
  probingLlm.value = true
  try {
    const resp = await getLlmHealth(true)
    Object.assign(llm, resp || {})
    ElMessage.success(resp?.available ? 'LLM 探测通过' : 'LLM 探测未通过')
  } finally {
    probingLlm.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.summary-row { margin-bottom: 16px; }
.block-card { margin-bottom: 16px; }
.card-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.metric-label { color: #64748b; font-size: 13px; }
.metric-value { margin-top: 8px; font-size: 24px; font-weight: 800; color: #0f172a; }
.tag-section { margin-top: 14px; }
.tag-title { color: #64748b; font-weight: 700; font-size: 13px; margin-bottom: 8px; }
.tag-item { margin-right: 8px; margin-bottom: 8px; }
.tip { margin-top: 12px; }
</style>
