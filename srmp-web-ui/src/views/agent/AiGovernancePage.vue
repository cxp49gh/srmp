<template>
  <AgentPageShell
    title="AI 能力治理"
    description="查看 Agent 当前能力、工具目录、编排策略，并用模拟器解释一次请求会命中什么能力和工具。"
  >
    <template #actions>
      <div class="header-actions">
        <el-button :loading="loading" @click="loadGovernance">刷新</el-button>
        <el-button :loading="coverageLoading" @click="loadPolicyCoverage">运行策略样例</el-button>
        <el-button type="primary" :loading="planning" @click="simulatePlan">运行模拟</el-button>
      </div>
    </template>

    <div class="governance-page">
      <section class="metric-grid">
        <el-card shadow="never" class="metric-card">
          <span>能力数量</span>
          <strong>{{ capabilityCount }}</strong>
          <p>启用 {{ enabledCapabilityCount }} 个</p>
        </el-card>
        <el-card shadow="never" class="metric-card">
          <span>工具数量</span>
          <strong>{{ toolCount }}</strong>
          <p>{{ toolCategories.join(' / ') || '-' }}</p>
        </el-card>
        <el-card shadow="never" class="metric-card">
          <span>策略校验</span>
          <strong>{{ validationStatus }}</strong>
          <p>错误 {{ validationErrorCount }}；告警 {{ validationWarningCount }}</p>
        </el-card>
        <el-card shadow="never" class="metric-card">
          <span>策略样例</span>
          <strong>{{ coverageStatus }}</strong>
          <p>通过 {{ coveragePassedCount }}；失败 {{ coverageFailedCount }}</p>
        </el-card>
        <el-card shadow="never" class="metric-card">
          <span>配置版本</span>
          <strong>{{ capabilityVersion || '-' }}</strong>
          <p>工具 {{ toolVersion || '-' }}</p>
        </el-card>
      </section>

      <el-alert
        v-if="validationErrorCount > 0"
        class="mb"
        type="error"
        show-icon
        title="治理配置存在错误，Runtime 应阻止生产发布。"
      />

      <el-tabs v-model="activeTab" class="governance-tabs">
        <el-tab-pane label="能力矩阵" name="capabilities">
          <el-table :data="capabilities" border stripe>
            <el-table-column prop="id" label="能力 ID" min-width="190" />
            <el-table-column prop="name" label="名称" width="130" />
            <el-table-column prop="category" label="分类" width="140" />
            <el-table-column prop="intent" label="Intent" width="150" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled === false ? 'info' : 'success'">{{ row.enabled === false ? '停用' : '启用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="触发摘要" min-width="280">
              <template #default="{ row }">
                <div class="tag-list">
                  <el-tag v-for="item in triggerTags(row)" :key="item" size="small" effect="plain">{{ item }}</el-tag>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="工具目录" name="tools">
          <el-table :data="tools" border stripe>
            <el-table-column prop="name" label="工具名" min-width="210" />
            <el-table-column prop="label" label="展示名" width="150" />
            <el-table-column prop="category" label="分类" width="130" />
            <el-table-column label="风险" width="110">
              <template #default="{ row }">
                <el-tag :type="row.writeRisk ? 'danger' : 'success'">{{ row.writeRisk ? '写风险' : '只读' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="280" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="编排策略" name="policy">
          <el-table :data="capabilities" border stripe>
            <el-table-column prop="name" label="能力" width="150" />
            <el-table-column label="Required" min-width="220">
              <template #default="{ row }">
                <div class="tag-list"><el-tag v-for="item in policyList(row, 'required')" :key="item" size="small">{{ item }}</el-tag></div>
              </template>
            </el-table-column>
            <el-table-column label="Optional" min-width="200">
              <template #default="{ row }">
                <div class="tag-list"><el-tag v-for="item in policyList(row, 'optional')" :key="item" size="small" type="info">{{ item }}</el-tag></div>
              </template>
            </el-table-column>
            <el-table-column label="Adaptive" min-width="200">
              <template #default="{ row }">
                <div class="tag-list"><el-tag v-for="item in policyList(row, 'adaptive')" :key="item" size="small" type="warning">{{ item }}</el-tag></div>
              </template>
            </el-table-column>
            <el-table-column label="Prohibited" min-width="240">
              <template #default="{ row }">
                <div class="tag-list"><el-tag v-for="item in policyList(row, 'prohibited')" :key="item" size="small" type="danger">{{ item }}</el-tag></div>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="策略样例" name="coverage">
          <div class="coverage-head">
            <div>
              <strong>样例矩阵</strong>
              <span>用配置中的典型请求校验能力命中和工具边界</span>
            </div>
            <el-button size="small" type="primary" :loading="coverageLoading" @click="loadPolicyCoverage">运行样例</el-button>
          </div>
          <el-alert
            v-if="coverageFailedCount > 0"
            class="mb"
            type="error"
            show-icon
            title="存在失败样例，请检查能力触发条件或工具策略。"
          />
          <el-table :data="coverageCases" border stripe v-loading="coverageLoading">
            <el-table-column prop="id" label="样例 ID" min-width="230" />
            <el-table-column prop="name" label="场景" min-width="150" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'PASS' ? 'success' : 'danger'">{{ row.status || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="能力命中" min-width="240">
              <template #default="{ row }">
                <div class="stacked">
                  <strong>{{ row.actualCapabilityName || row.actualCapabilityId || '-' }}</strong>
                  <span>{{ row.actualCapabilityId || '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="计划工具" min-width="320">
              <template #default="{ row }">
                <div class="tag-list">
                  <el-tag v-for="item in arrayValue(row.actualToolNames)" :key="item" size="small">{{ item }}</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="问题" min-width="260">
              <template #default="{ row }">
                <span v-if="arrayValue(row.warnings).length === 0" class="muted">无</span>
                <div v-else class="stacked">
                  <span v-for="item in arrayValue(row.warnings)" :key="item.code || item.message" class="error">{{ item.message || item.code }}</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="Plan 模拟器" name="simulator">
          <div class="simulator-grid">
            <el-card shadow="never">
              <template #header>请求上下文</template>
              <el-form label-width="90px">
                <el-form-item label="问题">
                  <el-input v-model="planForm.message" type="textarea" :rows="3" />
                </el-form-item>
                <el-form-item label="Action">
                  <el-select v-model="planForm.action" clearable placeholder="可选">
                    <el-option label="CHAT" value="CHAT" />
                    <el-option label="ANALYZE_OBJECT" value="ANALYZE_OBJECT" />
                    <el-option label="ANALYZE_ROUTE" value="ANALYZE_ROUTE" />
                    <el-option label="ANALYZE_REGION" value="ANALYZE_REGION" />
                    <el-option label="GENERATE_OBJECT_SOLUTION" value="GENERATE_OBJECT_SOLUTION" />
                    <el-option label="GENERATE_REGION_SOLUTION" value="GENERATE_REGION_SOLUTION" />
                    <el-option label="GENERATE_ROUTE_REPORT" value="GENERATE_ROUTE_REPORT" />
                  </el-select>
                </el-form-item>
                <el-form-item label="Mode">
                  <el-select v-model="planForm.mode">
                    <el-option label="ROUTE" value="ROUTE" />
                    <el-option label="OBJECT" value="OBJECT" />
                    <el-option label="REGION" value="REGION" />
                    <el-option label="FREE" value="FREE" />
                  </el-select>
                </el-form-item>
                <el-form-item label="对象类型">
                  <el-select v-model="planForm.objectType" clearable placeholder="可选">
                    <el-option label="ROAD_ROUTE" value="ROAD_ROUTE" />
                    <el-option label="ROAD_SECTION" value="ROAD_SECTION" />
                    <el-option label="DISEASE" value="DISEASE" />
                    <el-option label="ASSESSMENT_RESULT" value="ASSESSMENT_RESULT" />
                  </el-select>
                </el-form-item>
                <el-form-item label="路线">
                  <el-input v-model="planForm.routeCode" />
                </el-form-item>
                <el-form-item label="年份">
                  <el-input-number v-model="planForm.year" :min="2000" :max="2100" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="planning" @click="simulatePlan">运行模拟</el-button>
                </el-form-item>
              </el-form>
            </el-card>

            <el-card shadow="never">
              <template #header>命中结果</template>
              <el-empty v-if="!planResult" description="暂无模拟结果" />
              <template v-else>
                <section class="plan-summary">
                  <div><span>能力</span><strong>{{ planResult.capabilityId || '-' }}</strong></div>
                  <div><span>Intent</span><strong>{{ planResult.intent || '-' }}</strong></div>
                  <div><span>Trace</span><strong>{{ planResult.traceId || '-' }}</strong></div>
                </section>
                <div class="detail-block">
                  <h3>匹配规则</h3>
                  <div class="tag-list">
                    <el-tag v-for="item in planMatchedRules" :key="item" size="small" effect="plain">{{ item }}</el-tag>
                  </div>
                </div>
                <div class="detail-block">
                  <h3>计划工具</h3>
                  <div v-for="tool in planTools" :key="tool.toolName + tool.reason" class="tool-row">
                    <strong>{{ tool.label || tool.toolName }}</strong>
                    <span>{{ tool.reason || '-' }}</span>
                  </div>
                </div>
                <div class="detail-block">
                  <h3>禁用工具</h3>
                  <div class="tag-list">
                    <el-tag v-for="item in planProhibitedTools" :key="item" size="small" type="danger">{{ item }}</el-tag>
                  </div>
                </div>
              </template>
            </el-card>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AgentPageShell from './components/AgentPageShell.vue'
import {
  getAiGovernanceCapabilities,
  getAiGovernancePolicyCoverage,
  getAiGovernanceTools,
  simulateAiGovernancePlan,
  validateAiGovernancePolicies
} from '../../api/orchestrator'

const activeTab = ref('capabilities')
const loading = ref(false)
const planning = ref(false)
const coverageLoading = ref(false)
const capabilitiesPayload = ref<Record<string, any>>({})
const toolsPayload = ref<Record<string, any>>({})
const validationPayload = ref<Record<string, any>>({})
const coveragePayload = ref<Record<string, any>>({})
const planResult = ref<Record<string, any> | null>(null)

const planForm = reactive({
  message: '解释 PCI 指标',
  action: '',
  mode: 'ROUTE',
  objectType: '',
  routeCode: 'Y016140727',
  year: 2026
})

const capabilities = computed(() => arrayValue(capabilitiesPayload.value.capabilities))
const tools = computed(() => arrayValue(toolsPayload.value.tools))
const capabilityCount = computed(() => Number(capabilitiesPayload.value.capabilityCount ?? capabilities.value.length))
const enabledCapabilityCount = computed(() => Number(capabilitiesPayload.value.enabledCapabilityCount ?? capabilities.value.filter((item) => item.enabled !== false).length))
const toolCount = computed(() => Number(toolsPayload.value.toolCount ?? tools.value.length))
const capabilityVersion = computed(() => stringValue(capabilitiesPayload.value.version))
const toolVersion = computed(() => stringValue(toolsPayload.value.version))
const validation = computed(() => objectValue(validationPayload.value.validation || capabilitiesPayload.value.validation))
const validationErrorCount = computed(() => Number(validation.value.errorCount || 0))
const validationWarningCount = computed(() => Number(validation.value.warningCount || 0))
const validationStatus = computed(() => validationErrorCount.value > 0 ? '异常' : '通过')
const coverageCases = computed(() => arrayValue(coveragePayload.value.cases))
const coverageFailedCount = computed(() => Number(coveragePayload.value.failedCount || coverageCases.value.filter((item) => item.status !== 'PASS').length))
const coveragePassedCount = computed(() => Number(coveragePayload.value.passedCount || coverageCases.value.filter((item) => item.status === 'PASS').length))
const coverageStatus = computed(() => coverageFailedCount.value > 0 ? '异常' : coverageCases.value.length ? '通过' : '未运行')
const toolCategories = computed(() => uniqueStrings(tools.value.map((item) => item.category).filter(Boolean)))
const planTools = computed(() => arrayValue(planResult.value?.toolPlan))
const planMatchedRules = computed(() => arrayValue(planResult.value?.capability?.matchedRules))
const planProhibitedTools = computed(() => arrayValue(planResult.value?.capability?.toolPolicy?.prohibited))

onMounted(loadGovernance)

async function loadGovernance() {
  loading.value = true
  try {
    const [capabilitiesRes, toolsRes, validationRes] = await Promise.all([
      getAiGovernanceCapabilities(),
      getAiGovernanceTools(),
      validateAiGovernancePolicies()
    ])
    capabilitiesPayload.value = extractBody(capabilitiesRes)
    toolsPayload.value = extractBody(toolsRes)
    validationPayload.value = extractBody(validationRes)
  } finally {
    loading.value = false
  }
  await loadPolicyCoverage()
}

async function loadPolicyCoverage() {
  coverageLoading.value = true
  try {
    const response = await getAiGovernancePolicyCoverage()
    coveragePayload.value = extractBody(response)
  } finally {
    coverageLoading.value = false
  }
}

async function simulatePlan() {
  planning.value = true
  try {
    const mapObject = planForm.objectType ? { objectType: planForm.objectType, routeCode: planForm.routeCode, year: planForm.year } : undefined
    const response = await simulateAiGovernancePlan({
      action: planForm.action || undefined,
      message: planForm.message,
      mapContext: {
        mode: planForm.mode,
        routeCode: planForm.routeCode,
        year: planForm.year,
        mapObject
      },
      options: {
        topK: 3,
        traceId: `governance-plan-${Date.now()}`
      }
    })
    planResult.value = extractBody(response)
    activeTab.value = 'simulator'
  } finally {
    planning.value = false
  }
}

function triggerTags(row: Record<string, any>) {
  const triggers = objectValue(row.triggers)
  return [
    ...arrayValue(triggers.actions).map((item) => `action:${item}`),
    ...arrayValue(triggers.modes).map((item) => `mode:${item}`),
    ...arrayValue(triggers.objectTypes).map((item) => `object:${item}`),
    ...arrayValue(triggers.includeKeywords).slice(0, 5).map((item) => `kw:${item}`)
  ]
}

function policyList(row: Record<string, any>, key: string) {
  return arrayValue(row.toolPolicy?.[key])
}

function extractBody(value: any): Record<string, any> {
  if (value?.body && typeof value.body === 'object') return value.body
  if (value?.data?.body && typeof value.data.body === 'object') return value.data.body
  if (value && typeof value === 'object') return value
  return {}
}

function arrayValue(value: any): any[] {
  return Array.isArray(value) ? value : []
}

function objectValue(value: any): Record<string, any> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
}

function stringValue(value: any): string {
  return value == null ? '' : String(value)
}

function uniqueStrings(values: any[]): string[] {
  return Array.from(new Set(values.map((item) => String(item)).filter(Boolean)))
}
</script>

<style scoped>
.governance-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.header-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.metric-card span,
.plan-summary span {
  color: #64748b;
  font-size: 12px;
}

.metric-card strong {
  display: block;
  margin-top: 8px;
  font-size: 24px;
}

.metric-card p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
}

.mb {
  margin-bottom: 12px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.coverage-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.coverage-head div {
  display: grid;
  gap: 4px;
}

.coverage-head span,
.muted {
  color: #64748b;
  font-size: 12px;
}

.stacked {
  display: grid;
  gap: 4px;
}

.stacked span {
  color: #64748b;
  word-break: break-all;
}

.stacked .error {
  color: #dc2626;
}

.simulator-grid {
  display: grid;
  grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
  gap: 16px;
}

.plan-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.plan-summary div {
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f8fafc;
}

.plan-summary strong {
  display: block;
  margin-top: 6px;
  word-break: break-all;
}

.detail-block {
  margin-top: 16px;
}

.detail-block h3 {
  margin: 0 0 10px;
  font-size: 14px;
}

.tool-row {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #e5e7eb;
}

.tool-row span {
  color: #64748b;
}

@media (max-width: 1100px) {
  .metric-grid,
  .simulator-grid,
  .plan-summary {
    grid-template-columns: 1fr;
  }
}
</style>
