export interface GisSourceMapTarget {
  objectType?: string
  objectId?: string
  id?: string
  routeCode?: string
  startStake?: string | number
  endStake?: string | number
  geometry?: Record<string, any>
  bbox?: number[]
  title?: string
  sourceType?: string
  raw?: Record<string, any>
}

export function pickValue(source: any, ...keys: string[]) {
  if (!source || typeof source !== 'object') return undefined
  for (const key of keys) {
    const value = source[key]
    if (value !== undefined && value !== null && value !== '') return value
  }
  return undefined
}

export function firstPresent(...values: any[]) {
  return values.find((it) => it !== undefined && it !== null && it !== '')
}

export function normalizeGisContextType(rawType: any) {
  const type = String(rawType || '').trim().toUpperCase()
  if (!type) return ''
  if (type === 'ASSESSMENT' || type === 'ASSESSMENT_RESULT_RECORD') return 'ASSESSMENT_RESULT'
  if (type === 'DISEASE_RECORD') return 'DISEASE'
  if (type === 'ROADROUTE') return 'ROAD_ROUTE'
  if (type === 'ROADSECTION') return 'ROAD_SECTION'
  if (type === 'EVALUATIONUNIT') return 'EVALUATION_UNIT'
  if (type === 'REGION' || type === 'MAP_REGION_CONTEXT') return 'MAP_REGION'
  return type
}

export function gisContextTypeLabel(type: any) {
  const value = normalizeGisContextType(type)
  const map: Record<string, string> = {
    MAP_REGION: '区域',
    ROAD_ROUTE: '线路',
    ROAD_SECTION: '路段',
    EVALUATION_UNIT: '评定单元',
    DISEASE: '病害',
    ASSESSMENT_RESULT: '评定结果',
    VIEWPORT: '视窗',
    ROUTE: '线路筛选',
    FREE: '自由问答'
  }
  return map[value] || String(value || '地图对象')
}

export function formatStakeRange(start: any, end?: any) {
  if (start === undefined || start === null || start === '') return ''
  const normalize = (value: any) => String(value).startsWith('K') ? String(value) : `K${value}`
  const s = normalize(start)
  return end !== undefined && end !== null && end !== '' ? `${s}—${normalize(end)}` : s
}

export function buildRegionUnifiedContext(args: {
  geometry?: Record<string, any> | null
  geometryType?: string
  summary?: Record<string, any> | null
  query?: Record<string, any>
  layers?: string[]
}) {
  const summary = unwrapApiData(args.summary) || {}
  const diseaseSummary = pickValue(summary, 'diseaseSummary', 'disease_summary') || {}
  const assessmentSummary = pickValue(summary, 'assessmentSummary', 'assessment_summary') || {}
  const routeCode = args.query?.routeCode || pickValue(summary, 'routeCode', 'route_code')
  return {
    objectType: 'MAP_REGION',
    type: 'MAP_REGION',
    label: buildRegionLabel(summary, args.geometryType, routeCode),
    routeCode,
    year: args.query?.year,
    geometry: args.geometry || pickValue(summary, 'geometry'),
    geometryType: args.geometryType,
    summary,
    layers: args.layers || [],
    metrics: {
      routeCount: pickValue(summary, 'routeCount', 'route_count'),
      sectionCount: pickValue(summary, 'sectionCount', 'section_count'),
      unitCount: pickValue(summary, 'unitCount', 'unit_count'),
      diseaseCount: pickValue(diseaseSummary, 'diseaseCount', 'disease_count'),
      heavyDiseaseCount: pickValue(diseaseSummary, 'heavyCount', 'heavy_count'),
      avgMqi: pickValue(assessmentSummary, 'avgMqi', 'avg_mqi'),
      avgPci: pickValue(assessmentSummary, 'avgPci', 'avg_pci')
    },
    hotspots: Array.isArray(pickValue(summary, 'hotspots', 'hot_spots')) ? pickValue(summary, 'hotspots', 'hot_spots') : []
  }
}

