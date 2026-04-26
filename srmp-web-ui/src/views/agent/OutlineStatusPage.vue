<template>
  <AgentPageShell
    title="Outline 连接状态"
    description="查看后端 Outline 配置是否启用、是否可用。"
  >
    <el-card>
      <template #header>
        <div class="card-header">
          <span>连接状态</span>
          <el-button type="primary" :loading="loading" @click="loadStatus">刷新</el-button>
        </div>
      </template>

      <el-descriptions :column="1" border>
        <el-descriptions-item label="enabled">{{ status.enabled }}</el-descriptions-item>
        <el-descriptions-item label="usable">{{ status.usable }}</el-descriptions-item>
        <el-descriptions-item label="baseUrl">{{ status.baseUrl }}</el-descriptions-item>
        <el-descriptions-item label="syncEnabled">{{ status.syncEnabled }}</el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="!status.usable"
        class="tip"
        type="warning"
        show-icon
        title="Outline 当前不可用，请检查 OUTLINE_ENABLED、OUTLINE_BASE_URL、OUTLINE_API_TOKEN 配置。"
      />
    </el-card>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AgentPageShell from './components/AgentPageShell.vue'
import { getOutlineStatus } from '../../api/outline'

const loading = ref(false)
const status = ref<Record<string, any>>({})

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
</style>