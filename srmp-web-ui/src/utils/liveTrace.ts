export interface LiveTraceStep {
  name?: string
  label?: string
  status?: string
  elapsedMs?: number
  costMs?: number
  count?: number | string
  error?: string
  data?: Record<string, any>
}

export interface LiveTraceSnapshot {
  traceId?: string
  status: string
  action?: string
  graphName?: string
  currentStep?: LiveTraceStep | null
  steps: LiveTraceStep[]
  toolSummary: { planned: number; completed: number; success: number; failed: number }
  sourceSummary: { business: number; knowledge: number; outline: number }
  answerMeta: Record<string, any>
  finalTrace?: Record<string, any>
  error?: string
  costMs?: number
}

export function createWebTraceId(now = Date.now, random = Math.random): string {
  const suffix = Math.floor(random() * 2176782336).toString(36).padStart(6, '0').slice(0, 6)
  return `web-lg-${now()}-${suffix}`
}

export function normalizeLiveTraceSnapshot(input: any): LiveTraceSnapshot {
  const raw = unwrapBody(input)
  const toolSummary = raw.toolSummary || {}
  const sourceSummary = raw.sourceSummary || {}
  return {
    traceId: stringValue(raw.traceId || raw.trace_id),
    status: stringValue(raw.status) || 'UNKNOWN',
    action: stringValue(raw.action),
    graphName: stringValue(raw.graphName || raw.graph_name),
    currentStep: normalizeStep(raw.currentStep || raw.current_step),
    steps: Array.isArray(raw.steps)
      ? raw.steps.map((step: any) => normalizeStep(step)).filter(Boolean) as LiveTraceStep[]
      : [],
    toolSummary: {
      planned: numberValue(toolSummary.planned),
      completed: numberValue(toolSummary.completed),
      success: numberValue(toolSummary.success),
      failed: numberValue(toolSummary.failed)
    },
    sourceSummary: {
      business: numberValue(sourceSummary.business),
      knowledge: numberValue(sourceSummary.knowledge),
      outline: numberValue(sourceSummary.outline)
    },
    answerMeta: raw.answerMeta && typeof raw.answerMeta === 'object' ? raw.answerMeta : {},
    finalTrace: raw.finalTrace && typeof raw.finalTrace === 'object' ? raw.finalTrace : undefined,
    error: stringValue(raw.error || raw.errorMessage),
    costMs: optionalNumber(raw.costMs)
  }
}

export function buildLiveTraceSummary(snapshot: Partial<LiveTraceSnapshot>) {
  const steps = snapshot.steps || []
  const compactSteps = compactDisplaySteps(steps)
  const current = normalizeCurrentStep(snapshot.currentStep || null, compactSteps, snapshot.status)
  const tool = snapshot.toolSummary || { planned: 0, completed: 0, success: 0, failed: 0 }
  const source = snapshot.sourceSummary || { business: 0, knowledge: 0, outline: 0 }
  const sourceParts = []
  if (source.business) sourceParts.push(`业务 ${source.business}`)
  if (source.knowledge) sourceParts.push(`知识库 ${source.knowledge}`)
  if (source.outline) sourceParts.push(`Outline ${source.outline}`)
  const reuse = extractEvidenceReuse(snapshot)
  return {
    currentLabel: current?.label || current?.name || '',
    currentStatus: current?.status || snapshot.status || '',
    recentSteps: compactSteps.slice(-4),
    toolLabel: formatEvidenceOrToolLabel(tool, reuse),
    sourceLabel: sourceParts.join('｜'),
    error: snapshot.error || current?.error || ''
  }
}

export function shouldPauseLiveTracePolling(failureCount: number): boolean {
  return Number(failureCount || 0) >= 3
}

function unwrapBody(input: any): any {
  if (input?.body && typeof input.body === 'object') return input.body
  if (input?.data?.body && typeof input.data.body === 'object') return input.data.body
  if (input?.data && typeof input.data === 'object' && input.data.status) return input.data
  return input && typeof input === 'object' ? input : {}
}

function normalizeStep(input: any): LiveTraceStep | null {
  if (!input || typeof input !== 'object') return null
  return {
    name: stringValue(input.name || input.node || input.step_name),
    label: stringValue(input.label || input.message || input.step_label || input.name || input.node),
    status: stringValue(input.status || 'UNKNOWN'),
    elapsedMs: optionalNumber(input.elapsedMs ?? input.costMs ?? input.cost_ms),
    costMs: optionalNumber(input.costMs ?? input.cost_ms),
    count: input.count ?? input.hit_count ?? input.resultCount,
    error: stringValue(input.error || input.errorMessage || input.error_message),
    data: input.data && typeof input.data === 'object' ? input.data : {}
  }
}