export function buildUnifiedAnalysisTargets(args: {
  mapObject?: Record<string, any> | null
  regionContext?: Record<string, any> | null
  query?: Record<string, any>
}) {
  if (args.regionContext) {
    const metrics: any = args.regionContext.metrics || {}
    const routeCode = args.regionContext.routeCode
    return [
      { type: 'MAP_REGION', label: args.regionContext.label || '区域', routeCode, raw: args.regionContext },
      { type: 'ROAD_ROUTE', label: '区域内线路', count: metrics.routeCount, routeCode },
      { type: 'ROAD_SECTION', label: '区域内路段', count: metrics.sectionCount, routeCode },
      { type: 'EVALUATION_UNIT', label: '区域内评定单元', count: metrics.unitCount, routeCode },
      { type: 'DISEASE', label: '区域内病害', count: metrics.diseaseCount, routeCode },
      { type: 'ASSESSMENT_RESULT', label: '区域内评定结果', score: metrics.avgMqi || metrics.avgPci, routeCode }
    ].filter((it) => it.count !== undefined || it.score !== undefined || it.type === 'MAP_REGION')
  }
  if (args.mapObject) {
    const type = normalizeGisContextType(pickValue(args.mapObject, 'objectType', 'object_type', 'type', 'layerType'))
    const routeCode = pickValue(args.mapObject, 'routeCode', 'route_code') || args.query?.routeCode
    if (type === 'ROAD_ROUTE') {
      return [
        { type, label: gisContextTypeLabel(type), routeCode, raw: args.mapObject },
        { type: 'ROAD_SECTION', label: '路线相关路段', count: pickValue(args.mapObject, 'relatedSectionCount', 'related_section_count', 'sectionCount', 'section_count'), routeCode },
        { type: 'EVALUATION_UNIT', label: '路线相关评定单元', count: pickValue(args.mapObject, 'relatedEvaluationUnitCount', 'related_evaluation_unit_count', 'unitCount', 'unit_count'), routeCode },
        { type: 'DISEASE', label: '路线相关病害', count: pickValue(args.mapObject, 'relatedDiseaseCount', 'related_disease_count', 'diseaseCount', 'disease_count'), routeCode },
        { type: 'ASSESSMENT_RESULT', label: '路线相关评定结果', count: pickValue(args.mapObject, 'relatedAssessmentCount', 'related_assessment_count', 'assessmentCount', 'assessment_count'), routeCode }
      ].filter((it) => it.count !== undefined || it.type === 'ROAD_ROUTE')
    }
    if (type === 'ROAD_SECTION') {
      return [
        { type, label: gisContextTypeLabel(type), routeCode, raw: args.mapObject },
        { type: 'EVALUATION_UNIT', label: '路段相关评定单元', count: pickValue(args.mapObject, 'relatedEvaluationUnitCount', 'related_evaluation_unit_count', 'unitCount', 'unit_count'), routeCode },
        { type: 'DISEASE', label: '路段相关病害', count: pickValue(args.mapObject, 'relatedDiseaseCount', 'related_disease_count', 'diseaseCount', 'disease_count'), routeCode },
        { type: 'ASSESSMENT_RESULT', label: '路段相关评定结果', count: pickValue(args.mapObject, 'relatedAssessmentCount', 'related_assessment_count', 'assessmentCount', 'assessment_count'), routeCode }
      ].filter((it) => it.count !== undefined || it.type === 'ROAD_SECTION')
    }
    if (type === 'EVALUATION_UNIT') {
      return [
        { type, label: gisContextTypeLabel(type), routeCode, raw: args.mapObject },
        { type: 'DISEASE', label: '单元相关病害', count: pickValue(args.mapObject, 'relatedDiseaseCount', 'related_disease_count', 'diseaseCount', 'disease_count'), routeCode },
        { type: 'ASSESSMENT_RESULT', label: '单元相关评定结果', count: pickValue(args.mapObject, 'relatedAssessmentCount', 'related_assessment_count', 'assessmentCount', 'assessment_count'), routeCode }
      ].filter((it) => it.count !== undefined || it.type === 'EVALUATION_UNIT')
    }
    if (type === 'DISEASE') {
      return [
        { type, label: gisContextTypeLabel(type), routeCode, raw: args.mapObject },
        { type: 'DISEASE', label: '同桩号范围病害', count: pickValue(args.mapObject, 'relatedDiseaseCount', 'related_disease_count', 'diseaseCount', 'disease_count'), routeCode },
        { type: 'ASSESSMENT_RESULT', label: '同桩号范围评定结果', count: pickValue(args.mapObject, 'relatedAssessmentCount', 'related_assessment_count', 'assessmentCount', 'assessment_count'), routeCode }
      ].filter((it) => it.count !== undefined || it.raw)
    }
    if (type === 'ASSESSMENT_RESULT') {
      return [
        { type, label: gisContextTypeLabel(type), routeCode, raw: args.mapObject },
        { type: 'DISEASE', label: '评定范围相关病害', count: pickValue(args.mapObject, 'relatedDiseaseCount', 'related_disease_count', 'diseaseCount', 'disease_count'), routeCode }
      ].filter((it) => it.count !== undefined || it.raw)
    }
    return [{ type, label: gisContextTypeLabel(type), routeCode, raw: args.mapObject }]
  }
  return [{ type: 'ROUTE', label: args.query?.routeCode ? `线路筛选｜${args.query.routeCode}` : '全图筛选', routeCode: args.query?.routeCode, raw: args.query || {} }]
}

