<template>
  <AgentPageShell
    title="AI 健康检查"
    description="统一查看 Embedding、知识库和向量能力状态，辅助定位 RAG 降级、reindex 失败和配置问题。"
  >
    <template #actions>
      <el-button :loading="loading" type="primary" @click="loadAll">刷新</el-button>
    </template>

    <el-row :gutter="16" class="summary-row">
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
          <div class="metric-label">知识文档</div>
          <div class="metric-value">{{ knowledge.documentCount ?? 0 }}</div>
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
import { onMounted, reactive, ref } from 'vue'
import AgentPageShell from './components/AgentPageShell.vue'
import { getAiKnowledgeStats, getEmbeddingHealth } from '../../api/agent'

const loading = ref(false)
const embedding = reactive<Record<string, any>>({})
const knowledge = reactive<Record<string, any>>({})

async function loadAll() {
  loading.value = true
  try {
    const [embeddingResp, knowledgeResp] = await Promise.all([
      getEmbeddingHealth(),
      getAiKnowledgeStats()
    ])
    Object.assign(embedding, embeddingResp || {})
    Object.assign(knowledge, knowledgeResp || {})
  } finally {
    loading.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.summary-row {
  margin-bottom: 16px;
}

.block-card {
  margin-bottom: 16px;
}

.metric-label {
  color: #64748b;
  font-size: 13px;
}

.metric-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
}

.tag-section {
  margin-top: 14px;
}

.tag-title {
  color: #64748b;
  font-weight: 700;
  font-size: 13px;
  margin-bottom: 8px;
}

.tag-item {
  margin-right: 8px;
  margin-bottom: 8px;
}
</style>
