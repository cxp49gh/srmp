import request from '../utils/request'

export type AiFeedbackType = 'MISSING_KNOWLEDGE' | 'SOURCE_INACCURATE'

export interface AiKnowledgeFeedbackRequest {
  feedbackType: AiFeedbackType
  question?: string
  remark?: string
  userId?: string
  businessContext?: Record<string, any>
  citedSources?: Record<string, any>[]
}

export function createAiKnowledgeFeedback(data: AiKnowledgeFeedbackRequest): Promise<Record<string, any>> {
  return request.post('/api/knowledge/feedback', data)
}

export function listAiKnowledgeFeedback(params?: { feedbackType?: string; limit?: number }): Promise<Record<string, any>[]> {
  return request.get('/api/knowledge/feedback', { params })
}
