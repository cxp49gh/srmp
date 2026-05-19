export type KnowledgeReadinessStatus =
  | 'READY'
  | 'EMBEDDING_UNAVAILABLE'
  | 'NO_CHUNKS'
  | 'NO_EMBEDDED_CHUNKS'
  | 'PENDING_VECTOR'

export interface KnowledgeReadinessInput {
  knowledgeStats?: Record<string, any> | null
  outlineStats?: Record<string, any> | null
  embedding?: Record<string, any> | null
}

export interface KnowledgeReadiness {
  status: KnowledgeReadinessStatus
  statusLabel: string
  tagType: 'success' | 'warning' | 'danger' | 'info'
  requiresAction: boolean
  title: string
  detail: string
  actions: Array<'VECTORIZE_OUTLINE' | 'SYNC_OUTLINE' | 'VERIFY_KNOWLEDGE'>
  documentCount: number
  chunkCount: number
  embeddedChunkCount: number
  pendingEmbeddingChunkCount: number
}

export function knowledgeFallbackDisplayText(value: any): string {
  const reason = String(value ?? '').trim()
  if (!reason) return ''
  const normalized = reason.toLowerCase()
  if (normalized === 'no embedded chunks') {
    return '暂无可用向量切片，AI 已尝试关键词检索；请在 AI 运维总览执行补向量或同步入库。'
  }
  if (normalized === 'query is empty') return '知识库检索词为空，知识库未参与本次回答。'
  if (normalized.includes('pgvector')) return '向量检索扩展不可用，AI 已尝试关键词检索；请检查 pgvector 与向量配置。'
  return `知识检索已切换兜底策略：${reason}`
}

export function buildKnowledgeReadiness(input: KnowledgeReadinessInput = {}): KnowledgeReadiness {
  const knowledgeStats = input.knowledgeStats || {}
  const outlineStats = input.outlineStats || {}
  const embedding = input.embedding || {}
  const documentCount = numberValue(firstValue(knowledgeStats, ['documentCount', 'document_count']))
  const chunkCount = numberValue(firstValue(knowledgeStats, ['chunkCount', 'chunk_count']))
  const embeddedChunkCount = numberValue(firstValue(knowledgeStats, ['embeddedChunkCount', 'embedded_chunk_count']))
  const pendingEmbeddingChunkCount = numberValue(firstValue(outlineStats, ['pendingEmbeddingChunkCount', 'pending_embedding_chunk_count']))
  const embeddingAvailable = firstValue(embedding, ['available'])

  if (embeddingAvailable === false) {
    return readiness({
      status: 'EMBEDDING_UNAVAILABLE',
      statusLabel: 'EMBEDDING',
      tagType: 'danger',
      title: 'Embedding 当前不可用',
      detail: '知识库无法生成或补齐向量，AI 会使用关键词检索；请先检查 Embedding Provider、模型和网络配置。',
      actions: ['VERIFY_KNOWLEDGE'],
      documentCount,
      chunkCount,
      embeddedChunkCount,
      pendingEmbeddingChunkCount
    })
  }

  if (embeddedChunkCount <= 0) {
    const hasChunks = chunkCount > 0
    return readiness({
      status: hasChunks ? 'NO_EMBEDDED_CHUNKS' : 'NO_CHUNKS',
      statusLabel: hasChunks ? 'NO VECTOR' : 'NO CHUNK',
      tagType: 'warning',
      title: hasChunks ? '暂无可用向量切片' : '本地知识库暂无切片',
      detail: hasChunks
        ? '已有知识切片但尚未生成可用向量，AI 将使用关键词检索；建议补向量后再验证知识库检索。'
        : '当前本地知识库还没有可检索切片，AI 将只能依赖业务数据和关键词兜底；建议先同步 Outline 或导入知识文档。',
      actions: hasChunks ? ['VECTORIZE_OUTLINE', 'SYNC_OUTLINE', 'VERIFY_KNOWLEDGE'] : ['SYNC_OUTLINE', 'VERIFY_KNOWLEDGE'],
      documentCount,
      chunkCount,
      embeddedChunkCount,
      pendingEmbeddingChunkCount
    })
  }

  if (pendingEmbeddingChunkCount > 0) {
    return readiness({
      status: 'PENDING_VECTOR',
      statusLabel: 'PENDING',
      tagType: 'warning',
      title: `仍有 ${pendingEmbeddingChunkCount} 个切片待补向量`,
      detail: '当前已有可用向量，但 Outline 仍存在未向量化切片；补齐后 RAG 覆盖会更完整。',
      actions: ['VECTORIZE_OUTLINE', 'VERIFY_KNOWLEDGE'],
      documentCount,
      chunkCount,
      embeddedChunkCount,
      pendingEmbeddingChunkCount
    })
  }

  return readiness({
    status: 'READY',
    statusLabel: 'READY',
    tagType: 'success',
    title: '知识库向量已就绪',
    detail: '本地知识库已有可用向量切片，可参与 AI/RAG 检索。',
    actions: ['VERIFY_KNOWLEDGE'],
    documentCount,
    chunkCount,
    embeddedChunkCount,
    pendingEmbeddingChunkCount
  })
}

function readiness(value: Omit<KnowledgeReadiness, 'requiresAction'>): KnowledgeReadiness {
  return {
    ...value,
    requiresAction: value.status !== 'READY'
  }
}

function firstValue(obj: Record<string, any>, keys: string[]) {
  for (const key of keys) {
    const value = obj[key]
    if (value !== undefined && value !== null && value !== '') return value
  }
  return undefined
}

function numberValue(value: any): number {
  const num = Number(value)
  return Number.isFinite(num) ? num : 0
}
