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
  retrieval?: {
    query?: string
    originalQuery?: string
    rewrittenQuery?: string
    topK?: number
    filters: Record<string, any>
    sourceTypes: string[]
  }
}

export interface AiExecutionRepairAction {
  key: string
  label: string
  path: string
  description: string
  type?: 'primary' | 'success' | 'warning' | 'info' | 'danger'
}

export type AiExecutionDiagnosisSeverity = 'success' | 'info' | 'warning' | 'danger'

export interface AiExecutionDiagnosisTag {
  label: string
  value: string
  type?: AiExecutionDiagnosisSeverity
}

export interface AiExecutionDiagnosis {
  severity: AiExecutionDiagnosisSeverity
  title: string
  summary: string
  cause: string
  actions: AiExecutionRepairAction[]
  tags: AiExecutionDiagnosisTag[]
}

export interface AiExecutionPlanWarning {
  level: string
  code: string
  message: string
}

export interface AiExecutionPolicyCheck {
  status: string
  code: string
  severity: string
  message: string
  expectedToolNames: string[]
  actualToolNames: string[]
  missingToolNames: string[]
  extraToolNames: string[]
  prohibitedToolNames: string[]
}

export interface AiExecutionPlanExecution {
  available: boolean
  status: string
  planTraceId?: string
  runTraceId?: string
  plannedAction?: string
  actualAction?: string
  plannedIntent?: string
  actualIntent?: string
  plannedToolNames: string[]
  actualToolNames: string[]
  missingToolNames: string[]
  extraToolNames: string[]
  adaptiveExtraToolNames: string[]
  adaptiveReason?: string
  plannedSourceTypes: string[]
  actualSourceTypes: string[]
  missingSourceTypes: string[]
  warnings: AiExecutionPlanWarning[]
  raw: Record<string, any>
}

