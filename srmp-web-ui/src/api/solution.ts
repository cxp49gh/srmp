import request from '../utils/request'

export interface AiSolutionTemplateRequest {
  templateCode?: string
  templateName: string
  solutionType: string
  originType?: string
  objectType?: string
  isDefault?: boolean
  priority?: number
  sourceType?: string
  sourceId?: string
  category?: string
  version?: string
  content: string
  sourceUrl?: string
  changeNote?: string
}

export interface AiSolutionTemplateQuery {
  keyword?: string
  solutionType?: string
  originType?: string
  objectType?: string
  isDefault?: boolean
  status?: string
  limit?: number
}

export interface AiSolutionTemplateImportRequest {
  knowledgeDocumentId: string
  templateCode?: string
  templateName?: string
  solutionType?: string
  force?: boolean
}

export interface AiSolutionGenerateRequest {
  solutionType: string
  routeCode: string
  year: number
  templateCode?: string
  templateId?: string
  options?: Record<string, any>
}

export interface AiSolutionTaskQuery {
  solutionType?: string
  routeCode?: string
  year?: number
  status?: string
  draftStatus?: string
  limit?: number
}

export interface AiSolutionEvalCase {
  id: string
  name: string
  solutionType: string
  originType?: string
  objectType?: string
  minScore?: number
  requiredDimensions?: string[]
  expectedSourceTypes?: string[]
}

export interface AiSolutionEvalRequest {
  cases?: AiSolutionEvalCase[]
  useLatestTask?: boolean
}

export interface AiSolutionEvalResponse {
  total: number
  passed: number
  failed: number
  passRate: number
  sourceModeSummary?: Record<string, number>
  failedCaseIds?: string[]
  results?: Record<string, any>[]
}

export interface AiSolutionSourceSummaryRequest {
  sourceType?: string
  sourceTitle?: string
  sourceId?: string
  sourceUrl?: string
  contentExcerpt?: string
}

export interface AiSolutionDraftSaveRequest {
  solutionType: string
  title: string
  markdown: string
  routeCode?: string
  year?: number
  originType?: string
  objectType?: string
  objectId?: string
  mapObject: Record<string, any>
  objectSummary?: Record<string, any>
  regionSummary?: Record<string, any>
  qualityCheck?: Record<string, any>
  trace?: Record<string, any>
  sourceSummaries?: AiSolutionSourceSummaryRequest[]
  templateId?: string
  templateVersion?: string
  templateName?: string
  templateMeta?: Record<string, any>
  options?: Record<string, any>
  requestContext?: Record<string, any>
}

export interface AiSolutionAiContextUpdateRequest {
  aiTraceId?: string
  aiAnswer?: string
  aiSources?: Record<string, any>[]
  aiToolResults?: Record<string, any>[]
  aiEvidence?: Record<string, any>
  aiContext?: Record<string, any>
  generationMode?: string
}

export interface AiSolutionDraftUpdateRequest {
  title?: string
  markdown: string
  changeNote?: string
}

export function createSolutionTemplate(data: AiSolutionTemplateRequest): Promise<Record<string, any>> {
  return request.post('/api/ai/solution/templates', data)
}

export function listSolutionTemplates(data: AiSolutionTemplateQuery): Promise<Record<string, any>[]> {
  return request.post('/api/ai/solution/templates/list', data)
}

export function getSolutionTemplate(id: string): Promise<Record<string, any>> {
  return request.get(`/api/ai/solution/templates/${id}`)
}

export function getSolutionTemplateVersions(id: string): Promise<Record<string, any>[]> {
  return request.get(`/api/ai/solution/templates/${id}/versions`)
}

export function importTemplateFromKnowledge(data: AiSolutionTemplateImportRequest): Promise<Record<string, any>> {
  return request.post('/api/ai/solution/templates/import-from-knowledge', data)
}

export function matchSolutionTemplate(data: Record<string, any>): Promise<Record<string, any>> {
  return request.post('/api/ai/solution/templates/match-preview', data)
}

