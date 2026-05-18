<template>
  <AgentPageShell
    title="Outline 连接状态"
    description="查看 Outline 是否启用、配置是否完整，以及是否允许同步入库。"
  >
    <el-card>
      <template #header>
        <div class="card-header">
          <span>连接状态</span>
          <el-button type="primary" :loading="loading" @click="loadStatus">刷新</el-button>
        </div>
      </template>

      <el-descriptions :column="1" border>
        <el-descriptions-item label="enabled">
          <el-tag :type="status.enabled ? 'success' : 'info'">{{ status.enabled }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="usable">
          <el-tag :type="status.usable ? 'success' : 'danger'">{{ status.usable }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="baseUrl">{{ status.baseUrl || '-' }}</el-descriptions-item>
        <el-descriptions-item label="syncEnabled">
          <el-tag :type="status.syncEnabled ? 'success' : 'warning'">{{ status.syncEnabled }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="defaultCollectionId">{{ status.defaultCollectionId || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="status.message" label="message">{{ status.message }}</el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="status.enabled === false"
        class="tip"
        type="warning"
        show-icon
        title="Outline 功能未启用，请联系管理员开启"
      />
      <el-alert
        v-else-if="status.usable === false"
        class="tip"
        type="error"
        show-icon
        title="Outline 配置不完整或不可访问，请检查 baseUrl、Token、网络"
      />
      <el-alert
        v-else-if="status.syncEnabled === false"
        class="tip"
        type="info"
        show-icon
        title="当前只允许在线搜索，不允许同步入库"
      />

      <el-card v-if="diagnostics" shadow="never" class="diag-card">
        <template #header>诊断信息</template>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="baseUrl 已配置">{{ diagnostics.hasBaseUrl }}</el-descriptions-item>
          <el-descriptions-item label="Token 已配置">{{ diagnostics.hasApiToken }}</el-descriptions-item>
          <el-descriptions-item label="默认 Collection">{{ diagnostics.hasDefaultCollection }}</el-descriptions-item>
        </el-descriptions>
      </el-card>
    </el-card>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AgentPageShell from './components/AgentPageShell.vue'
import { getOutlineStatus } from '../../api/outline'

const loading = ref(false)
const status = ref<Record<string, any>>({})

const diagnostics = computed(() => {
  const d = status.value?.diagnostics
  return d && typeof d === 'object' ? d : null
})

onMounted(loadStatus)

async function loadStatus() {
  loading.value = true
  try {
    status.value = await getOutlineStatus()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tip {
  margin-top: 16px;
}

.diag-card {
  margin-top: 16px;
}
</style>
