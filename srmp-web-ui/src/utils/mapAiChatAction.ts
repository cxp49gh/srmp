import type { MapAgentAction } from '../api/agent'

type ContextMode = 'ROUTE' | 'OBJECT' | 'REGION' | 'VIEWPORT' | 'FREE' | string

const METRIC_CODES = ['MQI', 'PQI', 'PCI', 'RQI', 'RDI', 'SCI', 'BCI', 'TCI']
const METRIC_EXPLAIN_WORDS = ['指标', '解释', '说明', '介绍', '含义', '定义', '是什么', '什么意思', '如何理解', '怎么计算', '计算公式']
const BUSINESS_CONTEXT_WORDS = ['结合当前', '结合地图', '当前路线', '当前对象', '这条路线', '该路线', '这个对象', '为什么低', '偏低', '低分原因']

function containsAny(text: string, words: string[]) {
  return words.some((word) => word && text.includes(word))
}

function isMetricKnowledgeQuestion(message: string) {
  const text = message.toUpperCase()
  return METRIC_CODES.some((code) => text.includes(code)) && containsAny(message, METRIC_EXPLAIN_WORDS)
}

function asksForBusinessScopedMetric(message: string) {
  return isMetricKnowledgeQuestion(message) && containsAny(message, BUSINESS_CONTEXT_WORDS)
}

function isKnowledgeOnlyQuestion(message: string) {
  if (isMetricKnowledgeQuestion(message) && !asksForBusinessScopedMetric(message)) return true
  return containsAny(message, ['规范', '标准', '工艺', '依据']) && !containsAny(message, ['分析当前', '结合当前', '当前对象', '当前路线', '当前区域'])
}

export function resolveChatAction(contextMode: ContextMode, message: string): MapAgentAction {
  const mode = String(contextMode || '').toUpperCase()
  const text = String(message || '').trim()
  if (!text) return 'CHAT'
  if (isKnowledgeOnlyQuestion(text)) return 'CHAT'

  if (containsAny(text, ['分析当前地图选中对象', '分析当前对象', '选中对象'])) return 'ANALYZE_OBJECT'
  if (containsAny(text, ['综合分析当前区域', '分析当前区域', '当前区域内', '框选区域'])) return 'ANALYZE_REGION'
  if (containsAny(text, ['分析当前路线', '分析当前路线范围', '当前路线整体', '路线整体路况', '找出次差路段', '低分单元', '病害集中区域'])) return 'ANALYZE_ROUTE'
  if (asksForBusinessScopedMetric(text) && mode === 'ROUTE') return 'ANALYZE_ROUTE'
  if (containsAny(text, ['主要问题', '成因判断', '养护重点', '处置建议'])) {
    if (mode === 'OBJECT') return 'ANALYZE_OBJECT'
    if (mode === 'REGION') return 'ANALYZE_REGION'
    if (mode === 'ROUTE') return 'ANALYZE_ROUTE'
  }

  return 'CHAT'
}
