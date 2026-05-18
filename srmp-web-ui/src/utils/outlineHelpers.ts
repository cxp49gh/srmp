/** Outline 页面通用字段读取与状态展示 */
export function outlineValue(obj: any, ...keys: string[]) {
  if (!obj) return ''
  for (const key of keys) {
    const v = obj[key]
    if (v !== undefined && v !== null && String(v).trim() !== '') return v
  }
  return ''
}

export function outlineTruthy(v: any) {
  return v === true || v === 'true' || v === 1 || v === '1'
}

export function outlineTaskDryRun(item: Record<string, any>) {
  if (outlineTruthy(outlineValue(item, 'dryRun', 'dry_run'))) return true
  const status = String(outlineValue(item, 'status') || '')
  return status === 'DRY_RUN' || status === 'DRY_RUN_PARTIAL'
}

export function outlineStatusTagType(status: any) {
  const s = String(status || '')
  if (s === 'SUCCESS' || s === 'DRY_RUN') return 'success'
  if (s === 'FAILED') return 'danger'
  if (s === 'PARTIAL_SUCCESS' || s === 'DRY_RUN_PARTIAL' || s === 'RUNNING') return 'warning'
  if (s === 'SKIPPED') return 'info'
  return 'info'
}

export function outlineKbSyncLabel(status: string) {
  const map: Record<string, string> = {
    NOT_SYNCED: '未同步',
    INACTIVE: '不可用',
    PENDING_VECTOR: '待补向量',
    RAG_READY: '可用于 AI'
  }
  return map[status] || status || '-'
}

export function outlineKbSyncTagType(status: string) {
  if (status === 'RAG_READY') return 'success'
  if (status === 'PENDING_VECTOR') return 'warning'
  if (status === 'INACTIVE') return 'danger'
  if (status === 'NOT_SYNCED') return 'info'
  return 'info'
}

export function outlineRagReady(row: Record<string, any>) {
  return outlineTruthy(outlineValue(row, 'ragReady', 'rag_ready')) || outlineValue(row, 'kbSyncStatus', 'kb_sync_status') === 'RAG_READY'
}
