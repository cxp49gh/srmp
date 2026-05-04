<template>
  <AgentPageShell
    title="LangGraph 编排观测"
    description="查看 Java 编排路由、LangGraph Runtime、Tool Gateway 和最近编排调用，支持一键 smoke 验证。"
  >
    <template #actions>
      <div class="header-actions">
        <el-button :loading="loading" @click="loadSummary">刷新</el-button>
        <el-button type="primary" :loading="smoking" @click="runSmoke">运行 Smoke</el-button>
      </div>
    </template>

    <div class="ops-page">
      <section class="metric-grid">
        <el-card shadow="never" class="metric-card">
          <div class="metric-head">
            <span>当前 Provider</span>
            <el-tag :type="provider === 'langgraph' ? 'success' : 'info'">{{ provider || '-' }}</el-tag>
          </div>
          <div class="metric-value">{{ provider === 'langgraph' ? '远程编排' : '原生链路' }}</div>
          <div class="metric-desc">fallback：{{ fallbackToNative ? '开启' : '关闭' }}；写工具：{{ allowWriteTools ? '允许' : '禁止' }}</div>
        </el-card>

        <el-card shadow="never" class="metric-card">
          <div class="metric-head">
            <span>LangGraph Ready</span>
            <el-tag :type="readyOk ? 'success' : 'danger'">{{ readyOk ? 'UP' : 'DOWN' }}</el-tag>
          </div>
          <div class="metric-value">{{ value(langgraphReady, ['costMs']) || 0 }}ms</div>
          <div class="metric-desc">{{ value(langgraphReady, ['url']) || '-' }}</div>
        </el-card>

        <el-card shadow="never" class="metric-card">
          <div class="metric-head">
            <span>Runtime 调用</span>
            <el-tag :type="Number(value(runtimeSummary, ['failed']) || 0) > 0 ? 'warning' : 'success'">
              {{ Number(value(runtimeSummary, ['failed']) || 0) > 0 ? '有失败' : '正常' }}
            </el-tag>
          </div>
          <div class="metric-value">{{ value(runtimeSummary, ['total']) || 0 }}</div>
          <div class="metric-desc">成功 {{ value(runtimeSummary, ['success']) || 0 }}；失败 {{ value(runtimeSummary, ['failed']) || 0 }}；平均 {{ value(runtimeSummary, ['avgCostMs']) || 0 }}ms</div>
        </el-card>

        <el-card shadow="never" class="metric-card">
          <div class="metric-head">
            <span>Tool Gateway</span>
            <el-tag :type="toolGatewayOk ? 'success' : 'danger'">{{ toolGatewayOk ? 'OK' : '异常' }}</el-tag>
          </div>
          <div class="metric-value">{{ localToolCount }}</div>
          <div class="metric-desc">Java 本地工具数；Runtime 可达性见下方诊断</div>
        </el-card>
      </section>

      <el-alert
        v-if="provider !== 'langgraph'"
        class="mb"
        type="info"
        show-icon
        title="当前 Java 后端 provider 不是 langgraph。页面仍可查看 Runtime 状态，但 /api/agent/map-agent/chat 默认不会走 LangGraph。"
      />
      <el-alert
        v-if="!readyOk"
        class="mb"
        type="error"
        show-icon
        title="LangGraph Runtime 未 ready。优先检查 SRMP_LANGGRAPH_URL、SRMP_JAVA_BASE_URL，以及 Java /api/agent/tools 是否注册。"
      />

      <section class="content-grid">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>Smoke 请求</span>
              <el-button size="small" text @click="copySmokeResult">复制结果</el-button>
            </div>
          </template>
          <el-form label-width="90px" class="smoke-form">
            <el-form-item label="租户">
              <el-input v-model="smokeForm.tenantId" placeholder="default" />
            </el-form-item>
            <el-form-item label="路线">
              <el-input v-model="smokeForm.routeCode" placeholder="G210" />
            </el-form-item>
            <el-form-item label="年份">
              <el-input-number v-model="smokeForm.year" :min="2000" :max="2100" />
            </el-form-item>
            <el-form-item label="问题">
              <el-input v-model="smokeForm.message" type="textarea" :rows="4" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="smoking" @click="runSmoke">运行 Smoke</el-button>
            </el-form-item>
          </el-form>
          <el-empty v-if="!smokeResultText" description="暂无 smoke 结果" />
          <pre v-else>{{ smokeResultText }}</pre>
        </el-card>

        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>链路诊断</span>
              <el-button size="small" text @click="copySummary">复制诊断</el-button>
            </div>
          </template>
          <div class="diag-list">
            <div class="diag-row">
              <span>Java Provider</span>
              <strong>{{ provider || '-' }}</strong>
            </div>
            <div class="diag-row">
              <span>可用 Providers</span>
              <strong>{{ availableProviders.join(', ') || '-' }}</strong>
            </div>
            <div class="diag-row">
              <span>LangGraph URL</span>
              <strong>{{ langgraphUrl || '-' }}</strong>
            </div>
            <div class="diag-row">
              <span>Runtime Ready</span>
              <strong>{{ readyOk ? 'OK' : value(langgraphReady, ['error']) || '异常' }}</strong>
            </div>
            <div class="diag-row">
              <span>Tool Gateway</span>
              <strong>{{ toolGatewayOk ? 'OK' : value(toolGatewayDebug, ['error']) || '异常' }}</strong>
            </div>
            <div class="diag-row">
              <span>最近 Trace</span>
              <strong>{{ value(runtimeSummary, ['lastTraceId']) || '-' }}</strong>
            </div>
          </div>
        </el-card>
      </section>

      <el-card shadow="never" class="panel-card lower-card">
        <template #header>
          <div class="card-header">
            <span>最近 LangGraph 调用</span>
            <div>
              <el-select v-model="recentStatus" size="small" class="status-select" @change="loadRecent">
                <el-option label="全部" value="" />
                <el-option label="成功" value="SUCCESS" />
                <el-option label="失败" value="FAILED" />
              </el-select>
              <el-button size="small" :loading="recentLoading" @click="loadRecent">刷新最近</el-button>
            </div>
          </div>
        </template>
        <el-empty v-if="recentRecords.length === 0" description="暂无 Runtime 记录" />
        <el-table v-else :data="recentRecords" size="small" border>
          <el-table-column prop="status" label="状态" width="90">
            <template #default="scope">
              <el-tag size="small" :type="scope.row.status === 'SUCCESS' ? 'success' : 'danger'">{{ scope.row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="traceId" label="Trace" min-width="190" show-overflow-tooltip />
          <el-table-column prop="intent" label="意图" width="150" show-overflow-tooltip />
          <el-table-column prop="routeCode" label="路线" width="90" />
          <el-table-column prop="costMs" label="耗时" width="90">
            <template #default="scope">{{ scope.row.costMs || 0 }}ms</template>
          </el-table-column>
          <el-table-column label="工具" width="110">
            <template #default="scope">{{ scope.row.toolSuccessCount || 0 }}/{{ scope.row.toolTotalCount || 0 }}</template>
          </el-table-column>
          <el-table-column prop="messagePreview" label="问题" min-width="220" show-overflow-tooltip />
          <el-table-column label="详情" width="90">
            <template #default="scope">
              <el-button size="small" text @click="openRecord(scope.row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-dialog v-model="recordDialogVisible" title="Runtime 调用详情" width="760px">
        <pre>{{ selectedRecordText }}</pre>
      </el-dialog>
    </div>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import { getOrchestratorOpsSummary, getOrchestratorRecent, runOrchestratorSmoke } from '../../api/orchestrator'
import { copyToClipboard } from '../../utils/clipboard'

const loading = ref(false)
const recentLoading = ref(false)
const smoking = ref(false)
const summary = reactive<Record<string, any>>({})
const recentRecords = ref<Record<string, any>[]>([])
const recentStatus = ref('')
const smokeResultText = ref('')
const recordDialogVisible = ref(false)
const selectedRecordText = ref('')

const smokeForm = reactive({
  tenantId: 'default',
  routeCode: 'G210',
  year: 2026,
  message: '请基于 G210 当前路况和知识库，给出一段道路养护建议摘要。'
})

const provider = computed(() => String(summary.provider || ''))
const fallbackToNative = computed(() => Boolean(summary.fallbackToNative))
const allowWriteTools = computed(() => Boolean(summary.allowWriteTools))
const availableProviders = computed(() => Array.isArray(summary.availableProviders) ? summary.availableProviders : [])
const localToolCount = computed(() => Number(summary.localToolCount || 0))
const langgraphUrl = computed(() => String(summary.langgraphUrl || ''))
const langgraphReady = computed(() => value(summary, ['langgraphReady']) || {})
const runtimeSummary = computed(() => value(summary, ['runtimeSummary', 'body']) || {})
const toolGatewayDebug = computed(() => value(summary, ['toolGatewayDebug']) || {})
const readyOk = computed(() => Boolean(value(langgraphReady.value, ['ok']) && value(langgraphReady.value, ['body', 'status']) === 'UP'))
const toolGatewayOk = computed(() => Boolean(value(toolGatewayDebug.value, ['ok'])))

async function loadSummary() {
  loading.value = true
  try {
    const res = await getOrchestratorOpsSummary(10)
    Object.keys(summary).forEach((key) => delete summary[key])
    Object.assign(summary, res || {})
    const records = value(res, ['runtimeRecent', 'body', 'records'])
    recentRecords.value = Array.isArray(records) ? records : []
  } finally {
    loading.value = false
  }
}

async function loadRecent() {
  recentLoading.value = true
  try {
    const res = await getOrchestratorRecent(30, recentStatus.value || undefined)
    const records = value(res, ['body', 'records']) || value(res, ['records'])
    recentRecords.value = Array.isArray(records) ? records : []
  } finally {
    recentLoading.value = false
  }
}

async function runSmoke() {
  smoking.value = true
  try {
    const res = await runOrchestratorSmoke({
      tenantId: smokeForm.tenantId,
      routeCode: smokeForm.routeCode,
      year: smokeForm.year,
      mode: 'ROUTE',
      message: smokeForm.message
    })
    smokeResultText.value = JSON.stringify(res, null, 2)
    ElMessage.success('Smoke 已完成')
    await loadSummary()
  } catch (error: any) {
    smokeResultText.value = JSON.stringify(error?.response?.data || error?.message || error, null, 2)
    ElMessage.error('Smoke 失败，请查看结果')
  } finally {
    smoking.value = false
  }
}

function openRecord(row: Record<string, any>) {
  selectedRecordText.value = JSON.stringify(row, null, 2)
  recordDialogVisible.value = true
}

async function copySummary() {
  await copyToClipboard(JSON.stringify(summary, null, 2))
  ElMessage.success('诊断信息已复制')
}

async function copySmokeResult() {
  if (!smokeResultText.value) {
    ElMessage.warning('暂无 smoke 结果')
    return
  }
  await copyToClipboard(smokeResultText.value)
  ElMessage.success('Smoke 结果已复制')
}

function value(obj: any, keys: string[]) {
  let cur = obj
  for (const key of keys) {
    if (cur == null) return undefined
    cur = cur[key]
  }
  return cur
}

onMounted(loadSummary)
</script>

<style scoped>
.ops-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.metric-grid,
.content-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.content-grid {
  grid-template-columns: 1.2fr 0.8fr;
}

.metric-card,
.panel-card {
  border-radius: 14px;
}

.metric-head,
.card-header,
.diag-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.metric-head span,
.diag-row span {
  color: #64748b;
  font-size: 13px;
}

.metric-value {
  margin-top: 10px;
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
}

.metric-desc {
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  word-break: break-all;
}

.mb {
  margin-bottom: 0;
}

.smoke-form {
  max-width: 720px;
}

pre {
  max-height: 420px;
  overflow: auto;
  padding: 12px;
  margin: 12px 0 0;
  border-radius: 12px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 12px;
}

.diag-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.diag-row strong {
  max-width: 70%;
  text-align: right;
  word-break: break-all;
}

.lower-card {
  min-height: 260px;
}

.status-select {
  width: 110px;
  margin-right: 8px;
}

@media (max-width: 1180px) {
  .metric-grid,
  .content-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 780px) {
  .metric-grid,
  .content-grid {
    grid-template-columns: 1fr;
  }
}
</style>
