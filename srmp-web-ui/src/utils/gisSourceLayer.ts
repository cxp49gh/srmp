export type SourceLayerKey = 'roadRoute' | 'roadSection' | 'disease' | 'assessment'

export interface SourceLayerTarget {
  objectType?: string
  routeCode?: string
  startStake?: string | number
  endStake?: string | number
  geometry?: Record<string, any>
  bbox?: number[]
}

const DEFAULT_POINT_PADDING = 0.0005

export function geometryBbox(
  geometry: Record<string, any> | null | undefined,
  pointPadding = DEFAULT_POINT_PADDING
): number[] | undefined {
  if (!geometry || typeof geometry !== 'object') return undefined
  const points: Array<[number, number]> = []

  const collect = (value: any) => {
    if (!Array.isArray(value)) return
    if (
      value.length >= 2
      && Number.isFinite(Number(value[0]))
      && Number.isFinite(Number(value[1]))
    ) {
      points.push([Number(value[0]), Number(value[1])])
      return
    }
    value.forEach(collect)
  }

  if (geometry.type === 'GeometryCollection') {
    for (const item of geometry.geometries || []) {
      const bbox = geometryBbox(item, pointPadding)
      if (bbox) points.push([bbox[0], bbox[1]], [bbox[2], bbox[3]])
    }
  } else {
    collect(geometry.coordinates)
  }

  if (!points.length) return undefined
  let minLng = Math.min(...points.map(([lng]) => lng))
  let minLat = Math.min(...points.map(([, lat]) => lat))
  let maxLng = Math.max(...points.map(([lng]) => lng))
  let maxLat = Math.max(...points.map(([, lat]) => lat))
  if (minLng === maxLng) {
    minLng -= pointPadding
    maxLng += pointPadding
  }
  if (minLat === maxLat) {
    minLat -= pointPadding
    maxLat += pointPadding
  }
  return [minLng, minLat, maxLng, maxLat]
    .map((value) => Number(value.toFixed(7)))
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
  const bbox = target.bbox || geometryBbox(target.geometry)
  if (bbox && bbox.length === 4) {
    query.minLng = bbox[0]
    query.minLat = bbox[1]
    query.maxLng = bbox[2]
    query.maxLat = bbox[3]
  }
  return query
}
