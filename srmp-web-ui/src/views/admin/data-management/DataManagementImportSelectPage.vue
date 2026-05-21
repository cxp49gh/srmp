<template>
  <AgentPageShell
    title="项目导入"
    description="请先选择要导入数据的项目，再进入单项目导入工作台（路网 → 路段 → 病害）。"
  >
    <el-card shadow="never" class="select-card">
      <el-form :inline="true" size="small" @submit.prevent>
        <el-form-item label="项目名称">
          <el-input v-model="keyword" clearable placeholder="模糊查询" style="width: 220px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="load">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table
        v-loading="loading"
        :data="page.records"
        size="small"
        stripe
        highlight-current-row
        @current-change="onCurrentChange"
      >
        <el-table-column prop="name" label="项目名称" min-width="200" />
        <el-table-column prop="remark" label="备注" min-width="240" show-overflow-tooltip />
        <el-table-column prop="updatedAt" label="更新时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="goImport(row.id)">进入导入</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="pageNo"
          v-model:page-size="pageSize"
          :total="page.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="load"
          @size-change="load"
        />
      </div>

      <div class="footer-actions">
        <el-button type="primary" :disabled="!selectedId" @click="goImport(selectedId!)">进入导入工作台</el-button>
        <span v-if="!selectedId" class="hint">请先在表格中选择一行，或点击「进入导入」</span>
      </div>
    </el-card>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AgentPageShell from '../../agent/components/AgentPageShell.vue'
import { pageDataMgmtProjects, type DataMgmtProjectVO, type PageResult } from '../../../api/dataMgmt'

const router = useRouter()
const loading = ref(false)
const keyword = ref('')
const pageNo = ref(1)
const pageSize = ref(20)
const selectedId = ref<string | null>(null)
const page = ref<PageResult<DataMgmtProjectVO>>({ total: 0, pageNo: 1, pageSize: 20, records: [] })

async function load() {
  loading.value = true
  try {
    page.value = await pageDataMgmtProjects({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      nameKeyword: keyword.value || undefined
    })
  } finally {
    loading.value = false
  }
}

function onCurrentChange(row: DataMgmtProjectVO | undefined) {
  selectedId.value = row?.id ?? null
}

function goImport(projectId: string) {
  if (!projectId) return
  router.push(`/admin/data-management/${projectId}/import`)
}

onMounted(load)
</script>

<style scoped>
.select-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.pager {
  display: flex;
  justify-content: flex-end;
}
.footer-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid #e2e8f0;
}
.hint {
  color: #64748b;
  font-size: 13px;
}
</style>
