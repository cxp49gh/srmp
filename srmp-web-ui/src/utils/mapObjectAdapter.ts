import { getMetricGrade, getMetricMeta, getMetricValue, gradeLabel, formatMetricValue, type RoadConditionMetricCode } from './roadConditionMetrics'

export type MapObjectType = 'ROAD_ROUTE' | 'ROAD_SECTION' | 'EVALUATION_UNIT' | 'DISEASE' | 'ASSESSMENT_RESULT' | string

export interface MapObjectAdapterOptions {
  indexCode?: any
  defaultRouteCode?: any
  defaultYear?: any
  layerKey?: string
}

export interface PopupRow {
  label: string
  value: any
}

const OBJECT_TYPE_LABELS: Record<string, string> = {
  ROAD_ROUTE: '路线',
  ROAD_SECTION: '路段',
  EVALUATION_UNIT: '评定单元',
  DISEASE: '病害',
  ASSESSMENT: '评定结果',
  ASSESSMENT_RESULT: '评定结果',
  MAP_REGION: '区域'
}

const LAYER_OBJECT_TYPES: Record<string, string> = {
  roadRoute: 'ROAD_ROUTE',
  roadSection: 'ROAD_SECTION',
  evaluationUnit: 'EVALUATION_UNIT',
  disease: 'DISEASE',
  assessment: 'ASSESSMENT_RESULT',
  assessmentResult: 'ASSESSMENT_RESULT',
  inspectionTrack: 'INSPECTION_TRACK'
}

export function firstPresent(...values: any[]) {
  return values.find((it) => it !== undefined && it !== null && it !== '')
}

export function pickValue(source: any, ...keys: string[]) {
  if (!source || typeof source !== 'object') return undefined
  for (const key of keys) {
    const value = source[key]
    if (value !== undefined && value !== null && value !== '') return value
  }
  return undefined
}

export function normalizeMapObjectType(rawType: any) {
  const type = String(rawType || '').trim().toUpperCase()
  if (!type) return ''
  if (type === 'ASSESSMENT' || type === 'ASSESSMENT_RESULT_RECORD') return 'ASSESSMENT_RESULT'
  if (type === 'DISEASE_RECORD') return 'DISEASE'
  if (type === 'ROADROUTE') return 'ROAD_ROUTE'
  if (type === 'ROADSECTION') return 'ROAD_SECTION'
  if (type === 'EVALUATIONUNIT') return 'EVALUATION_UNIT'
  return type
}

export function layerKeyToMapObjectType(layerKey: string) {
  return LAYER_OBJECT_TYPES[layerKey] || String(layerKey || '').toUpperCase()
}

export function mapObjectTypeLabel(type: any) {
  const value = normalizeMapObjectType(type)
  return OBJECT_TYPE_LABELS[value] || '地图对象'
}

export function mergeObjectProperties(raw?: Record<string, any> | null, detail?: Record<string, any> | null) {
  const detailProps = detail?.properties && typeof detail.properties === 'object' ? detail.properties : detail
  return {
    ...(raw || {}),
    ...(detailProps || {})
  }
}

