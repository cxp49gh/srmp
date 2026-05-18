import type { PathOptions } from 'leaflet'
import { getGradeMeta, getMetricGrade, getMetricValue, gradeFromScore } from './roadConditionMetrics'

export function gradeColor(grade?: string): string {
  const meta = getGradeMeta(grade)
  if (meta) return meta.color
  switch ((grade || '').toUpperCase()) {
    case 'HEAVY':
    case 'SEVERE':
      return '#dc2626'
    case 'MEDIUM':
      return '#f97316'
    case 'LIGHT':
    case 'MINOR':
      return '#eab308'
    default:
      return '#64748b'
  }
}

/** 路段 / 评定单元：仅作几何展示，不按路况指标着色 */
export function neutralAssetLineStyle(properties: Record<string, any> = {}): PathOptions {
  const objectType = String(properties.objectType || properties.object_type || properties.layerType || '').toUpperCase()
  const color = '#94a3b8'
  return {
    color,
    weight: objectType === 'ROAD_ROUTE' ? 5 : 4,
    opacity: 0.72,
    fillColor: color,
    fillOpacity: objectType === 'DISEASE' ? 0.82 : 0.22
  }
}

export function layerStyle(properties: Record<string, any> = {}, indexCode?: string): PathOptions {
  const objectType = String(properties.objectType || properties.object_type || properties.layerType || '').toUpperCase()
  const metricValue = getMetricValue(properties, indexCode)
  let metricGrade = ''
  if (objectType === 'ASSESSMENT_RESULT') {
    const n = Number(metricValue)
    if (Number.isFinite(n)) {
      metricGrade = gradeFromScore(n)
    } else {
      metricGrade = getMetricGrade(properties, indexCode) || gradeFromScore(metricValue)
    }
    if (!metricGrade) {
      metricGrade = String(properties.grade || '').toUpperCase()
    }
  } else {
    metricGrade = getMetricGrade(properties, indexCode) || gradeFromScore(metricValue)
  }
  const color =
    objectType === 'ASSESSMENT_RESULT'
      ? gradeColor(metricGrade)
      : properties.color || gradeColor(metricGrade || properties.grade || properties.severity)
  return {
    color,
    weight: objectType === 'ROAD_ROUTE' ? 5 : objectType === 'DISEASE' ? diseaseLineWeight(properties) : 4,
    opacity: 0.86,
    fillColor: color,
    fillOpacity: objectType === 'DISEASE' ? 0.28 : 0.35,
    dashArray: objectType === 'DISEASE' ? diseaseDashArray(properties) : undefined
  }
}

function diseaseText(properties: Record<string, any> = {}) {
  return String(
    properties.diseaseName ||
    properties.disease_name ||
    properties.diseaseType ||
    properties.disease_type ||
    properties.diseaseCategory ||
    properties.disease_category ||
    ''
  )
}

function diseaseLineWeight(properties: Record<string, any> = {}) {
  const text = diseaseText(properties)
  if (/裂|缝/.test(text)) return 5
  if (/车辙|沉陷|坑|槽|松散|拥包/.test(text)) return 6
  if (/龟裂|块裂|网裂/.test(text)) return 4
  return 5
}

function diseaseDashArray(properties: Record<string, any> = {}) {
  const text = diseaseText(properties)
  if (/横向|纵向|裂|缝/.test(text)) return '10 6'
  if (/龟裂|块裂|网裂/.test(text)) return '3 5'
  return undefined
}

export function styleForMapLayer(
  layerKey: string | undefined,
  properties: Record<string, any> = {},
  indexCode?: string
): PathOptions {
  if (layerKey === 'roadSection' || layerKey === 'evaluationUnit') {
    return neutralAssetLineStyle(properties)
  }
  return layerStyle(properties, indexCode)
}
