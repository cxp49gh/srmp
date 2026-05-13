export interface MapAiSourceHint {
  sourceType: string
  label: string
  reason?: string
}

export interface MapAiPlanWarning {
  level: 'INFO' | 'WARN' | 'ERROR' | string
  code: string
  message: string
}

export interface MapAiPlannedTool {
  name: string
  label: string
  reason?: string
  readOnly: boolean
  writeRisk: boolean
  argsSummary: Record<string, any>
  raw: Record<string, any>
}

export interface MapAiPlanStep {
  name: string
  label: string
  status?: string
  elapsedMs?: number
  raw: Record<string, any>
}

export interface MapAiPlanPreview {
  status: 'SUCCESS' | 'FAILED'
  action?: string
  intent?: string
  traceId?: string
  contextSummary: Record<string, any>
  contextChips: string[]
  toolPlan: MapAiPlannedTool[]
  steps: MapAiPlanStep[]
  warnings: MapAiPlanWarning[]
  sourceHints: MapAiSourceHint[]
  normalizedRequest: Record<string, any>
  raw: Record<string, any>
  error?: string
}

export interface MapAiPlanPreviewMeta {
  planTraceId?: string
  action?: string
  intent?: string
  toolNames: string[]
  sourceTypes: string[]
  contextChips: string[]
  warningCodes: string[]
}

export interface MapAiPlanExecution {
  available: boolean
  status: 'MATCHED' | 'PARTIAL' | 'DIVERGED' | 'NO_PLAN' | string
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
  plannedSourceTypes: string[]
  actualSourceTypes: string[]
  missingSourceTypes: string[]
  warnings: MapAiPlanWarning[]
  raw: Record<string, any>
}

const TOOL_LABELS: Record<string, string> = {
  'gis.queryNearbyObjects': '查询周边对象',
  'gis.queryRegionSummary': '查询区域统计',
  'gis.queryAssessmentResults': '查询评定结果',
  'gis.queryDiseases': '查询病害',
  'gis.queryDiseasesByStakeRange': '查询桩号范围病害',
  'knowledge.retrieve': '知识库检索',
  'template.match': '匹配方案模板',
  'solution.generateDraft': '生成方案草稿',
  'solution.saveDraft': '保存方案草稿'
}

export function normalizeMapAiPlanResponse(input: any): MapAiPlanPreview {
  const raw = unwrapPlanPayload(input)
  const normalizedRequest = objectValue(raw.normalizedRequest || raw.request || input?.request)
  const toolPlan = normalizeToolPlan(raw.toolPlan || raw.tools || [])
  const basePlan = {
    action: stringValue(raw.action || normalizedRequest.action),
    intent: stringValue(raw.intent),
    contextSummary: objectValue(raw.contextSummary),
    normalizedRequest,
    toolPlan
  }
  const warnings = mergeWarnings(normalizeWarnings(raw.warnings), buildPlanWarnings(basePlan.normalizedRequest, toolPlan))
  const sourceHints = mergeSourceHints(normalizeSourceHints(raw.sourceHints), deriveSourceHints(basePlan))
  return {
    status: raw.error || raw.status === 'FAILED' ? 'FAILED' : 'SUCCESS',
    action: basePlan.action,
    intent: basePlan.intent,
    traceId: stringValue(raw.traceId || raw.trace_id),
    contextSummary: basePlan.contextSummary,
    contextChips: summarizePlanContext(basePlan),
    toolPlan,
    steps: normalizeSteps(raw.steps || []),
    warnings,
    sourceHints,
    normalizedRequest,
    raw,
    error: stringValue(raw.error || raw.errorMessage || raw.message)
  }
}

export function buildPlanPreviewMeta(plan?: MapAiPlanPreview | null): MapAiPlanPreviewMeta | null {
  if (!plan) return null
  return {
    planTraceId: plan.traceId,
    action: plan.action,
    intent: plan.intent,
    toolNames: uniqueStrings(plan.toolPlan.map((item) => item.name).filter(Boolean)),
    sourceTypes: uniqueStrings(plan.sourceHints.map((item) => item.sourceType).filter(Boolean)),
    contextChips: uniqueStrings(plan.contextChips),
    warningCodes: uniqueStrings(plan.warnings.map((item) => item.code).filter(Boolean))
  }
}

