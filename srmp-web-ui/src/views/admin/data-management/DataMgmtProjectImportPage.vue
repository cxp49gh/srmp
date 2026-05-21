<template>
  <AgentPageShell
    :title="`项目导入 — ${projectName || projectId}`"
    description="单项目数据工作台：须先导入路网，再导入路段与病害。"
  >
    <template #actions>
      <el-button @click="router.push('/admin/data-management/projects')">返回项目总览</el-button>
      <el-button @click="router.push('/admin/data-management/import')">切换项目</el-button>
      <router-link class="el-button" to="/admin/data-management/templates">模板与规范</router-link>
    </template>

    <el-card v-loading="summaryLoading" shadow="never" class="mb">
      <div class="summary-grid">
        <div>
          <span class="label">项目状态</span>
          <el-tag size="small" :type="projectStatusTagType(summary?.projectStatus)">
            {{ projectStatusLabel(summary?.projectStatus) }}
          </el-tag>
        </div>
        <div><span class="label">路线数</span><strong>{{ summary?.routeCount ?? 0 }}</strong></div>
        <div><span class="label">路段数</span><strong>{{ summary?.sectionCount ?? 0 }}</strong></div>
        <div><span class="label">病害数</span><strong>{{ summary?.diseaseCount ?? 0 }}</strong></div>
        <div><span class="label">最近成功导入</span>{{ summary?.lastSuccessImportTime || '-' }}</div>
      </div>
    </el-card>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="import-block">
          <template #header>导入路网</template>
          <p class="hint">.tar 内含唯一一组 Shapefile</p>
          <p class="last-result">最近：{{ formatLast(lastByType.ROAD_NETWORK) }}</p>
          <input ref="roadInput" type="file" accept=".tar" class="hidden" @change="onRoad" />
          <el-button type="primary" :loading="roadLoading" @click="roadInput?.click()">选择 .tar 上传</el-button>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="import-block">
          <template #header>导入路段</template>
          <p class="hint">路线级 + 台账级 Shapefile 包 .tar</p>
          <p class="last-result">最近：{{ formatLast(lastByType.SECTION_PACKAGE) }}</p>
          <input ref="secInput" type="file" accept=".tar" class="hidden" @change="onSection" />
          <el-button type="primary" :loading="secLoading" :disabled="!roadNetworkReady" @click="secInput?.click()">
            {{ roadNetworkReady ? '选择 .tar 上传' : '需先导入路网' }}
          </el-button>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="import-block">
          <template #header>导入病害</template>
          <p class="hint">病害台账 .xlsx</p>
          <p class="last-result">最近：{{ formatLast(lastByType.DISEASE_EXCEL) }}</p>
          <input ref="disInput" type="file" accept=".xlsx" class="hidden" @change="onDisease" />
          <el-button type="primary" :loading="disLoading" :disabled="!roadNetworkReady" @click="disInput?.click()">
            {{ roadNetworkReady ? '选择 .xlsx 上传' : '需先导入路网' }}
          </el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="mt">
      <template #header>本项目导入记录</template>
      <el-table :data="records.records" v-loading="recLoading" size="small" stripe max-height="360">
        <el-table-column prop="startedAt" label="开始时间" width="170" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ importTypeLabel(row.importType) }}</template>
        </el-table-column>
        <el-table-column prop="fileName" label="文件名" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="importStatusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="resultSummary" label="结果摘要" min-width="180" show-overflow-tooltip />
      </el-table>
    </el-card>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AgentPageShell from '../../agent/components/AgentPageShell.vue'
import {
  getDataMgmtProject,
  getProjectSummary,
  importDiseaseExcelForProject,
  importRoadNetworkForProject,
  importSectionPackageForProject,
  pageImportRecords,
  type DataImportRecordVO,
  type DataMgmtProjectSummaryVO,
  type PageResult
} from '../../../api/dataMgmt'
import { importStatusTagType, importTypeLabel, projectStatusLabel, projectStatusTagType } from '../../../utils/dataMgmtHelpers'

const route = useRoute()
const router = useRouter()
const projectId = ref(String(route.params.projectId || ''))
const projectName = ref('')
const summary = ref<DataMgmtProjectSummaryVO | null>(null)
const summaryLoading = ref(false)
const roadLoading = ref(false)
const secLoading = ref(false)
const disLoading = ref(false)
const recLoading = ref(false)
const records = reactive<PageResult<DataImportRecordVO>>({ total: 0, pageNo: 1, pageSize: 20, records: [] })
const roadInput = ref<HTMLInputElement | null>(null)
const secInput = ref<HTMLInputElement | null>(null)
const disInput = ref<HTMLInputElement | null>(null)
const lastByType = reactive<Record<string, DataImportRecordVO | undefined>>({})

const roadNetworkReady = computed(() => (summary.value?.routeCount ?? 0) > 0)

function formatLast(r?: DataImportRecordVO) {
  if (!r) return '暂无'
  return `${r.status} · ${r.fileName || '-'} · ${r.startedAt || ''}`
}

async function refreshSummary() {
  if (!projectId.value) return
  summaryLoading.value = true
  try {
    const [s, p] = await Promise.all([getProjectSummary(projectId.value), getDataMgmtProject(projectId.value)])
    summary.value = s
    projectName.value = p.name
  } finally {
    summaryLoading.value = false
  }
}

async function loadRecords() {
  if (!projectId.value) return
  recLoading.value = true
  try {
    const res = await pageImportRecords(projectId.value, { pageNo: 1, pageSize: 50 })
    records.records = res.records || []
    lastByType.ROAD_NETWORK = undefined
    lastByType.SECTION_PACKAGE = undefined
    lastByType.DISEASE_EXCEL = undefined
    for (const r of records.records) {
      if (!lastByType[r.importType]) lastByType[r.importType] = r
    }
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
    await Promise.all([refreshSummary(), loadRecords()])
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
    await Promise.all([refreshSummary(), loadRecords()])
  } finally {
    secLoading.value = false
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
    await Promise.all([refreshSummary(), loadRecords()])
  } finally {
    disLoading.value = false
  }
}

watch(
  () => route.params.projectId,
  (v) => {
    projectId.value = String(v || '')
    refreshSummary()
    loadRecords()
  }
)

onMounted(() => {
  if (!projectId.value) {
    router.replace('/admin/data-management/import')
    return
  }
  refreshSummary()
  loadRecords()
})
</script>

<style scoped>
.mb { margin-bottom: 16px; }
.mt { margin-top: 16px; }
.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}
.summary-grid .label {
  display: block;
  color: #64748b;
  font-size: 12px;
  margin-bottom: 4px;
}
.hint { color: #64748b; font-size: 13px; margin: 0 0 8px; }
.last-result { font-size: 12px; color: #475569; margin: 0 0 12px; }
.hidden { display: none; }
.import-block { min-height: 180px; }
</style>
