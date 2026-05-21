<template>
  <AgentPageShell title="清除与归档" description="集中执行清除、归档、删除等危险操作，操作前展示影响范围。">
    <el-card shadow="never">
      <el-form label-width="100px" size="small" style="max-width: 520px">
        <el-form-item label="项目">
          <el-select v-model="projectId" filterable placeholder="选择项目" style="width: 100%" @change="onProjectChange">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="清除范围">
          <el-select v-model="clearScope" style="width: 100%" @change="loadPreview">
            <el-option label="全部（含导入流水）" value="ALL" />
            <el-option label="路网" value="ROAD_NETWORK" />
            <el-option label="路段" value="SECTION_PACKAGE" />
            <el-option label="病害" value="DISEASE_EXCEL" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-alert v-if="preview" type="warning" show-icon :closable="false" class="mb">
        将影响：路线 {{ preview.routes }}、路段 {{ preview.sections }}、病害 {{ preview.diseaseRecords }}、导入流水 {{ preview.importRecords }}
      </el-alert>

      <div class="actions">
        <el-button type="danger" :disabled="!projectId" :loading="clearing" @click="doClear">执行清除</el-button>
        <el-button :disabled="!projectId" :loading="archiving" @click="doArchive">归档项目</el-button>
        <el-button :disabled="!projectId" :loading="restoring" @click="doRestore">恢复项目</el-button>
        <el-button type="danger" plain :disabled="!projectId" @click="doDelete">删除项目</el-button>
      </div>
    </el-card>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AgentPageShell from '../../agent/components/AgentPageShell.vue'
import {
  archiveDataMgmtProject,
  clearProjectData,
  deleteDataMgmtProject,
  pageDataMgmtProjects,
  previewClearProject,
  restoreDataMgmtProject,
  type DataMgmtClearPreviewVO,
  type DataMgmtClearScopeParam,
  type DataMgmtProjectVO
} from '../../../api/dataMgmt'

const projects = ref<DataMgmtProjectVO[]>([])
const projectId = ref('')
const clearScope = ref<DataMgmtClearScopeParam>('ALL')
const preview = ref<DataMgmtClearPreviewVO | null>(null)
const clearing = ref(false)
const archiving = ref(false)
const restoring = ref(false)

function currentName() {
  return projects.value.find((p) => p.id === projectId.value)?.name || ''
}

async function loadProjects() {
  const res = await pageDataMgmtProjects({ pageNo: 1, pageSize: 500, includeArchived: true })
  projects.value = res.records || []
}

function onProjectChange() {
  loadPreview()
}

async function loadPreview() {
  if (!projectId.value) {
    preview.value = null
    return
  }
  preview.value = await previewClearProject(projectId.value, clearScope.value)
}

async function doClear() {
  try {
    await ElMessageBox.confirm('确认按所选范围清除数据？', '清除确认', { type: 'warning' })
  } catch {
    return
  }
  clearing.value = true
  try {
    const r = await clearProjectData(projectId.value, clearScope.value)
    ElMessage.success(`已清除：路线 ${r.routes}、路段 ${r.sections}、病害 ${r.diseaseRecords}`)
    await loadPreview()
  } finally {
    clearing.value = false
  }
}

async function doArchive() {
  archiving.value = true
  try {
    await archiveDataMgmtProject(projectId.value)
    ElMessage.success('已归档')
    await loadProjects()
  } finally {
    archiving.value = false
  }
}

async function doRestore() {
  restoring.value = true
  try {
    await restoreDataMgmtProject(projectId.value)
    ElMessage.success('已恢复')
    await loadProjects()
  } finally {
    restoring.value = false
  }
}

async function doDelete() {
  const name = currentName()
  try {
    const { value } = await ElMessageBox.prompt(`请输入项目名称「${name}」以确认删除`, '删除项目', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      inputPattern: new RegExp(`^${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`),
      inputErrorMessage: '项目名称不一致'
    })
    if (value !== name) return
  } catch {
    return
  }
  await deleteDataMgmtProject(projectId.value)
  ElMessage.success('项目已删除')
  projectId.value = ''
  preview.value = null
  await loadProjects()
}

onMounted(loadProjects)
</script>

<style scoped>
.mb { margin: 16px 0; }
.actions { display: flex; flex-wrap: wrap; gap: 8px; }
</style>
