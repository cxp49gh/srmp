export type SourceLayerKey = 'roadRoute' | 'roadSection' | 'disease' | 'assessment'

export interface SourceLayerTarget {
  objectType?: string
  routeCode?: string
  startStake?: string | number
  endStake?: string | number
  geometry?: Record<string, any>
  bbox?: number[]
}

export function sourceLayerKey(target: SourceLayerTarget | null | undefined): SourceLayerKey | '' {
  if (!target || target.geometry || target.bbox) return ''
  const objectType = String(target.objectType || '').trim().toUpperCase()
  if (objectType === 'ROAD_ROUTE' || objectType === 'ROADROUTE') return 'roadRoute'
  if (['ROAD_SECTION', 'ROADSECTION', 'ROAD_SECTION_LINE', 'ROAD_SECTION_LEDGER', 'ROAD_SECTION_KM', 'ROAD_SECTION_HM'].includes(objectType)) {
    return 'roadSection'
  }
  if (objectType === 'DISEASE' || objectType === 'DISEASE_RECORD') return 'disease'
  if (['ASSESSMENT', 'ASSESSMENT_RESULT', 'ASSESSMENT_RESULT_RECORD'].includes(objectType)) return 'assessment'
  return ''
}

export function sourceTargetQuery(
  baseQuery: Record<string, any>,
  target: SourceLayerTarget
): Record<string, any> {
  const query = { ...(baseQuery || {}) }
  for (const key of [
    'grade',
    'diseaseType',
    'severity',
    'taskId',
    'stakeStart',
    'stakeEnd',
    'startStake',
    'endStake',
    'minLng',
    'minLat',
    'maxLng',
    'maxLat',
    'zoom'
  ]) {
    delete query[key]
  }
  if (target.routeCode !== undefined && target.routeCode !== null && String(target.routeCode).trim()) {
    query.routeCode = String(target.routeCode).trim()
  }
  if (target.startStake !== undefined && target.startStake !== null && target.startStake !== '') {
    query.stakeStart = target.startStake
  }
  if (target.endStake !== undefined && target.endStake !== null && target.endStake !== '') {
    query.stakeEnd = target.endStake
  }
  return query
}
