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

export function layerStyle(properties: Record<string, any> = {}, indexCode?: string): PathOptions {
  const metricValue = getMetricValue(properties, indexCode)
  const metricGrade = getMetricGrade(properties, indexCode) || gradeFromScore(metricValue)
  const color = properties.color || gradeColor(metricGrade || properties.grade || properties.severity)
  const objectType = String(properties.objectType || properties.object_type || properties.layerType || '').toUpperCase()
  return {
    color,
    weight: objectType === 'ROAD_ROUTE' ? 5 : 4,
    opacity: 0.86,
    fillColor: color,
    fillOpacity: objectType === 'DISEASE' ? 0.82 : 0.35
  }
}
