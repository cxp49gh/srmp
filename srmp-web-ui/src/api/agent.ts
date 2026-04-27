import { aiRequest } from '../utils/request'

export interface AgentChatRequest {
  message: string
  context?: Record<string, any>
  mapObject?: Record<string, any> | null
  options?: Record<string, any>
}

export function chat(data: AgentChatRequest): Promise<any> {
  return aiRequest.post('/api/agent/chat', data)
}
