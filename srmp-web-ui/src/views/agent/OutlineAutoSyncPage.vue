<template>
  <AgentPageShell title="Outline 自动同步" description="配置定时同步、Webhook 精准同步，以及同步后的自动向量化闭环。">
    <template #actions>
      <div class="header-actions">
        <el-button :loading="loading" @click="loadAll">刷新全部</el-button>
        <el-button type="success" plain :loading="vectorizing" @click="vectorizePending">补向量</el-button>
        <el-button type="primary" plain @click="openWebhookHelp">Webhook 接入说明</el-button>
      </div>
    </template>

    <div class="stats-row">
      <el-card shadow="never">
        <div class="stat-label">自动同步配置</div>
        <div class="stat-value">{{ configs.length }}</div>
      </el-card>
      <el-card shadow="never">
        <div class="stat-label">Outline 文档</div>
        <div class="stat-value">{{ stats.documentCount ?? 0 }}</div>
      </el-card>
      <el-card shadow="never">
        <div class="stat-label">Outline Chunk</div>
        <div class="stat-value">{{ stats.chunkCount ?? 0 }}</div>
      </el-card>
      <el-card shadow="never">
        <div class="stat-label">待补向量</div>
        <div class="stat-value" :class="Number(stats.pendingEmbeddingChunkCount || 0) > 0 ? 'warning' : 'success'">
          {{ stats.pendingEmbeddingChunkCount ?? 0 }}
        </div>
      </el-card>
    </div>

    <div class="auto-sync-page">
      <el-card class="config-card">
        <template #header>
          <div class="card-header">
            <span>{{ form.id ? '编辑配置' : '新增配置' }}</span>
            <el-button size="small" @click="resetForm">新建</el-button>
          </div>
        </template>

        <el-alert
          class="mb"
          type="info"
          show-icon
          title="Webhook 触发会把 documentId 传给后端，只同步指定 Outline 文档；定时任务则按 Collection 扫描。"
        />

        <el-form label-width="128px">
          <el-form-item label="配置ID">
            <el-input v-model="form.id" placeholder="新增时可留空" :disabled="!!editingId" />
          </el-form-item>
          <el-form-item label="名称">
            <el-input v-model="form.name" placeholder="例如：道路养护知识库自动同步" />
          </el-form-item>
          <el-form-item label="启用定时">
            <el-switch v-model="form.enabled" />
          </el-form-item>
          <el-form-item label="Collection">
            <el-select v-model="form.collectionId" clearable filterable placeholder="为空时同步默认文档列表" style="width: 100%">
              <el-option v-for="item in collections" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="同步间隔">
            <el-input-number v-model="form.intervalMinutes" :min="1" :max="1440" />
            <span class="unit">分钟</span>
          </el-form-item>
          <el-form-item label="强制同步">
            <el-switch v-model="form.force" />
            <span class="hint">忽略 hash，重新入库</span>
          </el-form-item>
          <el-form-item label="清理缺失文档">
            <el-switch v-model="form.cleanupMissing" />
          </el-form-item>
          <el-divider content-position="left">向量化</el-divider>
          <el-form-item label="同步后补向量">
            <el-switch v-model="form.vectorizeAfterSync" />
          </el-form-item>
          <el-form-item label="强制重建向量">
            <el-switch v-model="form.vectorForce" />
          </el-form-item>
          <el-form-item label="处理上限">
            <el-input-number v-model="form.vectorLimit" :min="1" :max="2000" />
          </el-form-item>
          <el-divider content-position="left">Webhook</el-divider>
          <el-form-item label="启用 Webhook">
            <el-switch v-model="form.webhookEnabled" />
          </el-form-item>
          <el-form-item label="Webhook Secret">
            <el-input v-model="form.webhookSecret" show-password placeholder="建议使用随机长字符串" />
          </el-form-item>
          <el-form-item label="Webhook 地址">
            <el-input :model-value="webhookUrl" readonly>
              <template #append><el-button @click="copyText(webhookUrl, 'Webhook 地址已复制')">复制</el-button></template>
            </el-input>
            <div class="hint block">可通过 VITE_WEBHOOK_BASE_URL 指向后端公网地址，避免复制成 Vite 前端地址。</div>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="saveConfig">保存配置</el-button>
            <el-button :disabled="!form.id" @click="openWebhookTest">测试 Webhook</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card class="list-card">
        <template #header>
          <div class="card-header">
            <span>配置列表</span>
            <el-button size="small" @click="loadConfigs">刷新</el-button>
          </div>
        </template>
        <el-empty v-if="configs.length === 0" description="暂无配置" />
        <div v-for="item in configs" :key="item.id" class="config-item" :class="{ active: item.id === form.id }" @click="selectConfig(item)">
          <div class="item-title">
            <strong>{{ value(item, 'name') || 'Outline 自动同步' }}</strong>
            <div class="tag-group">
              <el-tag size="small" :type="truthy(value(item, 'enabled')) ? 'success' : 'info'">{{ truthy(value(item, 'enabled')) ? '定时启用' : '定时停用' }}</el-tag>
              <el-tag v-if="truthy(value(item, 'webhookEnabled', 'webhook_enabled'))" size="small" type="warning">Webhook</el-tag>
              <el-tag size="small" :type="statusTag(value(item, 'status'))">{{ value(item, 'status') || 'IDLE' }}</el-tag>
            </div>
          </div>
          <p>{{ item.id }}</p>
          <div class="grid">
            <span>间隔：{{ value(item, 'intervalMinutes', 'interval_minutes') || 60 }} 分钟</span>
            <span>Collection：{{ value(item, 'collectionId', 'collection_id') || '-' }}</span>
            <span>上次：{{ value(item, 'lastRunAt', 'last_run_at') || '-' }}</span>
            <span>下次：{{ value(item, 'nextRunAt', 'next_run_at') || '-' }}</span>
            <span>最近任务：{{ value(item, 'lastTaskId', 'last_task_id') || '-' }}</span>
            <span>向量：{{ value(item, 'lastVectorStatus', 'last_vector_status') || '-' }}</span>
          </div>
          <div v-if="value(item, 'errorMessage', 'error_message')" class="error">{{ value(item, 'errorMessage', 'error_message') }}</div>
          <div class="actions">
            <el-button size="small" @click.stop="selectConfig(item)">编辑</el-button>
            <el-button size="small" type="primary" :loading="runningId === item.id" @click.stop="runNow(item)">立即运行</el-button>
            <el-button size="small" @click.stop="selectAndTest(item)">测 Webhook</el-button>
          </div>
        </div>
      </el-card>

      <el-card class="run-card">
        <template #header>
          <div class="card-header">
            <span>运行记录</span>
            <div>
              <el-button size="small" :loading="scanning" @click="scanDue">扫描到期配置</el-button>
              <el-button size="small" @click="loadRuns">刷新</el-button>
            </div>
          </div>
        </template>
        <el-table :data="runs" border size="small" height="calc(100vh - 280px)" empty-text="暂无运行记录">
          <el-table-column label="状态" width="120" fixed>
            <template #default="{ row }"><el-tag size="small" :type="statusTag(value(row, 'status'))">{{ value(row, 'status') }}</el-tag></template>
          </el-table-column>
          <el-table-column label="触发" width="110"><template #default="{ row }">{{ value(row, 'triggerType', 'trigger_type') }}</template></el-table-column>
          <el-table-column label="事件" width="160"><template #default="{ row }">{{ value(row, 'outlineEvent', 'outline_event') || '-' }}</template></el-table-column>
          <el-table-column label="文档ID" min-width="220" show-overflow-tooltip><template #default="{ row }">{{ value(row, 'outlineDocumentId', 'outline_document_id') || '-' }}</template></el-table-column>
          <el-table-column label="同步任务" min-width="190" show-overflow-tooltip><template #default="{ row }">{{ value(row, 'syncTaskId', 'sync_task_id') || '-' }}</template></el-table-column>
          <el-table-column label="向量" min-width="230" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag size="small" :type="statusTag(value(row, 'vectorizeStatus', 'vectorize_status'))">{{ value(row, 'vectorizeStatus', 'vectorize_status') || '-' }}</el-tag>
              <small>{{ value(row, 'vectorizeMessage', 'vectorize_message') || '' }}</small>
            </template>
          </el-table-column>
          <el-table-column label="错误" min-width="260" show-overflow-tooltip><template #default="{ row }"><span class="error">{{ value(row, 'errorMessage', 'error_message') }}</span></template></el-table-column>
          <el-table-column label="时间" width="180"><template #default="{ row }">{{ value(row, 'createdAt', 'created_at') }}</template></el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link type="primary" :disabled="!value(row, 'syncTaskId', 'sync_task_id')" @click="openSyncDetails(row)">明细</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <el-dialog v-model="webhookDialogVisible" title="Webhook 接入说明" width="760px">
      <el-alert type="success" show-icon title="推荐在 Outline Webhooks 中配置下面地址和 Secret。后端支持 X-Outline-Webhook-Secret、X-Webhook-Secret 或 Authorization: Bearer 三种传法。" />
      <el-descriptions class="mt" :column="1" border>
        <el-descriptions-item label="URL">{{ webhookUrl }}</el-descriptions-item>
        <el-descriptions-item label="Header">X-Outline-Webhook-Secret: {{ form.webhookSecret || '<你的 secret>' }}</el-descriptions-item>
        <el-descriptions-item label="事件">document.created / document.updated / document.deleted / document.archived</el-descriptions-item>
      </el-descriptions>
      <pre class="code">{{ webhookCurl }}</pre>
      <template #footer>
        <el-button @click="copyText(webhookCurl, 'curl 示例已复制')">复制 curl</el-button>
        <el-button type="primary" @click="webhookDialogVisible = false">知道了</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="webhookTestVisible" title="测试 Webhook 精准同步" width="720px">
      <el-alert class="mb" type="warning" show-icon title="测试会真实调用 /api/outline/auto-sync/webhook。document.updated 会触发指定文档同步；document.deleted 会把本地 OUTLINE 文档标记为 INACTIVE。" />
      <el-form label-width="110px">
        <el-form-item label="事件">
          <el-select v-model="webhookTest.event" style="width: 100%">
            <el-option label="document.updated" value="document.updated" />
            <el-option label="document.created" value="document.created" />
            <el-option label="document.deleted" value="document.deleted" />
            <el-option label="document.archived" value="document.archived" />
          </el-select>
        </el-form-item>
        <el-form-item label="Document ID">
          <el-select v-model="webhookTest.documentId" filterable clearable allow-create default-first-option placeholder="可手输，也可从当前 Collection 加载选择" style="width: 100%">
            <el-option v-for="doc in webhookDocs" :key="doc.id" :label="doc.title || doc.id" :value="doc.id">
              <span>{{ doc.title || doc.id }}</span>
              <small class="option-id">{{ doc.id }}</small>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="Collection ID">
          <el-input v-model="webhookTest.collectionId" placeholder="可留空；填写后可加载文档列表">
            <template #append><el-button :loading="loadingWebhookDocs" @click="loadWebhookDocs">加载文档</el-button></template>
          </el-input>
        </el-form-item>
        <el-form-item label="Secret"><el-input v-model="webhookTest.secret" show-password /></el-form-item>
      </el-form>
      <pre v-if="webhookTestResult" class="code">{{ webhookTestResult }}</pre>
      <template #footer>
        <el-button @click="webhookTestVisible = false">取消</el-button>
        <el-button type="primary" :loading="testingWebhook" @click="testWebhook">发送测试</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailDialogVisible" title="同步任务明细" width="1080px">
      <div class="detail-toolbar">
        <div>
          <div class="detail-title">任务：{{ detailTaskId || '-' }}</div>
          <div class="hint">从运行记录关联的 outline_sync_task_detail 读取，便于定位每篇文档是成功、跳过还是失败。</div>
        </div>
        <div class="detail-filters">
          <el-select v-model="detailStatus" clearable placeholder="全部状态" style="width: 150px" @change="loadSyncDetails">
            <el-option label="SUCCESS" value="SUCCESS" />
            <el-option label="SKIPPED" value="SKIPPED" />
            <el-option label="FAILED" value="FAILED" />
          </el-select>
          <el-button :loading="detailLoading" @click="loadSyncDetails">刷新</el-button>
          <el-button type="warning" plain :loading="retryingFailed" :disabled="!detailTaskId" @click="retryFailedDetails">重试失败</el-button>
        </div>
      </div>
      <el-table :data="syncDetails" border size="small" height="520px" empty-text="暂无明细">
        <el-table-column label="状态" width="100" fixed>
          <template #default="{ row }"><el-tag size="small" :type="statusTag(value(row, 'status'))">{{ value(row, 'status') }}</el-tag></template>
        </el-table-column>
        <el-table-column label="动作" width="110"><template #default="{ row }">{{ value(row, 'action') || '-' }}</template></el-table-column>
        <el-table-column label="文档标题" min-width="240" show-overflow-tooltip><template #default="{ row }">{{ value(row, 'outline_title', 'outlineTitle') || '-' }}</template></el-table-column>
        <el-table-column label="文档ID" min-width="220" show-overflow-tooltip><template #default="{ row }">{{ value(row, 'outline_document_id', 'outlineDocumentId') || '-' }}</template></el-table-column>
        <el-table-column label="切片" width="80"><template #default="{ row }">{{ value(row, 'chunk_count', 'chunkCount') || 0 }}</template></el-table-column>
        <el-table-column label="跳过原因" min-width="160" show-overflow-tooltip><template #default="{ row }">{{ value(row, 'skip_reason', 'skipReason') || '-' }}</template></el-table-column>
        <el-table-column label="错误类型" min-width="140" show-overflow-tooltip><template #default="{ row }">{{ value(row, 'error_type', 'errorType') || '-' }}</template></el-table-column>
        <el-table-column label="消息" min-width="280" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="value(row, 'error_message', 'errorMessage') ? 'error' : ''">
              {{ value(row, 'error_message', 'errorMessage') || value(row, 'detail_message', 'detailMessage') || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="90"><template #default="{ row }">{{ value(row, 'cost_ms', 'costMs') || '-' }} ms</template></el-table-column>
        <el-table-column label="创建时间" width="180"><template #default="{ row }">{{ value(row, 'created_at', 'createdAt') || '-' }}</template></el-table-column>
      </el-table>
    </el-dialog>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import { copyToClipboard } from '../../utils/clipboard'
import {
  createOutlineAutoSyncConfig,
  getOutlineAutoSyncConfigs,
  getOutlineAutoSyncRuns,
  getOutlineCollections,
  getOutlineKnowledgeStats,
  getOutlineSyncTaskDetails,
  listOutlineDocuments,
  retryOutlineFailedTask,
  runOutlineAutoSyncNow,
  scanOutlineAutoSyncDue,
  testOutlineAutoSyncWebhook,
  updateOutlineAutoSyncConfig,
  vectorizeOutline,
  type OutlineAutoSyncConfigRequest,
  type OutlineCollection,
  type OutlineDocument
} from '../../api/outline'

const loading = ref(false)
const configs = ref<Record<string, any>[]>([])
const runs = ref<Record<string, any>[]>([])
const stats = ref<Record<string, any>>({})
const collections = ref<OutlineCollection[]>([])
const saving = ref(false)
const scanning = ref(false)
const runningId = ref('')
const editingId = ref('')
const vectorizing = ref(false)
const webhookDialogVisible = ref(false)
const webhookTestVisible = ref(false)
const testingWebhook = ref(false)
const webhookTestResult = ref('')
const webhookDocs = ref<OutlineDocument[]>([])
const loadingWebhookDocs = ref(false)
const detailDialogVisible = ref(false)
const detailTaskId = ref('')
const detailStatus = ref('')
const detailLoading = ref(false)
const retryingFailed = ref(false)
const syncDetails = ref<Record<string, any>[]>([])

const form = reactive<OutlineAutoSyncConfigRequest>({
  id: '',
  name: 'Outline 自动同步',
  enabled: false,
  collectionId: '',
  intervalMinutes: 60,
  force: false,
  cleanupMissing: false,
  vectorizeAfterSync: true,
  vectorForce: false,
  vectorLimit: 500,
  webhookEnabled: false,
  webhookSecret: ''
})
const webhookTest = reactive({ event: 'document.updated', documentId: '', collectionId: '', secret: '' })

const webhookBaseUrl = computed(() => normalizeBaseUrl(import.meta.env.VITE_WEBHOOK_BASE_URL || import.meta.env.VITE_API_BASE_URL || location.origin))
const webhookUrl = computed(() => `${webhookBaseUrl.value}/api/outline/auto-sync/webhook`)
const webhookCurl = computed(() => `curl -X POST '${webhookUrl.value}' \\
  -H 'Content-Type: application/json' \\
  -H 'X-Outline-Webhook-Secret: ${form.webhookSecret || '<your-secret>'}' \\
  -d '{"event":"document.updated","documentId":"<outline-document-id>","collectionId":"${form.collectionId || ''}"}'`)

onMounted(loadAll)

async function loadAll() {
  loading.value = true
  try {
    await Promise.allSettled([loadCollections(), loadConfigs(), loadRuns(), loadStats()])
  } finally {
    loading.value = false
  }
}
async function loadCollections() { collections.value = await getOutlineCollections() }
async function loadConfigs() { configs.value = await getOutlineAutoSyncConfigs() }
async function loadRuns() { runs.value = await getOutlineAutoSyncRuns({ configId: form.id || undefined, limit: 100 }) }
async function loadStats() { stats.value = await getOutlineKnowledgeStats() }

function selectConfig(item: Record<string, any>) {
  editingId.value = value(item, 'id')
  Object.assign(form, {
    id: value(item, 'id'),
    name: value(item, 'name') || 'Outline 自动同步',
    enabled: truthy(value(item, 'enabled')),
    collectionId: value(item, 'collectionId', 'collection_id') || '',
    intervalMinutes: Number(value(item, 'intervalMinutes', 'interval_minutes') || 60),
    force: truthy(value(item, 'force')),
    cleanupMissing: truthy(value(item, 'cleanupMissing', 'cleanup_missing')),
    vectorizeAfterSync: !falsy(value(item, 'vectorizeAfterSync', 'vectorize_after_sync')),
    vectorForce: truthy(value(item, 'vectorForce', 'vector_force')),
    vectorLimit: Number(value(item, 'vectorLimit', 'vector_limit') || 500),
    webhookEnabled: truthy(value(item, 'webhookEnabled', 'webhook_enabled')),
    webhookSecret: value(item, 'webhookSecret', 'webhook_secret') || ''
  })
  loadRuns()
}
function resetForm() {
  editingId.value = ''
  Object.assign(form, { id: '', name: 'Outline 自动同步', enabled: false, collectionId: '', intervalMinutes: 60, force: false, cleanupMissing: false, vectorizeAfterSync: true, vectorForce: false, vectorLimit: 500, webhookEnabled: false, webhookSecret: '' })
}
async function saveConfig() {
  saving.value = true
  try {
    const payload = { ...form, collectionId: form.collectionId || undefined, id: form.id || undefined }
    const result = editingId.value ? await updateOutlineAutoSyncConfig(editingId.value, payload) : await createOutlineAutoSyncConfig(payload)
    ElMessage.success('保存成功')
    selectConfig(result)
    await Promise.allSettled([loadConfigs(), loadRuns()])
  } finally {
    saving.value = false
  }
}
async function runNow(item: Record<string, any>) {
  const id = value(item, 'id')
  if (!id) return
  runningId.value = id
  try {
    const result = await runOutlineAutoSyncNow(id, { triggerType: 'MANUAL' })
    ElMessage.success(`运行完成：${value(result, 'status')}`)
    await Promise.allSettled([loadConfigs(), loadRuns(), loadStats()])
  } finally {
    runningId.value = ''
  }
}
async function scanDue() {
  scanning.value = true
  try {
    const result = await scanOutlineAutoSyncDue()
    ElMessage.success(`扫描完成：执行 ${value(result, 'count') || 0} 个配置`)
    await Promise.allSettled([loadConfigs(), loadRuns(), loadStats()])
  } finally {
    scanning.value = false
  }
}
async function vectorizePending() {
  vectorizing.value = true
  try {
    const result = await vectorizeOutline({ force: false, limit: Number(form.vectorLimit || 500) })
    ElMessage.success(`补向量完成：${value(result, 'status') || value(result, 'message') || 'DONE'}`)
    await loadStats()
  } finally {
    vectorizing.value = false
  }
}
function openWebhookHelp() { webhookDialogVisible.value = true }
function openWebhookTest() {
  webhookTest.secret = form.webhookSecret || ''
  webhookTest.collectionId = form.collectionId || ''
  webhookTestResult.value = ''
  webhookTestVisible.value = true
  if (webhookTest.collectionId) loadWebhookDocs()
}
function selectAndTest(item: Record<string, any>) { selectConfig(item); openWebhookTest() }
async function loadWebhookDocs() {
  loadingWebhookDocs.value = true
  try {
    webhookDocs.value = await listOutlineDocuments({ collectionId: webhookTest.collectionId || undefined, limit: 100, offset: 0 })
    if (!webhookTest.documentId && webhookDocs.value.length > 0) webhookTest.documentId = webhookDocs.value[0].id
  } finally {
    loadingWebhookDocs.value = false
  }
}
async function testWebhook() {
  if (!webhookTest.secret) {
    ElMessage.warning('请先填写 Webhook Secret')
    return
  }
  if (!webhookTest.documentId) {
    ElMessage.warning('请填写 Outline Document ID')
    return
  }
  testingWebhook.value = true
  try {
    const result = await testOutlineAutoSyncWebhook(webhookTest.secret, {
      event: webhookTest.event,
      documentId: webhookTest.documentId,
      collectionId: webhookTest.collectionId || undefined
    })
    webhookTestResult.value = JSON.stringify(result, null, 2)
    ElMessage.success(`Webhook 测试完成：${value(result, 'status')}`)
    await Promise.allSettled([loadConfigs(), loadRuns(), loadStats()])
  } finally {
    testingWebhook.value = false
  }
}
async function openSyncDetails(row: Record<string, any>) {
  const taskId = value(row, 'syncTaskId', 'sync_task_id')
  if (!taskId) return
  detailTaskId.value = taskId
  detailStatus.value = ''
  syncDetails.value = []
  detailDialogVisible.value = true
  await loadSyncDetails()
}
async function loadSyncDetails() {
  if (!detailTaskId.value) return
  detailLoading.value = true
  try {
    syncDetails.value = await getOutlineSyncTaskDetails(detailTaskId.value, { status: detailStatus.value || undefined, limit: 500 })
  } finally {
    detailLoading.value = false
  }
}
async function retryFailedDetails() {
  if (!detailTaskId.value) return
  retryingFailed.value = true
  try {
    const result = await retryOutlineFailedTask(detailTaskId.value, true)
    ElMessage.success(`重试完成：${value(result, 'status') || 'DONE'}`)
    await Promise.allSettled([loadSyncDetails(), loadRuns(), loadStats()])
  } finally {
    retryingFailed.value = false
  }
}
async function copyText(text: string, message: string) {
  await copyToClipboard(text)
  ElMessage.success(message)
}
function normalizeBaseUrl(url: string) {
  return String(url || '').trim().replace(/\/+$/, '') || location.origin
}
function value(obj: any, ...keys: string[]) {
  if (!obj) return ''
  for (const key of keys) {
    const v = obj[key]
    if (v !== undefined && v !== null && String(v).trim() !== '') return v
  }
  return ''
}
function truthy(v: any) { return v === true || v === 'true' || v === 1 || v === '1' }
function falsy(v: any) { return v === false || v === 'false' || v === 0 || v === '0' }
function statusTag(status: any): 'success' | 'warning' | 'info' | 'danger' {
  const s = String(status || '')
  if (s === 'SUCCESS') return 'success'
  if (s === 'FAILED' || s === 'ERROR') return 'danger'
  if (s === 'PARTIAL_SUCCESS' || s === 'RUNNING' || s.includes('TIMEOUT')) return 'warning'
  if (s === 'SKIPPED') return 'info'
  return 'info'
}
</script>

<style scoped>
.header-actions { display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
.stats-row { display: grid; grid-template-columns: repeat(4, minmax(160px, 1fr)); gap: 12px; margin-bottom: 16px; }
.stat-label { color: #64748b; font-size: 13px; }
.stat-value { margin-top: 8px; font-size: 24px; font-weight: 800; color: #0f172a; }
.stat-value.success { color: #059669; }
.stat-value.warning { color: #d97706; }
.auto-sync-page { display: grid; grid-template-columns: minmax(380px, 440px) minmax(420px, 520px) minmax(520px, 1fr); gap: 16px; }
.config-card, .list-card, .run-card { min-height: calc(100vh - 220px); }
.card-header, .item-title, .actions { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.mb { margin-bottom: 12px; }
.mt { margin-top: 12px; }
.unit, .hint { margin-left: 8px; color: #64748b; font-size: 12px; }
.hint.block { margin-left: 0; margin-top: 6px; }
.config-item { padding: 12px; border-radius: 12px; background: #f8fafc; margin-bottom: 10px; cursor: pointer; font-size: 13px; border: 1px solid transparent; }
.config-item:hover, .config-item.active { background: #eef6ff; border-color: #93c5fd; }
.config-item p { color: #64748b; word-break: break-all; margin: 4px 0; }
.tag-group { display: flex; gap: 4px; flex-wrap: wrap; justify-content: flex-end; }
.grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 6px; margin: 8px 0; }
.actions { justify-content: flex-start; margin-top: 8px; }
.error { color: #dc2626; }
small { display: block; color: #64748b; margin-top: 4px; }
.option-id { display: inline-block; margin-left: 8px; color: #94a3b8; }
.code { margin-top: 12px; padding: 12px; background: #0f172a; color: #e2e8f0; border-radius: 10px; overflow: auto; white-space: pre-wrap; }
.detail-toolbar { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 12px; }
.detail-title { font-weight: 800; color: #0f172a; word-break: break-all; }
.detail-filters { display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
@media (max-width: 1500px) { .auto-sync-page { grid-template-columns: 420px 1fr; } .run-card { grid-column: 1 / -1; } }
@media (max-width: 980px) { .stats-row, .auto-sync-page { grid-template-columns: 1fr; } .run-card { grid-column: auto; } .detail-toolbar { flex-direction: column; } }
</style>
