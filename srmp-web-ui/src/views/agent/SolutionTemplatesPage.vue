<template>
  <AgentPageShell title="方案模板管理" description="管理 AI 方案生成模板，支持 {{变量}} 解析和从知识库文档登记为模板。">
    <div class="page-grid">
      <el-card class="left-card">
        <template #header>
          <div class="card-header">
            <span>模板列表</span>
            <el-button size="small" @click="loadTemplates">刷新</el-button>
          </div>
        </template>

        <el-form :inline="true" class="query-form">
          <el-form-item>
            <el-input v-model="query.keyword" clearable placeholder="搜索模板" />
          </el-form-item>
          <el-form-item>
            <el-select v-model="query.solutionType" clearable placeholder="方案类型" style="width: 180px">
              <el-option v-for="item in solutionTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-select v-model="query.originType" clearable placeholder="来源场景" style="width: 140px">
              <el-option v-for="item in originTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-select v-model="query.objectType" clearable placeholder="对象类型" style="width: 140px">
              <el-option v-for="item in objectTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-select v-model="query.status" clearable placeholder="状态" style="width: 110px">
              <el-option label="启用" value="ENABLED" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-select v-model="query.isDefault" clearable placeholder="默认" style="width: 110px">
              <el-option label="默认" :value="true" />
              <el-option label="非默认" :value="false" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadTemplates">查询</el-button>
          </el-form-item>
        </el-form>

        <el-empty v-if="templates.length === 0" description="暂无模板" />
        <div
          v-for="item in templates"
          :key="item.id"
          :class="['template-item', selected?.id === item.id ? 'active' : '']"
          @click="selectTemplate(item)"
        >
          <div class="row">
            <strong>{{ item.template_name }}</strong>
            <div class="tag-row">
              <el-tag v-if="item.is_default" size="small" type="success">默认</el-tag>
              <el-tag size="small" :type="item.status === 'ENABLED' ? 'success' : 'info'">{{ item.status }}</el-tag>
            </div>
          </div>
          <p>{{ item.template_code }}</p>
          <div class="meta">
            {{ formatTemplateScope(item) }}
          </div>
          <div class="row-actions">
            <el-button size="small" plain @click.stop="matchTemplate(item)">匹配</el-button>
            <el-button size="small" plain @click.stop="previewTemplate(item)">验证</el-button>
            <el-button size="small" plain @click.stop="setAsDefault(item)">默认</el-button>
            <el-button size="small" plain @click.stop="toggleStatus(item)">
              {{ item.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
          </div>
        </div>
      </el-card>

      <el-card class="middle-card">
        <template #header>新增模板</template>
        <el-form label-width="90px">
          <el-form-item label="模板名称">
            <el-input v-model="form.templateName" placeholder="例如：技术状况评定报告模板" />
          </el-form-item>
          <el-form-item label="模板编码">
            <el-input v-model="form.templateCode" placeholder="可选，为空后端自动生成" />
          </el-form-item>
          <el-form-item label="方案类型">
            <el-select v-model="form.solutionType" placeholder="选择类型">
              <el-option v-for="item in solutionTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="来源场景">
            <el-select v-model="form.originType">
              <el-option v-for="item in originTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="对象类型">
            <el-select v-model="form.objectType">
              <el-option v-for="item in objectTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="来源类型">
            <el-select v-model="form.sourceType">
              <el-option label="LOCAL" value="LOCAL" />
              <el-option label="OUTLINE" value="OUTLINE" />
              <el-option label="SYSTEM" value="SYSTEM" />
            </el-select>
          </el-form-item>
          <el-form-item label="版本">
            <el-input v-model="form.version" placeholder="v1" />
          </el-form-item>
          <el-form-item label="优先级">
            <el-input-number v-model="form.priority" :min="0" :max="999" />
          </el-form-item>
          <el-form-item label="默认模板">
            <el-switch v-model="form.isDefault" />
          </el-form-item>
          <el-form-item label="变更说明">
            <el-input v-model="form.changeNote" placeholder="可选，记录模板来源或改动原因" />
          </el-form-item>
          <el-form-item label="模板内容">
            <el-input v-model="form.content" type="textarea" :rows="18" placeholder="输入包含 {{变量}} 的 Markdown 模板" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="createTemplate">保存模板</el-button>
            <el-button @click="fillDemo">填充示例</el-button>
          </el-form-item>
        </el-form>

        <el-divider />

        <h3>从知识库文档登记为模板</h3>
        <el-form label-width="120px">
          <el-form-item label="知识库文档ID">
            <el-input v-model="importForm.knowledgeDocumentId" placeholder="knowledge_document.id" />
          </el-form-item>
          <el-form-item label="方案类型">
            <el-select v-model="importForm.solutionType">
              <el-option v-for="item in solutionTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button :loading="importing" @click="importTemplate">登记为模板</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card class="right-card">
        <template #header>模板详情与变量</template>
        <el-empty v-if="!selectedDetail" description="请选择模板" />
        <template v-else>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="名称">{{ selectedDetail.template_name }}</el-descriptions-item>
            <el-descriptions-item label="编码">{{ selectedDetail.template_code }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ selectedDetail.solution_type }}</el-descriptions-item>
            <el-descriptions-item label="版本">{{ selectedDetail.current_version }}</el-descriptions-item>
            <el-descriptions-item label="来源">{{ selectedDetail.source_type }}</el-descriptions-item>
            <el-descriptions-item label="来源场景">{{ selectedDetail.origin_type || '-' }}</el-descriptions-item>
            <el-descriptions-item label="对象类型">{{ selectedDetail.object_type || '-' }}</el-descriptions-item>
            <el-descriptions-item label="优先级">{{ selectedDetail.priority ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="默认模板">{{ selectedDetail.is_default ? '是' : '否' }}</el-descriptions-item>
          </el-descriptions>

          <h3>变量</h3>
          <div class="variable-list">
            <el-tag v-for="item in variables" :key="item">{{ item }}</el-tag>
          </div>

          <h3>内容</h3>
          <pre>{{ selectedDetail.content }}</pre>

          <h3>版本记录</h3>
          <div v-for="item in versions" :key="item.id" class="version-item">
            <strong>{{ item.version }}</strong>
            <p>{{ item.created_at }}</p>
            <div>{{ item.content_hash }}</div>
          </div>

          <h3>创建新版本</h3>
          <el-form label-width="76px">
            <el-form-item label="版本号">
              <el-input v-model="versionForm.version" placeholder="例如：v2" />
            </el-form-item>
            <el-form-item label="说明">
              <el-input v-model="versionForm.changeNote" placeholder="本次改动说明" />
            </el-form-item>
            <el-form-item label="内容">
              <el-input v-model="versionForm.content" type="textarea" :rows="8" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" plain :loading="versionSaving" @click="createTemplateVersion">保存新版本</el-button>
              <el-button @click="resetVersionForm">使用当前内容</el-button>
            </el-form-item>
          </el-form>
        </template>
      </el-card>
    </div>
    <TemplateRenderPreviewDialog v-model:visible="previewVisible" :result="previewResult" />
  </AgentPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import TemplateRenderPreviewDialog from './components/TemplateRenderPreviewDialog.vue'
import {
  createSolutionTemplateVersion,
  createSolutionTemplate,
  getSolutionTemplate,
  getSolutionTemplateVersions,
  importTemplateFromKnowledge,
  listSolutionTemplates,
  matchSolutionTemplate,
  renderSolutionTemplatePreview,
  setDefaultSolutionTemplate,
  updateSolutionTemplateStatus
} from '../../api/solution'

const solutionTypeOptions = [
  { label: '技术状况评定报告', value: 'ROAD_ASSESSMENT_REPORT' },
  { label: '养护建议方案', value: 'MAINTENANCE_SUGGESTION' },
  { label: '病害治理方案', value: 'DISEASE_TREATMENT_PLAN' },
  { label: '低分路段分析', value: 'LOW_SCORE_SECTION_ANALYSIS' },
  { label: '区域养护建议', value: 'REGION_MAINTENANCE_SUGGESTION' }
]

const originTypeOptions = [
  { label: '路线报告', value: 'ROUTE_REPORT' },
  { label: '地图对象', value: 'MAP_OBJECT' },
  { label: '框选区域', value: 'MAP_REGION' }
]

const objectTypeOptions = [
  { label: '路线', value: 'ROAD_ROUTE' },
  { label: '病害', value: 'DISEASE' },
  { label: '评定结果', value: 'ASSESSMENT_RESULT' },
  { label: '路段', value: 'ROAD_SECTION' },
  { label: '框选区域', value: 'MAP_REGION' }
]

const query = reactive({
  keyword: '',
  solutionType: '',
  originType: '',
  objectType: '',
  status: 'ENABLED',
  isDefault: undefined as boolean | undefined,
  limit: 50
})
const templates = ref<Record<string, any>[]>([])
const selected = ref<Record<string, any> | null>(null)
const selectedDetail = ref<Record<string, any> | null>(null)
const versions = ref<Record<string, any>[]>([])
const saving = ref(false)
const importing = ref(false)
const versionSaving = ref(false)
const previewVisible = ref(false)
const previewResult = ref<Record<string, any> | null>(null)

const form = reactive({
  templateName: '',
  templateCode: '',
  solutionType: 'ROAD_ASSESSMENT_REPORT',
  originType: 'ROUTE_REPORT',
  objectType: 'ROAD_ROUTE',
  sourceType: 'LOCAL',
  version: 'v1',
  priority: 0,
  isDefault: false,
  changeNote: '',
  content: ''
})

const versionForm = reactive({
  version: '',
  changeNote: '',
  content: ''
})

const importForm = reactive({
  knowledgeDocumentId: '',
  solutionType: 'ROAD_ASSESSMENT_REPORT'
})

const variables = computed(() => {
  const value = selectedDetail.value?.variables
  if (!value) return []
  if (Array.isArray(value)) return value
  try {
    return JSON.parse(value)
  } catch {
    return []
  }
})

onMounted(loadTemplates)

async function loadTemplates() {
  templates.value = await listSolutionTemplates(query)
}

async function selectTemplate(item: Record<string, any>) {
  selected.value = item
  selectedDetail.value = await getSolutionTemplate(item.id)
  versions.value = await getSolutionTemplateVersions(item.id)
  resetVersionForm()
}

function fillDemo() {
  form.templateName = '技术状况评定报告模板'
  form.templateCode = 'road_assessment_report_demo'
  form.solutionType = 'ROAD_ASSESSMENT_REPORT'
  form.originType = 'ROUTE_REPORT'
  form.objectType = 'ROAD_ROUTE'
  form.sourceType = 'LOCAL'
  form.version = 'v1'
  form.priority = 0
  form.isDefault = false
  form.changeNote = '内置演示模板'
  form.content = `# {{routeCode}} {{year}} 年技术状况评定报告草稿

## 一、路线概况
{{routeSummary}}

## 二、评定结果
{{assessmentSummary}}

## 三、主要病害
{{diseaseSummary}}

## 四、低分路段
{{lowScoreSections}}

## 五、问题分析
{{problemAnalysis}}

## 六、养护建议
{{maintenanceSuggestion}}

## 七、风险提示
{{riskNotice}}`
}

async function createTemplate() {
  if (!form.templateName || !form.solutionType || !form.content) {
    ElMessage.warning('请填写模板名称、方案类型和模板内容')
    return
  }
  saving.value = true
  try {
    const data = await createSolutionTemplate({
      ...form,
      category: 'SOLUTION_TEMPLATE'
    })
    ElMessage.success('模板已保存')
    await loadTemplates()
    await selectTemplate(data)
  } finally {
    saving.value = false
  }
}

async function importTemplate() {
  if (!importForm.knowledgeDocumentId) {
    ElMessage.warning('请填写知识库文档 ID')
    return
  }
  importing.value = true
  try {
    const data = await importTemplateFromKnowledge(importForm)
    ElMessage.success('已登记为方案模板')
    await loadTemplates()
    await selectTemplate(data)
  } finally {
    importing.value = false
  }
}

async function matchTemplate(item: Record<string, any>) {
  const sample = buildPreviewSample(item)
  const templateMeta = await matchSolutionTemplate({
    originType: sample.originType,
    objectType: sample.objectType,
    solutionType: sample.solutionType,
    templateId: item.id,
    templateCode: item.template_code
  })
  previewResult.value = {
    templateMeta,
    variables: sample.variables,
    missingVariables: templateMeta.missingVariables || templateMeta.missing_variables || [],
    unusedVariables: templateMeta.unusedVariables || templateMeta.unused_variables || []
  }
  previewVisible.value = true
}

async function previewTemplate(item: Record<string, any>) {
  previewResult.value = await renderSolutionTemplatePreview(item.id, buildPreviewSample(item))
  previewVisible.value = true
}

async function setAsDefault(item: Record<string, any>) {
  await setDefaultSolutionTemplate(item.id)
  ElMessage.success('已设为默认模板')
  await loadTemplates()
  if (selected.value?.id === item.id) {
    await selectTemplate(item)
  }
}

async function toggleStatus(item: Record<string, any>) {
  const next = item.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  await updateSolutionTemplateStatus(item.id, next)
  ElMessage.success(next === 'ENABLED' ? '模板已启用' : '模板已停用')
  await loadTemplates()
  if (selected.value?.id === item.id) {
    await selectTemplate({ ...item, status: next })
  }
}

async function createTemplateVersion() {
  if (!selectedDetail.value?.id) return
  if (!versionForm.version || !versionForm.content) {
    ElMessage.warning('请填写版本号和模板内容')
    return
  }
  versionSaving.value = true
  try {
    const data = await createSolutionTemplateVersion(selectedDetail.value.id, { ...versionForm })
    ElMessage.success('模板新版本已保存')
    await loadTemplates()
    await selectTemplate(data)
  } finally {
    versionSaving.value = false
  }
}

function resetVersionForm() {
  const detail = selectedDetail.value || {}
  versionForm.version = suggestNextVersion(String(detail.current_version || 'v1'))
  versionForm.changeNote = ''
  versionForm.content = String(detail.content || '')
}

function buildPreviewSample(item: Record<string, any>) {
  const originType = String(item.origin_type || item.originType || form.originType || 'ROUTE_REPORT')
  const objectType = String(item.object_type || item.objectType || form.objectType || 'ROAD_ROUTE')
  const solutionType = String(item.solution_type || item.solutionType || form.solutionType || 'ROAD_ASSESSMENT_REPORT')
  return {
    originType,
    objectType,
    solutionType,
    variables: buildPreviewVariables(originType, objectType, solutionType)
  }
}

function buildPreviewVariables(originType: string, objectType: string, solutionType: string) {
  const common = {
    routeCode: 'G210',
    routeName: 'G210 贵州段',
    year: 2026,
    title: 'G210 养护建议方案',
    solutionType,
    originType,
    objectType,
    maintenanceSuggestion: '优先处治重度病害点位，同步安排预防性养护和复核检测。',
    riskNotice: '样例数据仅用于模板变量验证，实际方案以业务分析结果为准。'
  }

  if (originType === 'MAP_REGION' || objectType === 'MAP_REGION') {
    return {
      ...common,
      areaKm2: 262.55,
      routeCount: 1,
      sectionCount: 4,
      unitCount: 23,
      diseaseCount: 59,
      heavyDiseaseCount: 12,
      mediumDiseaseCount: 26,
      avgMqi: 79.65,
      avgPqi: 78.6,
      avgPci: 77.05,
      hotspotSummary: 'G210 K97+474-K112+386 病害密集，重度病害 12 处。',
      regionSummary: '框选区域覆盖 4 个路段、23 个评定单元，MQI 均值 79.65。'
    }
  }

  if (objectType === 'DISEASE') {
    return {
      ...common,
      objectId: 'disease-demo-001',
      diseaseName: '裂缝',
      severity: 'HEAVY',
      quantity: 127.52,
      measureUnit: 'm',
      stakeRange: 'K100+000-K100+180',
      treatmentAdvice: '裂缝清缝灌缝后局部铣刨重铺，雨后复核排水条件。'
    }
  }

  if (objectType === 'ASSESSMENT_RESULT') {
    return {
      ...common,
      unitCode: 'G210-2026-U023',
      mqi: 72.4,
      pqi: 71.8,
      pci: 69.2,
      grade: '次',
      problemAnalysis: 'PCI 偏低，裂缝和坑槽对单元评分影响较大。'
    }
  }

  return {
    ...common,
    routeSummary: 'G210 本年度纳入评定 128.4 公里，覆盖 42 个路段。',
    assessmentSummary: 'MQI 平均 82.3，优良率 76.2%，差次率 4.3%。',
    diseaseSummary: '主要病害为裂缝、坑槽和沉陷，重度病害集中在局部连续路段。',
    lowScoreSections: 'K97+474-K112+386 为重点复核区间。',
    problemAnalysis: '低分区间与重载交通、排水不畅和既有裂缝发展相关。'
  }
}

function formatTemplateScope(item: Record<string, any>) {
  return [
    item.origin_type || '-',
    item.object_type || '-',
    item.solution_type || '-',
    item.current_version || '-',
    `P${item.priority ?? 0}`
  ].join(' / ')
}

function suggestNextVersion(version: string) {
  const matched = /^v(\d+)$/i.exec(version)
  if (!matched) return ''
  return `v${Number(matched[1]) + 1}`
}
</script>

<style scoped>
.page-grid {
  display: grid;
  grid-template-columns: 360px minmax(460px, 1fr) 420px;
  gap: 16px;
}

.left-card,
.middle-card,
.right-card {
  min-height: calc(100vh - 130px);
}

.card-header,
.row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.tag-row,
.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.row-actions {
  margin-top: 10px;
}

.query-form {
  margin-bottom: 12px;
}

.template-item,
.version-item {
  padding: 12px;
  border-radius: 10px;
  background: #f8fafc;
  margin-bottom: 10px;
  cursor: pointer;
}

.template-item.active {
  background: #dbeafe;
}

.template-item p,
.version-item p {
  margin: 4px 0;
  color: #64748b;
  word-break: break-all;
}

.meta {
  color: #64748b;
  font-size: 12px;
}

h3 {
  margin: 18px 0 10px;
  font-size: 15px;
}

.variable-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

pre {
  white-space: pre-wrap;
  background: #0f172a;
  color: #e2e8f0;
  padding: 12px;
  border-radius: 10px;
  max-height: 380px;
  overflow: auto;
}
</style>
