<template>
  <AgentPageShell
    title="LangGraph 编排观测"
    description="查看 Java 编排路由、LangGraph Runtime、Tool Gateway、工具契约和最近编排调用，支持 Smoke 与 Plan Debug。"
  >
    <template #actions>
      <div class="header-actions">
        <el-button :loading="loading" @click="loadSummary">刷新</el-button>
        <el-button :loading="contractLoading" @click="loadContract">契约诊断</el-button>
        <el-button :loading="healthLoading" @click="loadRuntimeDetails(true)">配置健康</el-button>
        <el-button :loading="llmProbeLoading" type="warning" plain @click="probeLangGraphLlm(true)">LLM 探测</el-button>
        <el-button :loading="exportLoading" @click="exportDiagnostics">导出诊断</el-button>
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
            <span>LangGraph LLM</span>
            <el-tag :type="langGraphLlmTagType">{{ langGraphLlmStatusText }}</el-tag>
          </div>
          <div class="metric-value">{{ langGraphLlmModel || '-' }}</div>
          <div class="metric-desc">enabled：{{ langGraphLlmEnabled ? 'true' : 'false' }}；retry：{{ langGraphLlmRetry ? 'true' : 'false' }}；{{ langGraphLlmCost }}</div>
        </el-card>

        <el-card shadow="never" class="metric-card">
          <div class="metric-head">
            <span>Runtime 调用</span>
            <el-tag :type="Number(value(runtimeSummary, ['failed']) || 0) > 0 ? 'warning' : 'success'">
              {{ Number(value(runtimeSummary, ['failed']) || 0) > 0 ? '有失败' : '正常' }}
            </el-tag>
          </div>
          <div class="metric-value">{{ value(runtimeSummary, ['total']) || 0 }}</div>
          <div class="metric-desc">成功 {{ value(runtimeSummary, ['success']) || 0 }}；失败 {{ value(runtimeSummary, ['failed']) || 0 }}；可回放 {{ value(runtimeSummary, ['replayableCount']) || 0 }}；平均 {{ value(runtimeSummary, ['avgCostMs']) || 0 }}ms</div>
        </el-card>

        <el-card shadow="never" class="metric-card">
          <div class="metric-head">
            <span>工具契约</span>
            <el-tag :type="contractOk ? 'success' : 'danger'">{{ contractOk ? '闭合' : '异常' }}</el-tag>
          </div>
          <div class="metric-value">{{ contractToolCount }}</div>
          <div class="metric-desc">Java 工具 / Runtime 白名单；缺失 {{ missingToolCount }} 个</div>
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
      <el-alert
        v-if="!contractOk"
        class="mb"
        type="warning"
        show-icon
        title="工具契约未闭合。建议先看下方 missingInJava / blockedByRuntimeWhitelist，再调整 Java 工具注册或 SRMP_LANGGRAPH_ALLOWED_TOOLS。"
      />

      <section class="content-grid">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>配置健康</span>
              <div>
                <el-button size="small" text :loading="snapshotLoading" @click="exportSnapshot">导出快照</el-button>
                <el-button size="small" text :loading="healthLoading" @click="loadRuntimeDetails(true)">刷新</el-button>
              </div>
            </div>
          </template>
          <div class="diag-list">
            <div class="diag-row">
              <span>健康状态</span>
              <strong>{{ healthStatus }}</strong>
            </div>
            <div class="diag-row">
              <span>Runtime</span>
              <strong>{{ runtimeAppVersion }}</strong>
            </div>
            <div class="diag-row">
              <span>策略版本</span>
              <strong>{{ configStrategyVersion }}</strong>
            </div>
            <div class="diag-row">
              <span>配置指纹</span>
              <strong>{{ configFingerprint }}</strong>
            </div>
            <div class="diag-row">
              <span>配置告警</span>
              <strong>{{ configWarningCount }} 个</strong>
            </div>
          </div>
          <el-alert
            v-if="configWarningTitle"
            class="mt"
            type="warning"
            show-icon
            :title="configWarningTitle"
          />
        </el-card>

        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>LangGraph LLM</span>
              <el-button size="small" text :loading="llmProbeLoading" @click="probeLangGraphLlm(true)">探测</el-button>
            </div>
          </template>
          <div class="diag-list">
            <div class="diag-row">
              <span>状态</span>
              <strong>{{ langGraphLlmStatusText }}</strong>
            </div>
            <div class="diag-row">
              <span>模型</span>
              <strong>{{ langGraphLlmModel || '-' }}</strong>
            </div>
            <div class="diag-row">
              <span>配置</span>
              <strong>base={{ langGraphLlmBaseConfigured ? 'yes' : 'no' }}；key={{ langGraphLlmKeyConfigured ? 'yes' : 'no' }}</strong>
            </div>
            <div class="diag-row">
              <span>超时/Token</span>
              <strong>{{ langGraphLlmConnectTimeout }}s / {{ langGraphLlmReadTimeout }}s / {{ langGraphLlmMaxTokens }}</strong>
            </div>
            <div class="diag-row">
              <span>最近耗时</span>
              <strong>{{ langGraphLlmCost }}</strong>
            </div>
            <div class="diag-row">
              <span>错误</span>
              <strong>{{ langGraphLlmError || '-' }}</strong>
            </div>
            <div class="diag-row">
              <span>响应预览</span>
              <strong>{{ langGraphLlmPreview || '-' }}</strong>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>审计持久化</span>
              <el-button size="small" text :loading="healthLoading" @click="loadRuntimeDetails(true)">刷新</el-button>
            </div>
          </template>
          <div class="diag-list">
            <div class="diag-row">
              <span>状态</span>
              <strong>{{ persistenceEnabled ? '已启用' : '内存模式' }}</strong>
            </div>
            <div class="diag-row">
              <span>路径</span>
              <strong>{{ persistencePath || '-' }}</strong>
            </div>
            <div class="diag-row">
              <span>容量</span>
              <strong>{{ persistenceSize }} / {{ persistenceMaxSize }}</strong>
            </div>
            <div class="diag-row">
              <span>写入/恢复</span>
              <strong>{{ persistenceWritten }} / {{ persistenceLoaded }}</strong>
            </div>
            <div class="diag-row">
              <span>最近错误</span>
              <strong>{{ persistenceLastError || '-' }}</strong>
            </div>
          </div>
          <div class="prune-row">
            <el-select v-model="pruneForm.status" size="small" class="status-select">
              <el-option label="全部" value="" />
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILED" />
            </el-select>
            <el-input-number v-model="pruneForm.retainLatest" size="small" :min="0" :max="1000" />
            <el-checkbox v-model="pruneForm.includePersist">同步文件</el-checkbox>
            <el-button size="small" type="warning" :loading="pruneLoading" @click="pruneAudit">清理审计</el-button>
          </div>
        </el-card>
      </section>

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
              <span>Plan Debug</span>
              <el-button size="small" text @click="copyPlanResult">复制结果</el-button>
            </div>
          </template>
          <el-form label-width="92px" class="smoke-form">
            <el-form-item label="模式">
              <el-select v-model="planForm.mode" placeholder="选择上下文模式">
                <el-option label="对象 OBJECT" value="OBJECT" />
                <el-option label="框选 POLYGON" value="POLYGON" />
                <el-option label="路线 ROUTE" value="ROUTE" />
                <el-option label="评定 ASSESSMENT" value="ASSESSMENT" />
              </el-select>
            </el-form-item>
            <el-form-item label="路线/年份">
              <div class="inline-fields">
                <el-input v-model="planForm.routeCode" placeholder="G210" />
                <el-input-number v-model="planForm.year" :min="2000" :max="2100" />
              </div>
            </el-form-item>
            <el-form-item label="对象信息">
              <div class="inline-fields">
                <el-input v-model="planForm.diseaseName" placeholder="裂缝/坑槽/评定单元" />
                <el-input v-model="planForm.severity" placeholder="轻度/中度/重度" />
              </div>
            </el-form-item>
            <el-form-item label="问题">
              <el-input v-model="planForm.message" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="Geometry">
              <el-input v-model="planForm.geometryText" type="textarea" :rows="3" placeholder='可选 GeoJSON，例如 {"type":"Polygon","coordinates":[]}' />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="planning" @click="runPlan">只规划不执行</el-button>
            </el-form-item>
          </el-form>
          <el-empty v-if="!planResultText" description="暂无 plan 结果" />
          <pre v-else>{{ planResultText }}</pre>
        </el-card>
      </section>

      <section class="content-grid">
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
              <span>Strategy</span>
              <strong>{{ value(runtimeSummary, ['strategyVersion']) || value(summary, ['langgraphReady', 'body', 'strategy', 'strategyVersion']) || '-' }}</strong>
            </div>
            <div class="diag-row">
              <span>最近 Trace</span>
              <strong>{{ value(runtimeSummary, ['lastTraceId']) || '-' }}</strong>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="card-header">
              <span>工具契约详情</span>
              <el-button size="small" text :loading="contractLoading" @click="loadContract">刷新契约</el-button>
            </div>
          </template>
          <div class="diag-list">
            <div class="diag-row">
              <span>Java 工具数</span>
              <strong>{{ contractToolCount }}</strong>
            </div>
            <div class="diag-row">
              <span>Runtime 白名单</span>
              <strong>{{ value(contractBody, ['runtimeAllowedToolCount']) || 0 }}</strong>
            </div>
            <div class="diag-row">
              <span>missingInJava</span>
              <strong>{{ missingTools.join(', ') || '-' }}</strong>
            </div>
            <div class="diag-row">
              <span>Runtime 未放行</span>
              <strong>{{ blockedTools.join(', ') || '-' }}</strong>
            </div>
            <div class="diag-row">
              <span>写工具屏蔽</span>
              <strong>{{ writeBlockedTools.join(', ') || '-' }}</strong>
            </div>
          </div>
          <pre v-if="contractResultText">{{ contractResultText }}</pre>
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
          <el-table-column label="操作" width="190" fixed="right">
            <template #default="scope">
              <el-button size="small" text @click="openRecord(scope.row)">执行过程</el-button>
              <el-button size="small" text :loading="replaying" @click="replayRecord(scope.row, false)">Plan回放</el-button>
              <el-button size="small" text type="warning" :loading="replaying" @click="replayRecord(scope.row, true)">执行回放</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card v-if="replayCompare" shadow="never" class="result-card">
        <template #header>回放对比</template>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="原始状态">{{ replayCompare.originalStatus || '-' }}</el-descriptions-item>
          <el-descriptions-item label="回放状态">{{ replayCompare.replayStatus || '-' }}</el-descriptions-item>
          <el-descriptions-item label="执行模式">{{ replayCompare.execute ? '执行回放' : 'Plan 回放' }}</el-descriptions-item>
          <el-descriptions-item label="原始意图">{{ replayCompare.originalIntent || '-' }}</el-descriptions-item>
          <el-descriptions-item label="回放意图">{{ replayCompare.replayIntent || '-' }}</el-descriptions-item>
          <el-descriptions-item label="工具">{{ replayCompare.toolText || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-dialog v-model="recordDialogVisible" title="Runtime 调用详情" width="760px">
        <pre>{{ selectedRecordText }}</pre>
      </el-dialog>

      <AiTraceDrawer
        v-model:visible="executionDrawerVisible"
        :record="selectedExecutionRecord"
        :replay-result="selectedReplayResult"
      />
    </div>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import AiTraceDrawer from './components/AiTraceDrawer.vue'
import {
  exportOrchestratorDiagnostics,
  getOrchestratorConfig,
  getOrchestratorContract,
  getOrchestratorHealthDetail,
  getOrchestratorOpsSummary,
  getOrchestratorPersistence,
  getOrchestratorRecent,
  getOrchestratorRecord,
  getOrchestratorSnapshot,
  probeOrchestratorLlm,
  pruneOrchestratorAudit,
  replayOrchestratorRecord,
  runOrchestratorPlan,
  runOrchestratorSmoke
} from '../../api/orchestrator'
import { copyToClipboard } from '../../utils/clipboard'

const loading = ref(false)
const recentLoading = ref(false)
const smoking = ref(false)
const planning = ref(false)
const contractLoading = ref(false)
const replaying = ref(false)
const exportLoading = ref(false)
const healthLoading = ref(false)
const llmProbeLoading = ref(false)
const snapshotLoading = ref(false)
const pruneLoading = ref(false)
const summary = reactive<Record<string, any>>({})
const configResult = reactive<Record<string, any>>({})
const healthDetail = reactive<Record<string, any>>({})
const persistenceResult = reactive<Record<string, any>>({})
const langGraphLlmResult = reactive<Record<string, any>>({})
const recentRecords = ref<Record<string, any>[]>([])
const recentStatus = ref('')
const smokeResultText = ref('')
const planResultText = ref('')
const contractResultText = ref('')
const recordDialogVisible = ref(false)
const selectedRecordText = ref('')
const executionDrawerVisible = ref(false)
const selectedExecutionRecord = ref<Record<string, any> | null>(null)
const selectedReplayResult = ref<Record<string, any> | null>(null)
const replayCompare = ref<Record<string, any> | null>(null)

const smokeForm = reactive({
  tenantId: 'default',
  routeCode: 'G210',
  year: 2026,
  message: '请基于 G210 当前路况和知识库，给出一段道路养护建议摘要。'
})

const planForm = reactive({
  tenantId: 'default',
  mode: 'POLYGON',
  routeCode: 'G210',
  year: 2026,
  diseaseName: '裂缝',
  severity: '中度',
  message: '框选区域裂缝怎么处置？',
  geometryText: '{"type":"Polygon","coordinates":[]}'
})

const pruneForm = reactive({
  status: '',
  retainLatest: 20,
  includePersist: true
})

const provider = computed(() => String(summary.provider || ''))
const fallbackToNative = computed(() => Boolean(summary.fallbackToNative))
const allowWriteTools = computed(() => Boolean(summary.allowWriteTools))
const availableProviders = computed(() => Array.isArray(summary.availableProviders) ? summary.availableProviders : [])
const langgraphUrl = computed(() => String(summary.langgraphUrl || ''))
const langgraphReady = computed(() => value(summary, ['langgraphReady']) || {})
const runtimeSummary = computed(() => value(summary, ['runtimeSummary', 'body']) || {})
const toolGatewayDebug = computed(() => value(summary, ['toolGatewayDebug']) || {})
const contractDebug = computed(() => value(summary, ['contractDebug']) || {})
const contractBody = computed(() => value(contractDebug.value, ['body']) || contractDebug.value || {})
const readyOk = computed(() => Boolean(value(langgraphReady.value, ['ok']) && value(langgraphReady.value, ['body', 'status']) === 'UP'))
const toolGatewayOk = computed(() => Boolean(value(toolGatewayDebug.value, ['ok'])))
const contractOk = computed(() => Boolean(value(contractBody.value, ['ok'])))
const contractToolCount = computed(() => Number(value(contractBody.value, ['javaToolCount']) || 0))
const missingTools = computed(() => asStringArray(value(contractBody.value, ['missingInJava'])))
const blockedTools = computed(() => asStringArray(value(contractBody.value, ['blockedByRuntimeWhitelist'])))
const writeBlockedTools = computed(() => asStringArray(value(contractBody.value, ['writeBlocked'])))
const missingToolCount = computed(() => missingTools.value.length)
const configBody = computed(() => value(configResult, ['body']) || configResult)
const healthBody = computed(() => value(healthDetail, ['body']) || healthDetail)
const persistenceBody = computed(() => value(persistenceResult, ['body']) || persistenceResult)
const configWarnings = computed(() => {
  const warnings = value(configBody.value, ['warnings'])
  return Array.isArray(warnings) ? warnings : []
})
const configWarningCount = computed(() => Number(value(configBody.value, ['warningCount']) ?? configWarnings.value.length))
const configWarningTitle = computed(() => configWarnings.value
  .slice(0, 3)
  .map((item: any) => String(item?.code || item?.message || item))
  .join('；'))
const healthStatus = computed(() => String(value(healthBody.value, ['status']) || '-'))
const runtimeAppVersion = computed(() => {
  const app = value(configBody.value, ['app']) || value(healthBody.value, ['app']) || '-'
  const version = value(configBody.value, ['version']) || value(healthBody.value, ['version']) || '-'
  return `${app} / ${version}`
})
const configStrategyVersion = computed(() => String(
  value(configBody.value, ['safeConfig', 'strategyVersion'])
  || value(configBody.value, ['strategy', 'strategyVersion'])
  || value(healthBody.value, ['strategy', 'strategyVersion'])
  || '-'
))
const configFingerprint = computed(() => String(value(configBody.value, ['fingerprint']) || '-'))
const persistenceEnabled = computed(() => Boolean(value(persistenceBody.value, ['enabled'])))
const persistencePath = computed(() => String(value(persistenceBody.value, ['path']) || ''))
const persistenceSize = computed(() => formatBytes(Number(value(persistenceBody.value, ['sizeBytes']) || 0)))
const persistenceMaxSize = computed(() => formatBytes(Number(value(persistenceBody.value, ['maxBytes']) || 0)))
const persistenceWritten = computed(() => Number(value(persistenceBody.value, ['written']) || 0))
const persistenceLoaded = computed(() => Number(value(persistenceBody.value, ['loadedFromDisk']) || 0))
const persistenceLastError = computed(() => String(value(persistenceBody.value, ['lastError']) || ''))
const langGraphLlmBody = computed(() => value(langGraphLlmResult, ['body']) || langGraphLlmResult)
const langGraphLlmEnabled = computed(() => Boolean(value(langGraphLlmBody.value, ['enabled'])))
const langGraphLlmStatusText = computed(() => String(value(langGraphLlmBody.value, ['status']) || (langGraphLlmEnabled.value ? 'READY' : 'SKIPPED')))
const langGraphLlmTagType = computed(() => {
  const status = langGraphLlmStatusText.value
  if (status === 'SUCCESS') return 'success'
  if (status === 'SKIPPED' || status === 'DISABLED') return 'info'
  if (status === 'FAILED' || status.includes('TIMEOUT') || status.includes('ERROR')) return 'danger'
  return 'warning'
})
const langGraphLlmModel = computed(() => String(value(langGraphLlmBody.value, ['model']) || value(configBody.value, ['safeConfig', 'llmModel']) || ''))
const langGraphLlmRetry = computed(() => Boolean(value(langGraphLlmBody.value, ['compactRetryEnabled']) ?? value(configBody.value, ['safeConfig', 'llmCompactRetryEnabled'])))
const langGraphLlmBaseConfigured = computed(() => Boolean(value(langGraphLlmBody.value, ['baseUrlConfigured']) ?? value(configBody.value, ['safeConfig', 'llmBaseUrlConfigured'])))
const langGraphLlmKeyConfigured = computed(() => Boolean(value(langGraphLlmBody.value, ['apiKeyConfigured']) ?? value(configBody.value, ['safeConfig', 'llmApiKeyConfigured'])))
const langGraphLlmConnectTimeout = computed(() => Number(value(langGraphLlmBody.value, ['connectTimeoutSeconds']) || value(configBody.value, ['safeConfig', 'llmConnectTimeoutSeconds']) || 0))
const langGraphLlmReadTimeout = computed(() => Number(value(langGraphLlmBody.value, ['readTimeoutSeconds']) || value(configBody.value, ['safeConfig', 'llmReadTimeoutSeconds']) || 0))
const langGraphLlmMaxTokens = computed(() => Number(value(langGraphLlmBody.value, ['maxTokens']) || value(configBody.value, ['safeConfig', 'llmMaxTokens']) || 0))
const langGraphLlmCost = computed(() => {
  const cost = value(langGraphLlmBody.value, ['probeCostMs']) ?? value(langGraphLlmBody.value, ['costMs'])
  return cost == null ? '-' : `${cost}ms`
})
const langGraphLlmError = computed(() => String(value(langGraphLlmBody.value, ['errorMessage']) || value(langGraphLlmBody.value, ['diagnostics', 'errorMessage']) || ''))
const langGraphLlmPreview = computed(() => String(value(langGraphLlmBody.value, ['probeAnswerPreview']) || value(langGraphLlmBody.value, ['rawResponsePreview']) || value(langGraphLlmBody.value, ['diagnostics', 'rawResponsePreview']) || ''))

async function loadSummary() {
  loading.value = true
  try {
    const res = await getOrchestratorOpsSummary(10)
    Object.keys(summary).forEach((key) => delete summary[key])
    Object.assign(summary, res || {})
    const records = value(res, ['runtimeRecent', 'body', 'records'])
    recentRecords.value = Array.isArray(records) ? records : []
    contractResultText.value = JSON.stringify(value(res, ['contractDebug']) || {}, null, 2)
    await loadRuntimeDetails(false)
  } finally {
    loading.value = false
  }
}

async function loadRuntimeDetails(showMessage = false) {
  healthLoading.value = true
  try {
    const [config, health, persistence, llmProbe] = await Promise.allSettled([
      getOrchestratorConfig(),
      getOrchestratorHealthDetail(true, true),
      getOrchestratorPersistence(),
      probeOrchestratorLlm(false)
    ])
    if (config.status === 'fulfilled') assignReactive(configResult, config.value || {})
    if (health.status === 'fulfilled') assignReactive(healthDetail, health.value || {})
    if (persistence.status === 'fulfilled') assignReactive(persistenceResult, persistence.value || {})
    if (llmProbe.status === 'fulfilled') assignReactive(langGraphLlmResult, llmProbe.value || {})
    if (showMessage) {
      ElMessage.success('配置健康已刷新')
    }
  } finally {
    healthLoading.value = false
  }
}

async function probeLangGraphLlm(runProbe = true) {
  llmProbeLoading.value = true
  try {
    const res = await probeOrchestratorLlm(runProbe)
    assignReactive(langGraphLlmResult, res || {})
    const body = value(res, ['body']) || res || {}
    ElMessage.success(body?.status === 'SUCCESS' ? 'LangGraph LLM 探测通过' : 'LangGraph LLM 探测完成')
  } catch (error: any) {
    assignReactive(langGraphLlmResult, error?.response?.data || { error: error?.message || String(error) })
    ElMessage.error('LangGraph LLM 探测失败')
  } finally {
    llmProbeLoading.value = false
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

async function loadContract() {
  contractLoading.value = true
  try {
    const res = await getOrchestratorContract()
    contractResultText.value = JSON.stringify(res || {}, null, 2)
    summary.contractDebug = res || {}
    ElMessage.success('契约诊断已刷新')
  } catch (error: any) {
    contractResultText.value = JSON.stringify(error?.response?.data || error?.message || error, null, 2)
    ElMessage.error('契约诊断失败')
  } finally {
    contractLoading.value = false
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

async function runPlan() {
  const geometry = parseOptionalJson(planForm.geometryText)
  if (geometry === false) {
    ElMessage.error('Geometry 不是合法 JSON')
    return
  }
  planning.value = true
  try {
    const context: Record<string, any> = {
      tenantId: planForm.tenantId,
      mode: planForm.mode,
      routeCode: planForm.routeCode,
      year: planForm.year,
      mapObject: {
        routeCode: planForm.routeCode,
        diseaseName: planForm.diseaseName,
        severity: planForm.severity
      }
    }
    if (geometry) {
      context.geometry = geometry
    }
    const res = await runOrchestratorPlan({
      message: planForm.message,
      context,
      options: { topK: 3, traceId: `phase50-9-plan-${Date.now()}` }
    })
    planResultText.value = JSON.stringify(res, null, 2)
    ElMessage.success('Plan Debug 已完成')
  } catch (error: any) {
    planResultText.value = JSON.stringify(error?.response?.data || error?.message || error, null, 2)
    ElMessage.error('Plan Debug 失败，请查看结果')
  } finally {
    planning.value = false
  }
}

async function openRecord(row: Record<string, any>) {
  selectedRecordText.value = JSON.stringify(row, null, 2)
  selectedExecutionRecord.value = row
  selectedReplayResult.value = null
  executionDrawerVisible.value = true
  const id = row?.id || row?.traceId
  if (!id) return
  try {
    const res = await getOrchestratorRecord(String(id))
    selectedRecordText.value = JSON.stringify(res, null, 2)
    selectedExecutionRecord.value = value(res, ['body']) || value(res, ['data']) || res
  } catch (error: any) {
    selectedRecordText.value = JSON.stringify({ row, detailError: error?.response?.data || error?.message || error }, null, 2)
  }
}

async function replayRecord(row: Record<string, any>, execute: boolean) {
  const id = row?.id || row?.traceId
  if (!id) {
    ElMessage.warning('该记录缺少 id / traceId，无法回放')
    return
  }
  replaying.value = true
  try {
    const res = await replayOrchestratorRecord(String(id), execute)
    const body = value(res, ['body']) || value(res, ['data']) || res
    planResultText.value = JSON.stringify(res, null, 2)
    selectedRecordText.value = JSON.stringify(res, null, 2)
    selectedReplayResult.value = body
    selectedExecutionRecord.value = row
    executionDrawerVisible.value = true
    replayCompare.value = buildReplayCompare(row, body, execute)
    ElMessage.success(execute ? '执行回放已完成' : 'Plan 回放已完成')
    if (execute) {
      await loadSummary()
    }
  } catch (error: any) {
    const err = error?.response?.data || error?.message || error
    selectedRecordText.value = JSON.stringify(err, null, 2)
    recordDialogVisible.value = true
    ElMessage.error('回放失败，请查看详情')
  } finally {
    replaying.value = false
  }
}

function buildReplayCompare(original: Record<string, any>, replay: Record<string, any>, execute: boolean) {
  const response = value(replay, ['response']) || replay
  const data = value(response, ['data']) || {}
  return {
    execute,
    originalStatus: original.status,
    replayStatus: value(replay, ['status']) || value(response, ['status']) || 'SUCCESS',
    originalIntent: original.intent,
    replayIntent: data.intent || value(response, ['intent']) || value(replay, ['intent']),
    toolText: `${data.toolSuccessCount ?? original.toolSuccessCount ?? 0}/${data.toolTotalCount ?? original.toolTotalCount ?? 0}`
  }
}

async function exportDiagnostics() {
  exportLoading.value = true
  try {
    const res = await exportOrchestratorDiagnostics(30, recentStatus.value || undefined)
    const text = JSON.stringify(res, null, 2)
    selectedRecordText.value = text
    recordDialogVisible.value = true
    await copyToClipboard(text)
    ElMessage.success('诊断快照已复制，并已打开预览')
  } catch (error: any) {
    selectedRecordText.value = JSON.stringify(error?.response?.data || error?.message || error, null, 2)
    recordDialogVisible.value = true
    ElMessage.error('导出诊断失败')
  } finally {
    exportLoading.value = false
  }
}

async function exportSnapshot() {
  snapshotLoading.value = true
  try {
    const res = await getOrchestratorSnapshot(30, recentStatus.value || undefined)
    const text = JSON.stringify(res, null, 2)
    selectedRecordText.value = text
    recordDialogVisible.value = true
    await copyToClipboard(text)
    ElMessage.success('Runtime 快照已复制，并已打开预览')
  } catch (error: any) {
    selectedRecordText.value = JSON.stringify(error?.response?.data || error?.message || error, null, 2)
    recordDialogVisible.value = true
    ElMessage.error('导出 Runtime 快照失败')
  } finally {
    snapshotLoading.value = false
  }
}

async function pruneAudit() {
  pruneLoading.value = true
  try {
    const res = await pruneOrchestratorAudit({
      status: pruneForm.status || undefined,
      retainLatest: pruneForm.retainLatest,
      includePersist: pruneForm.includePersist
    })
    selectedRecordText.value = JSON.stringify(res, null, 2)
    recordDialogVisible.value = true
    ElMessage.success('审计记录已按条件清理')
    await loadRuntimeDetails(false)
    await loadRecent()
  } catch (error: any) {
    selectedRecordText.value = JSON.stringify(error?.response?.data || error?.message || error, null, 2)
    recordDialogVisible.value = true
    ElMessage.error('清理审计失败')
  } finally {
    pruneLoading.value = false
  }
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

async function copyPlanResult() {
  if (!planResultText.value) {
    ElMessage.warning('暂无 plan 结果')
    return
  }
  await copyToClipboard(planResultText.value)
  ElMessage.success('Plan 结果已复制')
}

function parseOptionalJson(text: string) {
  if (!text || !text.trim()) return null
  try {
    return JSON.parse(text)
  } catch (e) {
    return false
  }
}

function asStringArray(value: any) {
  return Array.isArray(value) ? value.map((item) => String(item)) : []
}

function value(obj: any, keys: string[]) {
  let cur = obj
  for (const key of keys) {
    if (cur == null) return undefined
    cur = cur[key]
  }
  return cur
}

function assignReactive(target: Record<string, any>, data: Record<string, any>) {
  Object.keys(target).forEach((key) => delete target[key])
  Object.assign(target, data || {})
}

function formatBytes(value: number) {
  if (!Number.isFinite(value) || value <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let size = value
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex += 1
  }
  return `${size.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`
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
  grid-template-columns: repeat(2, minmax(0, 1fr));
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

.mt {
  margin-top: 12px;
}

.smoke-form {
  max-width: 720px;
}

.inline-fields {
  display: grid;
  width: 100%;
  grid-template-columns: 1fr 160px;
  gap: 8px;
}

pre {
  max-height: 420px;
  overflow: auto;
  padding: 12px;
  border-radius: 12px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}

.diag-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.diag-row {
  padding: 10px 0;
  border-bottom: 1px solid #eef2f7;
}

.diag-row strong {
  text-align: right;
  word-break: break-all;
}

.lower-card {
  min-height: 280px;
}

.result-card {
  border-radius: 14px;
}

.status-select {
  width: 110px;
  margin-right: 8px;
}

.prune-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

@media (max-width: 1200px) {
  .metric-grid,
  .content-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .metric-grid,
  .content-grid,
  .inline-fields {
    grid-template-columns: 1fr;
  }
}
</style>
