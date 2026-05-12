/** 与后端 ImportNetworkResultVO 对齐 */
export interface ImportNetworkResultVO {
  insertedCount: number
  updatedCount: number
  skippedCount: number
  warnings?: string[]
}

/** 与后端 ImportSectionPackageResultVO 对齐 */
export interface ImportSectionPackageResultVO {
  lineCount: number
  ledgerCount: number
  kmCount: number
  hmCount: number
  /** 与 lineCount 一致，兼容旧代码 */
  routeSectionCount: number
  /** 与 ledgerCount 一致，兼容旧代码 */
  evaluationUnitCount: number
  assessmentRowCount: number
  ignoredShapefileGroups?: string[]
  durationMs?: number
  warnings?: string[]
}

/** 与后端 ImportDiseaseExcelResultVO 对齐 */
export interface ImportDiseaseExcelResultVO {
  insertedCount: number
  /** 路网中无匹配路线但仍入库的行数（route_id 为空） */
  skippedMissingRouteCount?: number
  warnings?: string[]
}
