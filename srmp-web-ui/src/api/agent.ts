import { aiRequest } from '../utils/request'

export interface AgentChatRequest {
  message: string
  context?: Record<string, any>
  mapObject?: Record<string, any> | null
  options?: Record<string, any>
}

export type MapObjectSolutionType =
  | 'DISEASE_REVIEW'
  | 'DISEASE_TREATMENT'
  | 'LOW_SCORE_TREATMENT'
  | 'EVALUATION_UNIT_ADVICE'
  | 'SECTION_PLAN'
  | 'ROUTE_REPORT'
  | 'GENERAL_ADVICE'

export interface MapObjectSolutionRequest {
  tenantId?: string
  objectType?: string
  objectId?: string
  routeCode?: string
  year?: number
  solutionType: MapObjectSolutionType
  mapObject?: Record<string, any> | null
  options?: Record<string, any>
}

export interface MapObjectSolutionQualityItem {
  name: string
  passed: boolean
  message?: string
}

export interface MapObjectSolutionQualityCheck {
  passed: boolean
  warnings?: string[]
  items?: MapObjectSolutionQualityItem[]
}

export interface MapObjectSolutionResponse {
  solutionType: MapObjectSolutionType
  title: string
  markdown: string
  objectSummary?: Record<string, any>
  qualityCheck?: MapObjectSolutionQualityCheck
}

export function chat(data: AgentChatRequest): Promise<any> {
  return aiRequest.post('/api/agent/chat', data)
}

export function generateMapObjectSolution(data: MapObjectSolutionRequest): Promise<MapObjectSolutionResponse> {
  return aiRequest.post('/api/agent/map-object/solution', data)
}
