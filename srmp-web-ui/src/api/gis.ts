import request, { aiRequest } from '../utils/request'
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

export interface MapStatisticsQuery extends GisLayerQuery {
  /** 当前地图视野 bbox: [west, south, east, north] */
  bbox?: number[]
  /** 当前地图视野快照，便于后端按视野统计或记录调试上下文 */
  viewport?: {
    zoom?: number
    center?: { lat: number; lng: number }
    bbox?: number[]
  } | null
  /** 后端图层编码：ROAD_ROUTE / ROAD_SECTION / EVALUATION_UNIT / DISEASE / ASSESSMENT_RESULT */
  layers?: string[]
  enabledLayers?: string[]
  /** 前端图层 key，便于兼容老接口或日志追踪 */
  layerKeys?: string[]
}

export interface MapRegionAnalysisRequest {
  geometry: Record<string, any>
  query?: Record<string, any>
  layers?: string[]
  options?: Record<string, any>
}

export interface MapRegionSolutionRequest extends MapRegionAnalysisRequest {
  solutionType?: string
}

export interface MapRegionSolutionResponse {
  solutionType: string
  title: string
  markdown: string
  regionSummary?: Record<string, any>
  qualityCheck?: Record<string, any>
  templateMeta?: Record<string, any>
  answerMeta?: Record<string, any>
  sourceSummaries?: Record<string, any>[]
  trace?: Record<string, any>
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

export function generateMapRegionSolution(data: MapRegionSolutionRequest): Promise<MapRegionSolutionResponse> {
  return aiRequest.post('/api/gis/map-region/solution', data)
}

export function saveMapRegionSolutionDraft(data: AiSolutionDraftSaveRequest): Promise<Record<string, any>> {
  return request.post('/api/gis/map-region/drafts', data)
}