export function sourceToMapTarget(source: any): GisSourceMapTarget {
  const raw = unwrapApiData(source) || {}
  if (raw.raw && (raw.objectId || raw.routeCode || raw.geometry || raw.bbox || raw.startStake || raw.endStake)) {
    return raw as GisSourceMapTarget
  }

  const metadata = pickValue(raw, 'metadata', 'meta') || {}
  const mapContext = pickValue(raw, 'mapContext', 'map_context', 'context') || {}
  const mapTarget = pickValue(raw, 'mapTarget', 'map_target', 'target', 'geoTarget') || {}
  const properties = pickValue(raw, 'properties', 'props') || {}
  const candidates = [raw, metadata, mapTarget, mapContext, properties]
  const pickAny = (...keys: string[]) => firstPresent(...candidates.map((it) => pickValue(it, ...keys)))

  const objectType = normalizeGisContextType(pickAny(
    'objectType', 'object_type', 'targetType', 'target_type', 'sourceObjectType', 'source_object_type', 'layerType', 'layer_type', 'bizType', 'biz_type'
  ))
  const objectId = pickAny(
    'objectId', 'object_id', 'targetId', 'target_id', 'sourceObjectId', 'source_object_id', 'sourceId', 'source_id', 'featureId', 'feature_id', 'id'
  )
  return {
    objectType,
    objectId: objectId !== undefined ? String(objectId) : undefined,
    id: objectId !== undefined ? String(objectId) : undefined,
    routeCode: pickAny('routeCode', 'route_code', 'routeNo', 'route_no', 'roadCode', 'road_code'),
    startStake: pickAny('startStake', 'start_stake', 'stakeStart', 'stake_start', 'beginStake', 'begin_stake', 'startMileage', 'start_mileage'),
    endStake: pickAny('endStake', 'end_stake', 'stakeEnd', 'stake_end', 'finishStake', 'finish_stake', 'endMileage', 'end_mileage'),
    geometry: pickAny('geometry', 'geojson', 'geoJson', 'geom'),
    bbox: pickAny('bbox', 'bounds', 'extent'),
    title: pickValue(raw, 'title', 'docTitle', 'documentTitle', 'sourceTitle') || pickValue(metadata, 'title', 'docTitle', 'documentTitle', 'sourceTitle'),
    sourceType: pickValue(raw, 'sourceType', 'source_type') || pickValue(metadata, 'sourceType', 'source_type'),
    raw
  }
}

export function mapTargetLabel(target: GisSourceMapTarget | any) {
  const normalized = target?.raw ? target : sourceToMapTarget(target)
  const parts = [
    gisContextTypeLabel(normalized.objectType),
    normalized.title,
    normalized.routeCode,
    formatStakeRange(normalized.startStake, normalized.endStake)
  ].filter(Boolean)
  return parts.length ? parts.join('｜') : '未绑定地图对象'
}

export function hasLocatableTarget(target: GisSourceMapTarget | any) {
  const normalized = target?.raw ? target : sourceToMapTarget(target)
  return Boolean(normalized.objectId || normalized.geometry || normalized.bbox || normalized.routeCode)
}

export function unwrapApiData(value: any) {
  if (value && typeof value === 'object' && typeof value.code !== 'undefined' && 'data' in value) return value.data
  return value
}

function buildRegionLabel(summary: any, geometryType?: string, routeCode?: any) {
  const typeLabel = geometryType === 'RECTANGLE' ? '矩形区域' : geometryType === 'POLYGON' ? '多边形区域' : '地图区域'
  const sectionCount = pickValue(summary, 'sectionCount', 'section_count')
  const diseaseSummary = pickValue(summary, 'diseaseSummary', 'disease_summary') || {}
  const diseaseCount = pickValue(diseaseSummary, 'diseaseCount', 'disease_count')
  const parts = [typeLabel]
  if (routeCode) parts.push(String(routeCode))
  if (sectionCount !== undefined) parts.push(`路段 ${sectionCount}`)
  if (diseaseCount !== undefined) parts.push(`病害 ${diseaseCount}`)
  return parts.join('｜')
}
