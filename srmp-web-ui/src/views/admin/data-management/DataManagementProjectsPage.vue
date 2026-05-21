<template>
  <AgentPageShell
    title="项目总览"
    description="查看项目数据就绪状态；危险操作请前往「清除与归档」。"
  >
    <div class="dm-list">
      <el-card shadow="never" class="toolbar-card">
        <el-form :inline="true" :model="query" size="small" @submit.prevent>
          <el-form-item label="项目名称">
            <el-input v-model="query.nameKeyword" placeholder="模糊查询" clearable style="width: 220px" />
          </el-form-item>
          <el-form-item label="含归档">
            <el-switch v-model="query.includeArchived" @change="load" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" @click="load">查询</el-button>
            <el-button @click="resetQuery">重置</el-button>
            <el-button type="success" plain @click="openCreate">新建项目</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <el-table v-loading="loading" :data="page.records" size="small" stripe>
          <el-table-column prop="name" label="项目名称" min-width="140" />
          <el-table-column label="项目状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="projectStatusTagType(row.summary?.projectStatus)">
                {{ projectStatusLabel(row.summary?.projectStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="路线/路段/病害" width="130">
            <template #default="{ row }">
              {{ row.summary?.routeCount ?? 0 }} / {{ row.summary?.sectionCount ?? 0 }} / {{ row.summary?.diseaseCount ?? 0 }}
            </template>
          </el-table-column>
          <el-table-column label="最近导入" min-width="160">
            <template #default="{ row }">
              <template v-if="row.summary?.lastImportType">
                {{ importTypeLabel(row.summary.lastImportType) }}
                <el-tag size="small" :type="importStatusTagType(row.summary.lastImportStatus)" class="ml4">
                  {{ row.summary.lastImportStatus }}
                </el-tag>
              </template>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="最近导入时间" width="170">
            <template #default="{ row }">{{ row.summary?.lastImportTime || '-' }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="170" />
          <el-table-column prop="updatedAt" label="更新时间" width="170" />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="goGis(row)">查看 GIS</el-button>
              <el-button type="primary" link @click="goImport(row.id)">导入</el-button>
              <el-button type="primary" link @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="query.pageNo"
            v-model:page-size="query.pageSize"
            :total="page.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="load"
            @size-change="load"
          />
        </div>
      </el-card>
    </div>

    <el-dialog v-model="createVisible" title="新建项目" width="420px" destroy-on-close @closed="resetCreateForm">
      <el-form :model="createForm" label-width="88px">
        <el-form-item label="项目名称" required>
          <el-input v-model="createForm.name" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" :title="detailProject?.name || '项目详情'" width="520px">
      <el-descriptions v-if="detailProject" :column="1" border size="small">
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="projectStatusTagType(detailProject.summary?.projectStatus)">
            {{ projectStatusLabel(detailProject.summary?.projectStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="路线数">{{ detailProject.summary?.routeCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="路段数">{{ detailProject.summary?.sectionCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="病害数">{{ detailProject.summary?.diseaseCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="最近导入">
          {{ importTypeLabel(detailProject.summary?.lastImportType) }}
          {{ detailProject.summary?.lastImportStatus || '' }}
        </el-descriptions-item>
        <el-descriptions-item label="最近成功导入">{{ detailProject.summary?.lastSuccessImportTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailProject.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AgentPageShell from '../../agent/components/AgentPageShell.vue'
import {
  pageDataMgmtProjects,
  createDataMgmtProject,
  getDataMgmtProject,
  type DataMgmtProjectVO,
  type PageResult
} from '../../../api/dataMgmt'
import {
  canOpenGis,
  importStatusTagType,
  importTypeLabel,
  projectStatusLabel,
  projectStatusTagType
} from '../../../utils/dataMgmtHelpers'

const router = useRouter()
const loading = ref(false)
const query = reactive({ pageNo: 1, pageSize: 20, nameKeyword: '' as string | undefined, includeArchived: false })
const page = reactive<PageResult<DataMgmtProjectVO>>({ total: 0, pageNo: 1, pageSize: 20, records: [] })
const createVisible = ref(false)
const createLoading = ref(false)
const createForm = reactive({ name: '', remark: '' })
const detailVisible = ref(false)
const detailProject = ref<DataMgmtProjectVO | null>(null)

async function load() {
  loading.value = true
  try {
    const res = await pageDataMgmtProjects({ ...query })
    page.total = res.total
    page.records = res.records || []
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.nameKeyword = ''
  query.pageNo = 1
  query.includeArchived = false
  load()
}

function resetCreateForm() {
  createForm.name = ''
  createForm.remark = ''
}

function openCreate() {
  resetCreateForm()
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.name.trim()) {
    ElMessage.warning('请输入项目名称')
    return
  }
  createLoading.value = true
  try {
    await createDataMgmtProject({ name: createForm.name.trim(), remark: createForm.remark || undefined })
    ElMessage.success('创建成功')
    createVisible.value = false
    await load()
  } finally {
    createLoading.value = false
  }
}

function goImport(projectId: string) {
  router.push(`/admin/data-management/${projectId}/import`)
}

function goGis(row: DataMgmtProjectVO) {
  if (!canOpenGis(row.summary)) {
    ElMessage.warning('该项目尚未导入路网，请先在「项目导入」中导入路网数据')
    return
  }
  router.push({ path: '/gis/one-map', query: { projectId: row.id } })
}

async function openDetail(row: DataMgmtProjectVO) {
  detailProject.value = await getDataMgmtProject(row.id)
  detailVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.dm-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.toolbar-card :deep(.el-card__body) {
  padding-bottom: 8px;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.muted {
  color: #94a3b8;
}
.ml4 {
  margin-left: 4px;
}
</style>