export function adaptMapObject(args: {
  raw?: Record<string, any> | null
  detail?: Record<string, any> | null
  options?: MapObjectAdapterOptions
}) {
  const props = mergeObjectProperties(args.raw, args.detail)
  if (!props || Object.keys(props).length === 0) return null

  const options = args.options || {}
  const objectType = normalizeMapObjectType(firstPresent(
    props.objectType,
    props.object_type,
    props.type,
    props.layerType,
    props.layer_type,
    options.layerKey ? layerKeyToMapObjectType(options.layerKey) : undefined
  ))
  const objectId = firstPresent(props.objectId, props.object_id, props.id, props.featureId, props.feature_id, props.sourceId, props.source_id)
  const routeCode = firstPresent(props.routeCode, props.route_code, props.roadCode, props.road_code, options.defaultRouteCode)
  const year = firstPresent(props.year, options.defaultYear)
  const startStake = firstPresent(props.startStake, props.start_stake, props.beginStake, props.begin_stake, props.stakeStart, props.stake_start, props.startMileage, props.start_mileage)
  const endStake = firstPresent(props.endStake, props.end_stake, props.finishStake, props.finish_stake, props.stakeEnd, props.stake_end, props.endMileage, props.end_mileage)
  const activeMetricCode = getMetricMeta(options.indexCode || 'MQI').code
  const activeMetricValue = getMetricValue(props, activeMetricCode)
  const activeMetricGrade = getMetricGrade(props, activeMetricCode)

  const adapted = {
    ...props,
    objectType,
    object_type: objectType,
    objectId,
    object_id: objectId,
    id: firstPresent(props.id, objectId),
    routeCode,
    route_code: routeCode,
    year: year !== undefined && year !== null && year !== '' ? Number(year) : undefined,
    startStake,
    start_stake: startStake,
    endStake,
    end_stake: endStake,
    routeName: firstPresent(props.routeName, props.route_name, props.roadName, props.road_name),
    sectionName: firstPresent(props.sectionName, props.section_name),
    sectionCode: firstPresent(props.sectionCode, props.section_code),
    unitCode: firstPresent(props.unitCode, props.unit_code, props.unitId, props.unit_id),
    diseaseName: firstPresent(props.diseaseName, props.disease_name, props.diseaseType, props.disease_type),
    diseaseType: firstPresent(props.diseaseType, props.disease_type),
    diseaseCategory: firstPresent(props.diseaseCategory, props.disease_category),
    severity: firstPresent(props.severity, props.severityText, props.severity_text),
    direction: firstPresent(props.direction, props.directionText, props.direction_text),
    laneNo: firstPresent(props.laneNo, props.lane_no, props.lane),
    quantity: firstPresent(props.quantity, props.amount),
    measureUnit: firstPresent(props.measureUnit, props.measure_unit, props.unit),
    pavementType: firstPresent(props.pavementType, props.pavement_type),
    activeIndexCode: activeMetricCode,
    activeMetricValue,
    activeMetricGrade,
    metrics: buildMetricMap(props),
    displayTitle: buildMapObjectTitle(objectType, props, routeCode),
    displaySubtitle: buildMapObjectSubtitle(objectType, props, { startStake, endStake, activeMetricCode, activeMetricValue, activeMetricGrade }),
    raw: props
  }

  return adapted
}

export function normalizeFeatureProperties(layerKey: string, feature: any) {
  const raw = feature?.properties || {}
  const adapted = adaptMapObject({
    raw: {
      ...raw,
      layerKey,
      objectType: firstPresent(raw.objectType, raw.object_type, raw.type, raw.layerType, layerKeyToMapObjectType(layerKey)),
      objectId: firstPresent(raw.objectId, raw.object_id, raw.id, feature?.id)
    },
    options: { layerKey }
  })
  return adapted || {
    ...raw,
    layerKey,
    objectType: layerKeyToMapObjectType(layerKey),
    object_type: layerKeyToMapObjectType(layerKey),
    objectId: firstPresent(raw.objectId, raw.object_id, raw.id, feature?.id),
    object_id: firstPresent(raw.objectId, raw.object_id, raw.id, feature?.id),
    id: firstPresent(raw.id, raw.objectId, raw.object_id, feature?.id)
  }
}

