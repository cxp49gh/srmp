/** 与后端 ImportNetworkResultVO 对齐 */
export interface ImportNetworkResultVO {
  insertedCount: number
  updatedCount: number
  skippedCount: number
  warnings?: string[]
}
