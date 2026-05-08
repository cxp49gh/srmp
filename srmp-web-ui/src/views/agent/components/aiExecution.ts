export interface AiExecutionStep {
  key: string
  name?: string
  label: string
  status: string
  costMs?: number
  count?: number | string
  phase?: string
  error?: string
  data?: Record<string, any>
}

export interface AiExecutionTool {
  name: string
  success: boolean
  count?: number | string
  costMs?: number
  error?: string
}

export interface AiExecutionSnapshot {
  summary: {
    traceId?: string
    recordId?: string
    provider?: string
    intent?: string
    requestType?: string
    mapMode?: string
    status?: string
    costMs?: number
    fallback?: boolean
    toolTotalCount?: number
    toolSuccessCount?: number
    toolFailedCount?: number
    sourceCount?: number
  }
  answerMeta: Record<string, any>
  currentStep?: AiExecutionStep | null
  steps: AiExecutionStep[]
  tools: AiExecutionTool[]
  evidence: {
    sourceCount?: number
    knowledgeCount?: number
    businessCount?: number
    outlineCount?: number
    sources: Record<string, any>[]
  }
  quality?: Record<string, any>
  raw: Record<string, any>
  warnings: string[]
}

export interface AiExecutionInput {
  trace?: Record<string, any> | null
  answerMeta?: Record<string, any> | null
  toolResults?: any[] | null
  sources?: any[] | null
  record?: Record<string, any> | null
  replayResult?: Record<string, any> | null
  solution?: Record<string, any> | null
}

export function toAiExecutionSnapshot(input: AiExecutionInput): AiExecutionSnapshot | null {
  const trace = firstRecordOrNull(input.trace, extractTrace(input))
  const record = firstRecordOrNull(input.record) || null
  const replayResult = firstRecordOrNull(input.replayResult) || null
  const replayResponse = firstRecordOrNull(replayResult?.response, replayResult?.data?.response) || null
  const responsePreview = asRecord(record?.responsePreview)
  const responseData = firstRecord(responsePreview.data, replayResponse?.data, replayResult?.data)
  const solution = asRecord(input.solution)

  const answerMeta = firstRecord(
    input.answerMeta,
    solution.answerMeta,
    solution.answer_meta,
    responseData.answerMeta,
    responsePreview.answerMeta,
    replayResponse?.answerMeta,
    trace?.answerMeta
  )

  const toolResults = firstArray(
    input.toolResults,
    solution.toolResults,
    responseData.toolResults,
    replayResponse?.toolResults,
    responsePreview.toolResults,
    record?.toolResults
  )

  const sources = toRecordArray(firstArray(
    input.sources,
    solution.sources,
    solution.knowledgeSources,
    responseData.sources,
    responseData.knowledgeSources,
    replayResponse?.sources,
    responsePreview.sources
  ))

  const steps = normalizeSteps(firstArray(
    trace?.steps,
    record?.steps,
    replayResult?.steps,
    replayResponse?.trace?.steps,
    responsePreview.trace?.steps
  ))
  const currentStep = normalizeStep(trace?.currentStep || trace?.current_step, -1)
  const liveToolSummary = firstRecord(trace?.toolSummary, trace?.tool_summary)
  const liveSourceSummary = firstRecord(trace?.sourceSummary, trace?.source_summary)
  const tools = normalizeTools(toolResults)
  const quality = firstRecord(
    solution.quality,
    solution.qualityCheck,
    responseData.quality,
    responsePreview.quality,
    replayResponse?.quality,
    trace?.quality
  )

  if (!trace && !record && !replayResult && !Object.keys(solution).length) return null

  const summary = {
    traceId: stringValue(trace?.traceId || trace?.trace_id || replayResult?.traceId || replayResponse?.trace?.traceId || record?.traceId),
    recordId: stringValue(record?.id || replayResult?.sourceRecordId),
    provider: stringValue(trace?.orchestratorProvider || responseData.orchestratorProvider || record?.provider || record?.engine || replayResponse?.mode),
    intent: stringValue(responseData.intent || replayResponse?.intent || responsePreview.intent || replayResult?.intent || record?.intent),
    requestType: stringValue(trace?.requestType || trace?.request_type || record?.requestType),
    mapMode: stringValue(record?.mapMode || trace?.mode || responseData.mapMode),
    status: stringValue(record?.status || trace?.status || replayResult?.status || responsePreview.status || (replayResult ? 'SUCCESS' : undefined)),
    costMs: numberValue(trace?.costMs ?? trace?.totalCostMs ?? trace?.total_cost_ms ?? replayResult?.costMs ?? record?.costMs),
    fallback: booleanValue(answerMeta.fallback ?? trace?.fallback ?? record?.fallbackLike),
    toolTotalCount: numberValue(responseData.toolTotalCount ?? record?.toolTotalCount ?? liveToolSummary.planned ?? tools.length),
    toolSuccessCount: numberValue(responseData.toolSuccessCount ?? record?.toolSuccessCount ?? liveToolSummary.success ?? tools.filter((item) => item.success).length),
    toolFailedCount: numberValue(responseData.toolFailedCount ?? record?.toolFailedCount ?? liveToolSummary.failed ?? tools.filter((item) => !item.success).length),
    sourceCount: numberValue(responseData.sourceCount ?? responsePreview.sourceCount ?? record?.sourceCount ?? sources.length)
  }

  return {
    summary,
    answerMeta,
    currentStep,
    steps,
    tools,
    evidence: {
      sourceCount: summary.sourceCount,
      knowledgeCount: numberValue(liveSourceSummary.knowledge) ?? countSources(sources, 'KNOWLEDGE'),
      businessCount: numberValue(liveSourceSummary.business) ?? countSources(sources, 'BUSINESS'),
      outlineCount: numberValue(liveSourceSummary.outline) ?? countSources(sources, 'OUTLINE'),
      sources
    },
    quality,
    raw: compactRaw(input),
    warnings: buildWarnings(answerMeta, steps, summary.sourceCount, sources.length)
  }
}

