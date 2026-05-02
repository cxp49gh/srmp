<template>
  <AgentPageShell title="Outline 同步入库" description="将 Outline 文档同步到本地 AI 向量知识库，并查看每篇文档的同步明细。">
    <div class="sync-page">
      <el-card class="left-card">
        <template #header>
          <div class="card-header">
            <span>同步配置</span>
            <el-button size="small" @click="loadAll">刷新</el-button>
          </div>
        </template>

        <el-alert
          v-if="status && status.usable === false"
          type="warning"
          show-icon
          title="Outline 当前不可用，请先检查配置和 API Token。"
          class="mb"
        />

        <el-descriptions :column="1" border size="small" class="mb">
          <el-descriptions-item label="enabled">{{ status.enabled }}</el-descriptions-item>
          <el-descriptions-item label="usable">{{ status.usable }}</el-descriptions-item>
          <el-descriptions-item label="baseUrl">{{ status.baseUrl }}</el-descriptions-item>
        </el-descriptions>

        <el-form label-width="100px">
          <el-form-item label="Collection">
            <el-select
              v-model="form.collectionId"
              clearable
              filterable
              placeholder="为空时同步默认文档列表"
              style="width: 100%"
              @change="loadDocuments"
            >
              <el-option v-for="item in collections" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>

          <el-form-item label="同步数量">
            <el-input-number v-model="form.limit" :min="1" :max="2000" />
          </el-form-item>

          <el-form-item label="强制更新">
            <el-switch v-model="form.force" />
          </el-form-item>

          <el-form-item label="Dry Run">
            <el-switch v-model="form.dryRun" />
          </el-form-item>

          <el-form-item label="清理过期">
            <el-switch v-model="form.cleanupMissing" />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="syncing" @click="doSync">同步到知识库</el-button>
            <el-button :loading="syncing" @click="doDryRun">预演</el-button>
            <el-button @click="loadDocuments">预览文档</el-button>
          </el-form-item>
        </el-form>

        <el-alert
          type="info"
          show-icon
          :closable="false"
          class="mb"
          title="建议：正式同步前先 Dry Run；fail_count > 0 时点击任务查看明细，再重试失败文档。"
        />

        <el-divider />

        <h3>Collection 列表</h3>
        <el-empty v-if="collections.length === 0" description="暂无 Collection" />
        <div v-for="item in collections" :key="item.id" class="collection-item">
          <strong>{{ item.name }}</strong>
          <p>{{ item.id }}</p>
        </div>
      </el-card>

      <el-card class="middle-card">
        <template #header>
          <div class="card-header">
            <span>文档预览</span>
            <el-button size="small" @click="loadDocuments">刷新</el-button>
          </div>
        </template>
        <el-empty v-if="documents.length === 0" description="暂无文档" />
        <div v-for="item in documents" :key="item.id" class="doc-item">
          <strong>{{ item.title }}</strong>
          <p>{{ item.id }}</p>
          <div v-if="item.updatedAt">更新时间：{{ item.updatedAt }}</div>
        </div>
      </el-card>

      <el-card class="right-card">
        <template #header>
          <div class="card-header">
            <span>同步任务</span>
            <el-button size="small" @click="loadTasks">刷新</el-button>
          </div>
        </template>

        <el-empty v-if="tasks.length === 0" description="暂无任务" />

        <div v-for="item in tasks" :key="item.id" class="task-item" @click="openTask(item)">
          <div class="task-title">
            <strong>{{ item.status }}</strong>
            <div>
              <el-tag v-if="item.dry_run" size="small" type="info">DRY</el-tag>
              <el-tag size="small" :type="tagType(item.status)">{{ item.sync_mode }}</el-tag>
            </div>
          </div>
          <p>{{ item.id }}</p>
          <div class="task-grid">
            <span>总数：{{ item.total_count }}</span>
            <span>成功：{{ item.success_count }}</span>
            <span>跳过：{{ item.skip_count }}</span>
            <span>失败：{{ item.fail_count }}</span>
          </div>
          <div v-if="item.error_message" class="error">{{ item.error_message }}</div>
          <div class="task-actions">
            <el-button size="small" @click.stop="openTask(item)">明细</el-button>
            <el-button
              v-if="Number(item.fail_count || 0) > 0"
              size="small"
              type="warning"
              :loading="retrying"
              @click.stop="retryFailed(item)"
            >
              重试失败
            </el-button>
          </div>
        </div>
      </el-card>
    </div>

    <el-drawer v-model="detailVisible" title="Outline 同步明细" size="70%">
      <template v-if="selectedTask">
        <el-descriptions :column="4" border size="small" class="mb">
          <el-descriptions-item label="任务ID" :span="4">{{ selectedTask.id }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ selectedTask.status }}</el-descriptions-item>
          <el-descriptions-item label="总数">{{ selectedTask.total_count }}</el-descriptions-item>
          <el-descriptions-item label="成功">{{ selectedTask.success_count }}</el-descriptions-item>
          <el-descriptions-item label="失败">{{ selectedTask.fail_count }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-toolbar">
          <el-radio-group v-model="detailStatus" size="small" @change="loadDetails">
            <el-radio-button label="">全部</el-radio-button>
            <el-radio-button label="FAILED">失败</el-radio-button>
            <el-radio-button label="SUCCESS">成功</el-radio-button>
            <el-radio-button label="SKIPPED">跳过</el-radio-button>
          </el-radio-group>
          <div>
            <el-button size="small" @click="loadDetails">刷新明细</el-button>
            <el-button
              v-if="Number(selectedTask.fail_count || 0) > 0"
              size="small"
              type="warning"
              :loading="retrying"
              @click="retryFailed(selectedTask)"
            >
              重试失败文档
            </el-button>
          </div>
        </div>

        <el-table :data="details" border height="calc(100vh - 260px)" size="small">
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="tagType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="action" label="动作" width="160" />
          <el-table-column prop="outline_title" label="文档标题" min-width="220" show-overflow-tooltip />
          <el-table-column prop="outline_document_id" label="Outline ID" min-width="220" show-overflow-tooltip />
          <el-table-column prop="chunk_count" label="Chunk" width="80" />
          <el-table-column prop="cost_ms" label="耗时ms" width="90" />
          <el-table-column label="原因/错误" min-width="280" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.status === 'FAILED'" class="error">{{ row.error_type }}：{{ row.error_message }}</span>
              <span v-else>{{ row.skip_reason || '-' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import {
  getOutlineCollections,
  getOutlineStatus,
  getOutlineSyncTaskDetails,
  getOutlineSyncTasks,
  listOutlineDocuments,
  retryOutlineFailedTask,
  syncOutline,
  type OutlineCollection,
  type OutlineDocument
} from '../../api/outline'

const status = ref<Record<string, any>>({})
const collections = ref<OutlineCollection[]>([])
const documents = ref<OutlineDocument[]>([])
const tasks = ref<Record<string, any>[]>([])
const details = ref<Record<string, any>[]>([])
const selectedTask = ref<Record<string, any> | null>(null)
const syncing = ref(false)
const retrying = ref(false)
const detailVisible = ref(false)
const detailStatus = ref('')

const form = reactive({
  collectionId: '',
  limit: 500,
  force: false,
  dryRun: false,
  cleanupMissing: false
})

onMounted(loadAll)

async function loadAll() {
  await Promise.allSettled([loadStatus(), loadCollections(), loadDocuments(), loadTasks()])
}

async function loadStatus() {
  status.value = await getOutlineStatus()
}

async function loadCollections() {
  collections.value = await getOutlineCollections()
}

async function loadDocuments() {
  documents.value = await listOutlineDocuments({
    collectionId: form.collectionId || undefined,
    limit: Math.min(form.limit, 100),
    offset: 0
  })
}

async function loadTasks() {
  tasks.value = await getOutlineSyncTasks(30)
}

async function doSync() {
  await runSync(false)
}

async function doDryRun() {
  await runSync(true)
}

async function runSync(dryRun: boolean) {
  syncing.value = true
  try {
    const result = await syncOutline({
      collectionId: form.collectionId || undefined,
      limit: form.limit,
      force: form.force,
      dryRun,
      cleanupMissing: form.cleanupMissing
    })
    ElMessage.success(`${dryRun ? '预演' : '同步'}完成：状态 ${result.status}，成功 ${result.success_count || 0}，跳过 ${result.skip_count || 0}，失败 ${result.fail_count || 0}`)
    await loadTasks()
    if (result.id) {
      await openTask(result)
    }
  } finally {
    syncing.value = false
  }
}

async function openTask(item: Record<string, any>) {
  selectedTask.value = item
  detailVisible.value = true
  detailStatus.value = ''
  await loadDetails()
}

async function loadDetails() {
  if (!selectedTask.value?.id) return
  details.value = await getOutlineSyncTaskDetails(selectedTask.value.id, {
    status: detailStatus.value || undefined,
    limit: 1000
  })
}

async function retryFailed(item: Record<string, any>) {
  if (!item?.id) return
  retrying.value = true
  try {
    const result = await retryOutlineFailedTask(item.id, true)
    ElMessage.success(`重试完成：状态 ${result.status}，成功 ${result.success_count || 0}，失败 ${result.fail_count || 0}`)
    await loadTasks()
    if (result.id) {
      await openTask(result)
    }
  } finally {
    retrying.value = false
  }
}

function tagType(status: string) {
  if (status === 'SUCCESS' || status === 'DRY_RUN') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PARTIAL_SUCCESS' || status === 'DRY_RUN_PARTIAL') return 'warning'
  if (status === 'RUNNING') return 'warning'
  if (status === 'SKIPPED') return 'info'
  return 'info'
}
</script>

<style scoped>
.sync-page {
  display: grid;
  grid-template-columns: 360px minmax(360px, 1fr) 460px;
  gap: 16px;
}

.left-card,
.middle-card,
.right-card {
  min-height: calc(100vh - 130px);
}

.card-header,
.task-title,
.detail-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.mb {
  margin-bottom: 16px;
}

h3 {
  margin: 0 0 10px;
  font-size: 15px;
}

.collection-item,
.doc-item,
.task-item {
  padding: 12px;
  background: #f8fafc;
  border-radius: 10px;
  margin-bottom: 10px;
  font-size: 13px;
}

.task-item {
  cursor: pointer;
}

.task-item:hover {
  background: #eef6ff;
}

.collection-item p,
.doc-item p,
.task-item p {
  margin: 4px 0;
  color: #64748b;
  word-break: break-all;
}

.task-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px;
  margin-top: 8px;
}

.task-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

.error {
  color: #dc2626;
}

.detail-toolbar {
  margin-bottom: 12px;
}
</style>
