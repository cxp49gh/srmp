<template>
  <AgentPageShell
    title="向量知识库验证"
    description="用于导入道路养护知识、验证向量检索、检查一张图 Agent 是否调用 knowledge.retrieve。"
  >
    <el-row :gutter="16" class="summary-row">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="metric-label">文档数</div>
          <div class="metric-value">{{ stats.documentCount ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="metric-label">切片数</div>
          <div class="metric-value">{{ stats.chunkCount ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="metric-label">已生成 Embedding</div>
          <div class="metric-value">{{ stats.embeddedChunkCount ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="metric-label">向量能力</div>
          <div class="metric-value">
            <el-tag :type="stats.vectorEnabled ? 'success' : 'danger'">
              {{ stats.vectorEnabled ? '已启用' : '未启用' }}
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="block-card">
      <template #header>
        <div class="card-header">
          <span>知识库状态</span>
          <el-button size="small" :loading="loadingStats" @click="loadStats">刷新</el-button>
        </div>
      </template>

      <el-descriptions :column="3" border>
        <el-descriptions-item label="Embedding Provider">{{ stats.embeddingProvider || '-' }}</el-descriptions-item>
        <el-descriptions-item label="Embedding Model">{{ stats.embeddingModel || '-' }}</el-descriptions-item>
        <el-descriptions-item label="维度">{{ stats.embeddingDimensions || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ stats.status || '-' }}</el-descriptions-item>
        <el-descriptions-item label="说明" :span="2">{{ stats.message || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="source-types" v-if="stats.sourceTypes">
        <el-tag
          v-for="(count, type) in stats.sourceTypes"
          :key="type"
          class="source-type"
          effect="plain"
        >
          {{ type }}: {{ count }}
        </el-tag>
      </div>
    </el-card>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card class="block-card">
          <template #header>Markdown 知识导入</template>
          <el-form label-width="90px">
            <el-form-item label="标题">
              <el-input v-model="ingestForm.title" />
            </el-form-item>
            <el-form-item label="来源类型">
              <el-input v-model="ingestForm.sourceType" />
            </el-form-item>
            <el-form-item label="来源ID">
              <el-input v-model="ingestForm.sourceId" />
            </el-form-item>
            <el-form-item label="内容">
              <el-input
                v-model="ingestForm.content"
                type="textarea"
                :autosize="{ minRows: 8, maxRows: 16 }"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="ingesting" @click="ingest">导入知识</el-button>
            </el-form-item>
          </el-form>
          <pre v-if="ingestResult">{{ ingestResult }}</pre>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card class="block-card">
          <template #header>向量检索验证</template>
          <el-form label-width="90px">
            <el-form-item label="Query">
              <el-input v-model="searchForm.query" />
            </el-form-item>
            <el-form-item label="TopK">
              <el-input-number v-model="searchForm.topK" :min="1" :max="20" />
            </el-form-item>
            <el-form-item label="来源类型">
              <el-select v-model="searchForm.sourceTypes" multiple clearable style="width: 100%">
                <el-option label="MANUAL" value="MANUAL" />
                <el-option label="OUTLINE" value="OUTLINE" />
                <el-option label="TEMPLATE" value="TEMPLATE" />
                <el-option label="SOLUTION_TASK" value="SOLUTION_TASK" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="searching" @click="search">检索</el-button>
            </el-form-item>
          </el-form>

          <div v-if="searchResult" class="debug-line">
            <el-tag>{{ searchResult.searchMode || 'UNKNOWN' }}</el-tag>
            <el-tag :type="searchResult.vectorUsed ? 'success' : 'warning'">
              vectorUsed={{ searchResult.vectorUsed }}
            </el-tag>
            <el-tag v-if="searchResult.embeddingProvider">provider={{ searchResult.embeddingProvider }}</el-tag>
          </div>

          <el-empty v-if="searchHits.length === 0" description="暂无命中" />
          <div v-for="hit in searchHits" :key="hit.chunkId" class="hit-item">
            <strong>{{ hit.title }}</strong>
            <span class="score">score: {{ formatScore(hit.score) }}</span>
            <p>{{ hit.sectionTitle || hit.sourceType }}</p>
            <div>{{ hit.content }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="block-card">
      <template #header>一张图 Agent 验证</template>
      <el-form :inline="true">
        <el-form-item label="问题">
          <el-input v-model="agentQuestion" style="width: 420px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="askingAgent" @click="askAgent">调用 Agent</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="16">
        <el-col :span="12">
          <h3>回答</h3>
          <pre>{{ agentAnswer || '暂无回答' }}</pre>
        </el-col>
        <el-col :span="12">
          <h3>工具调用 / 参考资料</h3>
          <pre>{{ agentDebug || '暂无调试信息' }}</pre>
        </el-col>
      </el-row>
    </el-card>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import {
  getAiKnowledgeStats,
  ingestKnowledgeMarkdown,
  mapAgentChat,
  searchAiKnowledge
} from '../../api/agent'

const loadingStats = ref(false)
const ingesting = ref(false)
const searching = ref(false)
const askingAgent = ref(false)

const stats = reactive<Record<string, any>>({})
const ingestResult = ref('')
const searchResult = ref<Record<string, any> | null>(null)
const searchHits = ref<Record<string, any>[]>([])
const agentQuestion = ref('这个修补损坏病害怎么处理？')
const agentAnswer = ref('')
const agentDebug = ref('')

const ingestForm = reactive({
  title: '修补损坏处置技术指南',
  sourceType: 'MANUAL',
  sourceId: 'manual-ui-test',
  content: '# 修补损坏\\n修补损坏通常需要现场复核修补边界、基层状态和排水条件。若为表层损坏，可采用局部铣刨、清理、重新摊铺或热补修复。'
})

const searchForm = reactive({
  query: '修补损坏怎么处理',
  topK: 5,
  sourceTypes: ['MANUAL']
})

async function loadStats() {
  loadingStats.value = true
  try {
    const data: any = await getAiKnowledgeStats()
    Object.assign(stats, data?.data || data || {})
  } finally {
    loadingStats.value = false
  }
}

async function ingest() {
  ingesting.value = true
  try {
    const data: any = await ingestKnowledgeMarkdown({
      title: ingestForm.title,
      sourceType: ingestForm.sourceType,
      sourceId: ingestForm.sourceId,
      content: ingestForm.content
    })
    ingestResult.value = JSON.stringify(data?.data || data, null, 2)
    ElMessage.success('导入成功')
    await loadStats()
  } finally {
    ingesting.value = false
  }
}

async function search() {
  searching.value = true
  try {
    const data: any = await searchAiKnowledge({
      query: searchForm.query,
      topK: searchForm.topK,
      sourceTypes: searchForm.sourceTypes
    })
    const result = data?.data || data || {}
    searchResult.value = result
    searchHits.value = result.hits || []
  } finally {
    searching.value = false
  }
}

async function askAgent() {
  askingAgent.value = true
  try {
    const data: any = await mapAgentChat({
      message: agentQuestion.value,
      mapContext: {
        mode: 'OBJECT',
        routeCode: 'G210',
        year: 2026,
        mapObject: {
          objectType: 'DISEASE',
          routeCode: 'G210',
          startStake: 69.007,
          endStake: 69.034,
          diseaseName: '修补损坏',
          severity: 'MEDIUM'
        }
      },
      options: {
        useKnowledge: true,
        useBusinessData: true,
        useTools: true,
        topK: 5
      }
    })
    const result = data?.data || data || {}
    agentAnswer.value = result.answer || data?.answer || ''
    agentDebug.value = JSON.stringify({
      sources: result.sources || result.knowledgeSources || data?.knowledgeSources,
      toolResults: result.toolResults || data?.toolResults,
      trace: result.trace || data?.trace
    }, null, 2)
  } finally {
    askingAgent.value = false
  }
}

function formatScore(score: any) {
  const num = Number(score)
  return Number.isFinite(num) ? num.toFixed(3) : '-'
}

onMounted(loadStats)
</script>

<style scoped>
.summary-row {
  margin-bottom: 16px;
}

.block-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.metric-label {
  color: #64748b;
  font-size: 13px;
}

.metric-value {
  margin-top: 8px;
  font-size: 26px;
  font-weight: 800;
  color: #0f172a;
}

.source-types {
  margin-top: 12px;
}

.source-type {
  margin-right: 8px;
  margin-bottom: 8px;
}

.debug-line {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.hit-item {
  padding: 10px;
  border-radius: 10px;
  background: #f8fafc;
  margin-bottom: 10px;
}

.hit-item p {
  margin: 4px 0;
  color: #64748b;
}

.score {
  float: right;
  color: #2563eb;
}

pre {
  white-space: pre-wrap;
  line-height: 1.6;
  background: #0f172a;
  color: #dbeafe;
  padding: 12px;
  border-radius: 10px;
  max-height: 420px;
  overflow: auto;
}
</style>
