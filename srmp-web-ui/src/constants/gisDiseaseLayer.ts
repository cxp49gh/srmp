/** 病害 GIS 图层：仅在此级别请求并展示病害总和聚合。 */
export const ZOOM_DISEASE_SUMMARY = 14

/** 病害 GIS 图层：此级别及以下沿用 14 级聚合结果，不随视野刷新。 */
export const ZOOM_DISEASE_SUMMARY_FREEZE_MAX = 16

/** 病害 GIS 图层：达到此级别后返回具体病害，否则返回细粒度总和聚合。 */
export const ZOOM_DISEASE_DETAIL_MIN = 17

/** 兼容旧引用：病害图层从 14 级开始展示。 */
export const ZOOM_MIN_DISEASE_LAYER = ZOOM_DISEASE_SUMMARY
