import request from '../utils/request'

export function getDemoStatus(params?: { tenantId?: string; year?: number }): Promise<Record<string, any>> {
  return request.get('/api/demo/status', { params })
}

export function getDemoDashboard(params?: { tenantId?: string; year?: number }): Promise<Record<string, any>> {
  return request.get('/api/demo/dashboard', { params })
}

export function getDemoRoutes(params?: { tenantId?: string; year?: number }): Promise<Record<string, any>[]> {
  return request.get('/api/demo/routes', { params })
}

export function getDemoQuestions(): Promise<Record<string, any>[]> {
  return request.get('/api/demo/questions')
}