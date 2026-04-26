import request from '../utils/request'

export interface KnowledgeDocumentRequest {
  title: string
  content: string
  sourceType?: string
  docType?: string
  category?: string
  url?: string
}

export interface KnowledgeSearchRequest {
  query: string
  category?: string
  sourceType?: string
  topK?: number
}

export interface KnowledgeSearchResult {
  documentId: string
  chunkId: string
  title: string
  heading?: string
  content: string
  sourceType?: string
  sourceUrl?: string
  score?: number
}

export function createKnowledgeDocument(data: KnowledgeDocumentRequest): Promise<{ id: string }> {
  return request.post('/api/knowledge/documents', data)
}

export function searchKnowledge(data: KnowledgeSearchRequest): Promise<KnowledgeSearchResult[]> {
  return request.post('/api/knowledge/search', data)
}

export function askKnowledge(data: KnowledgeSearchRequest): Promise<any> {
  return request.post('/api/knowledge/ask', data)
}