export interface AiExecutionEvidenceReuse {
  status?: string
  evidenceSnapshotId?: string
  basedOnAnalysisTraceId?: string
  scopeFingerprint?: string
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
    capabilityId?: string
    capabilityName?: string
    capabilityIntent?: string
    capabilityContextUsage?: string
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
  capability: Record<string, any>
  planExecution: AiExecutionPlanExecution
  evidenceReuse: AiExecutionEvidenceReuse
  policyStatus: string
  policyChecks: AiExecutionPolicyCheck[]
  raw: Record<string, any>
  warnings: string[]
  repairActions: AiExecutionRepairAction[]
  diagnosis: AiExecutionDiagnosis
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
  const rawResponse = asRecord(record?.rawResponse || record?.raw_response)
  const responseData = firstRecord(responsePreview.data, rawResponse.data, replayResponse?.data, replayResult?.data)
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
    rawResponse.answerMeta,
    rawResponse.answer_meta,
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
    rawResponse.toolResults,
    rawResponse.tool_results,
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
    rawResponse.sources,
    rawResponse.knowledgeSources,
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
  const capability = sanitizeInternalDiagnostics(firstRecord(
    answerMeta.capability,
    trace?.capability,
    rawResponse.capability,
    rawResponse.data?.capability,
    responseData.capability,
    responsePreview.capability,
    replayResponse?.trace?.capability,
    record?.capability
  )) as Record<string, any>
  const planExecution = normalizePlanExecution(firstRecord(
    trace?.planExecution,
    trace?.plan_execution,
    record?.planExecution,
    record?.plan_execution,
    rawResponse.planExecution,
    rawResponse.plan_execution,
    rawResponse.data?.planExecution,
    rawResponse.data?.plan_execution,
    responseData.planExecution,
    responseData.plan_execution,
    responsePreview.planExecution,
    responsePreview.plan_execution,
    responsePreview.data?.planExecution,
    responsePreview.data?.plan_execution,
    replayResult?.planExecution,
    replayResult?.plan_execution,
    replayResult?.data?.planExecution,
    replayResult?.data?.plan_execution,
    replayResponse?.planExecution,
    replayResponse?.plan_execution,
    replayResponse?.data?.planExecution,
    replayResponse?.data?.plan_execution,
    solution.planExecution,
    solution.plan_execution
  ))

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
    capabilityId: stringValue(answerMeta.capabilityId || answerMeta.capability_id || capability.capabilityId || capability.capability_id || responseData.capabilityId || responseData.capability_id || record?.capabilityId || record?.capability_id),
    capabilityName: stringValue(answerMeta.capabilityName || answerMeta.capability_name || capability.name || capability.capabilityName || capability.capability_name || record?.capabilityName || record?.capability_name),
    capabilityIntent: stringValue(answerMeta.capabilityIntent || answerMeta.capability_intent || capability.intent || record?.capabilityIntent || record?.capability_intent),
    capabilityContextUsage: stringValue(answerMeta.capabilityContextUsage || answerMeta.capability_context_usage || capability.contextUsage || capability.context_usage),
    toolTotalCount: numberValue(responseData.toolTotalCount ?? responseData.tool_total_count ?? record?.toolTotalCount ?? record?.tool_total_count ?? liveToolSummary.planned ?? evidenceData.toolTotalCount ?? tools.length),
    toolSuccessCount: numberValue(responseData.toolSuccessCount ?? responseData.tool_success_count ?? record?.toolSuccessCount ?? record?.tool_success_count ?? liveToolSummary.success ?? evidenceData.toolSuccessCount ?? tools.filter((item) => item.success).length),
    toolFailedCount: numberValue(responseData.toolFailedCount ?? responseData.tool_failed_count ?? record?.toolFailedCount ?? record?.tool_failed_count ?? liveToolSummary.failed ?? evidenceData.toolFailedCount ?? tools.filter((item) => !item.success).length),
    sourceCount: numberValue(responseData.sourceCount ?? responseData.source_count ?? responsePreview.sourceCount ?? responsePreview.source_count ?? record?.sourceCount ?? record?.source_count ?? evidenceData.sourceCount ?? sources.length)
  }
  const persistedPolicyChecks = normalizePolicyChecks(firstArray(
    answerMeta.policyChecks,
    answerMeta.policy_checks,
    trace?.toolPolicy?.policyChecks,
    trace?.tool_policy?.policy_checks,
    responseData.toolPolicy?.policyChecks,
    responseData.tool_policy?.policy_checks,
    responsePreview.toolPolicy?.policyChecks,
    responsePreview.tool_policy?.policy_checks,
    record?.toolPolicy?.policyChecks,
    record?.tool_policy?.policy_checks
  ))
  const policyChecks = persistedPolicyChecks.length ? persistedPolicyChecks : buildExecutionPolicyChecks(capability, tools, planExecution)
  const persistedPolicyStatus = stringValue(
    answerMeta.policyStatus,
    answerMeta.policy_status,
    trace?.toolPolicy?.policyStatus,
    trace?.tool_policy?.policy_status,
    responseData.toolPolicy?.policyStatus,
    responseData.tool_policy?.policy_status,
    responsePreview.toolPolicy?.policyStatus,
    responsePreview.tool_policy?.policy_status,
    record?.toolPolicy?.policyStatus,
    record?.tool_policy?.policy_status
  )
  const policyStatus = persistedPolicyStatus || (policyChecks.some((item) => item.status === 'FAIL') ? 'FAIL' : 'PASS')
  const evidence = {
    sourceCount: summary.sourceCount,
    knowledgeCount: numberValue(liveSourceSummary.knowledge) ?? evidenceData.knowledgeHitCount ?? countSources(sources, 'KNOWLEDGE'),
    businessCount: numberValue(liveSourceSummary.business) ?? evidenceData.businessHitCount ?? countSources(sources, 'BUSINESS'),
    outlineCount: numberValue(liveSourceSummary.outline) ?? evidenceData.outlineHitCount ?? countSources(sources, 'OUTLINE'),
    sources
  }
  const evidenceReuse = extractEvidenceReuse(answerMeta, responseData, trace || {})
  const warnings = buildWarnings(answerMeta, steps, summary.sourceCount, sources.length, tools, summary.status, currentStep, policyChecks)
  const repairActions = buildRepairActions(tools, answerMeta)

  return {
    summary,
    answerMeta,
    currentStep,
    steps,
    tools,
    evidence,
    quality,
    businessScope,
    capability,
    planExecution,
    evidenceReuse,
    policyStatus,
    policyChecks,
    raw: sanitizeInternalDiagnostics(compactRaw(input)) as Record<string, any>,
    warnings,
    repairActions,
    diagnosis: buildDiagnosis({
      summary,
      answerMeta,
      currentStep,
      tools,
      evidence,
      capability,
      planExecution,
      evidenceReuse,
      policyChecks,
      repairActions
    })
  }
}

