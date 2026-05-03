export type RoadConditionMetricCode =
  | 'MQI'
  | 'PQI'
  | 'PCI'
  | 'RQI'
  | 'RDI'
  | 'SRI'
  | 'PSSI'
  | 'SCI'
  | 'BCI'
  | 'TCI'

export interface RoadConditionMetricMeta {
  code: RoadConditionMetricCode
  name: string
  shortName: string
  dimension: '综合' | '路面' | '路基' | '桥隧构造物' | '沿线设施'
  description: string
  higherIsBetter: boolean
}

export interface RoadConditionGradeMeta {
  code: string
  label: string
  shortLabel: string
  color: string
  rangeText: string
}

export const ROAD_CONDITION_METRICS: RoadConditionMetricMeta[] = [
  { code: 'MQI', name: '公路技术状况指数', shortName: '综合 MQI', dimension: '综合', description: '综合反映路面、路基、桥隧构造物和沿线设施技术状况。', higherIsBetter: true },
  { code: 'PQI', name: '路面使用性能指数', shortName: '路面 PQI', dimension: '路面', description: '综合反映路面损坏、行驶质量、车辙、抗滑等路面性能。', higherIsBetter: true },
  { code: 'PCI', name: '路面损坏状况指数', shortName: '损坏 PCI', dimension: '路面', description: '反映裂缝、坑槽、沉陷、松散等路面病害对路况的影响。', higherIsBetter: true },
  { code: 'RQI', name: '路面行驶质量指数', shortName: '平整 RQI', dimension: '路面', description: '反映行驶舒适性和平整度状况。', higherIsBetter: true },
  { code: 'RDI', name: '路面车辙深度指数', shortName: '车辙 RDI', dimension: '路面', description: '反映车辙深度对路面服务水平的影响。', higherIsBetter: true },
  { code: 'SRI', name: '路面抗滑性能指数', shortName: '抗滑 SRI', dimension: '路面', description: '反映路面抗滑性能和行车安全相关风险。', higherIsBetter: true },
  { code: 'PSSI', name: '路面结构强度指数', shortName: '强度 PSSI', dimension: '路面', description: '反映路面结构承载能力和结构性养护需求。', higherIsBetter: true },
  { code: 'SCI', name: '路基技术状况指数', shortName: '路基 SCI', dimension: '路基', description: '反映路基边坡、排水、防护等路基设施状况。', higherIsBetter: true },
  { code: 'BCI', name: '桥隧构造物技术状况指数', shortName: '桥隧 BCI', dimension: '桥隧构造物', description: '反映桥梁、隧道、涵洞等构造物技术状况。', higherIsBetter: true },
  { code: 'TCI', name: '沿线设施技术状况指数', shortName: '设施 TCI', dimension: '沿线设施', description: '反映交通安全设施、标志标线等沿线设施状况。', higherIsBetter: true }
]

export const ROAD_CONDITION_GRADES: RoadConditionGradeMeta[] = [
  { code: 'EXCELLENT', label: '优', shortLabel: '优', color: '#16a34a', rangeText: '≥ 90' },
  { code: 'GOOD', label: '良', shortLabel: '良', color: '#2563eb', rangeText: '80–89' },
  { code: 'MEDIUM', label: '中', shortLabel: '中', color: '#eab308', rangeText: '70–79' },
  { code: 'POOR', label: '次', shortLabel: '次', color: '#f97316', rangeText: '60–69' },
  { code: 'BAD', label: '差', shortLabel: '差', color: '#dc2626', rangeText: '< 60' }
]

export function normalizeMetricCode(value?: any): RoadConditionMetricCode {
  const code = String(value || 'MQI').trim().toUpperCase()
  return (ROAD_CONDITION_METRICS.some((item) => item.code === code) ? code : 'MQI') as RoadConditionMetricCode
}

export function getMetricMeta(value?: any): RoadConditionMetricMeta {
  const code = normalizeMetricCode(value)
  return ROAD_CONDITION_METRICS.find((item) => item.code === code) || ROAD_CONDITION_METRICS[0]
}

export function getGradeMeta(value?: any): RoadConditionGradeMeta | undefined {
  const code = String(value || '').trim().toUpperCase()
  return ROAD_CONDITION_GRADES.find((item) => item.code === code || item.label === value || item.shortLabel === value)
}

export function gradeFromScore(value?: any): string {
  const score = Number(value)
  if (!Number.isFinite(score)) return ''
  if (score >= 90) return 'EXCELLENT'
  if (score >= 80) return 'GOOD'
  if (score >= 70) return 'MEDIUM'
  if (score >= 60) return 'POOR'
  return 'BAD'
}

export function gradeLabel(value?: any): string {
  return getGradeMeta(value)?.label || String(value || '-')
}

export function metricDisplayName(value?: any) {
  const meta = getMetricMeta(value)
  return `${meta.code} ${meta.name}`
}

export function formatMetricValue(value?: any) {
  if (value === undefined || value === null || value === '') return '-'
  const num = Number(value)
  if (!Number.isFinite(num)) return String(value)
  return num % 1 === 0 ? String(num) : num.toFixed(1)
}

export function getMetricValue(source: Record<string, any> | undefined | null, indexCode?: any) {
  if (!source || typeof source !== 'object') return undefined
  const code = normalizeMetricCode(indexCode)
  const lower = code.toLowerCase()
  const camel = lower.charAt(0).toUpperCase() + lower.slice(1)
  const candidates = [
    code,
    lower,
    `${lower}Value`,
    `${lower}_value`,
    `avg${camel}`,
    `avg_${lower}`,
    `average${camel}`,
    `average_${lower}`,
    'metricValue',
    'metric_value',
    'score',
    'value'
  ]
  for (const key of candidates) {
    const value = source[key]
    if (value !== undefined && value !== null && value !== '') return value
  }
  const metricScores = source.metricScores || source.metric_scores || source.metrics || source.indexValues || source.index_values
  if (metricScores && typeof metricScores === 'object') {
    return metricScores[code] ?? metricScores[lower]
  }
  return undefined
}

export function getMetricGrade(source: Record<string, any> | undefined | null, indexCode?: any) {
  if (!source || typeof source !== 'object') return ''
  const code = normalizeMetricCode(indexCode)
  const lower = code.toLowerCase()
  const camel = lower.charAt(0).toUpperCase() + lower.slice(1)
  const candidates = [
    `${lower}Grade`,
    `${lower}_grade`,
    `${code}Grade`,
    `grade${camel}`,
    `grade_${lower}`,
    'metricGrade',
    'metric_grade'
  ]
  for (const key of candidates) {
    const value = source[key]
    if (value !== undefined && value !== null && value !== '') return String(value).toUpperCase()
  }
  const gradeMap = source.metricGrades || source.metric_grades || source.grades || source.indexGrades || source.index_grades
  if (gradeMap && typeof gradeMap === 'object') {
    const value = gradeMap[code] ?? gradeMap[lower]
    if (value !== undefined && value !== null && value !== '') return String(value).toUpperCase()
  }
  const explicitGrade = source.grade || source.conditionGrade || source.condition_grade
  const score = getMetricValue(source, indexCode)
  return explicitGrade ? String(explicitGrade).toUpperCase() : gradeFromScore(score)
}
