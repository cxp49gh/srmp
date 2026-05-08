import request from '../utils/request'
import type { GeoJsonFeatureCollection } from '../types/geojson'
import type { AiSolutionDraftSaveRequest } from './solution'

export interface GisLayerQuery {
  routeCode?: string
  year?: number | string
  indexCode?: string
  grade?: string
  diseaseType?: string
  severity?: string
  taskId?: string
}

export interface MapViewport {
  zoom?: number
  center?: { lat: number; lng: number }
  bbox?: number[]
}

export interface MapStatisticsQuery extends GisLayerQuery {
  bbox?: number[]
  viewport?: MapViewport | null
  layers?: string[]
  enabledLayers?: string[]
}

export interface MapRegionAnalysisRequest {
  geometry: Record<string, any>
  query?: Record<string, any>
  layers?: string[]
  options?: Record<string, any>
}

export function getLayers(): Promise<string[]> {
  return request.get('/api/gis/layers')
}

export function getRoadRoutes(params: GisLayerQuery): Promise<GeoJsonFeatureCollection> {
  return request.get('/api/gis/road-routes', { params })
}

export function getRoadSections(params: GisLayerQuery): Promise<GeoJsonFeatureCollection> {
  return request.get('/api/gis/road-sections', { params })
}

export function getEvaluationUnits(params: GisLayerQuery): Promise<GeoJsonFeatureCollection> {
  return request.get('/api/gis/evaluation-units', { params })
}

export function getDiseases(params: GisLayerQuery): Promise<GeoJsonFeatureCollection> {
  return request.get('/api/gis/diseases', { params })
}

export function getAssessmentResults(params: GisLayerQuery): Promise<GeoJsonFeatureCollection> {
  return request.get('/api/gis/assessment-results', { params })
}

export function getObjectDetail(params: { objectType: string; id: string }): Promise<Record<string, any>> {
  return request.get('/api/gis/object-detail', { params })
}

export function getMapStatistics(data: MapStatisticsQuery): Promise<Record<string, any>> {
  return request.post('/api/gis/map-statistics', data)
}

export function spatialQuery(data: MapRegionAnalysisRequest): Promise<Record<string, any>> {
  return request.post('/api/gis/spatial-query', data)
}

export function analyzeMapRegion(data: MapRegionAnalysisRequest): Promise<Record<string, any>> {
  return request.post('/api/gis/map-region/analysis', data)
}

export function saveMapRegionSolutionDraft(data: AiSolutionDraftSaveRequest): Promise<Record<string, any>> {
  return request.post('/api/gis/map-region/drafts', data)
}
