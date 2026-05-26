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
  action?: string
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

export function replayOrchestratorRecord(recordId: string, execute = false, adaptiveMode = 'default'): Promise<Record<string, any>> {
  return aiRequest.post(`/api/agent/orchestrator/ops/replay/${encodeURIComponent(recordId)}`, {}, {
    params: { execute, adaptiveMode }
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

export function getOrchestratorLiveTrace(traceId: string): Promise<Record<string, any>> {
  return aiRequest.get(`/api/agent/orchestrator/ops/live-trace/${encodeURIComponent(traceId)}`)
}

export function getOrchestratorPersistence(): Promise<Record<string, any>> {
  return aiRequest.get('/api/agent/orchestrator/ops/persistence')
}

export function getAiGovernanceCapabilities(): Promise<Record<string, any>> {
  return aiRequest.get('/api/agent/orchestrator/ops/governance/capabilities')
}

export function getAiGovernanceCapability(capabilityId: string): Promise<Record<string, any>> {
  return aiRequest.get(`/api/agent/orchestrator/ops/governance/capabilities/${encodeURIComponent(capabilityId)}`)
}

export function getAiGovernanceTools(): Promise<Record<string, any>> {
  return aiRequest.get('/api/agent/orchestrator/ops/governance/tools')
}

export function getAiGovernanceTool(toolName: string, includeContract = true): Promise<Record<string, any>> {
  return aiRequest.get(`/api/agent/orchestrator/ops/governance/tools/${encodeURIComponent(toolName)}`, {
    params: { includeContract }
  })
}

export function getAiGovernanceReadiness(includeContract = true, runCoverage = true): Promise<Record<string, any>> {
  return aiRequest.get('/api/agent/orchestrator/ops/governance/readiness', {
    params: { includeContract, runCoverage }
  })
}

export function getAiGovernanceConfig(): Promise<Record<string, any>> {
  return aiRequest.get('/api/agent/orchestrator/ops/governance/config')
}

export function validateAiGovernanceConfigDraft(data: Record<string, any>): Promise<Record<string, any>> {
  return aiRequest.post('/api/agent/orchestrator/ops/governance/config/draft/validate', data || {})
}

export function getAiGovernanceConfigPublishRequests(limit = 20): Promise<Record<string, any>> {
  return aiRequest.get('/api/agent/orchestrator/ops/governance/config/publish/requests', {
    params: { limit }
  })
}

export function getAiGovernanceConfigPublishRequest(requestId: string): Promise<Record<string, any>> {
  return aiRequest.get(`/api/agent/orchestrator/ops/governance/config/publish/requests/${encodeURIComponent(requestId)}`)
}

export function submitAiGovernanceConfigPublishRequest(data: Record<string, any>): Promise<Record<string, any>> {
  return aiRequest.post('/api/agent/orchestrator/ops/governance/config/publish/request', data || {})
}

export function requestAiGovernanceConfigRollback(requestId: string, data: Record<string, any>): Promise<Record<string, any>> {
  return aiRequest.post(`/api/agent/orchestrator/ops/governance/config/publish/requests/${encodeURIComponent(requestId)}/rollback`, data || {})
}

export function decideAiGovernanceConfigPublishRequest(requestId: string, action: string, data: Record<string, any>): Promise<Record<string, any>> {
  return aiRequest.post(`/api/agent/orchestrator/ops/governance/config/publish/requests/${encodeURIComponent(requestId)}/${encodeURIComponent(action)}`, data || {})
}

export function validateAiGovernancePolicies(): Promise<Record<string, any>> {
  return aiRequest.get('/api/agent/orchestrator/ops/governance/policies/validate')
}

export function getAiGovernancePolicyCoverage(): Promise<Record<string, any>> {
  return aiRequest.get('/api/agent/orchestrator/ops/governance/policies/coverage')
}

export function getAiGovernanceToolImpact(): Promise<Record<string, any>> {
  return aiRequest.get('/api/agent/orchestrator/ops/governance/tools/impact')
}

export function simulateAiGovernancePlan(data: OrchestratorPlanRequest): Promise<Record<string, any>> {
  return aiRequest.post('/api/agent/orchestrator/ops/governance/plan-simulate', data || {})
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
