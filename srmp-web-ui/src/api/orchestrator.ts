import { aiRequest } from '../utils/request'

export interface OrchestratorSmokeRequest {
  tenantId?: string
  message?: string
  mode?: string
  routeCode?: string
  year?: number
  traceId?: string
}

export interface OrchestratorPlanRequest {
  message?: string
  mapContext?: Record<string, any>
  context?: Record<string, any>
  mapObject?: Record<string, any>
  options?: Record<string, any>
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

export function runOrchestratorPlan(data: OrchestratorPlanRequest): Promise<Record<string, any>> {
  return aiRequest.post('/api/agent/orchestrator/ops/plan', data || {})
}

export function getOrchestratorContract(): Promise<Record<string, any>> {
  return aiRequest.get('/api/agent/orchestrator/ops/contract')
}

export function probeOrchestratorLlm(probe = false): Promise<Record<string, any>> {
  return aiRequest.get('/api/agent/orchestrator/ops/llm-probe', {
    params: { probe }
  })
}

export function getOrchestratorRecord(recordId: string): Promise<Record<string, any>> {
  return aiRequest.get(`/api/agent/orchestrator/ops/record/${encodeURIComponent(recordId)}`)
}

export function replayOrchestratorRecord(recordId: string, execute = false): Promise<Record<string, any>> {
  return aiRequest.post(`/api/agent/orchestrator/ops/replay/${encodeURIComponent(recordId)}`, {}, {
    params: { execute }
  })
}

export function exportOrchestratorDiagnostics(limit = 30, status?: string): Promise<Record<string, any>> {
  return aiRequest.get('/api/agent/orchestrator/ops/export', {
    params: status ? { limit, status } : { limit }
  })
}

export function getOrchestratorConfig(): Promise<Record<string, any>> {
  return aiRequest.get('/api/agent/orchestrator/ops/config')
}

export function getOrchestratorHealthDetail(includeGateway = true, includeContract = true): Promise<Record<string, any>> {
  return aiRequest.get('/api/agent/orchestrator/ops/health-detail', {
    params: { includeGateway, includeContract }
  })
}

export async function getOrchestratorQuickDiagnostics(): Promise<Record<string, any>> {
  const [healthDetail, summary] = await Promise.all([
    getOrchestratorHealthDetail(false, false),
    getOrchestratorOpsSummary(3)
  ])
  return { healthDetail, summary }
}

export function getOrchestratorPersistence(): Promise<Record<string, any>> {
  return aiRequest.get('/api/agent/orchestrator/ops/persistence')
}

export function getOrchestratorSnapshot(limit = 30, status?: string): Promise<Record<string, any>> {
  return aiRequest.get('/api/agent/orchestrator/ops/snapshot', {
    params: status ? { limit, status } : { limit }
  })
}

export function pruneOrchestratorAudit(params: {
  status?: string
  beforeMs?: number
  retainLatest?: number
  includePersist?: boolean
}): Promise<Record<string, any>> {
  return aiRequest.delete('/api/agent/orchestrator/ops/prune', {
    params: {
      status: params.status || undefined,
      beforeMs: params.beforeMs,
      retainLatest: params.retainLatest ?? 20,
      includePersist: params.includePersist ?? true
    }
  })
}
