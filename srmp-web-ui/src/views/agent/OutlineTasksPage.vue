<template>
  <AgentPageShell title="Outline 同步任务" description="集中查看同步任务历史、文档明细与失败重试，不执行同步配置。">
    <el-card class="filter-card">
      <el-form :inline="true">
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部" style="width: 140px">
            <el-option label="执行中" value="RUNNING" />
            <el-option label="成功" value="SUCCESS" />
            <el-option label="部分成功" value="PARTIAL_SUCCESS" />
            <el-option label="失败" value="FAILED" />
            <el-option label="Dry Run" value="DRY_RUN" />
            <el-option label="Dry Run 部分失败" value="DRY_RUN_PARTIAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="Dry Run">
          <el-select v-model="filters.dryRun" clearable placeholder="全部" style="width: 100px">
            <el-option label="是" :value="true" />
            <el-option label="否" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadTasks">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="errorStats.length > 0" class="mb">
      <template #header>失败类型统计</template>
      <div class="error-stats">
        <el-tag v-for="item in errorStats" :key="item.type" type="danger" class="stat-tag">
          {{ item.type }}：{{ item.count }}
        </el-tag>
      </div>
    </el-card>

    <el-card>
      <template #header>
        <div class="card-header">
          <span>任务列表</span>
          <el-button size="small" :loading="loading" @click="loadTasks">刷新</el-button>
        </div>
      </template>
      <el-empty v-if="filteredTasks.length === 0" description="暂无任务" />
      <el-table v-else :data="filteredTasks" border size="small" @row-click="openTask">
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="outlineStatusTagType(value(row, 'status'))" size="small">{{ value(row, 'status') }}</el-tag>
            <el-tag v-if="isDryRun(row)" size="small" type="info" class="ml">DRY</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="任务 ID" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ value(row, 'id') }}</template>
        </el-table-column>
        <el-table-column label="总数" width="70">
          <template #default="{ row }">{{ value(row, 'totalCount', 'total_count') }}</template>
        </el-table-column>
        <el-table-column label="成功" width="70">
          <template #default="{ row }">{{ value(row, 'successCount', 'success_count') }}</template>
        </el-table-column>
        <el-table-column label="跳过" width="70">
          <template #default="{ row }">{{ value(row, 'skipCount', 'skip_count') }}</template>
        </el-table-column>
        <el-table-column label="失败" width="70">
          <template #default="{ row }">{{ value(row, 'failCount', 'fail_count') }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ formatDateTime(value(row, 'createdAt', 'created_at')) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click.stop="openTask(row)">明细</el-button>
            <el-button
              v-if="Number(value(row, 'failCount', 'fail_count') || 0) > 0"
              size="small"
              type="warning"
              :loading="retrying"
              @click.stop="retryFailed(row)"
            >
              重试
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="detailVisible" title="同步任务明细" size="82%">
      <template v-if="selectedTask">
        <el-descriptions :column="4" border size="small" class="mb">
          <el-descriptions-item label="任务ID" :span="4">{{ value(selectedTask, 'id') }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ value(selectedTask, 'status') }}</el-descriptions-item>
          <el-descriptions-item label="成功">{{ value(selectedTask, 'successCount', 'success_count') }}</el-descriptions-item>
          <el-descriptions-item label="跳过">{{ value(selectedTask, 'skipCount', 'skip_count') }}</el-descriptions-item>
          <el-descriptions-item label="失败">{{ value(selectedTask, 'failCount', 'fail_count') }}</el-descriptions-item>
        </el-descriptions>
        <div class="summary-bar">
          <div class="summary-item success">成功 {{ statusCount.SUCCESS || 0 }}</div>
          <div class="summary-item skipped">跳过 {{ statusCount.SKIPPED || 0 }}</div>
          <div class="summary-item failed">失败 {{ statusCount.FAILED || 0 }}</div>
        </div>
        <div class="detail-toolbar">
          <el-radio-group v-model="detailStatus" size="small" @change="loadDetails">
            <el-radio-button label="">全部</el-radio-button>
            <el-radio-button label="SUCCESS">成功</el-radio-button>
            <el-radio-button label="SKIPPED">跳过</el-radio-button>
            <el-radio-button label="FAILED">失败</el-radio-button>
          </el-radio-group>
          <el-button
            v-if="Number(value(selectedTask, 'failCount', 'fail_count') || 0) > 0"
            size="small"
            type="warning"
            :loading="retrying"
            @click="retryFailed(selectedTask)"
          >
            重试失败文档
          </el-button>
        </div>
        <el-table :data="details" border size="small" height="calc(100vh - 280px)">
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="outlineStatusTagType(value(row, 'status'))">{{ value(row, 'status') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="标题" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">{{ value(row, 'outlineTitle', 'outline_title') }}</template>
          </el-table-column>
          <el-table-column label="知识库 ID" width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ value(row, 'knowledgeDocumentId', 'knowledge_document_id') || '-' }}</template>
          </el-table-column>
          <el-table-column label="Chunk" width="70">
            <template #default="{ row }">{{ value(row, 'chunkCount', 'chunk_count') || 0 }}</template>
          </el-table-column>
          <el-table-column label="Hash" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ value(row, 'contentHash', 'content_hash') || '-' }}</template>
          </el-table-column>
          <el-table-column label="错误" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="value(row, 'status') === 'FAILED'" class="error">
                {{ value(row, 'errorType', 'error_type') }}：{{ value(row, 'errorMessage', 'error_message') }}
              </span>
              <span v-else>{{ value(row, 'skipReason', 'skip_reason') || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="原文" width="80">
            <template #default="{ row }">
              <el-link v-if="outlineLink(value(row, 'outlineUrl', 'outline_url'))" :href="outlineLink(value(row, 'outlineUrl', 'outline_url'))" target="_blank" type="primary">打开</el-link>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import { getOutlineStatus, getOutlineSyncTask, getOutlineSyncTaskDetails, getOutlineSyncTasks, retryOutlineFailedTask } from '../../api/outline'
import { outlineStatusTagType, outlineTaskDryRun, outlineValue as value } from '../../utils/outlineHelpers'
import { formatDateTime } from '../../utils/dateFormat'

const route = useRoute()
const loading = ref(false)
const retrying = ref(false)
const status = ref<Record<string, any>>({})
const tasks = ref<Record<string, any>[]>([])
const details = ref<Record<string, any>[]>([])
const selectedTask = ref<Record<string, any> | null>(null)
const detailVisible = ref(false)
const detailStatus = ref('')
const filters = reactive<{ status: string; dryRun: boolean | undefined }>({ status: '', dryRun: undefined })

const filteredTasks = computed(() => {
  let list = [...tasks.value]
  if (filters.status) list = list.filter((t) => value(t, 'status') === filters.status)
  if (filters.dryRun === true) list = list.filter((t) => isDryRun(t))
  if (filters.dryRun === false) list = list.filter((t) => !isDryRun(t))
  return list
})

const statusCount = computed(() => {
  const stat: Record<string, number> = { SUCCESS: 0, SKIPPED: 0, FAILED: 0 }
  details.value.forEach((item) => {
    const s = String(value(item, 'status') || '')
    stat[s] = (stat[s] || 0) + 1
  })
  return stat
})

const errorStats = computed(() => {
  const map: Record<string, number> = {}
  details.value.forEach((row) => {
    if (value(row, 'status') !== 'FAILED') return
    const type = String(value(row, 'errorType', 'error_type') || 'UNKNOWN_ERROR')
    map[type] = (map[type] || 0) + 1
  })
  return Object.entries(map)
    .map(([type, count]) => ({ type, count }))
    .sort((a, b) => b.count - a.count)
})

function isDryRun(item: Record<string, any>) {
  return outlineTaskDryRun(item)
}

onMounted(async () => {
  await Promise.allSettled([loadStatus(), loadTasks()])
  const taskId = route.query.taskId
  if (taskId) {
    const found = tasks.value.find((t) => value(t, 'id') === taskId)
    if (found) await openTask(found)
    else {
      const detail = await getOutlineSyncTask(String(taskId)).catch(() => null)
      if (detail && value(detail, 'id')) await openTask(detail)
    }
  }
})

async function loadStatus() {
  status.value = await getOutlineStatus()
}

async function loadTasks() {
  loading.value = true
  try {
    tasks.value = await getOutlineSyncTasks({ limit: 50, status: filters.status || undefined })
  } finally {
    loading.value = false
  }
}

async function openTask(item: Record<string, any>) {
  selectedTask.value = item
  detailVisible.value = true
  detailStatus.value = ''
  await loadDetails()
}

async function loadDetails() {
  const id = value(selectedTask.value, 'id')
  if (!id) return
  details.value = await getOutlineSyncTaskDetails(String(id), {
    status: detailStatus.value || undefined,
    limit: 1000
  })
}

async function retryFailed(item: Record<string, any>) {
  const id = value(item, 'id')
  if (!id) return
  retrying.value = true
  try {
    const result = await retryOutlineFailedTask(String(id), true)
    ElMessage.success(`重试完成：${value(result, 'status')}`)
    await loadTasks()
    if (value(result, 'id')) await openTask(result)
  } finally {
    retrying.value = false
  }
}

function outlineLink(raw: any) {
  const url = String(raw || '').trim()
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  const base = String(value(status.value, 'baseUrl', 'base_url') || '').replace(/\/+$/, '')
  if (!base) return url
  return url.startsWith('/') ? `${base}${url}` : `${base}/${url}`
}
</script>

<style scoped>
.filter-card,
.mb {
  margin-bottom: 16px;
}
.card-header,
.detail-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}
.error-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.stat-tag {
  margin: 0;
}
.ml {
  margin-left: 4px;
}
.summary-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}
.summary-item {
  padding: 8px 12px;
  border-radius: 10px;
  background: #f1f5f9;
  font-size: 13px;
}
.summary-item.success {
  background: #ecfdf5;
  color: #047857;
}
.summary-item.skipped {
  color: #475569;
}
.summary-item.failed {
  background: #fef2f2;
  color: #dc2626;
}
.error {
  color: #dc2626;
}
</style>
