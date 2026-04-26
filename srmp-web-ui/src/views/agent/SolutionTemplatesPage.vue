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
              <el-option label="技术状况评定报告" value="ROAD_ASSESSMENT_REPORT" />
              <el-option label="养护建议方案" value="MAINTENANCE_SUGGESTION" />
              <el-option label="病害治理方案" value="DISEASE_TREATMENT_PLAN" />
              <el-option label="低分路段分析" value="LOW_SCORE_SECTION_ANALYSIS" />
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
            <el-tag size="small">{{ item.status }}</el-tag>
          </div>
          <p>{{ item.template_code }}</p>
          <div class="meta">
            {{ item.solution_type }} / {{ item.current_version }} / {{ item.source_type }}
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
              <el-option label="技术状况评定报告" value="ROAD_ASSESSMENT_REPORT" />
              <el-option label="养护建议方案" value="MAINTENANCE_SUGGESTION" />
              <el-option label="病害治理方案" value="DISEASE_TREATMENT_PLAN" />
              <el-option label="低分路段分析" value="LOW_SCORE_SECTION_ANALYSIS" />
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
              <el-option label="技术状况评定报告" value="ROAD_ASSESSMENT_REPORT" />
              <el-option label="养护建议方案" value="MAINTENANCE_SUGGESTION" />
              <el-option label="病害治理方案" value="DISEASE_TREATMENT_PLAN" />
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
        </template>
      </el-card>
    </div>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import {
  createSolutionTemplate,
  getSolutionTemplate,
  getSolutionTemplateVersions,
  importTemplateFromKnowledge,
  listSolutionTemplates
} from '../../api/solution'

const query = reactive({ keyword: '', solutionType: '', status: 'ENABLED', limit: 50 })
const templates = ref<Record<string, any>[]>([])
const selected = ref<Record<string, any> | null>(null)
const selectedDetail = ref<Record<string, any> | null>(null)
const versions = ref<Record<string, any>[]>([])
const saving = ref(false)
const importing = ref(false)

const form = reactive({
  templateName: '',
  templateCode: '',
  solutionType: 'ROAD_ASSESSMENT_REPORT',
  sourceType: 'LOCAL',
  version: 'v1',
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
}

function fillDemo() {
  form.templateName = '技术状况评定报告模板'
  form.templateCode = 'road_assessment_report_demo'
  form.solutionType = 'ROAD_ASSESSMENT_REPORT'
  form.sourceType = 'LOCAL'
  form.version = 'v1'
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