function extractTrace(input: AiExecutionInput): Record<string, any> | null {
  return firstRecordOrNull(
    input.solution?.trace,
    input.replayResult?.trace,
    input.replayResult?.data?.trace,
    input.replayResult?.response?.trace,
    input.replayResult?.response?.data?.trace
  )
}

function normalizeSteps(steps: any[]): AiExecutionStep[] {
  return steps
    .map((step, index) => normalizeStep(step, index))
    .filter((step): step is AiExecutionStep => Boolean(step))
}

function normalizeStep(step: any, index: number): AiExecutionStep | null {
  const item = asRecord(step)
  if (!Object.keys(item).length) return null
  const key = stringValue(item.key || item.id || item.step_name || item.name || `step-${index}`) || `step-${index}`
  return {
    key,
    name: stringValue(item.name || item.node || item.step_name || key),
    label: stringValue(item.step_label || item.label || item.message || item.step_name || item.name || key) || key,
    status: stringValue(item.status || 'UNKNOWN') || 'UNKNOWN',
    costMs: numberValue(item.cost_ms ?? item.costMs ?? item.elapsedMs),
    count: item.hit_count ?? item.count ?? item.resultCount,
    phase: stringValue(item.phase || item.node || item.group),
    error: stringValue(item.error_message || item.error || item.errorMessage),
    data: firstRecord(item.data, item.detail, item.details)
  }
}

function normalizeTools(tools: any[]): AiExecutionTool[] {
  return tools.map((tool, index) => {
    const item = asRecord(tool)
    const name = stringValue(item.name || item.tool || item.toolName || item.type || `tool-${index}`) || `tool-${index}`
    const status = String(item.status || '').toUpperCase()
    const success = item.success !== false && status !== 'FAILED' && status !== 'ERROR'
    return {
      name,
      success,
      count: item.count ?? item.hitCount ?? item.resultCount ?? item.total,
      costMs: numberValue(item.costMs ?? item.cost_ms ?? item.elapsedMs),
      error: stringValue(item.error || item.errorMessage || item.message)
    }
  })
}

function buildWarnings(answerMeta: Record<string, any>, steps: AiExecutionStep[], sourceCount?: number, actualSources = 0): string[] {
  const warnings: string[] = []
  const llmStep = steps.find((step) => step.key === 'llm_answer' || step.label.includes('LLM') || step.label.includes('大模型'))
  if (answerMeta.llmSuccess === true && llmStep && llmStep.status !== 'SUCCESS') {
    warnings.push('answerMeta 显示 LLM 成功，但 LLM 步骤不是 SUCCESS。')
  }
  if (typeof sourceCount === 'number' && actualSources > 0 && sourceCount !== actualSources) {
    warnings.push('来源数量与实际来源列表数量存在差异。')
  }
  if (!Object.keys(answerMeta).length) {
    warnings.push('未返回 answerMeta，可能是旧任务、旧接口或未经过 LangGraph 管线。')
  }
  return warnings
}

function countSources(sources: Record<string, any>[], type: string): number {
  return sources.filter((item) => {
    const sourceType = String(item.sourceType || item.type || item.source || '').toUpperCase()
    return sourceType.includes(type)
  }).length
}

function firstRecord(...values: any[]): Record<string, any> {
  return firstRecordOrNull(...values) || {}
}

function firstRecordOrNull(...values: any[]): Record<string, any> | null {
  for (const value of values) {
    const record = asRecord(value)
    if (Object.keys(record).length) return record
  }
  return null
}

function firstArray(...values: any[]): any[] {
  for (const value of values) {
    if (Array.isArray(value)) return value
  }
  return []
}

function toRecordArray(values: any[]): Record<string, any>[] {
  return values.map((item) => asRecord(item)).filter((item) => Object.keys(item).length)
}

function asRecord(value: any): Record<string, any> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
}

function stringValue(value: any): string | undefined {
  if (value === undefined || value === null || value === '') return undefined
  return String(value)
}

function numberValue(value: any): number | undefined {
  const num = Number(value)
  return Number.isFinite(num) ? num : undefined
}

function booleanValue(value: any): boolean | undefined {
  if (value === undefined || value === null || value === '') return undefined
  if (typeof value === 'boolean') return value
  return String(value).toLowerCase() === 'true'
}

function compactRaw(input: AiExecutionInput): Record<string, any> {
  return {
    trace: input.trace || undefined,
    answerMeta: input.answerMeta || undefined,
    toolResults: input.toolResults || undefined,
    sources: input.sources || undefined,
    record: input.record || undefined,
    replayResult: input.replayResult || undefined,
    solution: input.solution || undefined
  }
}
