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
    error: stringValue(raw.error || raw.errorMessage),
    costMs: optionalNumber(raw.costMs)
  }
}

export function buildLiveTraceSummary(snapshot: Partial<LiveTraceSnapshot>) {
  const current = snapshot.currentStep || null
  const tool = snapshot.toolSummary || { planned: 0, completed: 0, success: 0, failed: 0 }
  const source = snapshot.sourceSummary || { business: 0, knowledge: 0, outline: 0 }
  const sourceParts = []
  if (source.business) sourceParts.push(`业务 ${source.business}`)
  if (source.knowledge) sourceParts.push(`知识库 ${source.knowledge}`)
  if (source.outline) sourceParts.push(`Outline ${source.outline}`)
  return {
    currentLabel: current?.label || current?.name || '',
    currentStatus: current?.status || snapshot.status || '',
    recentSteps: (snapshot.steps || []).slice(-4),
    toolLabel: tool.planned || tool.completed ? `工具 ${tool.completed}/${tool.planned} 成功 ${tool.success}` : '',
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
