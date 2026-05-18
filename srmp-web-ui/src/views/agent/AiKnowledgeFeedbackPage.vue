<template>
  <AgentPageShell title="AI 知识反馈" description="查看用户提交的知识缺失与来源不准确反馈，用于内容运营治理。">
    <el-card class="filter-card">
      <el-form :inline="true">
        <el-form-item label="类型">
          <el-select v-model="filters.feedbackType" clearable placeholder="全部" style="width: 180px">
            <el-option label="知识缺失" value="MISSING_KNOWLEDGE" />
            <el-option label="来源不准确" value="SOURCE_INACCURATE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadList">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <el-table :data="rows" border size="small" v-loading="loading">
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="row.feedback_type === 'SOURCE_INACCURATE' ? 'warning' : 'info'">
              {{ typeLabel(row.feedback_type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="用户问题" min-width="220" show-overflow-tooltip prop="question" />
        <el-table-column label="备注" min-width="200" show-overflow-tooltip prop="remark" />
        <el-table-column label="用户" width="120" prop="user_id" />
        <el-table-column label="时间" width="170" prop="created_at" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="detailVisible" title="反馈详情" size="560px">
      <pre v-if="detail">{{ JSON.stringify(detail, null, 2) }}</pre>
    </el-drawer>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AgentPageShell from './components/AgentPageShell.vue'
import { listAiKnowledgeFeedback } from '../../api/knowledgeFeedback'

const loading = ref(false)
const rows = ref<Record<string, any>[]>([])
const detail = ref<Record<string, any> | null>(null)
const detailVisible = ref(false)
const filters = reactive({ feedbackType: '' })

function typeLabel(type: string) {
  if (type === 'SOURCE_INACCURATE') return '来源不准确'
  if (type === 'MISSING_KNOWLEDGE') return '知识缺失'
  return type || '-'
}

onMounted(loadList)

async function loadList() {
  loading.value = true
  try {
    rows.value = await listAiKnowledgeFeedback({
      feedbackType: filters.feedbackType || undefined,
      limit: 100
    })
  } finally {
    loading.value = false
  }
}

function openDetail(row: Record<string, any>) {
  detail.value = row
  detailVisible.value = true
}
</script>

<style scoped>
.filter-card {
  margin-bottom: 16px;
}

pre {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
}
</style>
