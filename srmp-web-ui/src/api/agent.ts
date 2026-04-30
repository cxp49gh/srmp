import { aiRequest } from '../utils/request'

export interface AgentChatRequest {
  message: string
  context?: Record<string, any>
  mapObject?: Record<string, any> | null
  options?: Record<string, any>
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
  nearbyObjects?: Array<Record<string, any>>
  userQuestion?: string
  extra?: Record<string, any>
}

export interface MapAgentChatRequest extends AgentChatRequest {
  mapContext?: MapAiContext
}

export interface AiKnowledgeMarkdownIngestRequest {
  tenantId?: string
  title: string
  sourceType?: string
  sourceId?: string
  content: string
  metadata?: Record<string, any>
}

export interface AiKnowledgeSearchRequest {
  tenantId?: string
  query: string
  topK?: number
  filters?: Record<string, any>
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

/**
 * Phase36：一张图 AI Agent 聊天接口。
 * 检查关键字：mapAgentChat
 */
export function mapAgentChat(data: MapAgentChatRequest): Promise<any> {
  return aiRequest.post('/api/agent/map-agent/chat', data)
}

export function generateMapObjectSolution(data: MapObjectSolutionRequest): Promise<MapObjectSolutionResponse> {
  return aiRequest.post('/api/agent/map-object/solution', data)
}


/**
 * Phase36：Markdown 知识入库。
 * 检查关键字：ingestKnowledgeMarkdown
 */
export function ingestKnowledgeMarkdown(data: AiKnowledgeMarkdownIngestRequest): Promise<any> {
  return aiRequest.post('/api/ai/knowledge/ingest/markdown', data)
}

/**
 * Phase36：AI 知识库检索。
 * 检查关键字：searchAiKnowledge
 */
export function searchAiKnowledge(data: AiKnowledgeSearchRequest): Promise<any> {
  return aiRequest.post('/api/ai/knowledge/search', data)
}

/**
 * 兼容别名。
 */
export function searchKnowledge(data: AiKnowledgeSearchRequest): Promise<any> {
  return searchAiKnowledge(data)
}
