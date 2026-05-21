export type DataMgmtProjectStatus =
  | 'NOT_IMPORTED'
  | 'PARTIAL'
  | 'AVAILABLE'
  | 'IMPORT_FAILED'
  | 'CLEARED'

const STATUS_LABEL: Record<DataMgmtProjectStatus, string> = {
  NOT_IMPORTED: '未导入',
  PARTIAL: '部分导入',
  AVAILABLE: '可用',
  IMPORT_FAILED: '导入失败',
  CLEARED: '已清空'
}

const STATUS_TYPE: Record<DataMgmtProjectStatus, '' | 'success' | 'warning' | 'danger' | 'info'> = {
  NOT_IMPORTED: 'info',
  PARTIAL: 'warning',
  AVAILABLE: 'success',
  IMPORT_FAILED: 'danger',
  CLEARED: 'info'
}

export function projectStatusLabel(status?: string) {
  return STATUS_LABEL[(status as DataMgmtProjectStatus) || 'NOT_IMPORTED'] || status || '-'
}

export function projectStatusTagType(status?: string) {
  return STATUS_TYPE[(status as DataMgmtProjectStatus) || 'NOT_IMPORTED'] || 'info'
}

export function importTypeLabel(type?: string) {
  const map: Record<string, string> = {
    ROAD_NETWORK: '路网',
    SECTION_PACKAGE: '路段',
    DISEASE_EXCEL: '病害'
  }
  return map[type || ''] || type || '-'
}

export function importStatusTagType(status?: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

export function operationTypeLabel(type?: string) {
  const map: Record<string, string> = {
    PROJECT_CREATE: '创建项目',
    PROJECT_DELETE: '删除项目',
    PROJECT_ARCHIVE: '归档项目',
    PROJECT_RESTORE: '恢复项目',
    IMPORT_ROAD_NETWORK: '导入路网',
    IMPORT_SECTION: '导入路段',
    IMPORT_DISEASE: '导入病害',
    CLEAR_DATA: '清除数据'
  }
  return map[type || ''] || type || '-'
}

export function canOpenGis(summary?: { routeCount?: number; roadNetworkReady?: boolean }) {
  return (summary?.routeCount ?? 0) > 0 || summary?.roadNetworkReady
}