function extractTrace(input: AiExecutionInput): Record<string, any> | null {
  return firstRecordOrNull(
    input.solution?.trace,
    input.record?.rawResponse?.trace,
    input.record?.raw_response?.trace,
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
    const rawResult = firstRecord(item.rawResult, item.raw_result)
    const data = firstRecord(item.data, item.detail, item.details, rawResult.data, rawResult)
    const name = stringValue(item.name || item.tool || item.toolName || item.type || `tool-${index}`) || `tool-${index}`
    const status = String(item.status || '').toUpperCase()
    const success = item.success !== false && status !== 'FAILED' && status !== 'ERROR'
    const fallbackReason = stringValue(item.fallbackReason || item.fallback_reason || item.reason || data.fallbackReason || data.fallback_reason || data.reason)
    const diagnostic = knowledgeFallbackDisplayText(fallbackReason)
    const retrieval = normalizeRetrievalRequest(firstRecord(item.request, item.args, data.request, data.request_summary, rawResult.request, rawResult.args))
    return {
      name,
      success,
      count: item.count ?? item.returnedCount ?? item.returned_count ?? item.hitCount ?? item.resultCount ?? item.total ?? data.count ?? data.hitCount ?? rawResult.count,
      costMs: numberValue(item.costMs ?? item.cost_ms ?? item.elapsedMs ?? data.costMs ?? data.cost_ms ?? rawResult.costMs ?? rawResult.cost_ms),
      error: stringValue(item.error || item.errorMessage || item.message),
      fallbackReason,
      diagnostic,
      retrieval
    }
  })
}

function normalizeRetrievalRequest(value: Record<string, any>): AiExecutionTool['retrieval'] | undefined {
  if (!Object.keys(value).length) return undefined
  const filters = firstRecord(value.filters, value.filter)
  const sourceTypes = stringList(value.sourceTypes || value.source_types)
  const topK = numberValue(value.topK ?? value.top_k)
  const retrieval = {
    query: stringValue(value.query),
    originalQuery: stringValue(value.originalQuery, value.original_query),
    rewrittenQuery: stringValue(value.rewrittenQuery, value.rewritten_query),
    topK,
    filters,
    sourceTypes
  }
  if (!retrieval.query && !retrieval.originalQuery && !retrieval.rewrittenQuery && topK === undefined && !Object.keys(filters).length && !sourceTypes.length) {
    return undefined
  }
  return retrieval
}

function buildWarnings(
  answerMeta: Record<string, any>,
  steps: AiExecutionStep[],
  sourceCount?: number,
  actualSources = 0,
  tools: AiExecutionTool[] = [],
  status?: string,
  currentStep?: AiExecutionStep | null,
  policyChecks: AiExecutionPolicyCheck[] = []
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
    const prefix = reason === 'no embedded chunks'
      ? '知识库向量未就绪'
      : reason === 'no knowledge chunks'
        ? '知识库暂无切片'
        : '知识库检索提示'
    warnings.push(`${prefix}：${knowledgeTool.diagnostic}`)
  }
  const failedPolicyChecks = policyChecks.filter((item) => item.status === 'FAIL')
  if (failedPolicyChecks.length) {
    const details = failedPolicyChecks
      .slice(0, 3)
      .map((item) => item.message || item.code)
      .filter(Boolean)
      .join('；')
    warnings.push(`能力策略违规：${details || '实际工具调用不符合当前能力策略。'}`)
  }
  return warnings
}