export function normalizePlanExecution(input: any): MapAiPlanExecution {
  const raw = objectValue(input?.planExecution || input?.data?.planExecution || input)
  return {
    available: raw.available === true,
    status: stringValue(raw.status) || (raw.available === false ? 'NO_PLAN' : 'NO_PLAN'),
    planTraceId: stringValue(raw.planTraceId),
    runTraceId: stringValue(raw.runTraceId),
    plannedAction: stringValue(raw.plannedAction),
    actualAction: stringValue(raw.actualAction),
    plannedIntent: stringValue(raw.plannedIntent),
    actualIntent: stringValue(raw.actualIntent),
    plannedToolNames: uniqueStrings(arrayValue(raw.plannedToolNames)),
    actualToolNames: uniqueStrings(arrayValue(raw.actualToolNames)),
    missingToolNames: uniqueStrings(arrayValue(raw.missingToolNames)),
    extraToolNames: uniqueStrings(arrayValue(raw.extraToolNames)),
    plannedSourceTypes: uniqueStrings(arrayValue(raw.plannedSourceTypes)),
    actualSourceTypes: uniqueStrings(arrayValue(raw.actualSourceTypes)),
    missingSourceTypes: uniqueStrings(arrayValue(raw.missingSourceTypes)),
    warnings: normalizeWarnings(arrayValue(raw.warnings)),
    raw
  }
}

export function summarizePlanContext(plan: Partial<MapAiPlanPreview> | Record<string, any>): string[] {
  const request = objectValue((plan as any).normalizedRequest || (plan as any).request)
  const ctx = objectValue(request.mapContext || (plan as any).mapContext)
  const summary = objectValue((plan as any).contextSummary)
  const mode = stringValue(ctx.mode || summary.mode || request.mode)
  const route = stringValue(ctx.routeCode || summary.routeCode || request.routeCode)
  const year = stringValue(ctx.year || summary.year || request.year)
  const chips: string[] = []
  if (mode === 'OBJECT') chips.push('对象模式')
  else if (mode === 'REGION') chips.push('区域模式')
  else if (mode === 'ROUTE') chips.push('路线模式')
  else if (mode) chips.push(`${mode} 模式`)
  if (route) chips.push(`路线 ${route}`)
  if (year) chips.push(`年度 ${year}`)
  if (ctx.mapObject) chips.push('已含地图对象')
  if (ctx.geometry || ctx.regionGeometry) chips.push(`几何 ${ctx.geometry?.type || ctx.regionGeometry?.type || 'Geometry'}`)
  if (ctx.regionSummary) chips.push('已含区域摘要')
  return chips
}

export function deriveSourceHints(plan: Partial<MapAiPlanPreview> | Record<string, any>): MapAiSourceHint[] {
  const request = objectValue((plan as any).normalizedRequest || (plan as any).request)
  const ctx = objectValue(request.mapContext || (plan as any).mapContext)
  const tools = ((plan as any).toolPlan || []) as Array<Partial<MapAiPlannedTool> | Record<string, any>>
  const hints: MapAiSourceHint[] = []
  if (ctx.mapObject) addSourceHint(hints, 'MAP_OBJECT', '地图对象', '当前请求包含选中地图对象。')
  if (ctx.geometry || ctx.regionGeometry || ctx.regionSummary || String(ctx.mode || '').toUpperCase() === 'REGION') {
    addSourceHint(hints, 'MAP_REGION', '框选区域', '当前请求包含区域范围或区域统计。')
  }
  tools.forEach((tool) => {
    const name = stringValue((tool as any).name || (tool as any).toolName) || ''
    if (name.startsWith('gis.')) addSourceHint(hints, 'BUSINESS_DATA', '业务数据', '计划会查询 GIS 业务图层、病害或评定结果。')
    if (name === 'knowledge.retrieve') addSourceHint(hints, 'KNOWLEDGE', '知识库', '计划会检索养护知识、规则或处置建议。')
    if (name === 'template.match' || name === 'solution.generateDraft') addSourceHint(hints, 'TEMPLATE', '方案模板', '计划会匹配或使用方案模板。')
  })
  return hints
}

export function buildPlanWarnings(requestLike: Record<string, any>, tools: Array<Partial<MapAiPlannedTool> | Record<string, any>>): MapAiPlanWarning[] {
  const request = objectValue(requestLike)
  const ctx = objectValue(request.mapContext)
  const action = String(request.action || '').toUpperCase()
  const mode = String(ctx.mode || '').toUpperCase()
  const warnings: MapAiPlanWarning[] = []
  if (action === 'ANALYZE_REGION' || action === 'GENERATE_REGION_SOLUTION' || mode === 'REGION') {
    if (!ctx.geometry && !ctx.regionGeometry) {
      warnings.push({ level: 'WARN', code: 'REGION_GEOMETRY_MISSING', message: '当前为区域分析，但未携带区域几何。' })
    }
  }
  if ((action === 'ANALYZE_OBJECT' || action === 'GENERATE_OBJECT_SOLUTION') && !ctx.mapObject) {
    warnings.push({ level: 'WARN', code: 'MAP_OBJECT_MISSING', message: '当前为对象分析，但未携带地图对象。' })
  }
  if (tools.some((tool) => Boolean((tool as any).writeRisk))) {
    warnings.push({ level: 'WARN', code: 'WRITE_TOOL_PLANNED', message: '计划中包含写入型工具，执行前需要确认。' })
  }
  return warnings
}

