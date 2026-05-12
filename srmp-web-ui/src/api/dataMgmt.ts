import request from '../utils/request'
import { longRequest } from '../utils/request'

export interface DataMgmtProjectVO {
  id: string
  name: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface DataImportRecordVO {
  id: string
  projectId: string
  importType: string
  fileName?: string
  startedAt?: string
  finishedAt?: string
  durationMs?: number
  status: string
  message?: string
  resultSummary?: string
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
}): Promise<PageResult<DataMgmtProjectVO>> {
  return request.post('/api/data-mgmt/projects/page', data)
}

export function createDataMgmtProject(data: { name: string; remark?: string }): Promise<string> {
  return request.post('/api/data-mgmt/projects', data)
}

export function getDataMgmtProject(id: string): Promise<DataMgmtProjectVO> {
  return request.get(`/api/data-mgmt/projects/${id}`)
}

/** 删除项目：后端会先按 ALL 物理清除归属数据与导入流水，再软删项目主档 */
export function deleteDataMgmtProject(id: string): Promise<void> {
  return request.delete(`/api/data-mgmt/projects/${id}`)
}

export function pageImportRecords(
  projectId: string,
  data: { pageNo?: number; pageSize?: number }
): Promise<PageResult<DataImportRecordVO>> {
  return request.post(`/api/data-mgmt/projects/${projectId}/import-records/page`, data)
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

/** 与后端 DataMgmtClearScope / DataImportType 一致 */
export type DataMgmtClearScopeParam = 'ALL' | 'ROAD_NETWORK' | 'SECTION_PACKAGE' | 'DISEASE_EXCEL'

export interface DataMgmtClearResultVO {
  routes: number
  sections: number
  diseaseRecords: number
  importRecords: number
}

/** 指定 scope 物理删除表中匹配租户 + 项目的业务数据（及 ALL 时的导入流水） */
export function clearProjectData(
  projectId: string,
  scope: DataMgmtClearScopeParam
): Promise<DataMgmtClearResultVO> {
  return request.post(`/api/data-mgmt/projects/${projectId}/clear`, { scope })
}