function buildDiagnosis(input: {
  summary: AiExecutionSnapshot['summary']
  answerMeta: Record<string, any>
  currentStep?: AiExecutionStep | null
  tools: AiExecutionTool[]
  evidence: AiExecutionSnapshot['evidence']
  capability: Record<string, any>
  planExecution: AiExecutionPlanExecution
  evidenceReuse: AiExecutionEvidenceReuse
  policyChecks: AiExecutionPolicyCheck[]
  repairActions: AiExecutionRepairAction[]
}): AiExecutionDiagnosis {
  const tags = buildDiagnosisTags(input.summary, input.answerMeta, input.tools, input.evidence, input.evidenceReuse)
  if (isExecutionInProgress(input.summary.status, input.currentStep)) {
    return {
      severity: 'info',
      title: '执行中',
      summary: input.currentStep?.label ? `当前正在${input.currentStep.label}。` : 'AI 执行仍在进行中，最终模型来源和证据结论尚未收口。',
      cause: input.currentStep?.status || input.summary.status || 'RUNNING',
      actions: [],
      tags
    }
  }

  const failedTools = input.tools.filter((tool) => !tool.success)
  if (failedTools.length) {
    const first = failedTools[0]
    return {
      severity: 'danger',
      title: '工具调用失败',
      summary: `共有 ${failedTools.length} 个工具失败，优先检查 ${first.name}。`,
      cause: stringValue(first.error, first.diagnostic, first.fallbackReason, '工具返回失败或异常') || '工具返回失败或异常',
      actions: input.repairActions,
      tags
    }
  }

  if (hasLlmFailure(input.answerMeta)) {
    return {
      severity: 'warning',
      title: '大模型生成失败',
      summary: '业务工具和证据已返回，但大模型生成未成功，当前回答来自兜底回答或模板结果。',
      cause: stringValue(
        input.answerMeta.errorMessage,
        input.answerMeta.error_message,
        input.answerMeta.fallbackReason,
        input.answerMeta.fallback_reason,
        input.answerMeta.errorType,
        input.answerMeta.error_type,
        input.answerMeta.llmStatus,
        input.answerMeta.llm_status,
        'LLM failed'
      ) || 'LLM failed',
      actions: input.repairActions,
      tags
    }
  }

  const knowledge = knowledgeDiagnosis(input.tools, input.answerMeta, input.repairActions, tags)
  if (knowledge) return knowledge

  if (hasBusinessEvidenceGap(input.capability, input.tools, input.evidence)) {
    return {
      severity: 'warning',
      title: '业务证据不足',
      summary: '当前能力需要 GIS 或业务数据支撑，但本次没有形成有效业务证据。',
      cause: '业务工具返回为空、未命中，或来源中缺少 BUSINESS_DATA。',
      actions: input.repairActions,
      tags
    }
  }

  const failedPolicyChecks = input.policyChecks.filter((item) => item.status === 'FAIL')
  if (failedPolicyChecks.length || input.planExecution.missingToolNames.length || input.planExecution.missingSourceTypes.length) {
    const missing = input.planExecution.missingToolNames.concat(input.planExecution.missingSourceTypes)
    return {
      severity: failedPolicyChecks.some((item) => String(item.severity).toUpperCase() === 'ERROR') ? 'danger' : 'warning',
      title: failedPolicyChecks.length ? '能力策略校验未通过' : '执行计划存在偏差',
      summary: failedPolicyChecks[0]?.message || (missing.length ? `缺少 ${missing.join(', ')}。` : '计划工具、实际工具或证据来源存在差异。'),
      cause: failedPolicyChecks[0]?.code || input.planExecution.status || 'PLAN_DIVERGENCE',
      actions: input.repairActions,
      tags
    }
  }

  if (!Object.keys(input.answerMeta).length) {
    return {
      severity: 'info',
      title: '模型来源元数据缺失',
      summary: '这条记录没有 answerMeta，通常来自旧记录、旧接口或未经过 LangGraph 管线的历史任务。',
      cause: 'answerMeta missing',
      actions: input.repairActions,
      tags
    }
  }

  return {
    severity: 'success',
    title: '执行证据完整',
    summary: '本次执行的模型、工具和证据链路已返回，可继续查看下方工具、证据和时间线细节。',
    cause: 'LLM、工具调用和证据来源均有可排查数据。',
    actions: input.repairActions,
    tags
  }
}

