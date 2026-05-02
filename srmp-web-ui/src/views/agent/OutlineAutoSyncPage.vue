<template>
  <AgentPageShell title="Outline 自动同步" description="配置定时同步、Webhook 精准同步，以及同步后的自动向量化。">
    <div class="auto-sync-page">
      <el-card class="config-card">
        <template #header><div class="card-header"><span>自动同步配置</span><el-button size="small" @click="loadAll">刷新</el-button></div></template>
        <el-form label-width="130px">
          <el-form-item label="配置ID"><el-input v-model="form.id" placeholder="新增时可留空" /></el-form-item>
          <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
          <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
          <el-form-item label="Collection">
            <el-select v-model="form.collectionId" clearable filterable placeholder="为空时同步默认列表" style="width: 100%">
              <el-option v-for="item in collections" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="同步间隔分钟"><el-input-number v-model="form.intervalMinutes" :min="1" :max="1440" /></el-form-item>
          <el-form-item label="Force"><el-switch v-model="form.force" /></el-form-item>
          <el-form-item label="清理过期"><el-switch v-model="form.cleanupMissing" /></el-form-item>
          <el-form-item label="同步后补向量"><el-switch v-model="form.vectorizeAfterSync" /></el-form-item>
          <el-form-item label="强制重建向量"><el-switch v-model="form.vectorForce" /></el-form-item>
          <el-form-item label="向量处理上限"><el-input-number v-model="form.vectorLimit" :min="1" :max="2000" /></el-form-item>
          <el-form-item label="启用 Webhook"><el-switch v-model="form.webhookEnabled" /></el-form-item>
          <el-form-item label="Webhook Secret"><el-input v-model="form.webhookSecret" /></el-form-item>
          <el-form-item label="Webhook 地址"><el-input :model-value="webhookUrl" readonly><template #append><el-button @click="copyWebhookUrl">复制</el-button></template></el-input></el-form-item>
          <el-form-item><el-button type="primary" :loading="saving" @click="saveConfig">保存配置</el-button><el-button @click="resetForm">清空</el-button></el-form-item>
        </el-form>
      </el-card>

      <el-card class="list-card">
        <template #header><div class="card-header"><span>配置列表</span><el-button size="small" @click="loadConfigs">刷新</el-button></div></template>
        <el-empty v-if="configs.length === 0" description="暂无配置" />
        <div v-for="item in configs" :key="item.id" class="config-item" @click="selectConfig(item)">
          <div class="item-title"><strong>{{ item.name || 'Outline 自动同步' }}</strong><div><el-tag size="small" :type="item.enabled ? 'success' : 'info'">{{ item.enabled ? '启用' : '停用' }}</el-tag><el-tag v-if="item.webhookEnabled || item.webhook_enabled" size="small" type="warning">Webhook</el-tag></div></div>
          <p>{{ item.id }}</p>
          <div class="grid">
            <span>间隔：{{ item.intervalMinutes || item.interval_minutes }} 分钟</span>
            <span>Collection：{{ item.collectionId || item.collection_id || '-' }}</span>
            <span>下次：{{ item.nextRunAt || item.next_run_at || '-' }}</span>
            <span>状态：{{ item.status || '-' }}</span>
            <span>最近任务：{{ item.lastTaskId || item.last_task_id || '-' }}</span>
            <span>向量：{{ item.lastVectorStatus || item.last_vector_status || '-' }}</span>
          </div>
          <div v-if="item.errorMessage || item.error_message" class="error">{{ item.errorMessage || item.error_message }}</div>
          <div class="actions"><el-button size="small" @click.stop="selectConfig(item)">编辑</el-button><el-button size="small" type="primary" :loading="running" @click.stop="runNow(item)">立即运行</el-button></div>
        </div>
      </el-card>

      <el-card class="run-card">
        <template #header><div class="card-header"><span>运行记录</span><div><el-button size="small" @click="scanDue">扫描到期配置</el-button><el-button size="small" @click="loadRuns">刷新</el-button></div></div></template>
        <el-empty v-if="runs.length === 0" description="暂无运行记录" />
        <el-table :data="runs" border size="small" height="calc(100vh - 180px)">
          <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag size="small" :type="tagType(row.status)">{{ row.status }}</el-tag></template></el-table-column>
          <el-table-column label="触发" width="110"><template #default="{ row }">{{ row.triggerType || row.trigger_type }}</template></el-table-column>
          <el-table-column label="事件" width="150"><template #default="{ row }">{{ row.outlineEvent || row.outline_event || '-' }}</template></el-table-column>
          <el-table-column label="文档ID" min-width="220" show-overflow-tooltip><template #default="{ row }">{{ row.outlineDocumentId || row.outline_document_id || '-' }}</template></el-table-column>
          <el-table-column label="同步任务" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ row.syncTaskId || row.sync_task_id || '-' }}</template></el-table-column>
          <el-table-column label="向量" min-width="190" show-overflow-tooltip><template #default="{ row }">{{ row.vectorizeStatus || row.vectorize_status || '-' }}<small>{{ row.vectorizeMessage || row.vectorize_message || '' }}</small></template></el-table-column>
          <el-table-column label="时间" width="180"><template #default="{ row }">{{ row.createdAt || row.created_at }}</template></el-table-column>
        </el-table>
      </el-card>
    </div>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import {
  createOutlineAutoSyncConfig,
  getOutlineAutoSyncConfigs,
  getOutlineAutoSyncRuns,
  getOutlineCollections,
  runOutlineAutoSyncNow,
  scanOutlineAutoSyncDue,
  updateOutlineAutoSyncConfig,
  type OutlineAutoSyncConfigRequest,
  type OutlineCollection
} from '../../api/outline'

