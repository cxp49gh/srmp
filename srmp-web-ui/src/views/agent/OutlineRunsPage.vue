<template>
  <AgentPageShell title="Outline 运行监控" description="查看自动同步与 Webhook 运行健康状态，不承担文档同步入口。">
    <div class="stats-grid">
      <el-card shadow="never">
        <div class="stat-label">最近成功</div>
        <div class="stat-value success">{{ formatDateTime(health.lastSuccessAt) }}</div>
      </el-card>
      <el-card shadow="never">
        <div class="stat-label">最近失败</div>
        <div class="stat-value" :class="health.lastFailedAt ? 'warning' : ''">{{ formatDateTime(health.lastFailedAt) }}</div>
      </el-card>
      <el-card shadow="never">
        <div class="stat-label">连续失败配置</div>
        <div class="stat-value" :class="health.maxConsecutiveFailures > 0 ? 'warning' : 'success'">{{ health.maxConsecutiveFailures }}</div>
      </el-card>
      <el-card shadow="never">
        <div class="stat-label">Webhook 触发 / 失败</div>
        <div class="stat-value">{{ health.webhookTriggers }} / <span class="failed">{{ health.webhookFailures }}</span></div>
      </el-card>
      <el-card shadow="never">
        <div class="stat-label">待补向量</div>
        <div class="stat-value" :class="Number(stats.pendingEmbeddingChunkCount || 0) > 0 ? 'warning' : 'success'">
          {{ stats.pendingEmbeddingChunkCount ?? 0 }}
        </div>
      </el-card>
    </div>

    <el-alert
      v-if="Number(stats.pendingEmbeddingChunkCount || 0) > 0"
      type="warning"
      show-icon
      class="mb"
      :title="`待补向量 ${stats.pendingEmbeddingChunkCount} 个，建议前往同步入库页执行补向量。`"
    />

    <el-card class="filter-card">
      <el-form :inline="true">
        <el-form-item label="配置">
          <el-select v-model="filters.configId" clearable filterable placeholder="全部" style="width: 200px">
            <el-option v-for="c in configs" :key="c.id" :label="c.name || c.id" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发方式">
          <el-select v-model="filters.triggerType" clearable placeholder="全部" style="width: 130px">
            <el-option label="定时" value="SCHEDULED" />
            <el-option label="手动" value="MANUAL" />
            <el-option label="Webhook" value="WEBHOOK" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部" style="width: 120px">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
            <el-option label="跳过" value="SKIPPED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadRuns">查询</el-button>
          <el-button :loading="scanning" @click="scanDue">到期扫描</el-button>
        </el-form-item>
      </el-form>
      <p class="hint">到期扫描为系统管理员操作，将触发已到期的自动同步配置。</p>
    </el-card>

    <el-card>
      <template #header>
        <div class="card-header">
          <span>运行记录</span>
          <el-button size="small" :loading="loading" @click="loadRuns">刷新</el-button>
        </div>
      </template>
      <el-table :data="filteredRuns" border size="small">
        <el-table-column label="配置" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ value(row, 'configName', 'config_name') || value(row, 'configId', 'config_id') }}</template>
        </el-table-column>
        <el-table-column label="触发" width="100">
          <template #default="{ row }">{{ value(row, 'triggerType', 'trigger_type') }}</template>
        </el-table-column>
        <el-table-column label="事件" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ value(row, 'outlineEvent', 'outline_event') || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="outlineStatusTagType(value(row, 'status'))">{{ value(row, 'status') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="同步任务" width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link v-if="value(row, 'syncTaskId', 'sync_task_id')" type="primary" @click="goTask(value(row, 'syncTaskId', 'sync_task_id'))">
              {{ value(row, 'syncTaskId', 'sync_task_id') }}
            </el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="消息" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ value(row, 'message') || value(row, 'errorMessage', 'error_message') || '-' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(value(row, 'createdAt', 'created_at')) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import {
  getOutlineAutoSyncConfigs,
  getOutlineAutoSyncRuns,
  getOutlineKnowledgeStats,
  scanOutlineAutoSyncDue
} from '../../api/outline'
import { outlineStatusTagType, outlineValue as value } from '../../utils/outlineHelpers'
import { formatDateTime } from '../../utils/dateFormat'

const router = useRouter()
const loading = ref(false)
const scanning = ref(false)
const configs = ref<Record<string, any>[]>([])
const runs = ref<Record<string, any>[]>([])
const stats = ref<Record<string, any>>({})
const filters = reactive({ configId: '', triggerType: '', status: '' })

const filteredRuns = computed(() => {
  let list = [...runs.value]
  if (filters.configId) list = list.filter((r) => value(r, 'configId', 'config_id') === filters.configId)
  if (filters.triggerType) list = list.filter((r) => value(r, 'triggerType', 'trigger_type') === filters.triggerType)
  if (filters.status) list = list.filter((r) => value(r, 'status') === filters.status)
  return list
})

const health = computed(() => {
  const sorted = [...runs.value].sort((a, b) => String(value(b, 'createdAt', 'created_at')).localeCompare(String(value(a, 'createdAt', 'created_at'))))
  let lastSuccessAt = ''
  let lastFailedAt = ''
  let webhookTriggers = 0
  let webhookFailures = 0
  const failStreakByConfig: Record<string, number> = {}
  const maxStreakByConfig: Record<string, number> = {}

  for (const run of sorted) {
    const st = String(value(run, 'status') || '')
    const at = String(value(run, 'createdAt', 'created_at') || '')
    if (st === 'SUCCESS' && !lastSuccessAt) lastSuccessAt = at
    if ((st === 'FAILED' || st === 'ERROR') && !lastFailedAt) lastFailedAt = at
    if (value(run, 'triggerType', 'trigger_type') === 'WEBHOOK') {
      webhookTriggers++
      if (st === 'FAILED' || st === 'ERROR') webhookFailures++
    }
  }

  const byConfig = [...runs.value].sort((a, b) => String(value(a, 'createdAt', 'created_at')).localeCompare(String(value(b, 'createdAt', 'created_at'))))
  for (const run of byConfig) {
    const cfgId = String(value(run, 'configId', 'config_id') || '_')
    const st = String(value(run, 'status') || '')
    if (st === 'FAILED' || st === 'ERROR') {
      failStreakByConfig[cfgId] = (failStreakByConfig[cfgId] || 0) + 1
      maxStreakByConfig[cfgId] = Math.max(maxStreakByConfig[cfgId] || 0, failStreakByConfig[cfgId])
    } else {
      failStreakByConfig[cfgId] = 0
    }
  }

  const maxConsecutiveFailures = Math.max(0, ...Object.values(maxStreakByConfig))
  return { lastSuccessAt, lastFailedAt, webhookTriggers, webhookFailures, maxConsecutiveFailures }
})

onMounted(async () => {
  await Promise.allSettled([loadConfigs(), loadStats(), loadRuns()])
})

async function loadConfigs() {
  configs.value = await getOutlineAutoSyncConfigs()
}

async function loadStats() {
  stats.value = await getOutlineKnowledgeStats()
}

async function loadRuns() {
  loading.value = true
  try {
    runs.value = await getOutlineAutoSyncRuns({ configId: filters.configId || undefined, limit: 100 })
  } finally {
    loading.value = false
  }
}

async function scanDue() {
  scanning.value = true
  try {
    const result = await scanOutlineAutoSyncDue()
    ElMessage.success(`扫描完成，触发 ${result?.count ?? 0} 个配置`)
    await loadRuns()
  } finally {
    scanning.value = false
  }
}

function goTask(taskId: string) {
  router.push({ path: '/agent/outline/tasks', query: { taskId } })
}
</script>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.stat-label {
  color: #64748b;
  font-size: 13px;
}
.stat-value {
  font-size: 18px;
  font-weight: 700;
  margin-top: 6px;
  word-break: break-all;
}
.stat-value.success {
  color: #059669;
}
.stat-value.warning {
  color: #d97706;
}
.failed {
  color: #dc2626;
}
.filter-card,
.mb {
  margin-bottom: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.hint {
  margin: 8px 0 0;
  color: #94a3b8;
  font-size: 12px;
}
</style>