function buildDiagnosisTags(
  summary: AiExecutionSnapshot['summary'],
  answerMeta: Record<string, any>,
  tools: AiExecutionTool[],
  evidence: AiExecutionSnapshot['evidence'],
  evidenceReuse: AiExecutionEvidenceReuse = {}
): AiExecutionDiagnosisTag[] {
  const llmUsed = answerMeta.llmSuccess === true || String(answerMeta.answerSource || answerMeta.answer_source || '').toUpperCase() === 'LLM'
  const toolFailed = Boolean(summary.toolFailedCount) || tools.some((tool) => !tool.success)
  const evidenceCount = evidence.sourceCount || evidence.sources.length || 0
  const fallbackUsed = Boolean(summary.fallback) || hasLlmFallback(answerMeta)
  const tags: AiExecutionDiagnosisTag[] = [
    { label: '模型', value: llmUsed ? '已使用' : (Object.keys(answerMeta).length ? '未成功' : '未知'), type: llmUsed ? 'success' : 'info' },
    { label: '降级', value: fallbackUsed ? '是' : '否', type: fallbackUsed ? 'warning' : 'success' },
    { label: '工具', value: `${summary.toolSuccessCount || 0}/${summary.toolTotalCount || tools.length || 0}`, type: toolFailed ? 'danger' : 'success' },
    { label: '证据', value: String(evidenceCount), type: evidenceCount ? 'success' : 'warning' }
  ]
  if (evidenceReuse.status) {
    tags.push({
      label: '证据复用',
      value: evidenceReuse.status,
      type: ['REUSED', 'PARTIAL_REUSED'].includes(evidenceReuse.status) ? 'success' : 'info'
    })
  }
  return tags
}

function hasLlmFailure(answerMeta: Record<string, any>): boolean {
  const llmStatus = String(answerMeta.llmStatus || answerMeta.llm_status || '').toUpperCase()
  if (answerMeta.llmSuccess === false || answerMeta.llm_success === false) return true
  if (['FAILED', 'ERROR', 'TIMEOUT'].includes(llmStatus)) return true
  return hasLlmFallback(answerMeta)
}

function hasLlmFallback(answerMeta: Record<string, any>): boolean {
  const answerSource = String(answerMeta.answerSource || answerMeta.answer_source || '').toUpperCase()
  return answerSource === 'FALLBACK' || Boolean(answerMeta.fallbackReason || answerMeta.fallback_reason)
}