function normalizeToolPlan(items: any[]): MapAiPlannedTool[] {
  return Array.isArray(items)
    ? items.map((item) => {
        const raw = objectValue(item)
        const name = stringValue(raw.toolName || raw.name) || ''
        const writeRisk = raw.writeRisk === true || isWriteTool(name)
        return {
          name,
          label: stringValue(raw.label) || TOOL_LABELS[name] || name || '未知工具',
          reason: stringValue(raw.reason),
          readOnly: raw.readOnly === false ? false : !writeRisk,
          writeRisk,
          argsSummary: objectValue(raw.argsSummary || summarizeArgs(raw.args || {})),
          raw
        }
      })
    : []
}

function normalizeSteps(items: any[]): MapAiPlanStep[] {
  return Array.isArray(items)
    ? items.map((item) => {
        const raw = objectValue(item)
        const name = stringValue(raw.name || raw.node || raw.step_name) || ''
        return {
          name,
          label: stringValue(raw.label || raw.message || raw.step_label || name) || '计划步骤',
          status: stringValue(raw.status),
          elapsedMs: numberValue(raw.elapsedMs ?? raw.costMs ?? raw.cost_ms),
          raw
        }
      })
    : []
}

function normalizeWarnings(items: any[]): MapAiPlanWarning[] {
  return Array.isArray(items)
    ? items.map((item) => {
        if (typeof item === 'string') return { level: 'WARN', code: item, message: item }
        const raw = objectValue(item)
        return {
          level: stringValue(raw.level) || 'WARN',
          code: stringValue(raw.code) || stringValue(raw.message) || 'PLAN_WARNING',
          message: stringValue(raw.message) || stringValue(raw.code) || '计划存在风险提示。'
        }
      })
    : []
}

function normalizeSourceHints(items: any[]): MapAiSourceHint[] {
  return Array.isArray(items)
    ? items.map((item) => {
        const raw = objectValue(item)
        return {
          sourceType: stringValue(raw.sourceType || raw.type) || 'UNKNOWN',
          label: stringValue(raw.label || raw.sourceTitle || raw.sourceType || raw.type) || '来源',
          reason: stringValue(raw.reason || raw.contentExcerpt)
        }
      })
    : []
}

function mergeWarnings(primary: MapAiPlanWarning[], derived: MapAiPlanWarning[]): MapAiPlanWarning[] {
  const result = [...primary]
  derived.forEach((item) => {
    if (!result.some((existing) => existing.code === item.code)) result.push(item)
  })
  return result
}

function mergeSourceHints(primary: MapAiSourceHint[], derived: MapAiSourceHint[]): MapAiSourceHint[] {
  const result = [...primary]
  derived.forEach((item) => addSourceHint(result, item.sourceType, item.label, item.reason || ''))
  return result
}

function summarizeArgs(args: Record<string, any>): Record<string, any> {
  const raw = objectValue(args)
  const summary: Record<string, any> = {}
  ;['tenantId', 'routeCode', 'year', 'mode', 'limit', 'topK', 'intent', 'solutionType', 'stakeStart', 'stakeEnd'].forEach((key) => {
    if (raw[key] !== undefined && raw[key] !== null && raw[key] !== '') summary[key] = raw[key]
  })
  if (raw.geometry && typeof raw.geometry === 'object') summary.geometryType = raw.geometry.type || 'Geometry'
  if (raw.regionSummary && typeof raw.regionSummary === 'object') summary.regionSummary = true
  return summary
}

function unwrapPlanPayload(input: any): Record<string, any> {
  if (input?.code === 0 && input.data && typeof input.data === 'object') return input.data
  if (input?.data?.body && typeof input.data.body === 'object') return input.data.body
  if (input?.body && typeof input.body === 'object') return input.body
  if (input?.data && typeof input.data === 'object' && (input.data.toolPlan || input.data.intent)) return input.data
  return objectValue(input)
}

function addSourceHint(hints: MapAiSourceHint[], sourceType: string, label: string, reason: string): void {
  if (hints.some((item) => item.sourceType === sourceType)) return
  hints.push({ sourceType, label, reason })
}

function isWriteTool(name: string): boolean {
  const lowered = String(name || '').toLowerCase()
  return ['.save', '.update', '.delete', '.confirm', '.archive', '.write'].some((token) => lowered.includes(token))
}

function objectValue(value: any): Record<string, any> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
}

function arrayValue(value: any): any[] {
  return Array.isArray(value) ? value : []
}

function uniqueStrings(values: any[]): string[] {
  const result: string[] = []
  values.forEach((value) => {
    const text = stringValue(value)
    if (text && !result.includes(text)) result.push(text)
  })
  return result
}

function stringValue(value: any): string | undefined {
  if (value === undefined || value === null || value === '') return undefined
  return String(value)
}

function numberValue(value: any): number | undefined {
  const num = Number(value)
  return Number.isFinite(num) ? num : undefined
}
