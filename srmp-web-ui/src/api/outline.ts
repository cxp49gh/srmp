import request from '../utils/request'

export interface OutlineSearchRequest { query: string; limit?: number }
export interface OutlineSearchResult { id: string; title: string; text?: string; url?: string; score?: number }
export interface OutlineCollection { id: string; name: string; description?: string; url?: string }
export interface OutlineDocument { id: string; collectionId?: string; title: string; text?: string; url?: string; updatedAt?: string }
export interface OutlineSyncRequest { collectionId?: string; limit?: number; force?: boolean }

export function getOutlineStatus(): Promise<Record<string, any>> { return request.get('/api/outline/status') }
export function searchOutline(data: OutlineSearchRequest): Promise<OutlineSearchResult[]> { return request.post('/api/outline/search', data) }
export function getOutlineDocument(id: string): Promise<Record<string, any>> { return request.get(`/api/outline/documents/${id}`) }
export function getOutlineCollections(): Promise<OutlineCollection[]> { return request.get('/api/outline/collections') }
export function listOutlineDocuments(data: { collectionId?: string; limit?: number; offset?: number }): Promise<OutlineDocument[]> {
  return request.post('/api/outline/documents/list', data)
}
export function syncOutline(data: OutlineSyncRequest): Promise<Record<string, any>> { return request.post('/api/outline/sync', data) }
export function getOutlineSyncTasks(limit = 20): Promise<Record<string, any>[]> {
  return request.get('/api/outline/sync-tasks', { params: { limit } })
}
export function getOutlineSyncTask(id: string): Promise<Record<string, any>> { return request.get(`/api/outline/sync-tasks/${id}`) }