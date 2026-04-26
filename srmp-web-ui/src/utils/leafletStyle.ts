import type { PathOptions } from 'leaflet'

export function gradeColor(grade?: string): string {
  switch ((grade || '').toUpperCase()) {
    case 'EXCELLENT':
      return '#16a34a'
    case 'GOOD':
      return '#2563eb'
    case 'MEDIUM':
      return '#eab308'
    case 'POOR':
      return '#f97316'
    case 'BAD':
      return '#dc2626'
    default:
      return '#64748b'
  }
}

export function layerStyle(properties: Record<string, any> = {}): PathOptions {
  const color = properties.color || gradeColor(properties.grade)
  return {
    color,
    weight: properties.objectType === 'ROAD_ROUTE' ? 5 : 4,
    opacity: 0.85,
    fillColor: color,
    fillOpacity: 0.35
  }
}
