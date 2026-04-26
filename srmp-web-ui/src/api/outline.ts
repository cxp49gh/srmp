import request from '../utils/request'

export interface OutlineSearchRequest {
  query: string
  limit?: number
}

export interface OutlineSearchResult {
  id: string
  title: string
  text?: string
  url?: string
  score?: number
}

export function getOutlineStatus(): Promise<Record<string, any>> {
  return request.get('/api/outline/status')
}

export function searchOutline(data: OutlineSearchRequest): Promise<OutlineSearchResult[]> {
  return request.post('/api/outline/search', data)
}

export function getOutlineDocument(id: string): Promise<Record<string, any>> {
  return request.get(`/api/outline/documents/${id}`)
}