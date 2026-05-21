<template>
  <AgentPageShell title="操作审计" description="查询项目创建、导入、清除、归档等操作记录。">
    <el-card shadow="never">
      <el-form :inline="true" :model="query" size="small" class="toolbar">
        <el-form-item label="项目">
          <el-select v-model="query.projectId" clearable filterable placeholder="全部" style="width: 180px">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作类型">
          <el-input v-model="query.operationType" clearable placeholder="如 CLEAR_DATA" style="width: 160px" />
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="query.operator" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="search">查询</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="loading" :data="page.records" size="small" stripe>
        <el-table-column prop="projectName" label="项目" min-width="120" />
        <el-table-column label="操作" width="130">
          <template #default="{ row }">{{ operationTypeLabel(row.operationType) }}</template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column prop="operatedAt" label="时间" width="170" />
        <el-table-column prop="result" label="结果" width="90" />
        <el-table-column prop="reason" label="原因" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row.id)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="page.total"
          layout="total, prev, pager, next"
          @current-change="load"
        />
      </div>
    </el-card>
    <el-dialog v-model="detailVisible" title="审计详情" width="560px">
      <el-descriptions v-if="detail" :column="1" border size="small">
        <el-descriptions-item label="项目">{{ detail.projectName }}</el-descriptions-item>
        <el-descriptions-item label="操作">{{ operationTypeLabel(detail.operationType) }}</el-descriptions-item>
        <el-descriptions-item label="操作前快照"><pre class="snap">{{ detail.snapshotBefore || '-' }}</pre></el-descriptions-item>
        <el-descriptions-item label="操作后快照"><pre class="snap">{{ detail.snapshotAfter || '-' }}</pre></el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AgentPageShell from '../../agent/components/AgentPageShell.vue'
import {
  getAuditLogDetail,
  pageAuditLogs,
  pageDataMgmtProjects,
  type DataMgmtAuditLogVO,
  type DataMgmtProjectVO,
  type PageResult
} from '../../../api/dataMgmt'
import { operationTypeLabel } from '../../../utils/dataMgmtHelpers'

const loading = ref(false)
const projects = ref<DataMgmtProjectVO[]>([])
const query = reactive({ pageNo: 1, pageSize: 20, projectId: '', operationType: '', operator: '' })
const page = reactive<PageResult<DataMgmtAuditLogVO>>({ total: 0, pageNo: 1, pageSize: 20, records: [] })
const detailVisible = ref(false)
const detail = ref<DataMgmtAuditLogVO | null>(null)

function search() {
  query.pageNo = 1
  load()
}

async function load() {
  loading.value = true
  try {
    const res = await pageAuditLogs({
      ...query,
      projectId: query.projectId || undefined,
      operationType: query.operationType || undefined,
      operator: query.operator || undefined
    })
    page.total = res.total
    page.records = res.records || []
  } finally {
    loading.value = false
  }
}

async function openDetail(id: string) {
  detail.value = await getAuditLogDetail(id)
  detailVisible.value = true
}

onMounted(async () => {
  const res = await pageDataMgmtProjects({ pageNo: 1, pageSize: 500, includeArchived: true })
  projects.value = res.records || []
  await load()
})
</script>

<style scoped>
.toolbar { margin-bottom: 12px; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
.snap { font-size: 11px; white-space: pre-wrap; margin: 0; max-height: 120px; overflow: auto; }
</style>
