import { aiRequest } from '../utils/request'

export interface OrchestratorSmokeRequest {
  tenantId?: string
  message?: string
  mode?: string
  routeCode?: string
  year?: number
  traceId?: string
}

export function getOrchestratorOpsSummary(recentLimit = 10): Promise<Record<string, any>> {
  return aiRequest.get('/api/agent/orchestrator/ops/summary', {
    params: { recentLimit }
  })
}

export function getOrchestratorRecent(limit = 20, status?: string): Promise<Record<string, any>> {
  return aiRequest.get('/api/agent/orchestrator/ops/recent', {
    params: status ? { limit, status } : { limit }
  })
}

export function runOrchestratorSmoke(data: OrchestratorSmokeRequest): Promise<Record<string, any>> {
  return aiRequest.post('/api/agent/orchestrator/ops/smoke', data || {})
}
