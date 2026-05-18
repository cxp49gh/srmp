export type AiSourceCategory = 'BUSINESS' | 'KNOWLEDGE' | 'OUTLINE'

export interface NormalizedAiSource {
  key: string
  category: AiSourceCategory
  title: string
  excerpt: string
  url: string
  updatedAt: string
  syncedAt: string
  fromLocalKb: boolean
  score?: number
  raw: Record<string, any>
}

const BUSINESS_TOOL_PREFIXES = ['route.', 'disease.', 'assessment.', 'index.', 'section.', 'business.']

export function pickField(obj: Record<string, any> | null | undefined, ...keys: string[]) {
  if (!obj) return ''
  for (const key of keys) {
    const v = obj[key]
    if (v !== undefined && v !== null && String(v).trim() !== '') return String(v)
  }
  return ''
}

export function classifySource(source: Record<string, any>): AiSourceCategory {
  const type = pickField(source, 'sourceType', 'source_type', 'type').toUpperCase()
  if (type === 'OUTLINE') return 'OUTLINE'
  if (type === 'BUSINESS' || source._category === 'BUSINESS') return 'BUSINESS'
  const toolName = pickField(source, 'toolName', 'tool_name', 'name').toLowerCase()
  if (toolName && BUSINESS_TOOL_PREFIXES.some((p) => toolName.startsWith(p))) return 'BUSINESS'
  return 'KNOWLEDGE'
}

export function categoryLabel(category: AiSourceCategory) {
  const map: Record<AiSourceCategory, string> = {
    BUSINESS: '业务数据',
    KNOWLEDGE: '本地知识库',
    OUTLINE: 'Outline 文档'
  }
  return map[category] || category
}

export function categoryTagType(category: AiSourceCategory): 'success' | 'warning' | 'info' | '' {
  if (category === 'OUTLINE') return 'warning'
  if (category === 'BUSINESS') return 'info'
  return 'success'
}

function metadataField(source: Record<string, any>, ...keys: string[]) {
  const meta = source.metadata || source.meta || {}
  return pickField(meta, ...keys) || pickField(source, ...keys)
}

export function normalizeSource(source: Record<string, any>, index = 0): NormalizedAiSource {
  const category = classifySource(source)
  const type = pickField(source, 'sourceType', 'source_type', 'type').toUpperCase()
  const fromLocalKb = category === 'OUTLINE' || type === 'OUTLINE' || Boolean(metadataField(source, 'outlineDocumentId', 'outline_document_id'))
  return {
    key: pickField(source, 'chunkId', 'chunk_id', 'id', 'documentId', 'document_id') || `source-${index}`,
    category,
    title: pickField(source, 'title', 'sourceTitle', 'source_title', 'documentTitle', 'document_title', 'name') || '未命名来源',
    excerpt: pickField(source, 'content', 'text', 'contentExcerpt', 'content_excerpt', 'excerpt', 'summary') || '',
    url: metadataField(source, 'sourceUrl', 'source_url', 'url', 'outlineUrl', 'outline_url') || pickField(source, 'sourceUrl', 'source_url', 'url'),
    updatedAt: metadataField(source, 'outlineUpdatedAt', 'outline_updated_at', 'updatedAt', 'updated_at') || pickField(source, 'updatedAt', 'updated_at'),
    syncedAt: pickField(source, 'syncedAt', 'synced_at', 'documentUpdatedAt', 'document_updated_at') || metadataField(source, 'syncedAt', 'synced_at'),
    fromLocalKb,
    score: source.score != null ? Number(source.score) : undefined,
    raw: source
  }
}

export function businessSourcesFromTools(toolResults: Record<string, any>[] = []) {
  const items: Record<string, any>[] = []
  toolResults.forEach((tool, index) => {
    const name = pickField(tool, 'toolName', 'tool_name', 'name')
    if (!name || name === 'knowledge.retrieve') return
    if (tool.success === false) return
    items.push({
      _category: 'BUSINESS',
      id: `tool-${index}`,
      title: name,
      content: pickField(tool, 'summary') || JSON.stringify(tool.data || tool.result || {}),
      sourceType: 'BUSINESS',
      raw: tool
    })
  })
  return items
}

export function mergeAiSources(sources: Record<string, any>[] = [], toolResults: Record<string, any>[] = [], outlineSources: Record<string, any>[] = []) {
  const merged: Record<string, any>[] = []
  sources.forEach((s) => merged.push(s))
  outlineSources.forEach((s) => merged.push({ ...s, sourceType: s.sourceType || 'OUTLINE' }))
  businessSourcesFromTools(toolResults).forEach((s) => merged.push(s))
  return merged.map((s, i) => normalizeSource(s, i))
}

export function groupNormalizedSources(items: NormalizedAiSource[]) {
  const groups: Record<AiSourceCategory, NormalizedAiSource[]> = {
    BUSINESS: [],
    KNOWLEDGE: [],
    OUTLINE: []
  }
  items.forEach((item) => groups[item.category].push(item))
  return groups
}