export function buildMapObjectPopupRows(properties: Record<string, any>, options: MapObjectAdapterOptions = {}): PopupRow[] {
  const obj = adaptMapObject({ raw: properties, options }) || properties
  const type = normalizeMapObjectType(firstPresent(obj.objectType, obj.object_type, obj.type, obj.layerType))
  const route = firstPresent(obj.routeCode, obj.route_code, options.defaultRouteCode)
  const stake = stakeRangeText(obj)
  const direction = directionText(firstPresent(obj.direction, obj.directionText, obj.direction_text))
  const metricRows = popupMetricRows(obj, options.indexCode)

  if (type === 'ROAD_ROUTE') {
    return compactRows([
      ['路线编号', route],
      ['路线名称', firstPresent(obj.routeName, obj.route_name, obj.roadName, obj.road_name)],
      ['路线类型', firstPresent(obj.routeType, obj.route_type)],
      ['技术等级', firstPresent(obj.technicalGrade, obj.technical_grade)],
      ['起讫桩号', stake],
      ['里程(km)', firstPresent(obj.lengthKm, obj.length_km, obj.routeLengthKm, obj.route_length_km)]
    ])
  }

  if (type === 'ROAD_SECTION') {
    return compactRows([
      ['路线编号', route],
      ['路段编号', firstPresent(obj.sectionCode, obj.section_code)],
      ['路段名称', firstPresent(obj.sectionName, obj.section_name)],
      ['方向', direction],
      ['起讫桩号', stake],
      ['长度(km)', firstPresent(obj.lengthKm, obj.length_km, obj.sectionLengthKm, obj.section_length_km)],
      ['路面类型', firstPresent(obj.pavementType, obj.pavement_type)],
      ...metricRows
    ])
  }

  if (type === 'EVALUATION_UNIT') {
    return compactRows([
      ['路线编号', route],
      ['单元编号', firstPresent(obj.unitCode, obj.unit_code, obj.unitId, obj.unit_id)],
      ['方向', direction],
      ['车道', firstPresent(obj.laneNo, obj.lane_no, obj.lane)],
      ['起讫桩号', stake],
      ['长度(m)', firstPresent(obj.lengthM, obj.length_m)],
      ['路面类型', firstPresent(obj.pavementType, obj.pavement_type)],
      ...metricRows
    ])
  }

  if (type === 'DISEASE') {
    return compactRows([
      ['路线编号', route],
      ['病害类型', firstPresent(obj.diseaseName, obj.disease_name, obj.diseaseType, obj.disease_type)],
      ['病害分类', firstPresent(obj.diseaseCategory, obj.disease_category)],
      ['严重程度', severityText(firstPresent(obj.severity, obj.severityText, obj.severity_text))],
      ['方向', direction],
      ['车道', firstPresent(obj.laneNo, obj.lane_no, obj.lane)],
      ['起讫桩号', stake],
      ['数量', quantityText(obj)],
      ['状态', statusText(firstPresent(obj.status, obj.verified))]
    ])
  }

  if (type === 'ASSESSMENT_RESULT') {
    return compactRows([
      ['路线编号', route],
      ['评定单元', firstPresent(obj.unitCode, obj.unit_code, obj.unitId, obj.unit_id)],
      ['方向', direction],
      ['起讫桩号', stake],
      ...metricRows,
      ['等级', gradeLabel(firstPresent(getMetricGrade(obj, options.indexCode || 'MQI'), obj.grade, obj.level))]
    ])
  }

  return compactRows([
    ['类型', mapObjectTypeLabel(type)],
    ['路线编号', route],
    ['起讫桩号', stake],
    ...metricRows,
    ['等级', gradeLabel(firstPresent(getMetricGrade(obj, options.indexCode || 'MQI'), obj.grade, obj.level))]
  ])
}

export function buildMapObjectPopupHtml(properties: Record<string, any>, options: MapObjectAdapterOptions = {}) {
  const obj = adaptMapObject({ raw: properties, options }) || properties
  const type = normalizeMapObjectType(firstPresent(obj.objectType, obj.object_type, obj.type, obj.layerType))
  const label = mapObjectTypeLabel(type)
  const title = obj.displayTitle || `${label}详情`
  const subtitle = obj.displaySubtitle
  const rows = buildMapObjectPopupRows(obj, options)
  return `
    <div class="object-popup-card">
      <div class="object-popup-title">${escapeHtml(title)}</div>
      ${subtitle ? `<div class="object-popup-subtitle">${escapeHtml(subtitle)}</div>` : ''}
      <div class="object-popup-grid">
        ${rows.map((row) => `<span>${escapeHtml(row.label)}</span><strong>${escapeHtml(row.value)}</strong>`).join('')}
      </div>
      <div class="object-popup-tip">已同步到 AI 养护助手，可继续分析或生成建议。</div>
    </div>
  `
}

function buildMetricMap(props: Record<string, any>) {
  const codes: RoadConditionMetricCode[] = ['MQI', 'PQI', 'PCI', 'RQI', 'RDI', 'SRI', 'PSSI', 'SCI', 'BCI', 'TCI']
  return codes.reduce((acc, code) => {
    acc[code] = getMetricValue(props, code)
    return acc
  }, {} as Record<RoadConditionMetricCode, any>)
}

