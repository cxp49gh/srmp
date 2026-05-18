export interface GeoJsonFeatureCollection {
  type: 'FeatureCollection'
  features: GeoJsonFeature[]
  mode?: 'summary' | 'cluster' | 'detail' | 'too_many' | string
  total?: number
  limit?: number
  message?: string
}

export interface GeoJsonFeature {
  type: 'Feature'
  id?: string
  geometry: any
  properties: Record<string, any>
}