function knowledgeDiagnosis(
  tools: AiExecutionTool[],
  answerMeta: Record<string, any>,
  repairActions: AiExecutionRepairAction[],
  tags: AiExecutionDiagnosisTag[]
): AiExecutionDiagnosis | null {
  const knowledgeTool = tools.find((tool) => tool.name === 'knowledge.retrieve' && (tool.fallbackReason || tool.diagnostic))
  const reason = stringValue(
    knowledgeTool?.fallbackReason,
    answerMeta.knowledgeFallbackReason,
    answerMeta.knowledge_fallback_reason,
    answerMeta.fallbackReason,
    answerMeta.fallback_reason
  )?.toLowerCase()
  if (!reason) return null

  if (reason === 'no embedded chunks') {
    return {
      severity: 'warning',
      title: '知识库向量未就绪',
      summary: '知识库有切片但没有可用向量，AI 已尝试关键词兜底，回答可能缺少语义检索证据。',
      cause: 'knowledge.retrieve fallbackReason: no embedded chunks',
      actions: repairActions,
      tags
    }
  }
  if (reason === 'no knowledge chunks') {
    return {
      severity: 'warning',
      title: '知识库暂无切片',
      summary: '本地知识库没有可检索切片，AI 只能依赖业务数据或模板兜底。',
      cause: 'knowledge.retrieve fallbackReason: no knowledge chunks',
      actions: repairActions,
      tags
    }
  }
  if (reason === 'query is empty') {
    return {
      severity: 'info',
      title: '知识检索词为空',
      summary: '本次问题没有形成有效知识库检索词，知识库未参与回答。',
      cause: 'knowledge.retrieve fallbackReason: query is empty',
      actions: repairActions,
      tags
    }
  }
  if (reason.includes('pgvector')) {
    return {
      severity: 'warning',
      title: '向量检索能力异常',
      summary: 'pgvector 或向量检索配置不可用，AI 已尝试关键词检索兜底。',
      cause: `knowledge.retrieve fallbackReason: ${reason}`,
      actions: repairActions,
      tags
    }
  }
  return null
}

function hasBusinessEvidenceGap(
  capability: Record<string, any>,
  tools: AiExecutionTool[],
  evidence: AiExecutionSnapshot['evidence']
): boolean {
  const capabilityText = [
    capability.capabilityId,
    capability.capability_id,
    capability.id,
    capability.name,
    capability.capabilityName,
    capability.capability_name,
    capability.intent,
    capability.contextUsage,
    capability.context_usage
  ].filter(Boolean).join(' ').toUpperCase()
  const expectsBusiness = capabilityText.includes('BUSINESS') || capabilityText.includes('MAP_') || capabilityText.includes('GIS')
  if (!expectsBusiness) return false

  const businessTools = tools.filter((tool) => tool.name.startsWith('gis.') || tool.name.startsWith('solution.'))
  if (!businessTools.length) return false
  const hasBusinessSource = Boolean(evidence.businessCount) || evidence.sources.some((source) => String(source.sourceType || source.source_type || source.type || '').toUpperCase().includes('BUSINESS'))
  const businessToolsHaveOnlyEmptyResults = businessTools.every((tool) => Number(tool.count || 0) === 0)
  return !hasBusinessSource && businessToolsHaveOnlyEmptyResults
}

function buildExecutionPolicyChecks(
  capability: Record<string, any>,
  tools: AiExecutionTool[] = [],
  planExecution?: AiExecutionPlanExecution
): AiExecutionPolicyCheck[] {
  const policy = firstRecord(capability.toolPolicy, capability.tool_policy)
  const requiredTools = uniqueStringList(stringList(policy.required).concat(stringList(policy.required_tools)))
  const prohibitedTools = uniqueStringList(stringList(policy.prohibited).concat(stringList(policy.prohibited_tools)))
  const observedToolNames = uniqueStringList(tools.map((item) => item.name).concat(planExecution?.actualToolNames || []))
  const actualToolNames = observedToolNames.length ? observedToolNames : uniqueStringList(planExecution?.plannedToolNames || [])
  const checks: AiExecutionPolicyCheck[] = []
  if (!actualToolNames.length) return checks

  if (requiredTools.length) {
    const missing = requiredTools.filter((name) => !actualToolNames.includes(name))
    if (missing.length) {
      checks.push(policyCheck(
        'FAIL',
        'REQUIRED_TOOL_MISSING',
        'ERROR',
        `缺少必选工具：${missing.join(', ')}`,
        requiredTools,
        actualToolNames,
        missing
      ))
    } else {
      checks.push(policyCheck(
        'PASS',
        'REQUIRED_TOOLS_PRESENT',
        'INFO',
        '必选工具均已调用。',
        requiredTools,
        actualToolNames
      ))
    }
  }

  if (prohibitedTools.length) {
    const hits = prohibitedTools.filter((name) => actualToolNames.includes(name))
    if (hits.length) {
      checks.push(policyCheck(
        'FAIL',
        'PROHIBITED_TOOL_USED',
        'ERROR',
        `调用了禁用工具：${hits.join(', ')}`,
        prohibitedTools,
        actualToolNames,
        [],
        [],
        hits
      ))
    } else {
      checks.push(policyCheck(
        'PASS',
        'PROHIBITED_TOOLS_ABSENT',
        'INFO',
        '禁用工具未被调用。',
        prohibitedTools,
        actualToolNames
      ))
    }
  }

  return checks
}