const configs = ref<Record<string, any>[]>([])
const runs = ref<Record<string, any>[]>([])
const collections = ref<OutlineCollection[]>([])
const saving = ref(false)
const running = ref(false)
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
const webhookUrl = computed(() => `${location.origin}/api/outline/auto-sync/webhook`)
onMounted(loadAll)
async function loadAll() { await Promise.allSettled([loadCollections(), loadConfigs(), loadRuns()]) }
async function loadCollections() { collections.value = await getOutlineCollections() }
async function loadConfigs() { configs.value = await getOutlineAutoSyncConfigs() }
async function loadRuns() { runs.value = await getOutlineAutoSyncRuns({ limit: 50 }) }
function selectConfig(item: Record<string, any>) {
  Object.assign(form, {
    id: item.id,
    name: item.name || 'Outline 自动同步',
    enabled: item.enabled === true || item.enabled === 'true',
    collectionId: item.collectionId || item.collection_id || '',
    intervalMinutes: Number(item.intervalMinutes || item.interval_minutes || 60),
    force: item.force === true || item.force === 'true',
    cleanupMissing: item.cleanupMissing === true || item.cleanup_missing === 'true',
    vectorizeAfterSync: item.vectorizeAfterSync !== false && item.vectorize_after_sync !== false,
    vectorForce: item.vectorForce === true || item.vector_force === true,
    vectorLimit: Number(item.vectorLimit || item.vector_limit || 500),
    webhookEnabled: item.webhookEnabled === true || item.webhook_enabled === true,
    webhookSecret: item.webhookSecret || item.webhook_secret || ''
  })
}
function resetForm() {
  Object.assign(form, { id: '', name: 'Outline 自动同步', enabled: false, collectionId: '', intervalMinutes: 60, force: false, cleanupMissing: false, vectorizeAfterSync: true, vectorForce: false, vectorLimit: 500, webhookEnabled: false, webhookSecret: '' })
}
async function saveConfig() {
  saving.value = true
  try {
    const payload = { ...form, collectionId: form.collectionId || undefined }
    const result = form.id ? await updateOutlineAutoSyncConfig(form.id, payload) : await createOutlineAutoSyncConfig(payload)
    ElMessage.success('保存成功')
    selectConfig(result)
    await loadConfigs()
  } finally {
    saving.value = false
  }
}
async function runNow(item: Record<string, any>) {
  running.value = true
  try {
    const result = await runOutlineAutoSyncNow(item.id, { triggerType: 'MANUAL' })
    ElMessage.success(`运行完成：${result.status}`)
    await Promise.allSettled([loadConfigs(), loadRuns()])
  } finally {
    running.value = false
  }
}
async function scanDue() {
  const result = await scanOutlineAutoSyncDue()
  ElMessage.success(`扫描完成：执行 ${result.count || 0} 个配置`)
  await Promise.allSettled([loadConfigs(), loadRuns()])
}
async function copyWebhookUrl() {
  await navigator.clipboard.writeText(webhookUrl.value)
  ElMessage.success('Webhook 地址已复制')
}
function tagType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PARTIAL_SUCCESS') return 'warning'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}
</script>

<style scoped>
.auto-sync-page { display: grid; grid-template-columns: 420px 460px minmax(520px, 1fr); gap: 16px; }
.config-card, .list-card, .run-card { min-height: calc(100vh - 130px); }
.card-header, .item-title, .actions { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.config-item { padding: 12px; border-radius: 12px; background: #f8fafc; margin-bottom: 10px; cursor: pointer; font-size: 13px; }
.config-item:hover { background: #eef6ff; }
.config-item p { color: #64748b; word-break: break-all; margin: 4px 0; }
.grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 6px; margin: 8px 0; }
.actions { justify-content: flex-start; margin-top: 8px; }
.error { color: #dc2626; margin-top: 6px; }
small { display: block; color: #64748b; margin-top: 4px; }
</style>
