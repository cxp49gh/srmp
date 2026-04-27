import request from '../utils/request'
export interface AiTraceQuery { status?: string; keyword?: string; limit?: number }
export function listAiTraces(params: AiTraceQuery): Promise<Record<string, any>[]> { return request.get('/api/ai/traces', { params }) }
export function getAiTrace(traceId: string): Promise<Record<string, any>> { return request.get(`/api/ai/traces/${traceId}`) }
export function getAiTraceSteps(traceId: string): Promise<Record<string, any>[]> { return request.get(`/api/ai/traces/${traceId}/steps`) }
