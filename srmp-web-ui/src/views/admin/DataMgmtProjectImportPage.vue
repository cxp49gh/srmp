<template>
  <AgentPageShell
    :title="`项目导入 — ${projectName || projectId}`"
    description="导入记录、路网 .tar、路段包 .tar、病害 .xlsx；均走编排接口并写入 projectId 与导入流水。"
  >
    <template #actions>
      <el-button @click="router.push('/admin/data-management')">返回项目列表</el-button>
    </template>

    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="导入记录" name="records">
        <el-table :data="records.records" v-loading="recLoading" size="small" stripe max-height="480">
          <el-table-column prop="startedAt" label="开始时间" width="170" />
          <el-table-column prop="finishedAt" label="结束时间" width="170" />
          <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
          <el-table-column prop="importType" label="类型" width="130" />
          <el-table-column prop="fileName" label="文件名" min-width="160" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column prop="message" label="说明" min-width="160" show-overflow-tooltip />
          <el-table-column prop="resultSummary" label="结果摘要" min-width="200" show-overflow-tooltip />
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="recQuery.pageNo"
            v-model:page-size="recQuery.pageSize"
            :total="records.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @current-change="loadRecords"
            @size-change="loadRecords"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="导入路网" name="road">
        <p class="hint">.tar 内含唯一一组 Shapefile（与 GIS「导入路网」一致）。</p>
        <input ref="roadInput" type="file" accept=".tar" class="hidden" @change="onRoad" />
        <el-button type="primary" :loading="roadLoading" @click="roadInput?.click()">选择 .tar 上传</el-button>
        <el-button type="danger" plain :loading="clearRoadLoading" class="ml8" @click="confirmClearScope('ROAD_NETWORK')">
          清除路网数据
        </el-button>
      </el-tab-pane>

      <el-tab-pane label="导入路段" name="section">
        <p class="hint">路线级 + 台账级 Shapefile 包 .tar。</p>
        <input ref="secInput" type="file" accept=".tar" class="hidden" @change="onSection" />
        <el-button type="primary" :loading="secLoading" @click="secInput?.click()">选择 .tar 上传</el-button>
        <el-button type="danger" plain :loading="clearSecLoading" class="ml8" @click="confirmClearScope('SECTION_PACKAGE')">
          清除路段数据
        </el-button>
      </el-tab-pane>

      <el-tab-pane label="导入病害" name="disease">
        <p class="hint">病害台账 .xlsx。</p>
        <input ref="disInput" type="file" accept=".xlsx" class="hidden" @change="onDisease" />
        <el-button type="primary" :loading="disLoading" @click="disInput?.click()">选择 .xlsx 上传</el-button>
        <el-button type="danger" plain :loading="clearDisLoading" class="ml8" @click="confirmClearScope('DISEASE_EXCEL')">
          清除病害数据
        </el-button>
      </el-tab-pane>
    </el-tabs>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AgentPageShell from '../agent/components/AgentPageShell.vue'
import {
  getDataMgmtProject,
  pageImportRecords,
  importRoadNetworkForProject,
  importSectionPackageForProject,
  importDiseaseExcelForProject,
  clearProjectData,
  type DataImportRecordVO,
  type DataMgmtClearScopeParam,
  type PageResult
} from '../../api/dataMgmt'

type ClearTabScope = Exclude<DataMgmtClearScopeParam, 'ALL'>

const route = useRoute()
const router = useRouter()
const projectId = ref(String(route.params.projectId || ''))
const projectName = ref('')

const activeTab = ref('records')

const recLoading = ref(false)
const recQuery = reactive({ pageNo: 1, pageSize: 20 })
const records = reactive<PageResult<DataImportRecordVO>>({ total: 0, pageNo: 1, pageSize: 20, records: [] })

const roadInput = ref<HTMLInputElement | null>(null)
const secInput = ref<HTMLInputElement | null>(null)
const disInput = ref<HTMLInputElement | null>(null)
const roadLoading = ref(false)
const secLoading = ref(false)
const disLoading = ref(false)
const clearRoadLoading = ref(false)
const clearSecLoading = ref(false)
const clearDisLoading = ref(false)

async function loadMeta() {
  if (!projectId.value) return
  try {
    const p = await getDataMgmtProject(projectId.value)
    projectName.value = p.name
  } catch {
    projectName.value = ''
  }
}

async function loadRecords() {
  if (!projectId.value) return
  recLoading.value = true
  try {
    const res = await pageImportRecords(projectId.value, { ...recQuery })
    records.total = res.total
    records.pageNo = res.pageNo
    records.pageSize = res.pageSize
    records.records = res.records || []
  } finally {
    recLoading.value = false
  }
}

function resetFile(el: HTMLInputElement | null) {
  if (el) el.value = ''
}

async function onRoad(ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  resetFile(input)
  if (!file) return
  roadLoading.value = true
  try {
    await importRoadNetworkForProject(projectId.value, file)
    ElMessage.success('路网导入成功')
    await loadRecords()
  } catch {
    /* 拦截器已提示 */
  } finally {
    roadLoading.value = false
  }
}

async function onSection(ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  resetFile(input)
  if (!file) return
  secLoading.value = true
  try {
    await importSectionPackageForProject(projectId.value, file)
    ElMessage.success('路段包导入成功')
    await loadRecords()
  } catch {
    /* */
  } finally {
    secLoading.value = false
  }
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
  return parts.length ? `已删除：${parts.join('，')}` : '没有需要清除的数据'
}

const clearConfirmText: Record<ClearTabScope, string> = {
  ROAD_NETWORK: '将物理删除本项目下已归属的路网（路线）数据，不影响路段与病害。是否继续？',
  SECTION_PACKAGE: '将物理删除本项目下已归属的路段数据，不影响路线与病害。是否继续？',
  DISEASE_EXCEL: '将物理删除本项目下已归属的病害台账数据，不影响路网与路段。是否继续？'
}

async function confirmClearScope(scope: ClearTabScope) {
  try {
    await ElMessageBox.confirm(clearConfirmText[scope], '清除数据', {
      type: 'warning',
      confirmButtonText: '清除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  const loading =
    scope === 'ROAD_NETWORK' ? clearRoadLoading : scope === 'SECTION_PACKAGE' ? clearSecLoading : clearDisLoading
  loading.value = true
  try {
    const r = await clearProjectData(projectId.value, scope)
    ElMessage.success(formatClearSummary(r))
  } catch {
    /* */
  } finally {
    loading.value = false
  }
}

async function onDisease(ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  resetFile(input)
  if (!file) return
  disLoading.value = true
  try {
    await importDiseaseExcelForProject(projectId.value, file)
    ElMessage.success('病害导入成功')
    await loadRecords()
  } catch {
    /* */
  } finally {
    disLoading.value = false
  }
}

watch(
  () => route.params.projectId,
  (v) => {
    projectId.value = String(v || '')
    loadMeta()
    loadRecords()
  }
)

watch(activeTab, (t) => {
  if (t === 'records') loadRecords()
})

onMounted(() => {
  loadMeta()
  loadRecords()
})
</script>

<style scoped>
.hint {
  color: #64748b;
  font-size: 13px;
  margin: 0 0 12px;
}
.hidden {
  display: none;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.ml8 {
  margin-left: 8px;
}
</style>
