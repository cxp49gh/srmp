import request from '../utils/request'

export interface AiSolutionTemplateRequest {
  templateCode?: string
  templateName: string
  solutionType: string
  sourceType?: string
  sourceId?: string
  category?: string
  version?: string
  content: string
  sourceUrl?: string
}

export interface AiSolutionTemplateQuery {
  keyword?: string
  solutionType?: string
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

export function disableSolutionTemplate(id: string): Promise<Record<string, any>> {
  return request.post(`/api/ai/solution/templates/${id}/disable`)
}
