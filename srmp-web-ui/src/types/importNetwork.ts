/** 与后端 ImportNetworkResultVO 对齐 */
export interface ImportNetworkResultVO {
  insertedCount: number
  updatedCount: number
  skippedCount: number
  warnings?: string[]
}

/** 与后端 ImportSectionPackageResultVO 对齐 */
export interface ImportSectionPackageResultVO {
  routeSectionCount: number
  evaluationUnitCount: number
  assessmentRowCount: number
  ignoredShapefileGroups?: string[]
  durationMs?: number
  warnings?: string[]
}

/** 与后端 ImportDiseaseExcelResultVO 对齐 */
export interface ImportDiseaseExcelResultVO {
  insertedCount: number
  /** 因路线编码在路网中不存在而跳过的行数 */
  skippedMissingRouteCount?: number
}
