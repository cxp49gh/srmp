import request from '../utils/request'
import type { GeoJsonFeatureCollection } from '../types/geojson'
import type { AiSolutionDraftSaveRequest } from './solution'

/** 评定/路段包专题粒度：路线级线段 / 台账 / 公里 / 百米 */
export type GisSectionTier = 'LINE' | 'LEDGER' | 'KM' | 'HM'

export interface GisLayerQuery {
  /** 数据管理项目 id；不传或空表示不按项目过滤（含 GIS 直连导入的 project_id 为空数据） */
  projectId?: string
  routeCode?: string
  /** 评定专题数据来源（对应 assessment_result.object_type），默认 LINE */
  sectionTier?: GisSectionTier | string
  year?: number | string
  indexCode?: string
  grade?: string
  diseaseType?: string
  severity?: string
  taskId?: string
  stakeStart?: number | string
  stakeEnd?: number | string
  startStake?: number | string
  endStake?: number | string
  /** 与地图视窗一致：west / south / east / north（WGS84），病害等大数据图层可按框查询 */
  minLng?: number
  minLat?: number
  maxLng?: number
  maxLat?: number
  zoom?: number
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

export interface SourceBindingVerifyRequest {
  projectId: string
  bindingType: 'OBJECT' | 'RANGE'
  mapTarget: Record<string, any>
}

export interface SourceBindingVerifyResponse {
  bindingStatus: 'VALID' | 'NOT_FOUND' | 'INVALID'
  bindingReason?: string
  resolvedTarget?: Record<string, any>
  recommendedLayer?: 'roadRoute' | 'roadSection' | 'disease' | 'assessment' | null
  matchedCount: number
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

export function verifySourceBinding(data: SourceBindingVerifyRequest): Promise<SourceBindingVerifyResponse> {
  return request.post('/api/gis/source-binding/verify', data)
}

export function saveMapRegionSolutionDraft(data: AiSolutionDraftSaveRequest): Promise<Record<string, any>> {
  return request.post('/api/gis/map-region/drafts', data)
}
