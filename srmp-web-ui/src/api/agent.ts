import { aiRequest } from '../utils/request'

export interface AgentChatRequest {
  message: string
  context?: Record<string, any>
  options?: Record<string, any>
}

export function chat(data: AgentChatRequest): Promise<any> {
  return aiRequest.post('/api/agent/chat', data)
}