function buildMapObjectTitle(type: string, props: Record<string, any>, routeCode: any) {
  if (type === 'ROAD_ROUTE') return [mapObjectTypeLabel(type), firstPresent(props.routeCode, props.route_code, routeCode), firstPresent(props.routeName, props.route_name, props.roadName, props.road_name)].filter(Boolean).join('｜')
  if (type === 'ROAD_SECTION') return [mapObjectTypeLabel(type), firstPresent(props.sectionCode, props.section_code), firstPresent(props.sectionName, props.section_name)].filter(Boolean).join('｜')
  if (type === 'EVALUATION_UNIT') return [mapObjectTypeLabel(type), firstPresent(props.unitCode, props.unit_code, props.unitId, props.unit_id)].filter(Boolean).join('｜')
  if (type === 'DISEASE') return [mapObjectTypeLabel(type), firstPresent(props.diseaseName, props.disease_name, props.diseaseType, props.disease_type)].filter(Boolean).join('｜')
  if (type === 'ASSESSMENT_RESULT') return [mapObjectTypeLabel(type), firstPresent(props.unitCode, props.unit_code, props.unitId, props.unit_id)].filter(Boolean).join('｜')
  return [mapObjectTypeLabel(type), routeCode].filter(Boolean).join('｜')
}

function buildMapObjectSubtitle(type: string, props: Record<string, any>, args: Record<string, any>) {
  const parts: string[] = []
  const stake = stakeRangeText({ ...props, startStake: args.startStake, endStake: args.endStake })
  if (stake) parts.push(stake)
  const direction = directionText(firstPresent(props.direction, props.directionText, props.direction_text))
  if (direction) parts.push(direction)
  if (args.activeMetricValue !== undefined && args.activeMetricValue !== null && args.activeMetricValue !== '') {
    const metricText = `${args.activeMetricCode} ${formatMetricValue(args.activeMetricValue)}${args.activeMetricGrade ? ` ${gradeLabel(args.activeMetricGrade)}` : ''}`
    parts.push(metricText)
  }
  if (type === 'DISEASE') {
    const severity = severityText(firstPresent(props.severity, props.severityText, props.severity_text))
    if (severity) parts.push(severity)
  }
  return parts.filter(Boolean).join('｜')
}

function popupMetricRows(properties: Record<string, any>, indexCode?: any): Array<[string, any]> {
  const metric = getMetricMeta(indexCode || 'MQI')
  const value = getMetricValue(properties, metric.code)
  const rows: Array<[string, any]> = []
  if (value !== undefined && value !== null && value !== '') {
    rows.push([metric.code, formatMetricValue(value)])
  }
  return rows
}

function compactRows(rows: Array<[string, any]>): PopupRow[] {
  return rows
    .map(([label, value]) => ({ label, value: displayValue(value) }))
    .filter((row) => row.value !== '-')
}

function displayValue(value: any) {
  if (value === undefined || value === null || value === '') return '-'
  return value
}

function quantityText(properties: Record<string, any>) {
  const quantity = firstPresent(properties.quantity, properties.amount)
  const unit = firstPresent(properties.measureUnit, properties.measure_unit, properties.unit)
  if (quantity && unit) return `${quantity}${unit}`
  return quantity || '-'
}

function directionText(direction: any) {
  const value = String(direction || '').toUpperCase()
  const map: Record<string, string> = {
    UP: '上行',
    DOWN: '下行',
    BOTH: '双向',
    LEFT: '左幅',
    RIGHT: '右幅',
    FORWARD: '上行',
    REVERSE: '下行'
  }
  return map[value] || direction || ''
}

function severityText(severity: any) {
  const value = String(severity || '').toUpperCase()
  const map: Record<string, string> = {
    LIGHT: '轻度',
    MEDIUM: '中度',
    HEAVY: '重度',
    SERIOUS: '严重',
    SEVERE: '严重'
  }
  return map[value] || severity || ''
}

function statusText(status: any) {
  if (status === true) return '已核验'
  if (status === false) return '未核验'
  return status || '-'
}

function stakeRangeText(properties: Record<string, any>) {
  const start = firstPresent(properties.startStake, properties.start_stake, properties.beginStake, properties.begin_stake, properties.stakeStart, properties.stake_start)
  const end = firstPresent(properties.endStake, properties.end_stake, properties.finishStake, properties.finish_stake, properties.stakeEnd, properties.stake_end)
  if (start && end) return `${formatStake(start)}—${formatStake(end)}`
  if (start || end) return formatStake(start || end)
  return ''
}

function formatStake(value: any) {
  if (value === undefined || value === null || value === '') return ''
  const text = String(value)
  if (/^[Kk]/.test(text)) return text.toUpperCase()
  return text
}

function escapeHtml(value: any) {
  return String(value ?? '-')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
