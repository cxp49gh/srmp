<template>
  <AgentPageShell title="AI 方案生成" description="选择方案模板，结合业务数据和知识库生成 Markdown 方案草稿。">
    <div class="page-grid">
      <el-card class="config-card">
        <template #header>生成配置</template>

        <el-form label-width="100px">
          <el-form-item label="方案类型">
            <el-select v-model="form.solutionType" @change="loadTemplates">
              <el-option label="路线技术状况报告" value="ROUTE_REPORT" />
              <el-option label="路段养护计划" value="SECTION_PLAN" />
              <el-option label="评定结果养护建议" value="EVALUATION_UNIT_ADVICE" />
              <el-option label="低分评定处置建议" value="LOW_SCORE_TREATMENT" />
              <el-option label="病害复核意见" value="DISEASE_REVIEW" />
              <el-option label="病害处置建议" value="DISEASE_TREATMENT" />
              <el-option label="区域养护建议" value="REGION_MAINTENANCE_SUGGESTION" />
            </el-select>
          </el-form-item>

          <el-form-item label="路线编号">
            <el-input v-model="form.routeCode" placeholder="G210" />
          </el-form-item>

          <el-form-item label="年度">
            <el-input-number v-model="form.year" :min="2000" :max="2100" />
          </el-form-item>

          <el-form-item label="模板">
            <el-select v-model="form.templateId" filterable clearable placeholder="选择模板；为空则后端按类型取最新">
              <el-option
                v-for="item in templates"
                :key="item.id"
                :label="`${item.template_name} / ${item.current_version}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="能力开关">
            <div class="switch-list">
              <el-checkbox v-model="form.options.useBusinessData">业务数据</el-checkbox>
              <el-checkbox v-model="form.options.useKnowledge">知识库</el-checkbox>
              <el-checkbox v-model="form.options.useOutline">Outline</el-checkbox>
            </div>
          </el-form-item>

          <el-form-item label="TopK">
            <el-input-number v-model="form.options.topK" :min="1" :max="20" />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="generating" @click="doGenerate">生成方案</el-button>
            <el-button @click="fillDemo">示例参数</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card class="result-card">
        <template #header>
          <div class="card-header">
            <span>方案草稿</span>
            <el-button v-if="result?.result_content" size="small" @click="copyResult">复制 Markdown</el-button>
          </div>
        </template>

        <el-empty v-if="!result" description="暂无生成结果" />
        <template v-else>
          <el-descriptions :column="2" border size="small" class="mb">
            <el-descriptions-item label="任务ID">{{ result.id }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ result.status }}</el-descriptions-item>
            <el-descriptions-item label="标题">{{ result.title }}</el-descriptions-item>
            <el-descriptions-item label="模板版本">{{ result.template_version }}</el-descriptions-item>
          </el-descriptions>
          <pre>{{ result.result_content }}</pre>
        </template>
      </el-card>

      <el-card class="source-card">
        <template #header>引用来源</template>
        <AiSourceList
          :sources="normalizedSources"
          :question="lastQuestion"
          :business-context="businessContext"
        />
      </el-card>
    </div>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import AiSourceList from './components/AiSourceList.vue'
import { generateSolution, getSolutionTaskSources, listSolutionTemplates } from '../../api/solution'

const templates = ref<Record<string, any>[]>([])
const result = ref<Record<string, any> | null>(null)
const sources = ref<Record<string, any>[]>([])
const lastQuestion = ref('方案生成')
const generating = ref(false)

const normalizedSources = computed(() =>
  sources.value.map((item) => ({
    ...item,
    title: item.source_title || item.title,
    content: item.content_excerpt || item.content,
    sourceType: item.source_type || item.sourceType,
    url: item.source_url || item.url,
    metadata: item.metadata || {}
  }))
)

const businessContext = computed(() => ({
  routeCode: form.routeCode,
  year: form.year,
  solutionType: form.solutionType,
  scope: 'SOLUTION_GENERATE'
}))

const form = reactive({
  solutionType: 'ROUTE_REPORT',
  routeCode: 'G210',
  year: 2026,
  templateId: '',
  options: {
    useBusinessData: true,
    useKnowledge: true,
    useOutline: false,
    topK: 5
  }
})

onMounted(loadTemplates)

function fillDemo() {
  form.solutionType = 'ROUTE_REPORT'
  form.routeCode = 'G210'
  form.year = 2026
  form.options.useBusinessData = true
  form.options.useKnowledge = true
  form.options.useOutline = false
  form.options.topK = 5
}

async function loadTemplates() {
  templates.value = await listSolutionTemplates({
    solutionType: form.solutionType,
    status: 'ENABLED',
    limit: 100
  })
}

async function doGenerate() {
  generating.value = true
  try {
    result.value = await generateSolution({
      solutionType: form.solutionType,
      routeCode: form.routeCode,
      year: form.year,
      templateId: form.templateId || undefined,
      options: form.options
    })
    sources.value = result.value?.sources || []
    if (result.value?.id && sources.value.length === 0) {
      sources.value = await getSolutionTaskSources(result.value.id)
    }
    ElMessage.success('方案生成完成')
  } finally {
    generating.value = false
  }
}

async function copyResult() {
  await navigator.clipboard.writeText(result.value?.result_content || '')
  ElMessage.success('已复制')
}
</script>

<style scoped>
.page-grid {
  display: grid;
  grid-template-columns: 330px minmax(520px, 1fr) 360px;
  gap: 16px;
}

.config-card,
.result-card,
.source-card {
  min-height: calc(100vh - 130px);
}

.switch-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.card-header,
.source-title {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.mb {
  margin-bottom: 12px;
}

pre {
  white-space: pre-wrap;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 12px;
  padding: 14px;
  line-height: 1.6;
  max-height: calc(100vh - 250px);
  overflow: auto;
}

.source-item {
  padding: 12px;
  background: #f8fafc;
  border-radius: 10px;
  margin-bottom: 10px;
  font-size: 13px;
}

.source-item p {
  color: #64748b;
  word-break: break-all;
  margin: 4px 0;
}
</style>
