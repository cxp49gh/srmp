<template>
  <AgentPageShell
    title="数据管理"
    description="按项目组织数据：新建项目后进入「导入」完成路网 / 路段 / 病害上传；可「清除」物理删除本项目归属数据保留项目主档，或「删除」项目（先物理清除归属数据后再软删主档）。"
  >
    <div class="dm-list">
      <el-card shadow="never" class="toolbar-card">
        <el-form :inline="true" :model="query" size="small" @submit.prevent>
          <el-form-item label="项目名称">
            <el-input v-model="query.nameKeyword" placeholder="模糊查询" clearable style="width: 220px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" @click="load">查询</el-button>
            <el-button @click="resetQuery">重置</el-button>
            <el-button type="success" plain @click="openCreate">新建项目</el-button>
            <router-link class="el-button el-button--default is-plain" to="/gis/one-map">GIS 一张图</router-link>
            <router-link class="el-button el-button--default is-plain" to="/demo/dashboard">演示看板</router-link>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never">
        <el-table :data="page.records" v-loading="loading" size="small" stripe>
          <el-table-column prop="name" label="项目名称" min-width="160" />
          <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="创建时间" width="170" />
          <el-table-column prop="updatedAt" label="更新时间" width="170" />
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="goImport(row.id)">导入</el-button>
              <router-link class="el-button is-link" :to="{ path: '/gis/one-map', query: { projectId: row.id } }">一张图</router-link>
              <el-button type="danger" link :loading="clearingId === row.id" @click="confirmClearAll(row.id)">清除</el-button>
              <el-button type="danger" link :loading="deletingId === row.id" @click="confirmDeleteProject(row)">删除</el-button>
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
  </AgentPageShell>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AgentPageShell from '../agent/components/AgentPageShell.vue'
import {
  pageDataMgmtProjects,
  createDataMgmtProject,
  clearProjectData,
  deleteDataMgmtProject,
  type DataMgmtProjectVO,
  type PageResult
} from '../../api/dataMgmt'

const router = useRouter()

const loading = ref(false)
const query = reactive({ pageNo: 1, pageSize: 20, nameKeyword: '' as string | undefined })
const page = reactive<PageResult<DataMgmtProjectVO>>({
  total: 0,
  pageNo: 1,
  pageSize: 20,
  records: []
})

const createVisible = ref(false)
const createLoading = ref(false)
const createForm = reactive({ name: '', remark: '' })
const clearingId = ref<string | null>(null)
const deletingId = ref<string | null>(null)

async function load() {
  loading.value = true
  try {
    const res = await pageDataMgmtProjects({ ...query })
    page.total = res.total
    page.pageNo = res.pageNo
    page.pageSize = res.pageSize
    page.records = res.records || []
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.nameKeyword = ''
  query.pageNo = 1
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

function formatClearSummary(r: {
  routes: number
  sections: number
  diseaseRecords: number
  importRecords: number
}) {
  const parts: string[] = []
  if (r.routes) parts.push(`路线 ${r.routes}`)
  if (r.sections) parts.push(`路段 ${r.sections}`)
  if (r.diseaseRecords) parts.push(`病害 ${r.diseaseRecords}`)
  if (r.importRecords) parts.push(`导入流水 ${r.importRecords}`)
  return parts.length ? `已删除：${parts.join('，')}` : '没有需要清除的数据（可能均为空或已清除）'
}

async function confirmDeleteProject(row: DataMgmtProjectVO) {
  try {
    await ElMessageBox.confirm(
      `将删除项目「${row.name}」：会先物理删除本项目下路网、路段、病害及导入流水，再软删项目主档。删除后列表不可见（业务数据行将从库中移除）。是否继续？`,
      '删除项目',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  deletingId.value = row.id
  try {
    await deleteDataMgmtProject(row.id)
    ElMessage.success('项目已删除')
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    deletingId.value = null
  }
}

async function confirmClearAll(projectId: string) {
  try {
    await ElMessageBox.confirm(
      '将物理删除本项目下已归属的路网、路段、病害数据及本项目的导入流水记录。项目主档保留。是否继续？',
      '清除项目数据',
      { type: 'warning', confirmButtonText: '清除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  clearingId.value = projectId
  try {
    const r = await clearProjectData(projectId, 'ALL')
    ElMessage.success(formatClearSummary(r))
  } catch {
    /* 拦截器已提示 */
  } finally {
    clearingId.value = null
  }
}

onMounted(() => {
  load()
})
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
</style>
