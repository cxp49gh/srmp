export type WaitFeedback = {
  elapsedLabel: string
  longWait: boolean
  title: string
  message: string
}

export type RunTimingSummary = {
  elapsedLabel: string
  costMs: number
  llmStatus: string
  llmModel: string
  traceId: string
}

export type LangGraphDiagnostics = {
  status: string
  provider: string
  runtimeOk: boolean
  toolGatewayOk: boolean
  contractOk: boolean
  llmEnabled: boolean
  llmModel: string
  llmReadTimeoutSeconds: number | null
  successRateLabel: string
  avgCostLabel: string
  lastStatus: string
  total: number
  failed: number
  costLabel: string
}

export function formatElapsedMs(ms: number) {
  const safeMs = Math.max(0, Number(ms) || 0)
  if (safeMs >= 60_000) {
    const minutes = Math.floor(safeMs / 60_000)
    const seconds = Math.floor((safeMs % 60_000) / 1000)
    return `${minutes}m${String(seconds).padStart(2, '0')}s`
  }
  if (safeMs < 1000) {
    return `${(Math.floor(safeMs / 100) / 10).toFixed(1)}s`
  }
  return `${(safeMs / 1000).toFixed(1)}s`
}

export function buildWaitFeedback(elapsedMs: number, thresholdMs = 8000): WaitFeedback {
  const longWait = elapsedMs >= thresholdMs
  return {
    elapsedLabel: formatElapsedMs(elapsedMs),
    longWait,
    title: longWait ? 'LangGraph 仍在生成' : 'LangGraph 正在处理',
    message: longWait
      ? '真实 LLM 调用可能需要 30s 左右，可稍后通过 Trace 查看各步骤耗时。'
      : '正在识别意图、检索证据并生成回答。'
  }
}

export function summarizeRunTiming(response: Record<string, any> = {}): RunTimingSummary {
  const trace = objectValue(response.trace)
  const data = objectValue(response.data)
  const meta = objectValue(response.answerMeta) || objectValue(data.answerMeta)
  const costMs = numberValue(data.remoteCostMs) ?? numberValue(trace.costMs) ?? numberValue(data.costMs) ?? 0
  return {
    elapsedLabel: formatElapsedMs(costMs),
    costMs,
    llmStatus: stringValue(meta.llmStatus),
    llmModel: stringValue(meta.llmModel),
    traceId: stringValue(trace.traceId || trace.trace_id || trace.id)
  }
}

export function normalizeLangGraphDiagnostics(input: {
  healthDetail?: Record<string, any>
  summary?: Record<string, any>
}): LangGraphDiagnostics {
  const healthEnvelope = objectValue(input.healthDetail)
  const healthBody = objectValue(healthEnvelope.body) || objectValue(healthEnvelope.data?.body) || objectValue(healthEnvelope.data)
  const summary = objectValue(input.summary)
  const runtimeSummaryEnvelope = objectValue(summary.runtimeSummary)
  const runtimeSummary = objectValue(runtimeSummaryEnvelope.body) || runtimeSummaryEnvelope
  const safeConfig = objectValue(healthBody.config?.safeConfig)
  const checks = Array.isArray(healthBody.checks) ? healthBody.checks : []
  const toolGatewayCheck = checks.find((item: any) => item?.name === 'java.toolGateway.list')
  const contractCheck = checks.find((item: any) => item?.name === 'java.toolGateway.contract')
  const healthCostMs = numberValue(healthEnvelope.costMs) ?? 0
  const successRate = numberValue(runtimeSummary.successRate)
  const avgCostMs = numberValue(runtimeSummary.avgCostMs) ?? 0

  return {
    status: stringValue(healthBody.status || summary.status, '-'),
    provider: stringValue(summary.provider, '-'),
    runtimeOk: Boolean(healthEnvelope.ok ?? summary.langgraphReady?.ok),
    toolGatewayOk: Boolean(toolGatewayCheck?.ok ?? summary.toolGatewayDebug?.ok),
    contractOk: Boolean(contractCheck?.ok ?? summary.contractDebug?.ok),
    llmEnabled: Boolean(safeConfig.useLlm),
    llmModel: stringValue(safeConfig.llmModel),
    llmReadTimeoutSeconds: numberValue(safeConfig.llmReadTimeoutSeconds),
    successRateLabel: typeof successRate === 'number' ? `${Math.round(successRate * 100)}%` : '-',
    avgCostLabel: avgCostMs > 0 ? formatElapsedMs(avgCostMs) : '-',
    lastStatus: stringValue(runtimeSummary.lastStatus, '-'),
    total: numberValue(runtimeSummary.total) ?? 0,
    failed: numberValue(runtimeSummary.failed) ?? 0,
    costLabel: healthCostMs > 0 ? `${Math.round(healthCostMs)}ms` : '-'
  }
}

function objectValue(value: any): Record<string, any> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
}

function numberValue(value: any): number | null {
  const num = Number(value)
  return Number.isFinite(num) ? num : null
}

function stringValue(value: any, fallback = ''): string {
  if (value === undefined || value === null || value === '') return fallback
  return String(value)
}
