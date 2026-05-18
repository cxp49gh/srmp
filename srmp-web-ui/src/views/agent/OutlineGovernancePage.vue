<template>
  <AgentPageShell title="Outline 内容治理" description="查看 OUTLINE 知识覆盖、低质量文档、引用热度和待治理事项。">
    <template #actions>
      <el-button type="primary" :loading="loading" @click="loadDashboard">刷新</el-button>
    </template>

    <div class="stats-grid">
      <el-card shadow="never">
        <div class="stat-label">有效文档</div>
        <div class="stat-value">{{ value(summary, 'documentCount') || 0 }}</div>
      </el-card>
      <el-card shadow="never">
        <div class="stat-label">Chunk / 待补向量</div>
        <div class="stat-value">
          {{ value(summary, 'chunkCount') || 0 }} /
          <span :class="Number(value(summary, 'pendingEmbeddingChunkCount') || 0) > 0 ? 'warning' : 'success'">
            {{ value(summary, 'pendingEmbeddingChunkCount') || 0 }}
          </span>
        </div>
      </el-card>
      <el-card shadow="never">
        <div class="stat-label">零引用文档</div>
        <div class="stat-value" :class="Number(value(summary, 'zeroReferenceDocumentCount') || 0) > 0 ? 'warning' : 'success'">
          {{ value(summary, 'zeroReferenceDocumentCount') || 0 }}
        </div>
      </el-card>
      <el-card shadow="never">
        <div class="stat-label">长期未更新</div>
        <div class="stat-value" :class="Number(value(summary, 'staleDocumentCount') || 0) > 0 ? 'warning' : 'success'">
          {{ value(summary, 'staleDocumentCount') || 0 }}
        </div>
      </el-card>
      <el-card shadow="never">
        <div class="stat-label">低质量候选</div>
        <div class="stat-value" :class="lowQualityDocuments.length > 0 ? 'warning' : 'success'">
          {{ lowQualityDocuments.length }}
        </div>
      </el-card>
    </div>

    <el-alert
      v-if="tasks.length"
      type="warning"
      show-icon
      class="mb"
      title="存在待治理事项，建议按优先级处理。"
    />

    <el-row :gutter="16" class="mb">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>知识覆盖</span>
              <span class="muted">按关键词粗粒度统计</span>
            </div>
          </template>
          <el-table :data="coverage" size="small" border v-loading="loading">
            <el-table-column label="维度" width="110" prop="dimension" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="coverageTag(row.status)">{{ coverageLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="文档数" width="90" prop="documentCount" />
            <el-table-column label="覆盖率" width="100">
              <template #default="{ row }">{{ row.coverageRate || 0 }}%</template>
            </el-table-column>
            <el-table-column label="关键词" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">{{ (row.keywords || []).join('、') }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>待治理任务</span>
              <el-button link type="primary" @click="goFeedback">知识反馈</el-button>
            </div>
          </template>
          <el-empty v-if="!tasks.length" description="暂无待治理任务" />
          <div v-else class="task-list">
            <div v-for="task in tasks" :key="`${task.priority}-${task.title}`" class="task-item">
              <el-tag size="small" :type="priorityTag(task.priority)">{{ task.priority }}</el-tag>
              <div class="task-body">
                <strong>{{ task.title }}</strong>
                <p>{{ task.description }}</p>
                <span>{{ task.action }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="mb">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>低质量文档候选</span>
              <span class="muted">空文档、过短、默认文档、长期未更新、零引用</span>
            </div>
          </template>
          <el-table :data="lowQualityDocuments" size="small" border v-loading="loading" max-height="420">
            <el-table-column label="级别" width="86">
              <template #default="{ row }">
                <el-tag size="small" :type="qualityTag(row.qualityLevel)">{{ qualityLabel(row.qualityLevel) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="标题" min-width="220" show-overflow-tooltip prop="title" />
            <el-table-column label="问题" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ (row.issues || []).join('、') }}</template>
            </el-table-column>
            <el-table-column label="字符" width="90">
              <template #default="{ row }">{{ value(row, 'content_chars', 'contentChars') || 0 }}</template>
            </el-table-column>
            <el-table-column label="引用" width="80">
              <template #default="{ row }">{{ value(row, 'reference_count', 'referenceCount') || 0 }}</template>
            </el-table-column>
            <el-table-column label="更新时间" width="168">
              <template #default="{ row }">{{ formatDateTime(value(row, 'updated_at', 'updatedAt')) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>引用热度</span>
              <span class="muted">方案来源中被引用的 OUTLINE 文档</span>
            </div>
          </template>
          <el-table :data="citationHeat" size="small" border v-loading="loading" max-height="420">
            <el-table-column label="文档" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <el-link v-if="value(row, 'source_url', 'sourceUrl')" :href="value(row, 'source_url', 'sourceUrl')" target="_blank">
                  {{ value(row, 'source_title', 'sourceTitle') || value(row, 'source_id', 'sourceId') }}
                </el-link>
                <span v-else>{{ value(row, 'source_title', 'sourceTitle') || value(row, 'source_id', 'sourceId') }}</span>
              </template>
            </el-table-column>
            <el-table-column label="引用" width="80">
              <template #default="{ row }">{{ value(row, 'reference_count', 'referenceCount') || 0 }}</template>
            </el-table-column>
            <el-table-column label="最近引用" width="168">
              <template #default="{ row }">{{ formatDateTime(value(row, 'last_referenced_at', 'lastReferencedAt')) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>用户反馈概览</span>
          <span class="muted">来自 AI 答案来源区的知识缺失和来源不准确反馈</span>
        </div>
      </template>
      <el-table :data="feedbackSummary" size="small" border v-loading="loading">
        <el-table-column label="反馈类型" width="160">
          <template #default="{ row }">{{ feedbackLabel(value(row, 'feedback_type', 'feedbackType')) }}</template>
        </el-table-column>
        <el-table-column label="数量" width="100">
          <template #default="{ row }">{{ value(row, 'feedback_count', 'feedbackCount') || 0 }}</template>
        </el-table-column>
        <el-table-column label="最近反馈" width="180">
          <template #default="{ row }">{{ formatDateTime(value(row, 'last_feedback_at', 'lastFeedbackAt')) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AgentPageShell from './components/AgentPageShell.vue'
import { getOutlineGovernanceDashboard } from '../../api/outline'
import { outlineValue as value } from '../../utils/outlineHelpers'
import { formatDateTime } from '../../utils/dateFormat'

const router = useRouter()
const loading = ref(false)
const dashboard = ref<Record<string, any>>({})

const summary = computed(() => dashboard.value.summary || {})
const coverage = computed(() => dashboard.value.coverage || [])
const lowQualityDocuments = computed(() => dashboard.value.lowQualityDocuments || [])
const citationHeat = computed(() => dashboard.value.citationHeat || [])
const feedbackSummary = computed(() => dashboard.value.feedbackSummary || [])
const tasks = computed(() => dashboard.value.tasks || [])

onMounted(loadDashboard)

async function loadDashboard() {
  loading.value = true
  try {
    dashboard.value = await getOutlineGovernanceDashboard()
  } finally {
    loading.value = false
  }
}

function coverageLabel(status: string) {
  if (status === 'OK') return '正常'
  if (status === 'WEAK') return '薄弱'
  if (status === 'MISSING') return '缺失'
  return status || '-'
}

function coverageTag(status: string) {
  if (status === 'OK') return 'success'
  if (status === 'WEAK') return 'warning'
  if (status === 'MISSING') return 'danger'
  return 'info'
}

function qualityLabel(level: string) {
  if (level === 'HIGH') return '高'
  if (level === 'MEDIUM') return '中'
  if (level === 'LOW') return '低'
  return level || '-'
}

function qualityTag(level: string) {
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  return 'info'
}

function priorityTag(priority: string) {
  if (priority === 'P0') return 'danger'
  if (priority === 'P1') return 'warning'
  return 'info'
}

function feedbackLabel(type: string) {
  if (type === 'MISSING_KNOWLEDGE') return '知识缺失'
  if (type === 'SOURCE_INACCURATE') return '来源不准确'
  return type || '-'
}

function goFeedback() {
  router.push('/agent/knowledge-feedback')
}
</script>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.stat-label,
.muted {
  color: #64748b;
  font-size: 13px;
}

.stat-value {
  font-size: 22px;
  font-weight: 800;
  margin-top: 6px;
  word-break: break-word;
}

.success {
  color: #059669;
}

.warning {
  color: #d97706;
}

.mb {
  margin-bottom: 16px;
}

.panel-card {
  height: 100%;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.task-body {
  min-width: 0;
}

.task-body strong {
  display: block;
  font-size: 14px;
  margin-bottom: 4px;
}

.task-body p {
  margin: 0 0 4px;
  color: #475569;
  font-size: 13px;
}

.task-body span {
  color: #64748b;
  font-size: 12px;
}
</style>