function compactDisplaySteps(steps: LiveTraceStep[]): LiveTraceStep[] {
  const meaningful = steps.filter((step) => String(step.status || '').toUpperCase() !== 'SKIPPED')
  return meaningful.length ? meaningful : steps
}

function normalizeCurrentStep(
  current: LiveTraceStep | null,
  compactSteps: LiveTraceStep[],
  snapshotStatus?: string
): LiveTraceStep | null {
  const currentStatus = String(current?.status || '').toUpperCase()
  const runStatus = String(snapshotStatus || '').toUpperCase()
  if (current && currentStatus !== 'SKIPPED') return current
  if (['SUCCESS', 'FAILED', 'TIMEOUT', 'COMPLETED'].includes(runStatus)) return null
  return compactSteps.length ? compactSteps[compactSteps.length - 1] : current
}

function formatToolLabel(tool: { planned?: number; completed?: number; success?: number; failed?: number }): string {
  const planned = numberValue(tool.planned)
  const completed = numberValue(tool.completed)
  const success = numberValue(tool.success)
  const failed = numberValue(tool.failed)
  if (!planned && !completed) return ''
  const parts = [`工具 ${completed}/${planned}`, `成功 ${success}`]
  if (failed > 0) parts.push(`失败 ${failed}`)
  return parts.join(' ')
}

function formatEvidenceOrToolLabel(
  tool: { planned?: number; completed?: number; success?: number; failed?: number },
  reuse: { status?: string; reusedCount?: number; supplementalCount?: number }
): string {
  const status = String(reuse.status || '').toUpperCase()
  if (status === 'REUSED') {
    const count = reuse.reusedCount || numberValue(tool.completed) || numberValue(tool.success)
    return count ? `复用分析证据 ${count} 项` : '复用分析证据'
  }
  if (status === 'PARTIAL_REUSED') {
    const parts = []
    if (reuse.reusedCount) parts.push(`复用 ${reuse.reusedCount} 项`)
    if (reuse.supplementalCount) parts.push(`补充查询 ${reuse.supplementalCount} 项`)
    return parts.length ? parts.join('，') : '部分复用分析证据'
  }
  return formatToolLabel(tool)
}

function extractEvidenceReuse(snapshot: Partial<LiveTraceSnapshot>): { status?: string; reusedCount?: number; supplementalCount?: number } {
  const answerMeta = snapshot.answerMeta || {}
  const finalTrace = snapshot.finalTrace || {}
  const reuse = firstRecord(
    finalTrace.evidenceReuse,
    finalTrace.evidence_reuse,
    findStepEvidenceReuse(snapshot.steps || []),
    answerMeta.evidenceReuse,
    answerMeta.evidence_reuse
  )
  const status = stringValue(
    answerMeta.evidenceReuseStatus ||
    answerMeta.evidence_reuse_status ||
    reuse.status
  )
  return {
    status,
    reusedCount: arrayLength(reuse.reusedToolNames || reuse.reused_tool_names),
    supplementalCount: arrayLength(reuse.supplementalToolNames || reuse.supplemental_tool_names)
  }
}

function findStepEvidenceReuse(steps: LiveTraceStep[]): Record<string, any> {
  for (let i = steps.length - 1; i >= 0; i -= 1) {
    const data = steps[i]?.data
    const reuse = data?.evidenceReuse || data?.evidence_reuse
    if (reuse && typeof reuse === 'object') return reuse
  }
  return {}
}

function firstRecord(...values: any[]): Record<string, any> {
  for (const value of values) {
    if (value && typeof value === 'object' && !Array.isArray(value)) return value
  }
  return {}
}

function arrayLength(value: any): number {
  return Array.isArray(value) ? value.length : 0
}

function stringValue(value: any): string | undefined {
  if (value === undefined || value === null || value === '') return undefined
  return String(value)
}

function numberValue(value: any): number {
  const num = Number(value)
  return Number.isFinite(num) ? num : 0
}

function optionalNumber(value: any): number | undefined {
  const num = Number(value)
  return Number.isFinite(num) ? num : undefined
}