export function renderSolutionTemplatePreview(id: string, data: Record<string, any>): Promise<Record<string, any>> {
  return request.post(`/api/ai/solution/templates/${id}/render-preview`, data)
}

export function updateSolutionTemplateStatus(id: string, status: string): Promise<Record<string, any>> {
  return request.post(`/api/ai/solution/templates/${id}/status`, { status })
}

export function setDefaultSolutionTemplate(id: string): Promise<Record<string, any>> {
  return request.post(`/api/ai/solution/templates/${id}/default`)
}

export function createSolutionTemplateVersion(id: string, data: Record<string, any>): Promise<Record<string, any>> {
  return request.post(`/api/ai/solution/templates/${id}/versions`, data)
}

export function disableSolutionTemplate(id: string): Promise<Record<string, any>> {
  return request.post(`/api/ai/solution/templates/${id}/disable`)
}

export function generateSolution(data: AiSolutionGenerateRequest): Promise<Record<string, any>> {
  return request.post('/api/ai/solution/generate', data)
}

export function listSolutionTasks(data: AiSolutionTaskQuery): Promise<Record<string, any>[]> {
  return request.post('/api/ai/solution/tasks/list', data)
}

export function getSolutionTask(id: string): Promise<Record<string, any>> {
  return request.get(`/api/ai/solution/tasks/${id}`)
}

export function getSolutionTaskSources(id: string): Promise<Record<string, any>[]> {
  return request.get(`/api/ai/solution/tasks/${id}/sources`)
}

export function saveMapObjectSolutionDraft(data: AiSolutionDraftSaveRequest): Promise<Record<string, any>> {
  return request.post('/api/ai/solution/tasks/map-object-drafts', data)
}

export function updateSolutionTask(id: string, data: AiSolutionDraftUpdateRequest): Promise<Record<string, any>> {
  return request.put(`/api/ai/solution/tasks/${id}`, data)
}

export function updateSolutionTaskDraftStatus(id: string, draftStatus: string): Promise<Record<string, any>> {
  return request.post(`/api/ai/solution/tasks/${id}/draft-status`, { draftStatus })
}

export function getSolutionTaskVersions(id: string): Promise<Record<string, any>[]> {
  return request.get(`/api/ai/solution/tasks/${id}/versions`)
}

export function checkSolutionQuality(id: string): Promise<Record<string, any>> {
  return request.post(`/api/ai/solution/tasks/${id}/quality-check`)
}

export function getSolutionQualityResult(id: string): Promise<Record<string, any>> {
  return request.get(`/api/ai/solution/tasks/${id}/quality-result`)
}

export function getSolutionMarkdownExportUrl(id: string): string {
  const base = import.meta.env.VITE_API_BASE_URL || ''
  return `${base}/api/ai/solution/tasks/${id}/export/markdown`
}

export function updateSolutionTaskAiContext(id: string, data: AiSolutionAiContextUpdateRequest): Promise<Record<string, any>> {
  return request.post(`/api/ai/solution/tasks/${id}/ai-context`, data)
}

export function getSolutionTaskAiContext(id: string): Promise<Record<string, any>> {
  return request.get(`/api/ai/solution/tasks/${id}/ai-context`)
}

export function getSolutionTaskStatusTimeline(id: string): Promise<Record<string, any>[]> {
  return request.get(`/api/ai/solution/tasks/${id}/status-timeline`)
}

export function restoreSolutionTaskVersion(id: string, versionNo: number, changeNote?: string): Promise<Record<string, any>> {
  return request.post(`/api/ai/solution/tasks/${id}/versions/${versionNo}/restore`, { changeNote })
}

export function getSolutionMarkdownV2ExportUrl(id: string): string {
  const base = import.meta.env.VITE_API_BASE_URL || ''
  return `${base}/api/ai/solution/tasks/${id}/export/markdown-v2`
}

export function getDefaultSolutionEvalCases(): Promise<AiSolutionEvalCase[]> {
  return request.get('/api/ai/solution/eval/cases')
}

export function runSolutionEval(data: AiSolutionEvalRequest): Promise<AiSolutionEvalResponse> {
  return request.post('/api/ai/solution/eval/run', data)
}
