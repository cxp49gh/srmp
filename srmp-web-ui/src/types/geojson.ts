export interface GeoJsonFeatureCollection {
  type: 'FeatureCollection'
  features: GeoJsonFeature[]
}

export interface GeoJsonFeature {
  type: 'Feature'
  id?: string
  geometry: any
  properties: Record<string, any>
}
