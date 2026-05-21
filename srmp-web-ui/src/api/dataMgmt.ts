import request from '../utils/request'
import { longRequest } from '../utils/request'

export interface DataMgmtProjectSummaryVO {
  projectStatus?: string
  routeCount?: number
  sectionCount?: number
  diseaseCount?: number
  lastImportType?: string
  lastImportStatus?: string
  lastImportTime?: string
  lastSuccessImportTime?: string
  roadNetworkReady?: boolean
}

export interface DataMgmtProjectVO {
  id: string
  name: string
  remark?: string
  archived?: boolean
  archivedAt?: string
  createdAt?: string
  updatedAt?: string
  summary?: DataMgmtProjectSummaryVO
}

export interface DataImportRecordVO {
  id: string
  projectId: string
  projectName?: string
  importType: string
  fileName?: string
  startedAt?: string
  finishedAt?: string
  durationMs?: number
  status: string
  message?: string
  resultSummary?: string
  uploadedBy?: string
}

export interface DataImportRecordDetailVO extends DataImportRecordVO {
  resultStats?: unknown
  failureDetails?: { rowNo?: number; field?: string; reason?: string; fileName?: string }[]
  warnings?: string[]
  technicalInfo?: string
}

export interface DataMgmtQualityReportVO {
  projectId: string
  projectName?: string
  routeCount: number
  sectionCount: number
  diseaseCount: number
  routeMatchRate?: number | null
  unmatchedRouteCount: number
  unmatchedSectionRouteCount?: number
  unmatchedDiseaseRouteCount?: number
  unclassifiedDiseaseCount: number
  emptyGeometryCount: number
  emptyRouteGeometryCount?: number
  emptySectionGeometryCount?: number
  emptyDiseaseGeometryCount?: number
  abnormalCoordinateCount: number
  sectionBreakdown?: { label: string; value: number }[]
  routeMatchBreakdown?: { label: string; value: number }[]
  geometryBreakdown?: { label: string; value: number }[]
  diseaseCategoryBreakdown?: { label: string; value: number }[]
  diseaseSeverityBreakdown?: { label: string; value: number }[]
  issues?: { issueType: string; count: number; suggestion: string }[]
}

export interface DataMgmtClearPreviewVO {
  routes: number
  sections: number
  diseaseRecords: number
  importRecords: number
}

export interface DataMgmtAuditLogVO {
  id: string
  projectId?: string
  projectName?: string
  operationType: string
  operator?: string
  operatedAt?: string
  result: string
  reason?: string
  snapshotBefore?: string
  snapshotAfter?: string
}

export interface PageResult<T> {
  total: number
  pageNo: number
  pageSize: number
  records: T[]
}

export function pageDataMgmtProjects(data: {
  pageNo?: number
  pageSize?: number
  nameKeyword?: string
  includeArchived?: boolean
}): Promise<PageResult<DataMgmtProjectVO>> {
  return request.post('/api/data-mgmt/projects/page', data)
}

export function createDataMgmtProject(data: { name: string; remark?: string }): Promise<string> {
  return request.post('/api/data-mgmt/projects', data)
}

export function getDataMgmtProject(id: string): Promise<DataMgmtProjectVO> {
  return request.get(`/api/data-mgmt/projects/${id}`)
}

export function getProjectSummary(id: string): Promise<DataMgmtProjectSummaryVO> {
  return request.get(`/api/data-mgmt/projects/${id}/summary`)
}

export function getQualityReport(id: string): Promise<DataMgmtQualityReportVO> {
  return request.get(`/api/data-mgmt/projects/${id}/quality-report`)
}

export function deleteDataMgmtProject(id: string): Promise<void> {
  return request.delete(`/api/data-mgmt/projects/${id}`)
}

export function archiveDataMgmtProject(id: string): Promise<void> {
  return request.post(`/api/data-mgmt/projects/${id}/archive`)
}

export function restoreDataMgmtProject(id: string): Promise<void> {
  return request.post(`/api/data-mgmt/projects/${id}/restore`)
}

export function pageImportRecords(
  projectId: string,
  data: { pageNo?: number; pageSize?: number }
): Promise<PageResult<DataImportRecordVO>> {
  return request.post(`/api/data-mgmt/projects/${projectId}/import-records/page`, data)
}

export function pageImportRecordsGlobal(data: {
  pageNo?: number
  pageSize?: number
  projectId?: string
  importType?: string
  status?: string
  startedFrom?: string
  startedTo?: string
  uploadedBy?: string
}): Promise<PageResult<DataImportRecordVO>> {
  return request.post('/api/data-mgmt/import-records/page', data)
}

export function getImportRecordDetail(projectId: string, recordId: string): Promise<DataImportRecordDetailVO> {
  return request.get(`/api/data-mgmt/projects/${projectId}/import-records/${recordId}`)
}

export function importRoadNetworkForProject(projectId: string, file: File) {
  const fd = new FormData()
  fd.append('file', file)
  return longRequest.post(`/api/data-mgmt/projects/${projectId}/imports/road-network`, fd)
}

export function importSectionPackageForProject(projectId: string, file: File) {
  const fd = new FormData()
  fd.append('file', file)
  return longRequest.post(`/api/data-mgmt/projects/${projectId}/imports/section-package`, fd)
}

export function importDiseaseExcelForProject(projectId: string, file: File) {
  const fd = new FormData()
  fd.append('file', file)
  return longRequest.post(`/api/data-mgmt/projects/${projectId}/imports/disease-excel`, fd)
}

export type DataMgmtClearScopeParam = 'ALL' | 'ROAD_NETWORK' | 'SECTION_PACKAGE' | 'DISEASE_EXCEL'

export interface DataMgmtClearResultVO {
  routes: number
  sections: number
  diseaseRecords: number
  importRecords: number
}

export function previewClearProject(projectId: string, scope?: DataMgmtClearScopeParam): Promise<DataMgmtClearPreviewVO> {
  return request.post(`/api/data-mgmt/projects/${projectId}/clear-preview`, { scope })
}

export function clearProjectData(projectId: string, scope: DataMgmtClearScopeParam): Promise<DataMgmtClearResultVO> {
  return request.post(`/api/data-mgmt/projects/${projectId}/clear`, { scope })
}

export function pageAuditLogs(params: {
  pageNo?: number
  pageSize?: number
  projectId?: string
  operationType?: string
  operator?: string
  operatedFrom?: string
  operatedTo?: string
}): Promise<PageResult<DataMgmtAuditLogVO>> {
  return request.get('/api/data-mgmt/audit-logs/page', { params })
}

export function getAuditLogDetail(id: string): Promise<DataMgmtAuditLogVO> {
  return request.get(`/api/data-mgmt/audit-logs/${id}`)
}

export function templateDownloadUrl(type: 'road-network' | 'section' | 'disease') {
  return `/api/data-mgmt/templates/${type}/download`
}
