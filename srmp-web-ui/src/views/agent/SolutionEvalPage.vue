<template>
  <AgentPageShell
    title="方案回归评测"
    description="按一张图六类方案场景检查模板命中、业务证据、地图关联和场景匹配，帮助管理员快速发现生成链路退化。"
  >
    <template #actions>
      <el-button @click="loadDefaultCases">加载默认用例</el-button>
      <el-button type="primary" :loading="running" @click="runEval">执行评测</el-button>
    </template>

    <el-card shadow="never" class="block-card">
      <template #header>
        <div class="card-header">
          <span>评测参数</span>
          <el-tag type="info" effect="plain">最近任务 / 内置样例</el-tag>
        </div>
      </template>
      <el-form :inline="true" class="eval-form">
        <el-form-item label="优先使用最近任务">
          <el-switch v-model="useLatestTask" />
        </el-form-item>
        <el-form-item label="用例数">
          <el-tag>{{ cases.length }}</el-tag>
        </el-form-item>
      </el-form>
      <div class="case-chips">
        <el-tag v-for="item in scenarioBadges" :key="item.id" effect="plain">
          {{ item.name }} · {{ item.solutionType }}
        </el-tag>
      </div>
    </el-card>

    <el-card v-if="summary" shadow="never" class="block-card">
      <template #header>评测结果</template>
      <el-row :gutter="14">
        <el-col :xs="12" :sm="6">
          <div class="metric">
            <span>总数</span>
            <strong>{{ summary.total }}</strong>
          </div>
        </el-col>
        <el-col :xs="12" :sm="6">
          <div class="metric">
            <span>通过</span>
            <strong>{{ summary.passed }}</strong>
          </div>
        </el-col>
        <el-col :xs="12" :sm="6">
          <div class="metric">
            <span>失败</span>
            <strong>{{ summary.failed }}</strong>
          </div>
        </el-col>
        <el-col :xs="12" :sm="6">
          <div class="metric">
            <span>通过率</span>
            <strong>{{ percent(summary.passRate) }}</strong>
          </div>
        </el-col>
      </el-row>
      <div class="source-summary">
        <span>最近任务 {{ sourceModeSummary.savedTask || 0 }}</span>
        <span>内置样例 {{ sourceModeSummary.fixture || 0 }}</span>
        <span v-if="failedCaseIds.length">失败用例：{{ failedCaseIds.join('，') }}</span>
      </div>
    </el-card>

    <el-card v-if="results.length" shadow="never" class="block-card">
      <template #header>质量快照</template>
      <el-table :data="results" row-key="id" border>
        <el-table-column label="场景" min-width="220">
          <template #default="{ row }">
            <div class="case-title">{{ row.name || row.id }}</div>
            <div class="muted">{{ row.solutionType }} · {{ row.objectType || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="150">
          <template #default="{ row }">
            <el-tag :type="row.sourceMode === 'SAVED_TASK' ? 'success' : 'info'" effect="plain">
              {{ row.sourceMode === 'SAVED_TASK' ? '最近任务' : '内置样例' }}
            </el-tag>
            <div v-if="row.taskId" class="muted id-line">{{ row.taskId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.passed ? 'success' : 'danger'">{{ row.passed ? '通过' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="评分" width="100">
          <template #default="{ row }">
            <strong>{{ row.score }}</strong>
            <span class="muted"> / {{ row.level || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="维度" min-width="360">
          <template #default="{ row }">
            <div class="dimension-grid">
              <span
                v-for="dimension in displayDimensions(row)"
                :key="dimension.code"
                class="dimension-chip"
                :class="dimension.level"
              >
                {{ dimensionLabel(dimension.code) }}：{{ dimension.level || '-' }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="来源类型" min-width="180">
          <template #default="{ row }">
            <div class="source-types">{{ (row.sourceTypes || []).join(' / ') || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="300">
          <template #default="{ row }">
            <div v-if="row.errors?.length" class="error-list">
              <div v-for="error in row.errors" :key="error">{{ error }}</div>
            </div>
            <span v-else class="muted">暂无阻断问题</span>
            <div v-if="row.fallbackReason" class="muted fallback">{{ row.fallbackReason }}</div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="block-card">
      <template #header>评测用例 JSON</template>
      <el-input v-model="casesJson" type="textarea" :autosize="{ minRows: 8, maxRows: 18 }" />
    </el-card>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import {
  getDefaultSolutionEvalCases,
  runSolutionEval,
  type AiSolutionEvalCase,
  type AiSolutionEvalResponse
} from '../../api/solution'

const useLatestTask = ref(true)
const running = ref(false)
const cases = ref<AiSolutionEvalCase[]>([])
const casesJson = ref('[]')
const summary = ref<AiSolutionEvalResponse | null>(null)
const results = ref<Record<string, any>[]>([])

const defaultScenarioBadges: AiSolutionEvalCase[] = [
  { id: 'route-report', name: '路线技术状况报告', solutionType: 'ROUTE_REPORT' },
  { id: 'section-plan', name: '路段养护计划', solutionType: 'SECTION_PLAN' },
  { id: 'evaluation-advice', name: '评定结果养护建议', solutionType: 'EVALUATION_UNIT_ADVICE' },
  { id: 'low-score-treatment', name: '低分评定处置建议', solutionType: 'LOW_SCORE_TREATMENT' },
  { id: 'disease-review', name: '病害复核意见', solutionType: 'DISEASE_REVIEW' },
  { id: 'disease-treatment', name: '病害处置建议', solutionType: 'DISEASE_TREATMENT' },
  { id: 'region-maintenance', name: '区域养护建议', solutionType: 'REGION_MAINTENANCE_SUGGESTION' }
]

const scenarioBadges = computed(() => (cases.value.length ? cases.value : defaultScenarioBadges))
const sourceModeSummary = computed(() => summary.value?.sourceModeSummary || {})
const failedCaseIds = computed(() => summary.value?.failedCaseIds || [])

const dimensionLabels: Record<string, string> = {
  template: '模板命中',
  businessEvidence: '业务证据',
  mapBinding: '地图关联',
  llm: '大模型',
  scenario: '场景匹配'
}

async function loadDefaultCases() {
  const data: any = await getDefaultSolutionEvalCases()
  const value = data?.data || data || []
  cases.value = value
  casesJson.value = JSON.stringify(value, null, 2)
}

async function runEval() {
  running.value = true
  try {
    const parsedCases = JSON.parse(casesJson.value || '[]')
    const data: any = await runSolutionEval({
      cases: parsedCases,
      useLatestTask: useLatestTask.value
    })
    const value = data?.data || data || {}
    summary.value = value
    results.value = value.results || []
    ElMessage.success('方案回归评测完成')
  } catch (e: any) {
    ElMessage.error(e?.message || '方案回归评测失败')
  } finally {
    running.value = false
  }
}

function percent(value: any) {
  return `${(Number(value || 0) * 100).toFixed(1)}%`
}

function displayDimensions(row: Record<string, any>) {
  const dimensions = Array.isArray(row.dimensions) ? row.dimensions : []
  const priority = ['template', 'businessEvidence', 'mapBinding', 'scenario', 'llm']
  return priority
    .map((code) => dimensions.find((item: any) => item.code === code) || { code, level: '-' })
    .filter(Boolean)
}

function dimensionLabel(code: string) {
  return dimensionLabels[code] || code
}

onMounted(loadDefaultCases)
</script>

<style scoped>
.block-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.eval-form {
  margin-bottom: 8px;
}

.case-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.metric {
  min-height: 70px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.metric span {
  display: block;
  margin-bottom: 6px;
  color: #64748b;
  font-size: 13px;
}

.metric strong {
  color: #0f172a;
  font-size: 24px;
}

.source-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 12px;
  color: #475569;
}

.case-title {
  color: #0f172a;
  font-weight: 600;
}

.muted {
  color: #64748b;
  font-size: 12px;
}

.id-line {
  margin-top: 4px;
  word-break: break-all;
}

.dimension-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.dimension-chip {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 6px;
  border: 1px solid #dbeafe;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
}

.dimension-chip.WARN {
  border-color: #fde68a;
  background: #fffbeb;
  color: #b45309;
}

.dimension-chip.ERROR {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.dimension-chip.OK {
  border-color: #bbf7d0;
  background: #f0fdf4;
  color: #15803d;
}

.source-types,
.error-list {
  line-height: 1.6;
}

.error-list {
  color: #b91c1c;
}

.fallback {
  margin-top: 6px;
}
</style>