function normalizePolicyChecks(values: any[]): AiExecutionPolicyCheck[] {
  return values.map((value) => {
    const item = asRecord(value)
    const code = stringValue(item.code)
    const message = stringValue(item.message)
    if (!code && !message) return null
    return policyCheck(
      stringValue(item.status) || 'INFO',
      code || message || 'TOOL_POLICY_CHECK',
      stringValue(item.severity) || 'INFO',
      message || code || '',
      stringList(item.expectedToolNames || item.expected_tool_names),
      stringList(item.actualToolNames || item.actual_tool_names),
      stringList(item.missingToolNames || item.missing_tool_names),
      stringList(item.extraToolNames || item.extra_tool_names),
      stringList(item.prohibitedToolNames || item.prohibited_tool_names)
    )
  }).filter((item): item is AiExecutionPolicyCheck => Boolean(item))
}

function policyCheck(
  status: string,
  code: string,
  severity: string,
  message: string,
  expectedToolNames: string[] = [],
  actualToolNames: string[] = [],
  missingToolNames: string[] = [],
  extraToolNames: string[] = [],
  prohibitedToolNames: string[] = []
): AiExecutionPolicyCheck {
  return {
    status,
    code,
    severity,
    message,
    expectedToolNames,
    actualToolNames,
    missingToolNames,
    extraToolNames,
    prohibitedToolNames
  }
}

function buildRepairActions(tools: AiExecutionTool[], answerMeta: Record<string, any> = {}): AiExecutionRepairAction[] {
  const knowledgeTool = tools.find((tool) => tool.name === 'knowledge.retrieve' && tool.fallbackReason)
  const reason = stringValue(
    knowledgeTool?.fallbackReason ||
    answerMeta.knowledgeFallbackReason ||
    answerMeta.knowledge_fallback_reason ||
    answerMeta.fallbackReason ||
    answerMeta.fallback_reason
  )?.toLowerCase()

  if (reason === 'no knowledge chunks') {
    return [
      {
        key: 'SYNC_OUTLINE',
        label: '同步 Outline 入库',
        path: '/agent/outline/sync',
        description: '知识库没有切片，先把 Outline 文档同步进本地知识库。',
        type: 'primary'
      },
      {
        key: 'IMPORT_KNOWLEDGE',
        label: '导入知识文档',
        path: '/agent/knowledge-documents',
        description: '没有 Outline 数据时，可直接导入 Markdown 知识文档生成切片。',
        type: 'success'
      },
      {
        key: 'VERIFY_KNOWLEDGE',
        label: '验证知识检索',
        path: '/agent/knowledge-vector',
        description: '同步或导入后，用同类问题验证知识库是否能返回命中。',
        type: 'info'
      }
    ]
  }

  if (reason === 'no embedded chunks') {
    return [
      {
        key: 'VECTORIZE_OUTLINE',
        label: '补 Outline 向量',
        path: '/agent/ai-ops',
        description: '已有切片但没有可用向量，进入运维总览执行补向量。',
        type: 'primary'
      },
      {
        key: 'SYNC_OUTLINE',
        label: '同步入库',
        path: '/agent/outline/sync',
        description: '如果切片来源不完整，先重新同步 Outline 再补向量。',
        type: 'warning'
      },
      {
        key: 'VERIFY_KNOWLEDGE',
        label: '验证知识检索',
        path: '/agent/knowledge-vector',
        description: '补向量后验证向量检索和关键词兜底结果。',
        type: 'info'
      }
    ]
  }

  return []
}

