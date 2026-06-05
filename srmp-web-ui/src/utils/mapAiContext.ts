export interface BuildMapAiContextPayloadOptions {
  mode?: string
  message?: string
  context?: Record<string, any> | null
  mapObject?: Record<string, any> | null
  region?: Record<string, any> | null
  regionSummary?: Record<string, any> | null
  analysisTargets?: Record<string, any>[] | null
  nearbyObjects?: Record<string, any>[] | null
}

export function buildMapAiContextPayload(options: BuildMapAiContextPayloadOptions) {
  const context = asRecord(options.context)
  const query = asRecord(firstPresent(context.query, context))
  const mapObject = asRecord(options.mapObject)
  const region = asRecord(options.region)
  const regionSummary = asRecord(firstPresent(options.regionSummary, context.regionSummary, region.summary))
  const mode = String(firstPresent(options.mode, context.contextScope, context.mode, 'FREE')).toUpperCase()
  const geometry = firstPresent(context.geometry, context.regionGeometry, region.geometry, regionSummary.geometry, null)
  const selectedRouteCode = pickValue(mapObject, 'routeCode', 'route_code', 'route', 'routeNo', 'route_no')
  const queryRouteCode = firstPresent(query.routeCode, query.route_code, context.routeCode, context.route_code)
  const queryYear = firstPresent(query.year, context.year)

  const routeCode = mode === 'OBJECT'
    ? firstPresent(selectedRouteCode, queryRouteCode, pickValue(region, 'routeCode', 'route_code'))
    : firstPresent(queryRouteCode, pickValue(region, 'routeCode', 'route_code'), selectedRouteCode)
  const year = normalizeYear(queryYear)

  return {
    tenantId: firstPresent(context.tenantId, context.tenant_id),
    mode,
    routeCode,
    year,
    mapObject: options.mapObject || null,
    region: options.region || context.regionContext || context.region || null,
    regionSummary: options.regionSummary || context.regionSummary || region.summary || null,
    regionGeometry: geometry,
    geometry,
    viewport: firstPresent(context.viewport, context.bounds, null),
    selectedLayers: Array.isArray(context.selectedLayers) ? context.selectedLayers : [],
    analysisTargets: Array.isArray(options.analysisTargets) ? options.analysisTargets : [],
    nearbyObjects: Array.isArray(options.nearbyObjects) ? options.nearbyObjects : [],
    userQuestion: options.message || '',
    extra: {
      rawContext: context,
      contextScope: firstPresent(context.contextScope, mode),
      unifiedContextVersion: 'phase51-evidence-v1'
    }
  }
}

function asRecord(value: any): Record<string, any> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
}

function pickValue(source: Record<string, any>, ...keys: string[]) {
  for (const key of keys) {
    const value = source[key]
    if (value !== undefined && value !== null && value !== '') return value
  }
  const raw = asRecord(source.raw)
  if (raw !== source && Object.keys(raw).length) {
    return pickValue(raw, ...keys)
  }
  return undefined
}

function firstPresent(...values: any[]) {
  for (const value of values) {
    if (value !== undefined && value !== null && value !== '') return value
  }
  return undefined
}

function normalizeYear(value: any) {
  const year = Number(value)
  return Number.isFinite(year) ? year : undefined
}
