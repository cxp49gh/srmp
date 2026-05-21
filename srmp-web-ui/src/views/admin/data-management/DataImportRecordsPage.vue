<template>
  <AgentPageShell title="导入记录" description="跨项目查询导入流水，支持失败筛选与详情查看。">
    <el-card shadow="never">
      <el-form :inline="true" :model="query" size="small" class="toolbar" @submit.prevent>
        <el-form-item label="项目">
          <el-select v-model="query.projectId" clearable filterable placeholder="全部" style="width: 200px">
            <el-option v-for="p in projectOptions" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="query.importType" clearable placeholder="全部" style="width: 130px">
            <el-option label="路网" value="ROAD_NETWORK" />
            <el-option label="路段" value="SECTION_PACKAGE" />
            <el-option label="病害" value="DISEASE_EXCEL" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 110px">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
        <el-form-item label="上传人">
          <el-input v-model="query.uploadedBy" clearable placeholder="created_by" style="width: 120px" />
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 340px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="search">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="page.records" size="small" stripe>
        <el-table-column prop="projectName" label="项目" min-width="120" show-overflow-tooltip />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ importTypeLabel(row.importType) }}</template>
        </el-table-column>
        <el-table-column prop="fileName" label="文件名" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="importStatusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startedAt" label="开始时间" width="170" />
        <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
        <el-table-column prop="uploadedBy" label="上传人" width="100" show-overflow-tooltip />
        <el-table-column prop="resultSummary" label="结果摘要" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">详情</el-button>
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
          @size-change="load"
        />
      </div>
    </el-card>

    <el-drawer v-model="drawerVisible" title="导入记录详情" size="480px">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="项目">{{ detail.projectName }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ importTypeLabel(detail.importType) }}</el-descriptions-item>
          <el-descriptions-item label="文件">{{ detail.fileName }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
          <el-descriptions-item label="上传人">{{ detail.uploadedBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="说明">{{ detail.message || '-' }}</el-descriptions-item>
        </el-descriptions>
        <h4 class="sub">失败明细</h4>
        <el-empty v-if="!detail.failureDetails?.length" description="无" />
        <ul v-else class="fail-list">
          <li v-for="(f, i) in detail.failureDetails" :key="i">
            {{ f.fileName }} {{ f.rowNo != null ? `行 ${f.rowNo}` : '' }} {{ f.field ? `字段 ${f.field}` : '' }}：{{ f.reason }}
          </li>
        </ul>
        <h4 class="sub">警告</h4>
        <el-empty v-if="!detail.warnings?.length" description="无" />
        <ul v-else><li v-for="(w, i) in detail.warnings" :key="i">{{ w }}</li></ul>
        <h4 class="sub">技术信息</h4>
        <pre class="tech">{{ detail.technicalInfo || '-' }}</pre>
      </template>
    </el-drawer>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AgentPageShell from '../../agent/components/AgentPageShell.vue'
import {
  getImportRecordDetail,
  pageDataMgmtProjects,
  pageImportRecordsGlobal,
  type DataImportRecordDetailVO,
  type DataImportRecordVO,
  type DataMgmtProjectVO,
  type PageResult
} from '../../../api/dataMgmt'
import { importStatusTagType, importTypeLabel } from '../../../utils/dataMgmtHelpers'

const loading = ref(false)
const projectOptions = ref<DataMgmtProjectVO[]>([])
const timeRange = ref<[string, string] | null>(null)
const query = reactive({
  pageNo: 1,
  pageSize: 20,
  projectId: '',
  importType: '',
  status: '',
  uploadedBy: ''
})
const page = reactive<PageResult<DataImportRecordVO>>({ total: 0, pageNo: 1, pageSize: 20, records: [] })
const drawerVisible = ref(false)
const detail = ref<DataImportRecordDetailVO | null>(null)

async function loadProjects() {
  const res = await pageDataMgmtProjects({ pageNo: 1, pageSize: 500, includeArchived: true })
  projectOptions.value = res.records || []
}

function search() {
  query.pageNo = 1
  load()
}

async function load() {
  loading.value = true
  try {
    const res = await pageImportRecordsGlobal({
      ...query,
      projectId: query.projectId || undefined,
      importType: query.importType || undefined,
      status: query.status || undefined,
      uploadedBy: query.uploadedBy || undefined,
      startedFrom: timeRange.value?.[0],
      startedTo: timeRange.value?.[1]
    })
    page.total = res.total
    page.records = res.records || []
  } finally {
    loading.value = false
  }
}

async function openDetail(row: DataImportRecordVO) {
  detail.value = await getImportRecordDetail(row.projectId, row.id)
  drawerVisible.value = true
}

onMounted(async () => {
  await loadProjects()
  await load()
})
</script>

<style scoped>
.toolbar { margin-bottom: 12px; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
.sub { margin: 16px 0 8px; font-size: 14px; }
.fail-list { font-size: 13px; color: #b91c1c; }
.tech { font-size: 12px; background: #f8fafc; padding: 8px; overflow: auto; max-height: 200px; }
</style>
