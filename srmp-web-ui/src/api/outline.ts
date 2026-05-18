import request, { longRequest } from '../utils/request'

export interface OutlineSearchRequest { query: string; limit?: number }
export interface OutlineSearchResult { id: string; title: string; text?: string; url?: string; score?: number }
export interface OutlineCollection { id: string; name: string; description?: string; url?: string }
export interface OutlineDocument {
  id: string
  collectionId?: string
  title: string
  text?: string
  url?: string
  updatedAt?: string
  knowledgeDocumentId?: string
  kbSyncStatus?: 'NOT_SYNCED' | 'INACTIVE' | 'PENDING_VECTOR' | 'RAG_READY'
  chunkCount?: number
  embeddedChunkCount?: number
  ragReady?: boolean
}

export interface OutlineSyncRequest {
  collectionId?: string
  limit?: number
  force?: boolean
  dryRun?: boolean
  cleanupMissing?: boolean
  documentIds?: string[]
  retryTaskId?: string
}

export function getOutlineStatus(): Promise<Record<string, any>> { return request.get('/api/outline/status') }
export function searchOutline(data: OutlineSearchRequest): Promise<OutlineSearchResult[]> { return request.post('/api/outline/search', data) }
export function getOutlineDocument(id: string): Promise<Record<string, any>> { return request.get(`/api/outline/documents/${id}`) }
export function getOutlineCollections(): Promise<OutlineCollection[]> { return request.get('/api/outline/collections') }
export function listOutlineDocuments(data: { collectionId?: string; limit?: number; offset?: number }): Promise<OutlineDocument[]> {
  return request.post('/api/outline/documents/list', data)
}
export function syncOutline(data: OutlineSyncRequest): Promise<Record<string, any>> { return longRequest.post('/api/outline/sync', data) }
export function getOutlineSyncTasks(params?: {
  limit?: number
  status?: string
  collectionId?: string
  dryRun?: boolean
  from?: string
  to?: string
}): Promise<Record<string, any>[]> {
  return request.get('/api/outline/sync-tasks', { params: { limit: params?.limit ?? 20, ...params } })
}
export function getOutlineSyncTask(id: string): Promise<Record<string, any>> { return request.get(`/api/outline/sync-tasks/${id}`) }
export function getOutlineSyncTaskDetails(id: string, params?: { status?: string; limit?: number }): Promise<Record<string, any>[]> {
  return request.get(`/api/outline/sync-tasks/${id}/details`, { params })
}
export function retryOutlineFailedTask(id: string, force = true): Promise<Record<string, any>> {
  return request.post(`/api/outline/sync-tasks/${id}/retry-failed`, { force })
}

export function getOutlineKnowledgeStats(): Promise<Record<string, any>> {
  return request.get('/api/outline/knowledge-stats')
}

export function vectorizeOutline(data: { force?: boolean; dryRun?: boolean; limit?: number } = {}): Promise<Record<string, any>> {
  return longRequest.post('/api/outline/vectorize', data)
}


export interface OutlineAutoSyncConfigRequest {
  id?: string
  name?: string
  enabled?: boolean
  collectionId?: string
  syncScope?: 'COLLECTION' | 'SINGLE_DOCUMENT' | 'MULTIPLE_DOCUMENTS' | string
  documentIds?: string[]
  intervalMinutes?: number
  force?: boolean
  cleanupMissing?: boolean
  vectorizeAfterSync?: boolean
  vectorForce?: boolean
  vectorLimit?: number
  webhookEnabled?: boolean
  webhookSecret?: string
}

export interface OutlineAutoSyncRunRequest {
  triggerType?: string
  outlineEvent?: string
  outlineDocumentId?: string
  outlineCollectionId?: string
  force?: boolean
  vectorizeAfterSync?: boolean
}

export function getOutlineAutoSyncConfigs(): Promise<Record<string, any>[]> {
  return request.get('/api/outline/auto-sync/configs')
}

export function createOutlineAutoSyncConfig(data: OutlineAutoSyncConfigRequest): Promise<Record<string, any>> {
  return request.post('/api/outline/auto-sync/configs', data)
}

export function updateOutlineAutoSyncConfig(id: string, data: OutlineAutoSyncConfigRequest): Promise<Record<string, any>> {
  return request.put(`/api/outline/auto-sync/configs/${id}`, data)
}

export function stopOutlineAutoSyncConfig(id: string): Promise<Record<string, any>> {
  return request.post(`/api/outline/auto-sync/configs/${id}/stop`)
}

export function deleteOutlineAutoSyncConfig(id: string): Promise<Record<string, any>> {
  return request.delete(`/api/outline/auto-sync/configs/${id}`)
}

export function runOutlineAutoSyncNow(id: string, data?: OutlineAutoSyncRunRequest): Promise<Record<string, any>> {
  return longRequest.post(`/api/outline/auto-sync/configs/${id}/run`, data || { triggerType: 'MANUAL' })
}

export function getOutlineAutoSyncRuns(params?: { configId?: string; limit?: number }): Promise<Record<string, any>[]> {
  return request.get('/api/outline/auto-sync/runs', { params })
}

export function scanOutlineAutoSyncDue(): Promise<Record<string, any>> {
  return longRequest.post('/api/outline/auto-sync/scan-due')
}

export function testOutlineAutoSyncWebhook(secret: string, payload: Record<string, any>): Promise<Record<string, any>> {
  return longRequest.post('/api/outline/auto-sync/webhook', payload, {
    headers: { 'X-Outline-Webhook-Secret': secret }
  })
}

export function getOutlineGovernanceDashboard(): Promise<Record<string, any>> {
  return request.get('/api/outline/governance/dashboard')
}
