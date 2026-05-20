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
  fallbackReason?: string
  diagnostic?: string
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
  businessScope: Record<string, any>
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

const INTERNAL_DIAGNOSTIC_KEYS = new Set([
  'strategyVersion',
  'strategy_version',
  'orchestratorStrategy',
  'orchestrator_strategy',
  'parityVersion',
  'parity_version'
])

export function toAiExecutionSnapshot(input: AiExecutionInput): AiExecutionSnapshot | null {
  const trace = firstRecordOrNull(input.trace, extractTrace(input))
  const record = firstRecordOrNull(input.record) || null
  const replayResult = firstRecordOrNull(input.replayResult) || null
  const replayResponse = firstRecordOrNull(replayResult?.response, replayResult?.data?.response) || null
  const responsePreview = asRecord(record?.responsePreview || record?.response_preview)
  const responseData = firstRecord(responsePreview.data, replayResponse?.data, replayResult?.data)
  const solution = asRecord(input.solution)
  const rawSteps = firstArray(
    trace?.steps,
    record?.steps,
    replayResult?.steps,
    replayResponse?.trace?.steps,
    responsePreview.trace?.steps
  )

  const answerMeta = sanitizeInternalDiagnostics(firstRecord(
    input.answerMeta,
    solution.answerMeta,
    solution.answer_meta,
    responseData.answerMeta,
    responseData.answer_meta,
    responsePreview.answerMeta,
    responsePreview.answer_meta,
    replayResponse?.answerMeta,
    replayResponse?.answer_meta,
    trace?.answerMeta,
    trace?.answer_meta,
    extractAnswerMetaFromSteps(rawSteps)
  )) as Record<string, any>

  const toolResults = firstArray(
    input.toolResults,
    solution.toolResults,
    solution.tool_results,
    responseData.toolResults,
    responseData.tool_results,
    replayResponse?.toolResults,
    replayResponse?.tool_results,
    responsePreview.toolResults,
    responsePreview.tool_results,
    record?.toolResults,
    record?.tool_results,
    extractToolsFromSteps(rawSteps)
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

  const steps = normalizeSteps(rawSteps)
  const currentStep = normalizeStep(trace?.currentStep || trace?.current_step, -1)
  const liveToolSummary = firstRecord(trace?.toolSummary, trace?.tool_summary)
  const liveSourceSummary = firstRecord(trace?.sourceSummary, trace?.source_summary)
  const evidenceData = extractEvidenceFromSteps(rawSteps)
  const tools = normalizeTools(toolResults)
  const businessScope = sanitizeInternalDiagnostics(firstRecord(
    record?.businessScope,
    record?.business_scope,
    trace?.businessScope,
    trace?.business_scope,
    responseData.businessScope,
    responseData.business_scope,
    responsePreview.businessScope,
    responsePreview.business_scope
  )) as Record<string, any>
  const quality = sanitizeInternalDiagnostics(firstRecord(
    solution.quality,
    solution.qualityCheck,
    solution.quality_check,
    responseData.quality,
    responsePreview.quality,
    replayResponse?.quality,
    trace?.quality
  )) as Record<string, any>

  if (!trace && !record && !replayResult && !Object.keys(solution).length) return null

  const summary = {
    traceId: stringValue(trace?.traceId || trace?.trace_id || replayResult?.traceId || replayResult?.trace_id || replayResponse?.trace?.traceId || record?.traceId || record?.trace_id),
    recordId: stringValue(record?.id || replayResult?.sourceRecordId || replayResult?.source_record_id),
    provider: stringValue(trace?.orchestratorProvider || trace?.orchestrator_provider || responseData.orchestratorProvider || responseData.orchestrator_provider || record?.provider || record?.engine || replayResponse?.mode),
    intent: stringValue(responseData.intent || replayResponse?.intent || responsePreview.intent || replayResult?.intent || record?.intent),
    requestType: stringValue(trace?.requestType || trace?.request_type || record?.requestType || record?.request_type),
    mapMode: stringValue(record?.mapMode || record?.map_mode || trace?.mode || responseData.mapMode || responseData.map_mode),
    status: stringValue(record?.status || trace?.status || replayResult?.status || responsePreview.status || (replayResult ? 'SUCCESS' : undefined)),
    costMs: numberValue(trace?.costMs ?? trace?.cost_ms ?? trace?.totalCostMs ?? trace?.total_cost_ms ?? replayResult?.costMs ?? replayResult?.cost_ms ?? record?.costMs ?? record?.cost_ms ?? record?.total_cost_ms),
    fallback: booleanValue(answerMeta.fallback ?? trace?.fallback ?? record?.fallbackLike ?? record?.fallback_like ?? record?.fallback),
    toolTotalCount: numberValue(responseData.toolTotalCount ?? responseData.tool_total_count ?? record?.toolTotalCount ?? record?.tool_total_count ?? liveToolSummary.planned ?? evidenceData.toolTotalCount ?? tools.length),
    toolSuccessCount: numberValue(responseData.toolSuccessCount ?? responseData.tool_success_count ?? record?.toolSuccessCount ?? record?.tool_success_count ?? liveToolSummary.success ?? evidenceData.toolSuccessCount ?? tools.filter((item) => item.success).length),
    toolFailedCount: numberValue(responseData.toolFailedCount ?? responseData.tool_failed_count ?? record?.toolFailedCount ?? record?.tool_failed_count ?? liveToolSummary.failed ?? evidenceData.toolFailedCount ?? tools.filter((item) => !item.success).length),
    sourceCount: numberValue(responseData.sourceCount ?? responseData.source_count ?? responsePreview.sourceCount ?? responsePreview.source_count ?? record?.sourceCount ?? record?.source_count ?? evidenceData.sourceCount ?? sources.length)
  }

  return {
    summary,
    answerMeta,
    currentStep,
    steps,
    tools,
    evidence: {
      sourceCount: summary.sourceCount,
      knowledgeCount: numberValue(liveSourceSummary.knowledge) ?? evidenceData.knowledgeHitCount ?? countSources(sources, 'KNOWLEDGE'),
      businessCount: numberValue(liveSourceSummary.business) ?? evidenceData.businessHitCount ?? countSources(sources, 'BUSINESS'),
      outlineCount: numberValue(liveSourceSummary.outline) ?? evidenceData.outlineHitCount ?? countSources(sources, 'OUTLINE'),
      sources
    },
    quality,
    businessScope,
    raw: sanitizeInternalDiagnostics(compactRaw(input)) as Record<string, any>,
    warnings: buildWarnings(answerMeta, steps, summary.sourceCount, sources.length, tools, summary.status, currentStep)
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
    data: sanitizeInternalDiagnostics(firstRecord(item.data, item.step_data, item.detail, item.details)) as Record<string, any>
  }
}

function extractAnswerMetaFromSteps(steps: any[]): Record<string, any> {
  for (const step of steps) {
    const item = asRecord(step)
    const data = firstRecord(item.data, item.step_data, item.detail, item.details)
    const meta = firstRecord(data.answerMeta, data.answer_meta)
    if (Object.keys(meta).length) return meta
  }

  for (const step of steps) {
    const item = asRecord(step)
    const data = firstRecord(item.data, item.step_data, item.detail, item.details)
    const stepName = String(item.name || item.step_name || item.node || '').toLowerCase()
    const hasLlmData = data.llmStatus || data.llm_status || data.llmSuccess !== undefined || data.llm_success !== undefined || stepName.includes('llm')
    if (!hasLlmData) continue
    const status = stringValue(data.llmStatus || data.llm_status || item.status)
    const llmSuccess = booleanValue(data.llmSuccess ?? data.llm_success) ?? (status === 'SUCCESS')
    return {
      answerSource: data.answerSource || data.answer_source || (llmSuccess ? 'LLM' : undefined),
      answerSourceLabel: data.answerSourceLabel || data.answer_source_label,
      llmSuccess,
      llmStatus: status,
      llmModel: data.llmModel || data.llm_model || data.model,
      fallback: data.fallback,
      fallbackReason: data.fallbackReason || data.fallback_reason || data.reason || data.errorMessage || data.error_message || item.error_message || item.error,
      errorType: data.errorType || data.error_type,
      errorMessage: data.errorMessage || data.error_message || item.error_message || item.error
    }
  }

  return {}
}

function extractToolsFromSteps(steps: any[]): any[] {
  for (const step of steps) {
    const data = firstRecord(asRecord(step).data, asRecord(step).step_data, asRecord(step).detail, asRecord(step).details)
    const toolSummary = firstArray(data.toolSummary, data.tool_summary)
    if (toolSummary.length) return toolSummary
  }

  for (const step of steps) {
    const data = firstRecord(asRecord(step).data, asRecord(step).step_data, asRecord(step).detail, asRecord(step).details)
    const tools = firstArray(data.toolResults, data.tool_results, data.tools)
    if (!tools.length) continue
    if (tools.every((item) => typeof item === 'string')) {
      return tools.map((name) => ({ toolName: name, success: true }))
    }
    return tools
  }

  return []
}

function extractEvidenceFromSteps(steps: any[]): Record<string, number | undefined> {
  for (const step of steps) {
    const data = firstRecord(asRecord(step).data, asRecord(step).step_data, asRecord(step).detail, asRecord(step).details)
    const evidence = firstRecord(data.evidence)
    const sourceData = Object.keys(evidence).length ? evidence : data
    const businessHitCount = numberValue(sourceData.businessHitCount ?? sourceData.business_hit_count)
    const knowledgeHitCount = numberValue(sourceData.knowledgeHitCount ?? sourceData.knowledge_hit_count)
    const outlineHitCount = numberValue(sourceData.outlineHitCount ?? sourceData.outline_hit_count)
    const toolSuccessCount = numberValue(sourceData.toolSuccessCount ?? sourceData.tool_success_count)
    const toolFailedCount = numberValue(sourceData.toolFailedCount ?? sourceData.tool_failed_count)
    if ([businessHitCount, knowledgeHitCount, outlineHitCount, toolSuccessCount, toolFailedCount].some((value) => value !== undefined)) {
      return {
        businessHitCount,
        knowledgeHitCount,
        outlineHitCount,
        toolSuccessCount,
        toolFailedCount,
        toolTotalCount: (toolSuccessCount || 0) + (toolFailedCount || 0),
        sourceCount: (businessHitCount || 0) + (knowledgeHitCount || 0) + (outlineHitCount || 0)
      }
    }
  }
  return {}
}

function sanitizeInternalDiagnostics(value: any): any {
  if (Array.isArray(value)) return value.map((item) => sanitizeInternalDiagnostics(item))
  if (!value || typeof value !== 'object') return value

  const next: Record<string, any> = {}
  Object.entries(value).forEach(([key, item]) => {
    if (INTERNAL_DIAGNOSTIC_KEYS.has(key)) return
    next[key] = sanitizeInternalDiagnostics(item)
  })
  return next
}

function normalizeTools(tools: any[]): AiExecutionTool[] {
  return tools.map((tool, index) => {
    const item = asRecord(tool)
    const data = firstRecord(item.data, item.detail, item.details)
    const name = stringValue(item.name || item.tool || item.toolName || item.type || `tool-${index}`) || `tool-${index}`
    const status = String(item.status || '').toUpperCase()
    const success = item.success !== false && status !== 'FAILED' && status !== 'ERROR'
    const fallbackReason = stringValue(item.fallbackReason || item.fallback_reason || item.reason || data.fallbackReason || data.fallback_reason || data.reason)
    const diagnostic = knowledgeFallbackDisplayText(fallbackReason)
    return {
      name,
      success,
      count: item.count ?? item.returnedCount ?? item.returned_count ?? item.hitCount ?? item.resultCount ?? item.total ?? data.count ?? data.hitCount,
      costMs: numberValue(item.costMs ?? item.cost_ms ?? item.elapsedMs),
      error: stringValue(item.error || item.errorMessage || item.message),
      fallbackReason,
      diagnostic
    }
  })
}

function buildWarnings(
  answerMeta: Record<string, any>,
  steps: AiExecutionStep[],
  sourceCount?: number,
  actualSources = 0,
  tools: AiExecutionTool[] = [],
  status?: string,
  currentStep?: AiExecutionStep | null
): string[] {
  const warnings: string[] = []
  const llmStep = steps.find((step) => step.key === 'llm_answer' || step.label.includes('LLM') || step.label.includes('大模型'))
  if (answerMeta.llmSuccess === true && llmStep && llmStep.status !== 'SUCCESS') {
    warnings.push('answerMeta 显示 LLM 成功，但 LLM 步骤不是 SUCCESS。')
  }
  if (typeof sourceCount === 'number' && actualSources > 0 && sourceCount !== actualSources) {
    warnings.push('来源数量与实际来源列表数量存在差异。')
  }
  if (!Object.keys(answerMeta).length && !isExecutionInProgress(status, currentStep)) {
    warnings.push('该记录没有模型来源元数据；请结合业务范围、工具调用和原始诊断继续排查。')
  }
  const knowledgeTool = tools.find((tool) => tool.name === 'knowledge.retrieve' && tool.diagnostic)
  if (knowledgeTool?.diagnostic) {
    const reason = String(knowledgeTool.fallbackReason || '').toLowerCase()
    const prefix = reason === 'no embedded chunks' ? '知识库向量未就绪' : '知识库检索提示'
    warnings.push(`${prefix}：${knowledgeTool.diagnostic}`)
  }
  return warnings
}

function isExecutionInProgress(status?: string, currentStep?: AiExecutionStep | null): boolean {
  const values = [status, currentStep?.status].map((item) => String(item || '').toUpperCase())
  return values.some((item) => ['RUNNING', 'PROCESSING', 'PENDING', 'QUEUED'].includes(item))
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

function knowledgeFallbackDisplayText(value: any): string {
  const reason = String(value ?? '').trim()
  if (!reason) return ''
  const normalized = reason.toLowerCase()
  if (normalized === 'no embedded chunks') {
    return '暂无可用向量切片，AI 已尝试关键词检索；请在 AI 运维总览执行补向量或同步入库。'
  }
  if (normalized === 'query is empty') return '知识库检索词为空，知识库未参与本次回答。'
  if (normalized.includes('pgvector')) return '向量检索扩展不可用，AI 已尝试关键词检索；请检查 pgvector 与向量配置。'
  return `知识检索已切换兜底策略：${reason}`
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
