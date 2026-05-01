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
  level?: string
  code?: string
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
  templateMeta?: Record<string, any>
  sourceSummaries?: Record<string, any>[]
  trace?: Record<string, any>
}

export function chat(data: AgentChatRequest): Promise<any> {
  return aiRequest.post('/api/agent/chat', data)
}

export function generateMapObjectSolution(data: MapObjectSolutionRequest): Promise<MapObjectSolutionResponse> {
  return aiRequest.post('/api/agent/map-object/solution', data)
}

export interface MapAiContext {
  tenantId?: string
  mode?: 'OBJECT' | 'REGION' | 'VIEWPORT' | 'ROUTE' | 'FREE' | string
  routeCode?: string
  year?: number
  mapObject?: Record<string, any> | null
  regionSummary?: Record<string, any> | null
  viewport?: Record<string, any> | null
  selectedLayers?: string[]
  nearbyObjects?: Record<string, any>[]
  userQuestion?: string
  extra?: Record<string, any>
}

export interface MapAiAgentRequest {
  message: string
  mapContext?: MapAiContext
  context?: Record<string, any>
  mapObject?: Record<string, any> | null
  options?: Record<string, any>
}

export interface MapAiAgentResponse {
  answer: string
  mode?: string
  intent?: string
  mapContext?: MapAiContext
  toolResults?: Record<string, any>[]
  knowledgeSources?: Record<string, any>[]
  trace?: Record<string, any>
  data?: Record<string, any>
}

export interface AiKnowledgeIngestMarkdownRequest {
  tenantId?: string
  title: string
  sourceType?: string
  sourceId?: string
  content: string
  url?: string
  metadata?: Record<string, any>
}

export interface AiKnowledgeSearchRequest {
  tenantId?: string
  query: string
  topK?: number
  sourceTypes?: string[]
  filters?: Record<string, any>
}

export function mapAgentChat(data: MapAiAgentRequest): Promise<MapAiAgentResponse> {
  return aiRequest.post('/api/agent/map-agent/chat', data)
}

export function ingestKnowledgeMarkdown(data: AiKnowledgeIngestMarkdownRequest): Promise<Record<string, any>> {
  return aiRequest.post('/api/ai/knowledge/ingest/markdown', data)
}

export function searchAiKnowledge(data: AiKnowledgeSearchRequest): Promise<Record<string, any>> {
  return aiRequest.post('/api/ai/knowledge/search', data)
}


export interface AiKnowledgeStatsResponse {
  documentCount: number
  chunkCount: number
  embeddedChunkCount: number
  sourceTypes?: Record<string, number>
  embeddingProvider?: string
  embeddingModel?: string
  embeddingDimensions?: number
  vectorEnabled?: boolean
  status?: string
  message?: string
}

/**
 * Phase36 补强：知识库统计。
 */
export function getAiKnowledgeStats(tenantId?: string): Promise<AiKnowledgeStatsResponse> {
  return aiRequest.get('/api/ai/knowledge/stats', {
    params: tenantId ? { tenantId } : {}
  })
}


export interface RagEvalCase {
  id?: string
  question: string
  mapContext?: Record<string, any>
  expectedKeywords?: string[]
  expectedSources?: string[]
  options?: Record<string, any>
}

export interface RagEvalRequest {
  tenantId?: string
  cases: RagEvalCase[]
  topK?: number
  requireVectorUsed?: boolean
}

export function runRagEval(data: RagEvalRequest): Promise<Record<string, any>> {
  return aiRequest.post('/api/ai/eval/rag/run', data)
}

export function getDefaultRagEvalCases(): Promise<RagEvalCase[]> {
  return aiRequest.get('/api/ai/eval/rag/default-cases')
}
