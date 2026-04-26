<template>
  <AgentPageShell title="Outline 同步入库" description="将 Outline 文档同步到本地知识库，供 RAG 稳定检索。">
    <div class="sync-page">
      <el-card class="left-card">
        <template #header><div class="card-header"><span>同步配置</span><el-button size="small" @click="loadAll">刷新</el-button></div></template>
        <el-alert v-if="status && status.usable === false" type="warning" show-icon title="Outline 当前不可用，请先检查配置和 API Token。" class="mb" />
        <el-descriptions :column="1" border size="small" class="mb">
          <el-descriptions-item label="enabled">{{ status.enabled }}</el-descriptions-item>
          <el-descriptions-item label="usable">{{ status.usable }}</el-descriptions-item>
          <el-descriptions-item label="baseUrl">{{ status.baseUrl }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="90px">
          <el-form-item label="Collection">
            <el-select v-model="form.collectionId" clearable filterable placeholder="为空时同步默认文档列表" style="width:100%" @change="loadDocuments">
              <el-option v-for="item in collections" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="同步数量"><el-input-number v-model="form.limit" :min="1" :max="200" /></el-form-item>
          <el-form-item label="强制更新"><el-switch v-model="form.force" /></el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="syncing" @click="doSync">同步到知识库</el-button>
            <el-button @click="loadDocuments">预览文档</el-button>
          </el-form-item>
        </el-form>
        <el-divider />
        <h3>Collection 列表</h3>
        <el-empty v-if="collections.length === 0" description="暂无 Collection" />
        <div v-for="item in collections" :key="item.id" class="collection-item">
          <strong>{{ item.name }}</strong><p>{{ item.id }}</p>
        </div>
      </el-card>

      <el-card class="middle-card">
        <template #header>文档预览</template>
        <el-empty v-if="documents.length === 0" description="暂无文档" />
        <div v-for="item in documents" :key="item.id" class="doc-item">
          <strong>{{ item.title }}</strong><p>{{ item.id }}</p>
          <div v-if="item.updatedAt">更新时间：{{ item.updatedAt }}</div>
        </div>
      </el-card>

      <el-card class="right-card">
        <template #header><div class="card-header"><span>同步任务</span><el-button size="small" @click="loadTasks">刷新</el-button></div></template>
        <el-empty v-if="tasks.length === 0" description="暂无任务" />
        <div v-for="item in tasks" :key="item.id" class="task-item">
          <div class="task-title"><strong>{{ item.status }}</strong><el-tag size="small" :type="tagType(item.status)">{{ item.sync_mode }}</el-tag></div>
          <p>{{ item.id }}</p>
          <div class="task-grid">
            <span>总数：{{ item.total_count }}</span><span>成功：{{ item.success_count }}</span>
            <span>跳过：{{ item.skip_count }}</span><span>失败：{{ item.fail_count }}</span>
          </div>
          <div v-if="item.error_message" class="error">{{ item.error_message }}</div>
        </div>
      </el-card>
    </div>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import { getOutlineCollections, getOutlineStatus, getOutlineSyncTasks, listOutlineDocuments, syncOutline, type OutlineCollection, type OutlineDocument } from '../../api/outline'

const status = ref<Record<string, any>>({})
const collections = ref<OutlineCollection[]>([])
const documents = ref<OutlineDocument[]>([])
const tasks = ref<Record<string, any>[]>([])
const syncing = ref(false)
const form = reactive({ collectionId: '', limit: 50, force: false })

onMounted(loadAll)

async function loadAll() { await Promise.allSettled([loadStatus(), loadCollections(), loadDocuments(), loadTasks()]) }
async function loadStatus() { status.value = await getOutlineStatus() }
async function loadCollections() { collections.value = await getOutlineCollections() }
async function loadDocuments() { documents.value = await listOutlineDocuments({ collectionId: form.collectionId || undefined, limit: form.limit, offset: 0 }) }
async function loadTasks() { tasks.value = await getOutlineSyncTasks(20) }
async function doSync() {
  syncing.value = true
  try {
    const result = await syncOutline({ collectionId: form.collectionId || undefined, limit: form.limit, force: form.force })
    ElMessage.success(`同步完成：成功 ${result.success_count || 0}，跳过 ${result.skip_count || 0}，失败 ${result.fail_count || 0}`)
    await loadTasks()
  } finally { syncing.value = false }
}
function tagType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}
</script>

<style scoped>
.sync-page{display:grid;grid-template-columns:360px minmax(360px,1fr) 420px;gap:16px}.left-card,.middle-card,.right-card{min-height:calc(100vh - 130px)}.card-header{display:flex;align-items:center;justify-content:space-between}.mb{margin-bottom:16px}h3{margin:0 0 10px;font-size:15px}.collection-item,.doc-item,.task-item{padding:12px;background:#f8fafc;border-radius:10px;margin-bottom:10px;font-size:13px}.collection-item p,.doc-item p,.task-item p{margin:4px 0;color:#64748b;word-break:break-all}.task-title{display:flex;justify-content:space-between;gap:8px}.task-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:6px;margin-top:8px}.error{margin-top:8px;color:#dc2626}
</style>