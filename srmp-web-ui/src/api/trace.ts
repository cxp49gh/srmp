import request from '../utils/request'
export interface AiTraceQuery {
  status?: string
  keyword?: string
  limit?: number
  projectId?: string
  routeCode?: string
  objectType?: string
  action?: string
  intent?: string
  toolName?: string
  fallback?: string | boolean
  hasAnswerMeta?: string | boolean
  hasBusinessEvidence?: string | boolean
}
export function listAiTraces(params: AiTraceQuery): Promise<Record<string, any>[]> { return request.get('/api/ai/traces', { params }) }
export function getAiTrace(traceId: string): Promise<Record<string, any>> { return request.get(`/api/ai/traces/${traceId}`) }
export function getAiTraceSteps(traceId: string): Promise<Record<string, any>[]> { return request.get(`/api/ai/traces/${traceId}/steps`) }
export function listAiExecutions(params: AiTraceQuery): Promise<Record<string, any>[]> { return request.get('/api/ai/executions', { params }) }
export function getAiExecution(traceId: string): Promise<Record<string, any>> { return request.get(`/api/ai/executions/${traceId}`) }
