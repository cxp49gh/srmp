export type AssessmentSolutionAction = {
  type: 'LOW_SCORE_TREATMENT' | 'EVALUATION_UNIT_ADVICE'
  label: string
}

const LOW_GRADE_CODES = new Set(['POOR', 'BAD'])
const NON_LOW_GRADE_CODES = new Set(['EXCELLENT', 'GOOD', 'MEDIUM'])

export function isLowAssessmentResult(source: Record<string, any> | undefined | null, indexCode: any = 'MQI') {
  const grade = normalizeAssessmentGrade(pickAssessmentGrade(source, indexCode))
  if (LOW_GRADE_CODES.has(grade)) return true
  if (NON_LOW_GRADE_CODES.has(grade)) return false

  const score = Number(pickAssessmentScore(source, indexCode))
  return Number.isFinite(score) && score < 70
}

export function assessmentAnalyzeLabel(source: Record<string, any> | undefined | null, indexCode: any = 'MQI') {
  return isLowAssessmentResult(source, indexCode) ? '分析低分原因' : '分析评定结果'
}

export function assessmentSolutionAction(source: Record<string, any> | undefined | null, indexCode: any = 'MQI'): AssessmentSolutionAction {
  if (isLowAssessmentResult(source, indexCode)) {
    return { type: 'LOW_SCORE_TREATMENT', label: '生成低分处置建议' }
  }
  return { type: 'EVALUATION_UNIT_ADVICE', label: '生成评定养护建议' }
}

export function assessmentSolutionLabel(source: Record<string, any> | undefined | null, indexCode: any = 'MQI') {
  return assessmentSolutionAction(source, indexCode).label
}

export function assessmentOperationHint(source: Record<string, any> | undefined | null, indexCode: any = 'MQI') {
  if (isLowAssessmentResult(source, indexCode)) {
    return '分析低分原因用于解释指标问题；生成低分处置建议会生成结构化建议，预览后可保存为方案草稿。'
  }
  return '分析评定结果用于解释指标表现和关联病害；生成评定养护建议会生成结构化建议，预览后可保存为方案草稿。'
}

function pickAssessmentGrade(source: Record<string, any> | undefined | null, indexCode: any) {
  const record = asRecord(source)
  const activeMetricCode = normalizeMetricCode(pickDeep(record, 'activeMetricCode', 'active_metric_code', 'activeIndexCode', 'active_index_code'))
  if (!activeMetricCode || activeMetricCode === normalizeMetricCode(indexCode)) {
    const activeGrade = pickDeep(record, 'activeMetricGrade', 'active_metric_grade', 'activeIndexGrade', 'active_index_grade')
    if (hasValue(activeGrade)) return activeGrade
  }

  const code = normalizeMetricCode(indexCode)
  const lower = code.toLowerCase()
  const camel = lower.charAt(0).toUpperCase() + lower.slice(1)
  return pickDeep(
    record,
    `${lower}Grade`,
    `${lower}_grade`,
    `${code}Grade`,
    `grade${camel}`,
    `grade_${lower}`,
    'metricGrade',
    'metric_grade',
    'grade',
    'conditionGrade',
    'condition_grade'
  )
}

function pickAssessmentScore(source: Record<string, any> | undefined | null, indexCode: any) {
  const record = asRecord(source)
  const activeMetricCode = normalizeMetricCode(pickDeep(record, 'activeMetricCode', 'active_metric_code', 'activeIndexCode', 'active_index_code'))
  if (!activeMetricCode || activeMetricCode === normalizeMetricCode(indexCode)) {
    const activeScore = pickDeep(record, 'activeMetricValue', 'active_metric_value', 'activeIndexValue', 'active_index_value')
    if (hasValue(activeScore)) return activeScore
  }

  const code = normalizeMetricCode(indexCode)
  const lower = code.toLowerCase()
  const camel = lower.charAt(0).toUpperCase() + lower.slice(1)
  return pickDeep(
    record,
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
    'value',
    'mqi',
    'pqi',
    'pci'
  )
}

function normalizeAssessmentGrade(value: any) {
  const raw = String(value || '').trim()
  const upper = raw.toUpperCase()
  if (['EXCELLENT', '优', '优秀'].includes(upper) || ['优', '优秀'].includes(raw)) return 'EXCELLENT'
  if (['GOOD', '良', '良好'].includes(upper) || ['良', '良好'].includes(raw)) return 'GOOD'
  if (['MEDIUM', '中', '中等'].includes(upper) || ['中', '中等'].includes(raw)) return 'MEDIUM'
  if (['POOR', '次', '较差'].includes(upper) || ['次', '较差'].includes(raw)) return 'POOR'
  if (['BAD', '差', '很差'].includes(upper) || ['差', '很差'].includes(raw)) return 'BAD'
  return upper
}

function normalizeMetricCode(value: any) {
  return String(value || 'MQI').trim().toUpperCase()
}

function pickDeep(record: Record<string, any>, ...keys: string[]) {
  const direct = pick(record, keys)
  if (hasValue(direct)) return direct

  const metricScores = asRecord(record.metricScores || record.metric_scores || record.metrics || record.indexValues || record.index_values)
  const metricGrades = asRecord(record.metricGrades || record.metric_grades || record.grades || record.indexGrades || record.index_grades)
  const fromMaps = pick(metricScores, keys) ?? pick(metricGrades, keys)
  if (hasValue(fromMaps)) return fromMaps

  const raw = asRecord(record.raw)
  if (raw !== record && Object.keys(raw).length) {
    return pickDeep(raw, ...keys)
  }
  return undefined
}

function pick(record: Record<string, any>, keys: string[]) {
  for (const key of keys) {
    const value = record[key]
    if (hasValue(value)) return value
  }
  return undefined
}

function asRecord(value: any): Record<string, any> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
}

function hasValue(value: any) {
  return value !== undefined && value !== null && String(value).trim() !== ''
}