function normalizePlanExecution(input: any): AiExecutionPlanExecution {
  const raw = firstRecord(input?.planExecution, input?.data?.planExecution, input)
  return {
    available: raw.available === true,
    status: stringValue(raw.status) || (raw.available === false ? 'NO_PLAN' : 'NO_PLAN') || 'NO_PLAN',
    planTraceId: stringValue(raw.planTraceId || raw.plan_trace_id),
    runTraceId: stringValue(raw.runTraceId || raw.run_trace_id),
    plannedAction: stringValue(raw.plannedAction || raw.planned_action),
    actualAction: stringValue(raw.actualAction || raw.actual_action),
    plannedIntent: stringValue(raw.plannedIntent || raw.planned_intent),
    actualIntent: stringValue(raw.actualIntent || raw.actual_intent),
    plannedToolNames: stringList(raw.plannedToolNames || raw.planned_tool_names),
    actualToolNames: stringList(raw.actualToolNames || raw.actual_tool_names),
    missingToolNames: stringList(raw.missingToolNames || raw.missing_tool_names),
    extraToolNames: stringList(raw.extraToolNames || raw.extra_tool_names),
    adaptiveExtraToolNames: stringList(raw.adaptiveExtraToolNames || raw.adaptive_extra_tool_names),
    adaptiveReason: stringValue(raw.adaptiveReason || raw.adaptive_reason),
    plannedSourceTypes: stringList(raw.plannedSourceTypes || raw.planned_source_types),
    actualSourceTypes: stringList(raw.actualSourceTypes || raw.actual_source_types),
    missingSourceTypes: stringList(raw.missingSourceTypes || raw.missing_source_types),
    warnings: planWarnings(raw.warnings),
    raw
  }
}

function planWarnings(values: any): AiExecutionPlanWarning[] {
  if (!Array.isArray(values)) return []
  return values.map((value) => {
    const item = asRecord(value)
    return {
      level: stringValue(item.level) || 'INFO',
      code: stringValue(item.code) || '',
      message: stringValue(item.message) || stringValue(item.code) || ''
    }
  }).filter((item) => item.code || item.message)
}

function stringList(values: any): string[] {
  if (!Array.isArray(values)) return []
  const result: string[] = []
  values.forEach((value) => {
    const item = stringValue(value)
    if (item && !result.includes(item)) result.push(item)
  })
  return result
}

function uniqueStringList(values: any[]): string[] {
  const result: string[] = []
  values.forEach((value) => {
    const item = stringValue(value)
    if (item && !result.includes(item)) result.push(item)
  })
  return result
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

function extractEvidenceReuse(
  answerMeta: Record<string, any>,
  data: Record<string, any>,
  trace: Record<string, any>
): AiExecutionEvidenceReuse {
  const reuse = firstRecord(data.evidenceReuse, data.evidence_reuse, trace.evidenceReuse, trace.evidence_reuse)
  return {
    status: stringValue(answerMeta.evidenceReuseStatus, answerMeta.evidence_reuse_status, reuse.status),
    evidenceSnapshotId: stringValue(answerMeta.evidenceSnapshotId, answerMeta.evidence_snapshot_id, reuse.evidenceSnapshotId, reuse.evidence_snapshot_id),
    basedOnAnalysisTraceId: stringValue(answerMeta.basedOnAnalysisTraceId, answerMeta.based_on_analysis_trace_id, reuse.basedOnAnalysisTraceId, reuse.based_on_analysis_trace_id),
    scopeFingerprint: stringValue(answerMeta.scopeFingerprint, answerMeta.scope_fingerprint, reuse.scopeFingerprint, reuse.scope_fingerprint)
  }
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

function stringValue(...values: any[]): string | undefined {
  for (const value of values) {
    if (value === undefined || value === null || value === '') continue
    return String(value)
  }
  return undefined
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
  if (normalized === 'no knowledge chunks') {
    return '本地知识库暂无切片，AI 只能依赖业务数据；请先同步 Outline 或导入知识文档。'
  }
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
