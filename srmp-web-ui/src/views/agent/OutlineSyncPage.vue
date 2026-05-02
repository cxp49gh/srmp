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
          <el-descriptions-item label="enabled">{{ value(status, 'enabled') }}</el-descriptions-item>
          <el-descriptions-item label="usable">{{ value(status, 'usable') }}</el-descriptions-item>
          <el-descriptions-item label="baseUrl">{{ value(status, 'baseUrl', 'base_url') }}</el-descriptions-item>
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
          <div class="doc-title">
            <strong>{{ value(item, 'title') || '未命名文档' }}</strong>
            <el-link v-if="value(item, 'url')" :href="value(item, 'url')" target="_blank" type="primary">打开</el-link>
          </div>
          <p>{{ value(item, 'id') }}</p>
          <div v-if="value(item, 'updatedAt', 'updated_at')">更新时间：{{ value(item, 'updatedAt', 'updated_at') }}</div>
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
            <strong>{{ value(item, 'status') }}</strong>
            <div>
              <el-tag v-if="truthy(value(item, 'dryRun', 'dry_run'))" size="small" type="info">DRY</el-tag>
              <el-tag size="small" :type="tagType(value(item, 'status'))">{{ value(item, 'syncMode', 'sync_mode') }}</el-tag>
            </div>
          </div>
          <p>{{ value(item, 'id') }}</p>
          <div class="task-grid">
            <span>总数：{{ value(item, 'totalCount', 'total_count') || 0 }}</span>
            <span>成功：{{ value(item, 'successCount', 'success_count') || 0 }}</span>
            <span>跳过：{{ value(item, 'skipCount', 'skip_count') || 0 }}</span>
            <span>失败：{{ value(item, 'failCount', 'fail_count') || 0 }}</span>
          </div>
          <div v-if="value(item, 'errorMessage', 'error_message')" class="error">{{ value(item, 'errorMessage', 'error_message') }}</div>
          <div class="task-actions">
            <el-button size="small" @click.stop="openTask(item)">明细</el-button>
            <el-button
              v-if="Number(value(item, 'failCount', 'fail_count') || 0) > 0"
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

    <el-drawer v-model="detailVisible" title="Outline 同步明细" size="82%">
      <template v-if="selectedTask">
        <el-descriptions :column="4" border size="small" class="mb">
          <el-descriptions-item label="任务ID" :span="4">{{ value(selectedTask, 'id') }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ value(selectedTask, 'status') }}</el-descriptions-item>
          <el-descriptions-item label="总数">{{ value(selectedTask, 'totalCount', 'total_count') }}</el-descriptions-item>
          <el-descriptions-item label="成功">{{ value(selectedTask, 'successCount', 'success_count') }}</el-descriptions-item>
          <el-descriptions-item label="失败">{{ value(selectedTask, 'failCount', 'fail_count') }}</el-descriptions-item>
        </el-descriptions>

        <div class="summary-bar">
          <div class="summary-item success">成功 {{ statusCount.SUCCESS }}</div>
          <div class="summary-item skipped">跳过 {{ statusCount.SKIPPED }}</div>
          <div class="summary-item failed">失败 {{ statusCount.FAILED }}</div>
          <div class="summary-item">总计 {{ details.length }}</div>
        </div>

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
              v-if="Number(value(selectedTask, 'failCount', 'fail_count') || 0) > 0"
              size="small"
              type="warning"
              :loading="retrying"
              @click="retryFailed(selectedTask)"
            >
              重试失败文档
            </el-button>
          </div>
        </div>

        <el-table :data="details" border height="calc(100vh - 300px)" size="small" row-key="id">
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="detail-expand">
                <el-descriptions :column="2" border size="small">
                  <el-descriptions-item label="Outline ID">{{ value(row, 'outlineDocumentId', 'outline_document_id') }}</el-descriptions-item>
                  <el-descriptions-item label="Knowledge Document">{{ value(row, 'knowledgeDocumentId', 'knowledge_document_id') || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="URL" :span="2">
                    <el-link v-if="value(row, 'outlineUrl', 'outline_url')" :href="value(row, 'outlineUrl', 'outline_url')" target="_blank">
                      {{ value(row, 'outlineUrl', 'outline_url') }}
                    </el-link>
                    <span v-else>-</span>
                  </el-descriptions-item>
                  <el-descriptions-item label="Outline更新时间">{{ value(row, 'outlineUpdatedAt', 'outline_updated_at') || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="本地创建时间">{{ value(row, 'createdAt', 'created_at') || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="内容字符数">{{ value(row, 'contentChars', 'content_chars') || 0 }}</el-descriptions-item>
                  <el-descriptions-item label="Chunk数">{{ value(row, 'chunkCount', 'chunk_count') || 0 }}</el-descriptions-item>
                  <el-descriptions-item label="当前Hash" :span="2">{{ value(row, 'contentHash', 'content_hash') || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="旧Hash" :span="2">{{ value(row, 'oldContentHash', 'old_content_hash') || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="详情说明" :span="2">{{ value(row, 'detailMessage', 'detail_message') || value(row, 'skipReason', 'skip_reason') || '-' }}</el-descriptions-item>
                  <el-descriptions-item v-if="value(row, 'errorMessage', 'error_message')" label="错误" :span="2">
                    <span class="error">{{ value(row, 'errorType', 'error_type') }}：{{ value(row, 'errorMessage', 'error_message') }}</span>
                  </el-descriptions-item>
                </el-descriptions>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="100" fixed>
            <template #default="{ row }">
              <el-tag size="small" :type="tagType(value(row, 'status'))">{{ value(row, 'status') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="动作" width="170">
            <template #default="{ row }">{{ value(row, 'action') }}</template>
          </el-table-column>
          <el-table-column label="文档标题" min-width="260" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="doc-title">
                <strong>{{ value(row, 'outlineTitle', 'outline_title') || '未命名文档' }}</strong>
                <el-link v-if="value(row, 'outlineUrl', 'outline_url')" :href="value(row, 'outlineUrl', 'outline_url')" target="_blank" type="primary">打开</el-link>
              </div>
              <small>{{ value(row, 'outlineDocumentId', 'outline_document_id') }}</small>
            </template>
          </el-table-column>
          <el-table-column label="Chunk" width="80">
            <template #default="{ row }">{{ value(row, 'chunkCount', 'chunk_count') || 0 }}</template>
          </el-table-column>
          <el-table-column label="字符数" width="90">
            <template #default="{ row }">{{ value(row, 'contentChars', 'content_chars') || 0 }}</template>
          </el-table-column>
          <el-table-column label="耗时ms" width="90">
            <template #default="{ row }">{{ value(row, 'costMs', 'cost_ms') || 0 }}</template>
          </el-table-column>
          <el-table-column label="原因/错误" min-width="320" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="value(row, 'status') === 'FAILED'" class="error">
                {{ value(row, 'errorType', 'error_type') }}：{{ value(row, 'errorMessage', 'error_message') }}
              </span>
              <span v-else>{{ value(row, 'detailMessage', 'detail_message') || value(row, 'skipReason', 'skip_reason') || '-' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
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

const statusCount = computed(() => {
  const stat: Record<string, number> = { SUCCESS: 0, SKIPPED: 0, FAILED: 0 }
  details.value.forEach((item) => {
    const s = String(value(item, 'status') || '')
    if (!stat[s]) stat[s] = 0
    stat[s] += 1
  })
  return stat
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
    ElMessage.success(`${dryRun ? '预演' : '同步'}完成：状态 ${value(result, 'status')}，成功 ${value(result, 'successCount', 'success_count') || 0}，跳过 ${value(result, 'skipCount', 'skip_count') || 0}，失败 ${value(result, 'failCount', 'fail_count') || 0}`)
    await loadTasks()
    if (value(result, 'id')) await openTask(result)
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
    ElMessage.success(`重试完成：状态 ${value(result, 'status')}，成功 ${value(result, 'successCount', 'success_count') || 0}，失败 ${value(result, 'failCount', 'fail_count') || 0}`)
    await loadTasks()
    if (value(result, 'id')) await openTask(result)
  } finally {
    retrying.value = false
  }
}

function value(obj: any, ...keys: string[]) {
  if (!obj) return ''
  for (const key of keys) {
    const v = obj[key]
    if (v !== undefined && v !== null && String(v).trim() !== '') return v
  }
  return ''
}

function truthy(v: any) {
  return v === true || v === 'true' || v === 1 || v === '1'
}

function tagType(status: any) {
  const s = String(status || '')
  if (s === 'SUCCESS' || s === 'DRY_RUN') return 'success'
  if (s === 'FAILED') return 'danger'
  if (s === 'PARTIAL_SUCCESS' || s === 'DRY_RUN_PARTIAL') return 'warning'
  if (s === 'RUNNING') return 'warning'
  if (s === 'SKIPPED') return 'info'
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
.detail-toolbar,
.doc-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.mb {
  margin-bottom: 16px;
}

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
  background: #f8fafc;
  color: #475569;
}

.summary-item.failed {
  background: #fef2f2;
  color: #dc2626;
}

.detail-expand {
  padding: 14px;
  background: #f8fafc;
}

small {
  color: #94a3b8;
}
</style>
