<template>
  <div class="one-map-page" :class="{ 'agent-open': agentVisible, 'region-active': !!regionGeometry }">
    <div id="map" class="map"></div>

    <div class="top-toolbar">
      <MapToolbar
        :query="query"
        :region-mode="regionMode"
        :has-region="!!regionGeometry"
        @search="handleSearch"
        @reset="handleReset"
        @fit="handleFitAll"
        @start-region="startRegionDraw"
        @clear-region="clearRegion"
      />
    </div>

    <div class="left-map-stack">
      <GisLeftWorkbench
        v-model:layers="layers"
        :statistics="statistics"
        :layer-counts="layerCounts"
        :layer-errors="layerErrors"
        :query="query"
        :loading="loading"
        @change="handleLayerChange"
        @reload="handleLayerRefresh"
        @fit="handleFitAll"
      />

      <LegendPanel class="map-legend-fixed" :index-code="query.indexCode" />

      <div class="map-zoom-panel srmp-card" aria-label="地图缩放控制">
        <button type="button" title="放大" @click="zoomInMap">+</button>
        <span>级别 {{ currentZoom }}</span>
        <button type="button" title="缩小" @click="zoomOutMap">−</button>
      </div>
    </div>

    <div v-if="regionMode !== 'NONE'" class="region-draw-tip">
      <strong>{{ regionMode === 'RECTANGLE' ? '矩形框选中' : '多边形框选中' }}</strong>
      <span>{{ regionMode === 'RECTANGLE' ? '按住鼠标拖拽形成区域' : '单击添加顶点，双击结束' }}</span>
      <em>右键或 Esc 取消</em>
    </div>

    <AgentChatFloat
      v-model:visible="agentVisible"
      :context="agentContext"
      :map-object="selectedMapObject"
      :auto-question="pendingAiQuestion"
      :region-loading="regionLoading"
      :region-summary-loading="regionSummaryLoading"
      :context-scope="contextScope"
      @auto-question-consumed="pendingAiQuestion = ''"
      @locate-source="handleLocateAiSource"
      @ask-with-source="handleAskAiSource"
      @ai-analyze-object="openAiForSelected"
      @ai-analyze-region="askAiForRegion"
      @generate-region="generateRegionSolution"
      @trace="regionTraceDrawerVisible = true"
      @clear-region="clearRegion"
      @close-detail="clearSelection"
    />

    <button
      v-if="!agentVisible"
      class="ai-float-button"
      type="button"
      title="AI 助手"
      @click="agentVisible = true"
    >
      AI
    </button>

    <SolutionPreviewDialog
      v-model:visible="regionPreviewVisible"
      :solution="regionSolution"
      :trace="regionSolution?.trace || null"
      :save-loading="regionDraftSaving"
      :saved-task="regionSavedTask"
      @save="saveRegionDraft"
    />

    <AiTraceDrawer
      v-model:visible="regionTraceDrawerVisible"
      :trace="regionSolution?.trace || null"
      :answer-meta="regionSolution?.answerMeta || null"
      :tool-results="regionSolutionToolResults"
      :sources="regionSolutionSources"
      :solution="regionSolution || null"
    />

    <div v-if="mapLoadingMask" class="loading-mask">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>图层加载中...</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import L, { GeoJSON, LatLngBounds, Map as LeafletMap } from 'leaflet'
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import MapToolbar from './components/MapToolbar.vue'
import {
  analyzeMapRegion,
  getAssessmentResults,
  getDiseases,
  getEvaluationUnits,
  getMapStatistics,
  getObjectDetail,
  getRoadRoutes,
  getRoadSections,
  saveMapRegionSolutionDraft,
  type GisLayerQuery
} from '../../api/gis'
import { mapAgentRun } from '../../api/agent'
import { layerStyle } from '../../utils/leafletStyle'
import { getMetricGrade, getMetricMeta, getMetricValue } from '../../utils/roadConditionMetrics'
import type { GeoJsonFeatureCollection } from '../../types/geojson'
import { type LayerState } from './components/LayerDrawer.vue'
import GisLeftWorkbench from './components/GisLeftWorkbench.vue'
import AgentChatFloat from './components/AgentChatFloat.vue'
import LegendPanel from './components/LegendPanel.vue'
import SolutionPreviewDialog from './components/SolutionPreviewDialog.vue'
import AiTraceDrawer from '../agent/components/AiTraceDrawer.vue'
import { buildRegionUnifiedContext, buildUnifiedAnalysisTargets, sourceToMapTarget, type GisSourceMapTarget } from '../../utils/gisUnifiedContext'
import { createLatestRequestGuard } from '../../utils/latestRequestGuard'

type MapRegionSolutionResponse = {
  solutionType: string
  title: string
  markdown: string
  regionSummary?: Record<string, any>
  qualityCheck?: Record<string, any>
  templateMeta?: Record<string, any>
  answerMeta?: Record<string, any>
  sourceSummaries?: Record<string, any>[]
  trace?: Record<string, any>
  toolResults?: Record<string, any>[]
  sources?: Record<string, any>[]
  knowledgeSources?: Record<string, any>[]
}

const query = reactive<GisLayerQuery>({
  routeCode: 'G210',
  year: '2026',
  indexCode: 'MQI',
  grade: ''
})

const layers = reactive<LayerState>({
  roadRoute: true,
  roadSection: true,
  evaluationUnit: true,
  disease: true,
  assessment: true
})

const statistics = ref<Record<string, any>>({})
const selectedDetail = ref<Record<string, any> | null>(null)
const selectedFeatureProperties = ref<Record<string, any> | null>(null)
const loading = ref(false)
const mapLoadingMask = ref(false)
const agentVisible = ref(true)
const pendingAiQuestion = ref('')
const regionMode = ref<'NONE' | 'RECTANGLE' | 'POLYGON'>('NONE')
const regionGeometryType = ref<'RECTANGLE' | 'POLYGON'>('RECTANGLE')
const regionGeometry = ref<Record<string, any> | null>(null)
const regionSummary = ref<Record<string, any> | null>(null)
const regionSolution = ref<MapRegionSolutionResponse | null>(null)
const regionSolutionToolResults = computed(() => (regionSolution.value as any)?.toolResults || [])
const regionSolutionSources = computed(() => (regionSolution.value as any)?.sources || (regionSolution.value as any)?.knowledgeSources || [])
const regionLoading = ref(false)
const regionSummaryLoading = ref(false)
const regionPreviewVisible = ref(false)
const regionTraceDrawerVisible = ref(false)
const regionDraftSaving = ref(false)
const regionSavedTask = ref<Record<string, any> | null>(null)
type ContextScope = 'ROUTE' | 'OBJECT' | 'REGION'
const contextScope = ref<ContextScope>('ROUTE')
const polygonPoints = ref<L.LatLng[]>([])
const layerCounts = reactive<Record<string, number>>({
  roadRoute: 0,
  roadSection: 0,
  evaluationUnit: 0,
  disease: 0,
  assessment: 0
})
const layerErrors = reactive<Record<string, string>>({})

let map: LeafletMap
const layerMap = new Map<string, GeoJSON>()
let selectedLayer: L.Layer | null = null
const currentZoom = ref(11)
let regionLayer: L.Layer | null = null
let regionPreviewLayer: L.Layer | null = null
let aiSourceHighlightLayer: L.Layer | null = null
let rectangleStart: L.LatLng | null = null
let regionSummaryRequestSeq = 0
let layerChangeTimer: ReturnType<typeof window.setTimeout> | null = null
let mapDisposed = false
const objectDetailRequestGuard = createLatestRequestGuard()
const regionSolutionRequestGuard = createLatestRequestGuard()

const regionFinalStyle = { color: '#0284c7', weight: 3, fillColor: '#38bdf8', fillOpacity: 0.16 }
const regionPreviewStyle = { color: '#2563eb', weight: 2, dashArray: '6 5', fillColor: '#7dd3fc', fillOpacity: 0.1 }

function normalizeObjectType(rawType: any) {
  const type = String(rawType || '').toUpperCase()
  if (type === 'ASSESSMENT') return 'ASSESSMENT_RESULT'
  if (type === 'DISEASE_RECORD') return 'DISEASE'
  return type
}

function firstValue(...values: any[]) {
  return values.find((it) => it !== undefined && it !== null && it !== '')
}

const selectedMapObject = computed(() => {
  const raw: any = selectedFeatureProperties.value || {}
  const detail: any = selectedDetail.value || {}
  const detailProps: any = detail.properties || detail
  const props: any = { ...raw, ...detailProps }

  if (!props || Object.keys(props).length === 0) return null

  const objectType = normalizeObjectType(
    firstValue(props.objectType, props.object_type, props.type, props.layerType)
  )
  const objectId = firstValue(props.objectId, props.object_id, props.id, raw.objectId, raw.object_id, raw.id)

  return {
    objectType,
    objectId,
    id: objectId,
    routeCode: firstValue(props.routeCode, props.route_code, raw.routeCode, raw.route_code, query.routeCode),
    year: Number(firstValue(props.year, query.year, 2026)),
    startStake: firstValue(props.startStake, props.start_stake, raw.startStake, raw.start_stake),
    endStake: firstValue(props.endStake, props.end_stake, raw.endStake, raw.end_stake),
    routeName: firstValue(props.routeName, props.route_name, raw.routeName, raw.route_name),
    sectionName: firstValue(props.sectionName, props.section_name, raw.sectionName, raw.section_name),
    sectionCode: firstValue(props.sectionCode, props.section_code, raw.sectionCode, raw.section_code),
    unitCode: firstValue(props.unitCode, props.unit_code, raw.unitCode, raw.unit_code),
    diseaseName: firstValue(props.diseaseName, props.disease_name, raw.diseaseName, raw.disease_name),
    diseaseType: firstValue(props.diseaseType, props.disease_type, raw.diseaseType, raw.disease_type),
    severity: firstValue(props.severity, raw.severity),
    quantity: firstValue(props.quantity, raw.quantity),
    measureUnit: firstValue(props.measureUnit, props.measure_unit, raw.measureUnit, raw.measure_unit),
    mqi: firstValue(props.mqi, raw.mqi),
    pqi: firstValue(props.pqi, raw.pqi),
    pci: firstValue(props.pci, raw.pci),
    grade: firstValue(props.grade, raw.grade),
    activeIndexCode: getMetricMeta(query.indexCode || 'MQI').code,
    activeMetricValue: getMetricValue(props, query.indexCode || 'MQI'),
    activeMetricGrade: getMetricGrade(props, query.indexCode || 'MQI'),
    metrics: {
      MQI: getMetricValue(props, 'MQI'),
      PQI: getMetricValue(props, 'PQI'),
      PCI: getMetricValue(props, 'PCI'),
      RQI: getMetricValue(props, 'RQI'),
      RDI: getMetricValue(props, 'RDI'),
      SRI: getMetricValue(props, 'SRI'),
      PSSI: getMetricValue(props, 'PSSI'),
      SCI: getMetricValue(props, 'SCI'),
      BCI: getMetricValue(props, 'BCI'),
      TCI: getMetricValue(props, 'TCI')
    },
    raw: props
  }
})

const activeRegionContext = computed(() => {
  if (!regionGeometry.value) return null
  return buildRegionUnifiedContext({
    geometry: regionGeometry.value,
    geometryType: regionGeometryType.value,
    summary: regionSummary.value,
    query: { ...query },
    layers: activeLayerNames()
  })
})

const analysisTargets = computed(() => buildUnifiedAnalysisTargets({
  mapObject: selectedMapObject.value,
  regionContext: activeRegionContext.value,
  query: { ...query }
}))

const agentContext = computed(() => ({
  query: { ...query },
  selected: selectedDetail.value,
  mapObject: contextScope.value === 'REGION' ? null : selectedMapObject.value,
  selectedMapObject: selectedMapObject.value,
  region: contextScope.value === 'OBJECT' ? null : activeRegionContext.value,
  regionContext: contextScope.value === 'OBJECT' ? null : activeRegionContext.value,
  regionSummary: regionSummary.value,
  regionGeometry: regionGeometry.value,
  regionGeometryType: regionGeometryType.value,
  selectedLayers: activeLayerNames(),
  analysisTargets: analysisTargets.value,
  viewport: currentViewport(),
  metric: getMetricMeta(query.indexCode),
  statistics: statistics.value,
  regionTrace: regionSolution.value?.trace || null,
  regionSolution: regionSolution.value,
  regionLoading: regionLoading.value,
  regionSummaryLoading: regionSummaryLoading.value,
  hasRegion: Boolean(regionGeometry.value),
  indexCode: query.indexCode,
  grade: query.grade,
  contextScope: contextScope.value,
  layerErrors: { ...layerErrors }
}))

onMounted(async () => {
  mapDisposed = false
  map = L.map('map', {
    center: [26.65, 106.63],
    zoom: 11,
    preferCanvas: true,
    zoomControl: false,
    attributionControl: false
  })

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: ''
  }).addTo(map)

  currentZoom.value = map.getZoom()
  map.on('zoomend', () => {
    currentZoom.value = map.getZoom()
  })
  map.on('moveend', loadStatistics)
  map.on('mousedown', handleRegionMouseDown)
  map.on('mousemove', handleRegionMouseMove)
  map.on('mouseup', handleRegionMouseUp)
  map.on('click', handleRegionClick)
  map.on('dblclick', handleRegionDoubleClick)
  map.on('contextmenu', cancelRegionDraw)
  window.addEventListener('keydown', handleRegionKeydown)

  await nextTick()
  map.invalidateSize(false)
  await reloadLayers({ mask: true, preserveViewport: false })
  await loadStatistics()
  handleFitAll()
})

onUnmounted(() => {
  mapDisposed = true
  objectDetailRequestGuard.invalidate()
  regionSolutionRequestGuard.invalidate()
  regionSummaryRequestSeq += 1
  regionLoading.value = false
  regionSummaryLoading.value = false
  window.removeEventListener('keydown', handleRegionKeydown)
  if (layerChangeTimer) {
    window.clearTimeout(layerChangeTimer)
    layerChangeTimer = null
  }
  if (map) {
    map.off()
    map.remove()
  }
  layerMap.clear()
  selectedLayer = null
  regionLayer = null
  regionPreviewLayer = null
  aiSourceHighlightLayer = null
  rectangleStart = null
})

function isBlankQueryValue(value: any) {
  return value === undefined || value === null || String(value).trim() === ''
}

function layerQuery(): GisLayerQuery {
  const next: Record<string, any> = {}
  Object.entries(query).forEach(([key, value]) => {
    if (isBlankQueryValue(value)) return
    next[key] = typeof value === 'string' ? value.trim() : value
  })
  return next as GisLayerQuery
}

async function handleSearch(nextQuery?: GisLayerQuery) {
  if (nextQuery) {
    Object.assign(query, nextQuery)
  }
  await reloadLayers({ preserveViewport: true, clearSelection: true })
  await loadStatistics()
}

function handleLayerChange(nextLayers?: LayerState) {
  if (nextLayers) {
    Object.assign(layers, nextLayers)
  }

  if (layerChangeTimer) {
    window.clearTimeout(layerChangeTimer)
  }

  layerChangeTimer = window.setTimeout(() => {
    layerChangeTimer = null
    void syncVisibleLayers()
  }, 80)
}

async function handleLayerRefresh() {
  await reloadLayers({ preserveViewport: true, clearSelection: false })
  await loadStatistics()
}

async function handleReset() {
  query.routeCode = 'G210'
  query.year = '2026'
  query.indexCode = 'MQI'
  query.grade = ''
  await handleSearch()
}

type ReloadLayerOptions = {
  preserveViewport?: boolean
  clearSelection?: boolean
  mask?: boolean
}

type ViewSnapshot = {
  center: L.LatLng
  zoom: number
}

function captureViewSnapshot(): ViewSnapshot | null {
  if (!map) return null
  return { center: map.getCenter(), zoom: map.getZoom() }
}

function restoreViewSnapshot(snapshot: ViewSnapshot | null) {
  if (!map || !snapshot) return
  map.setView(snapshot.center, snapshot.zoom, { animate: false })
}

async function reloadLayers(options: ReloadLayerOptions = {}) {
  if (!map || mapDisposed) return
  const snapshot = options.preserveViewport ? captureViewSnapshot() : null
  loading.value = true
  mapLoadingMask.value = Boolean(options.mask)
  try {
    clearLayers({ clearSelection: options.clearSelection !== false })
    resetLayerCounts()
    resetLayerErrors()

    const tasks: Promise<void>[] = []
    const params = layerQuery()

    if (layers.roadRoute) tasks.push(loadLayerSafely('roadRoute', () => getRoadRoutes(params)))
    if (layers.roadSection) tasks.push(loadLayerSafely('roadSection', () => getRoadSections(params)))
    if (layers.evaluationUnit) tasks.push(loadLayerSafely('evaluationUnit', () => getEvaluationUnits(params)))
    if (layers.disease) tasks.push(loadLayerSafely('disease', () => getDiseases(params)))
    if (layers.assessment || layers.assessmentResult) {
      tasks.push(loadLayerSafely('assessment', () => getAssessmentResults(params)))
    }

    await settleLayerTasks(tasks, '图层加载')
  } catch (error: any) {
    ElMessage.error(error?.message || '图层加载失败')
  } finally {
    restoreViewSnapshot(snapshot)
    loading.value = false
    mapLoadingMask.value = false
  }
}

async function syncVisibleLayers() {
  if (!map || mapDisposed) return
  loading.value = true
  mapLoadingMask.value = false
  try {
    const params = layerQuery()
    const tasks: Promise<void>[] = []

    if (!layers.roadRoute) removeLayerByKey('roadRoute')
    else if (!layerMap.has('roadRoute')) tasks.push(loadLayerSafely('roadRoute', () => getRoadRoutes(params)))

    if (!layers.roadSection) removeLayerByKey('roadSection')
    else if (!layerMap.has('roadSection')) tasks.push(loadLayerSafely('roadSection', () => getRoadSections(params)))

    if (!layers.evaluationUnit) removeLayerByKey('evaluationUnit')
    else if (!layerMap.has('evaluationUnit')) tasks.push(loadLayerSafely('evaluationUnit', () => getEvaluationUnits(params)))

    if (!layers.disease) removeLayerByKey('disease')
    else if (!layerMap.has('disease')) tasks.push(loadLayerSafely('disease', () => getDiseases(params)))

    if (!(layers.assessment || layers.assessmentResult)) removeLayerByKey('assessment')
    else if (!layerMap.has('assessment')) tasks.push(loadLayerSafely('assessment', () => getAssessmentResults(params)))

    await settleLayerTasks(tasks, '图层切换')
    await loadStatistics()
  } catch (error: any) {
    ElMessage.error(error?.message || '图层切换失败')
  } finally {
    loading.value = false
  }
}

function removeLayerByKey(layerKey: string) {
  const layer = layerMap.get(layerKey)
  if (!layer) return
  map.removeLayer(layer)
  layerMap.delete(layerKey)
  layerCounts[layerKey] = 0
  delete layerErrors[layerKey]
  if (selectedLayer && layer === selectedLayer) {
    selectedLayer = null
  }
}

function clearLayers(options: { clearSelection?: boolean } = {}) {
  layerMap.forEach((layer) => {
    map.removeLayer(layer)
  })
  layerMap.clear()
  selectedLayer = null
  clearAiSourceHighlight()
  if (options.clearSelection !== false) {
    objectDetailRequestGuard.invalidate()
    selectedDetail.value = null
    selectedFeatureProperties.value = null
    contextScope.value = regionGeometry.value ? 'REGION' : 'ROUTE'
  }
}

function resetLayerCounts() {
  Object.keys(layerCounts).forEach((key) => {
    layerCounts[key] = 0
  })
}

function resetLayerErrors() {
  Object.keys(layerErrors).forEach((key) => delete layerErrors[key])
}

function layerKeyText(layerKey: string) {
  const labels: Record<string, string> = {
    roadRoute: '路线',
    roadSection: '路段',
    evaluationUnit: '评定单元',
    disease: '病害',
    assessment: '评定专题'
  }
  return labels[layerKey] || layerKey
}

function errorMessage(error: any) {
  return error?.message || error?.response?.data?.message || error?.data?.message || '加载失败'
}

async function loadLayerSafely(layerKey: string, loader: () => Promise<GeoJsonFeatureCollection>) {
  try {
    await loadLayer(layerKey, loader)
    delete layerErrors[layerKey]
  } catch (error: any) {
    layerCounts[layerKey] = 0
    layerErrors[layerKey] = errorMessage(error)
    throw error
  }
}

async function settleLayerTasks(tasks: Promise<void>[], actionName: string) {
  if (!tasks.length) return
  const results = await Promise.allSettled(tasks)
  const failedCount = results.filter((item) => item.status === 'rejected').length
  if (failedCount > 0) {
    ElMessage.warning(`${actionName}完成，${failedCount} 个图层失败，其他图层已保留`)
  }
}

async function loadLayer(layerKey: string, loader: () => Promise<GeoJsonFeatureCollection>) {
  const collection: any = await loader()
  if (mapDisposed) return
  layerCounts[layerKey] = Array.isArray(collection?.features) ? collection.features.length : 0
  if (!collection || !collection.features || collection.features.length === 0) return

  const geoLayer = L.geoJSON(collection as any, {
    style: (feature: any) => layerStyle(feature?.properties || feature, query.indexCode || 'MQI'),
    pointToLayer: (feature, latlng) => {
      const style = layerStyle(feature?.properties || feature, query.indexCode || 'MQI') as any
      return L.circleMarker(latlng, {
        radius: style.radius || 5,
        color: style.color || '#2563eb',
        weight: style.weight || 2,
        fillColor: style.fillColor || style.color || '#2563eb',
        fillOpacity: style.fillOpacity ?? 0.85
      })
    },
    onEachFeature: (feature: any, layer: L.Layer) => {
      layer.on('click', () => handleFeatureClick(layerKey, feature, layer))
    }
  })

  geoLayer.addTo(map)
  layerMap.set(layerKey, geoLayer)
}

async function handleFeatureClick(layerKey: string, feature: any, layer: L.Layer) {
  if (regionMode.value !== 'NONE') return
  const properties = normalizeFeatureProperties(layerKey, feature)

  contextScope.value = 'OBJECT'
  selectedFeatureProperties.value = properties
  selectedDetail.value = properties
  highlightLayer(layer)
  openFeaturePopup(layer, properties)

  const detailApplied = await loadObjectDetail(properties)
  if (detailApplied && selectedDetail.value) openFeaturePopup(layer, selectedDetail.value)
}

function normalizeFeatureProperties(layerKey: string, feature: any) {
  const raw = feature?.properties || {}
  const objectType = normalizeObjectType(firstValue(raw.objectType, raw.object_type, raw.type, raw.layerType, layerKeyToObjectType(layerKey)))
  const objectId = firstValue(raw.objectId, raw.object_id, raw.id, feature?.id)
  return {
    ...raw,
    layerKey,
    objectType,
    object_type: objectType,
    objectId,
    object_id: objectId,
    id: firstValue(raw.id, objectId)
  }
}

function highlightLayer(layer: L.Layer) {
  if (selectedLayer && (selectedLayer as any).setStyle) {
    const feature = (selectedLayer as any).feature
    ;(selectedLayer as any).setStyle(layerStyle(feature?.properties || feature, query.indexCode || 'MQI'))
  }

  selectedLayer = layer

  if ((layer as any).setStyle) {
    ;(layer as any).setStyle({
      color: '#0ea5e9',
      weight: 5,
      fillOpacity: 0.95
    })
  }
}


function openFeaturePopup(layer: L.Layer, properties: Record<string, any>) {
  if (!map) return
  const center = layerCenter(layer)
  if (!center) return
  L.popup({
    closeButton: true,
    autoPan: true,
    maxWidth: 300,
    className: 'map-object-popup'
  })
    .setLatLng(center)
    .setContent(buildFeaturePopupHtml(properties))
    .openOn(map)
}

function layerCenter(layer: L.Layer) {
  const anyLayer: any = layer
  if (anyLayer.getLatLng) return anyLayer.getLatLng()
  if (anyLayer.getBounds && anyLayer.getBounds().isValid()) return anyLayer.getBounds().getCenter()
  return null
}

function buildFeaturePopupHtml(properties: Record<string, any>) {
  const type = normalizeObjectType(firstValue(properties.objectType, properties.object_type, properties.type, properties.layerType))
  const label = objectTypeText(type)
  const rows = popupRowsByType(type, properties)
  return `
    <div class="object-popup-card">
      <div class="object-popup-title">${escapeHtml(label)}详情</div>
      <div class="object-popup-grid">
        ${rows.map(([k, v]) => `<span>${escapeHtml(k)}</span><strong>${escapeHtml(v)}</strong>`).join('')}
      </div>
      <div class="object-popup-tip">已同步到右侧 AI 养护助手，可继续分析或生成建议。</div>
    </div>
  `
}

function popupRowsByType(type: string, properties: Record<string, any>): Array<[string, any]> {
  const route = routeCodeText(properties)
  const stake = stakeRangeText(properties)
  const direction = directionText(firstValue(properties.direction, properties.directionText, properties.direction_text))
  const metricRows = popupMetricRows(properties)

  if (type === 'ROAD_ROUTE') {
    return compactRows([
      ['路线编号', route],
      ['路线名称', firstValue(properties.routeName, properties.route_name)],
      ['路线类型', firstValue(properties.routeType, properties.route_type)],
      ['技术等级', firstValue(properties.technicalGrade, properties.technical_grade)],
      ['起讫桩号', stake],
      ['里程(km)', firstValue(properties.lengthKm, properties.length_km)]
    ])
  }

  if (type === 'ROAD_SECTION') {
    return compactRows([
      ['路线编号', route],
      ['路段编号', firstValue(properties.sectionCode, properties.section_code)],
      ['路段名称', firstValue(properties.sectionName, properties.section_name)],
      ['方向', direction],
      ['起讫桩号', stake],
      ['长度(km)', firstValue(properties.lengthKm, properties.length_km)],
      ['路面类型', firstValue(properties.pavementType, properties.pavement_type)]
    ])
  }

  if (type === 'EVALUATION_UNIT') {
    return compactRows([
      ['路线编号', route],
      ['单元编号', firstValue(properties.unitCode, properties.unit_code)],
      ['方向', direction],
      ['车道', firstValue(properties.laneNo, properties.lane_no)],
      ['起讫桩号', stake],
      ['长度(m)', firstValue(properties.lengthM, properties.length_m)],
      ['路面类型', firstValue(properties.pavementType, properties.pavement_type)]
    ])
  }

  if (type === 'DISEASE') {
    return compactRows([
      ['路线编号', route],
      ['病害类型', firstValue(properties.diseaseName, properties.disease_name, properties.diseaseType, properties.disease_type)],
      ['病害分类', firstValue(properties.diseaseCategory, properties.disease_category)],
      ['严重程度', severityText(firstValue(properties.severity, properties.severityText, properties.severity_text))],
      ['方向', direction],
      ['车道', firstValue(properties.laneNo, properties.lane_no)],
      ['起讫桩号', stake],
      ['数量', quantityText(properties)],
      ['状态', firstValue(properties.status, properties.verified)]
    ])
  }

  if (type === 'ASSESSMENT_RESULT') {
    return compactRows([
      ['路线编号', route],
      ['评定单元', firstValue(properties.unitCode, properties.unit_code, properties.unitId, properties.unit_id)],
      ['年度', firstValue(properties.year, query.year)],
      ['方向', direction],
      ['起讫桩号', stake],
      ...metricRows,
      ['等级', gradeText(getMetricGrade(properties, query.indexCode || 'MQI') || firstValue(properties.grade, properties.level))]
    ])
  }

  return compactRows([
    ['类型', objectTypeText(type)],
    ['路线编号', route],
    ['起讫桩号', stake],
    ...metricRows,
    ['等级', gradeText(getMetricGrade(properties, query.indexCode || 'MQI') || firstValue(properties.grade, properties.level))]
  ])
}

function popupMetricRows(properties: Record<string, any>): Array<[string, any]> {
  const metric = getMetricMeta(query.indexCode || 'MQI')
  const value = getMetricValue(properties, metric.code)
  const rows: Array<[string, any]> = []
  if (value !== undefined && value !== null && value !== '') {
    rows.push([metric.code, value])
  }
  return rows
}

function compactRows(rows: Array<[string, any]>): Array<[string, any]> {
  return rows
    .map(([key, value]) => [key, displayValue(value)] as [string, any])
    .filter(([, value]) => value !== '-')
}

function displayValue(value: any) {
  if (value === undefined || value === null || value === '') return '-'
  return value
}

function routeCodeText(properties: Record<string, any>) {
  return firstValue(properties.routeCode, properties.route_code, properties.roadCode, properties.road_code, query.routeCode, '-')
}

function quantityText(properties: Record<string, any>) {
  const quantity = firstValue(properties.quantity, properties.amount)
  const unit = firstValue(properties.measureUnit, properties.measure_unit, properties.unit)
  if (quantity && unit) return `${quantity}${unit}`
  return quantity || '-'
}

function directionText(direction: any) {
  const value = String(direction || '').toUpperCase()
  const map: Record<string, string> = {
    UP: '上行',
    DOWN: '下行',
    BOTH: '双向',
    LEFT: '左幅',
    RIGHT: '右幅'
  }
  return map[value] || direction || '-'
}

function severityText(severity: any) {
  const value = String(severity || '').toUpperCase()
  const map: Record<string, string> = {
    LIGHT: '轻度',
    MEDIUM: '中度',
    HEAVY: '重度',
    SERIOUS: '严重'
  }
  return map[value] || severity || '-'
}

function gradeText(grade: any) {
  const value = String(grade || '').toUpperCase()
  const map: Record<string, string> = {
    EXCELLENT: '优',
    GOOD: '良',
    FAIR: '中',
    POOR: '次',
    BAD: '差'
  }
  return map[value] || grade || '-'
}

function objectTypeText(type: string) {
  const map: Record<string, string> = {
    ROAD_ROUTE: '路线',
    ROAD_SECTION: '路段',
    EVALUATION_UNIT: '评定单元',
    DISEASE: '病害',
    ASSESSMENT_RESULT: '评定结果'
  }
  return map[type] || '地图对象'
}

function stakeRangeText(properties: Record<string, any>) {
  const start = firstValue(properties.startStake, properties.start_stake, properties.beginStake, properties.begin_stake)
  const end = firstValue(properties.endStake, properties.end_stake, properties.finishStake, properties.finish_stake)
  if (start && end) return `${start}—${end}`
  return start || end || '-'
}

function escapeHtml(value: any) {
  return String(value ?? '-')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function layerKeyToObjectType(layerKey: string) {
  const map: Record<string, string> = {
    roadRoute: 'ROAD_ROUTE',
    roadSection: 'ROAD_SECTION',
    evaluationUnit: 'EVALUATION_UNIT',
    disease: 'DISEASE',
    assessment: 'ASSESSMENT_RESULT'
  }
  return map[layerKey] || layerKey.toUpperCase()
}

async function loadObjectDetail(properties: Record<string, any>) {
  const requestToken = objectDetailRequestGuard.next()
  if (mapDisposed) return false
  const objectType = normalizeObjectType(
    firstValue(properties.objectType, properties.object_type, properties.type, properties.layerType)
  )
  const id = firstValue(properties.objectId, properties.object_id, properties.id)

  if (!objectType || !id) {
    if (!objectDetailRequestGuard.isCurrent(requestToken)) return false
    selectedDetail.value = { ...properties, objectType, objectId: id, id }
    return true
  }

  try {
    const detail = await getObjectDetail({ objectType, id })
    if (!objectDetailRequestGuard.isCurrent(requestToken)) return false
    selectedDetail.value = {
      ...properties,
      ...(detail || {}),
      objectType,
      objectId: id,
      id
    }
    return true
  } catch {
    if (!objectDetailRequestGuard.isCurrent(requestToken)) return false
    selectedDetail.value = {
      ...properties,
      objectType,
      objectId: id,
      id
    }
    return true
  }
}

function clearSelection() {
  objectDetailRequestGuard.invalidate()
  if (map) map.closePopup()
  selectedDetail.value = null
  selectedFeatureProperties.value = null
  contextScope.value = regionGeometry.value ? 'REGION' : 'ROUTE' 
  if (selectedLayer && (selectedLayer as any).setStyle) {
    const feature = (selectedLayer as any).feature
    ;(selectedLayer as any).setStyle(layerStyle(feature?.properties || feature, query.indexCode || 'MQI'))
  }
  selectedLayer = null
}

function openAiForSelected() {
  if (!selectedDetail.value) {
    ElMessage.warning('请先在地图上选择一个对象')
    return
  }
  contextScope.value = 'OBJECT'
  agentVisible.value = true
  pendingAiQuestion.value = '分析当前地图选中对象，说明主要问题、成因判断，并给出养护处置建议'
}

function askAiForRegion() {
  if (!regionGeometry.value) {
    ElMessage.warning('请先框选一个区域')
    return
  }
  contextScope.value = 'REGION'
  agentVisible.value = true
  pendingAiQuestion.value = '综合分析当前区域内线路、路段、评定单元、病害和评定结果，判断养护重点、风险原因与处置优先级'
}

function handleAskAiSource(source: Record<string, any>) {
  agentVisible.value = true
  const target = sourceToMapTarget(source)
  const label = [target.title, target.objectType, target.routeCode].filter(Boolean).join('｜') || '当前参考来源'
  pendingAiQuestion.value = `结合参考来源「${label}」，继续分析其与当前地图范围、线路、路段、病害和评定结果的关系，并给出下一步处置建议`
}

function handleLocateAiSource(source: Record<string, any> | GisSourceMapTarget) {
  const target = normalizeIncomingMapTarget(source)
  if (locateMapTarget(target)) {
    ElMessage.success('已定位到来源关联的地图对象/区域')
  } else {
    ElMessage.warning('该来源暂未携带可定位的对象编号、路线桩号、bbox 或 geometry；需要后端 sources 补齐地图关联字段')
  }
}

function normalizeIncomingMapTarget(source: Record<string, any> | GisSourceMapTarget): GisSourceMapTarget {
  if (source && (source as GisSourceMapTarget).raw && ((source as GisSourceMapTarget).objectId || (source as GisSourceMapTarget).routeCode || (source as GisSourceMapTarget).geometry || (source as GisSourceMapTarget).bbox)) {
    return source as GisSourceMapTarget
  }
  return sourceToMapTarget(source)
}

function startRegionDraw(mode: 'RECTANGLE' | 'POLYGON') {
  clearRegion()
  regionMode.value = mode
  regionGeometryType.value = mode
  objectDetailRequestGuard.invalidate()
  selectedDetail.value = null
  selectedFeatureProperties.value = null
  polygonPoints.value = []
  rectangleStart = null
  updateMapDrawState()
  if (map) {
    if (mode === 'RECTANGLE') {
      map.dragging.disable()
      map.doubleClickZoom.enable()
    } else {
      map.dragging.enable()
      map.doubleClickZoom.disable()
    }
  }
  ElMessage.info(mode === 'RECTANGLE' ? '拖拽地图绘制矩形选区，右键或 Esc 取消' : '点击地图绘制多边形，双击结束，右键或 Esc 取消')
}

function clearRegion() {
  regionSummaryRequestSeq += 1
  regionSolutionRequestGuard.invalidate()
  if (regionLayer && map) {
    map.removeLayer(regionLayer)
    regionLayer = null
  }
  clearRegionPreview()
  if (map) {
    map.dragging.enable()
    map.doubleClickZoom.enable()
  }
  regionMode.value = 'NONE'
  regionGeometry.value = null
  regionSummary.value = null
  regionSolution.value = null
  regionLoading.value = false
  regionSummaryLoading.value = false
  regionSavedTask.value = null
  regionPreviewVisible.value = false
  contextScope.value = selectedDetail.value ? 'OBJECT' : 'ROUTE'
  polygonPoints.value = []
  rectangleStart = null
  updateMapDrawState()
}

function setRegionLayer(layer: L.Layer, geometry: Record<string, any>, geometryType: 'RECTANGLE' | 'POLYGON') {
  regionSolutionRequestGuard.invalidate()
  if (regionLayer && map) {
    map.removeLayer(regionLayer)
  }
  clearRegionPreview()
  regionLayer = layer
  regionLayer.addTo(map)
  contextScope.value = 'REGION'
  regionGeometryType.value = geometryType
  regionGeometry.value = geometry
  regionSummary.value = buildClientRegionSummary(geometry)
  regionSolution.value = null
  regionSavedTask.value = null
  regionMode.value = 'NONE'
  map.dragging.enable()
  map.doubleClickZoom.enable()
  updateMapDrawState()
  loadRegionSummary(geometry)
}

function handleRegionMouseDown(event: L.LeafletMouseEvent) {
  if (regionMode.value !== 'RECTANGLE') return
  rectangleStart = event.latlng
  clearRegionPreview()
}

function handleRegionMouseMove(event: L.LeafletMouseEvent) {
  if (regionMode.value === 'RECTANGLE' && rectangleStart) {
    const bounds = L.latLngBounds(rectangleStart, event.latlng)
    renderRectanglePreview(bounds)
    return
  }
  if (regionMode.value === 'POLYGON' && polygonPoints.value.length > 0) {
    renderPolygonPreview(event.latlng)
  }
}

function handleRegionMouseUp(event: L.LeafletMouseEvent) {
  if (regionMode.value !== 'RECTANGLE' || !rectangleStart) return
  const bounds = L.latLngBounds(rectangleStart, event.latlng)
  rectangleStart = null
  if (!isUsefulBounds(bounds)) {
    clearRegionPreview()
    ElMessage.warning('框选范围过小，请重新拖拽绘制')
    return
  }
  const layer = L.rectangle(bounds, regionFinalStyle)
  setRegionLayer(layer, boundsToPolygon(bounds), 'RECTANGLE')
}

function handleRegionClick(event: L.LeafletMouseEvent) {
  if (regionMode.value !== 'POLYGON') return
  polygonPoints.value.push(event.latlng)
  renderPolygonPreview()
}

function handleRegionDoubleClick(event: L.LeafletMouseEvent) {
  if (regionMode.value !== 'POLYGON') return
  event.originalEvent.preventDefault()
  if (polygonPoints.value.length < 3) {
    ElMessage.warning('多边形至少需要 3 个点')
    return
  }
  const layer = L.polygon(polygonPoints.value, regionFinalStyle)
  setRegionLayer(layer, pointsToPolygon(polygonPoints.value), 'POLYGON')
}

function clearRegionPreview() {
  if (regionPreviewLayer && map) {
    map.removeLayer(regionPreviewLayer)
    regionPreviewLayer = null
  }
}

function renderRectanglePreview(bounds: L.LatLngBounds) {
  if (!map || !bounds.isValid()) return
  clearRegionPreview()
  regionPreviewLayer = L.rectangle(bounds, regionPreviewStyle).addTo(map)
}

function renderPolygonPreview(cursor?: L.LatLng) {
  if (!map) return
  clearRegionPreview()
  const points = cursor ? [...polygonPoints.value, cursor] : [...polygonPoints.value]
  const previewLayers: L.Layer[] = []
  if (points.length >= 2) {
    previewLayers.push(L.polyline(points, regionPreviewStyle))
  }
  polygonPoints.value.forEach((point, index) => {
    previewLayers.push(L.circleMarker(point, {
      radius: index === 0 ? 5 : 4,
      color: '#2563eb',
      weight: 2,
      fillColor: '#ffffff',
      fillOpacity: 0.95
    }))
  })
  regionPreviewLayer = L.layerGroup(previewLayers).addTo(map)
}

function cancelRegionDraw() {
  if (regionMode.value === 'NONE') return
  polygonPoints.value = []
  rectangleStart = null
  regionMode.value = 'NONE'
  clearRegionPreview()
  if (map) {
    map.dragging.enable()
    map.doubleClickZoom.enable()
  }
  updateMapDrawState()
  ElMessage.info('已取消框选')
}

function handleRegionKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') cancelRegionDraw()
}

function updateMapDrawState() {
  if (!map) return
  const container = map.getContainer()
  container.classList.toggle('region-drawing', regionMode.value !== 'NONE')
  container.classList.toggle('region-rectangle', regionMode.value === 'RECTANGLE')
  container.classList.toggle('region-polygon', regionMode.value === 'POLYGON')
}

function isUsefulBounds(bounds: L.LatLngBounds) {
  if (!bounds.isValid()) return false
  const sw = bounds.getSouthWest()
  const ne = bounds.getNorthEast()
  return Math.abs(sw.lat - ne.lat) > 0.00001 && Math.abs(sw.lng - ne.lng) > 0.00001
}

function boundsToPolygon(bounds: L.LatLngBounds) {
  const sw = bounds.getSouthWest()
  const ne = bounds.getNorthEast()
  const nw = L.latLng(ne.lat, sw.lng)
  const se = L.latLng(sw.lat, ne.lng)
  return pointsToPolygon([sw, se, ne, nw])
}

function pointsToPolygon(points: L.LatLng[]) {
  const coords = points.map((p) => [Number(p.lng.toFixed(7)), Number(p.lat.toFixed(7))])
  const first = coords[0]
  const last = coords[coords.length - 1]
  if (first[0] !== last[0] || first[1] !== last[1]) {
    coords.push([first[0], first[1]])
  }
  return { type: 'Polygon', coordinates: [coords] }
}

function buildClientRegionSummary(geometry: Record<string, any>) {
  return {
    geometry,
    areaKm2: '-',
    routeCount: '-',
    sectionCount: '-',
    unitCount: '-',
    diseaseSummary: {},
    assessmentSummary: {},
    hotspots: []
  }
}

function unwrapApiPayload(value: any) {
  if (value && typeof value === 'object' && typeof value.code !== 'undefined' && 'data' in value) {
    return value.data
  }
  return value
}

function normalizeRegionSummary(value: any, geometry: Record<string, any>) {
  const summary = unwrapApiPayload(value)
  if (!summary || typeof summary !== 'object') {
    return buildClientRegionSummary(geometry)
  }
  return {
    ...buildClientRegionSummary(geometry),
    ...summary,
    geometry: summary.geometry || geometry,
    diseaseSummary: summary.diseaseSummary || summary.disease_summary || {},
    assessmentSummary: summary.assessmentSummary || summary.assessment_summary || {},
    hotspots: Array.isArray(summary.hotspots) ? summary.hotspots : []
  }
}

async function loadRegionSummary(geometry: Record<string, any>) {
  const requestSeq = ++regionSummaryRequestSeq
  regionSummaryLoading.value = true
  try {
    const result = await analyzeMapRegion({
      geometry,
      query: { ...query, indexCode: query.indexCode, grade: query.grade },
      layers: activeLayerNames(),
      options: { useBusinessData: true, indexCode: query.indexCode, grade: query.grade }
    })
    if (requestSeq === regionSummaryRequestSeq && regionGeometry.value) {
      regionSummary.value = normalizeRegionSummary(result, geometry)
    }
  } catch (error: any) {
    if (requestSeq === regionSummaryRequestSeq && regionGeometry.value) {
      regionSummary.value = buildClientRegionSummary(geometry)
    }
    ElMessage.warning(error?.message || '区域统计摘要获取失败')
  } finally {
    if (requestSeq === regionSummaryRequestSeq) {
      regionSummaryLoading.value = false
    }
  }
}

async function generateRegionSolution() {
  if (!regionGeometry.value) {
    ElMessage.warning('请先绘制区域')
    return
  }
  const requestToken = regionSolutionRequestGuard.next()
  const geometrySnapshot = regionGeometry.value
  const geometryTypeSnapshot = regionGeometryType.value
  const querySnapshot = { ...query }
  const layerSnapshot = activeLayerNames()
  const summarySnapshot = regionSummary.value || undefined
  contextScope.value = 'REGION'
  regionLoading.value = true
  try {
    const result = unwrapApiPayload(await mapAgentRun({
      action: 'GENERATE_REGION_SOLUTION',
      message: '生成框选区域养护建议',
      mapContext: {
        tenantId: 'default',
        mode: 'REGION',
        routeCode: querySnapshot.routeCode,
        year: Number(querySnapshot.year),
        geometry: geometrySnapshot,
        regionSummary: summarySnapshot,
        selectedLayers: layerSnapshot,
        extra: {
          indexCode: querySnapshot.indexCode,
          grade: querySnapshot.grade,
          geometryType: geometryTypeSnapshot
        }
      },
      actionInput: {
        solutionType: 'REGION_MAINTENANCE_SUGGESTION'
      },
      options: { useBusinessData: true, useKnowledge: true, useOutline: false, topK: 5, requireAi: true, indexCode: querySnapshot.indexCode, grade: querySnapshot.grade }
    })) as any
    if (!regionSolutionRequestGuard.isCurrent(requestToken) || !isSameRegionSolutionContext(geometrySnapshot, querySnapshot, layerSnapshot)) return
    const actionResult = result.actionResult || {}
    const toolData = Array.isArray(result.toolResults)
      ? (result.toolResults.find((item: any) => item?.toolName === 'solution.generateDraft')?.data || result.toolResults[0]?.data || {})
      : {}
    const sourceSummaries = Array.isArray(toolData.sourceSummaries)
      ? toolData.sourceSummaries
      : (Array.isArray(result.sources) ? result.sources : (Array.isArray(result.knowledgeSources) ? result.knowledgeSources : []))
    const templateMeta = actionResult.templateMeta || toolData.templateMeta || {}
    regionSolution.value = {
      solutionType: String(actionResult.solutionType || toolData.solutionType || 'REGION_MAINTENANCE_SUGGESTION'),
      title: actionResult.title || toolData.title || '区域养护建议',
      markdown: actionResult.markdown || toolData.markdown || result.answer || '',
      regionSummary: actionResult.regionSummary || toolData.regionSummary || regionSummary.value || {},
      qualityCheck: actionResult.qualityCheck || toolData.qualityCheck || {},
      templateMeta,
      answerMeta: result.answerMeta || toolData.answerMeta || {},
      toolResults: result.toolResults || [],
      sources: result.sources || result.knowledgeSources || [],
      sourceSummaries,
      trace: result.trace || {}
    } as MapRegionSolutionResponse
    if (actionResult.regionSummary) {
      regionSummary.value = normalizeRegionSummary(actionResult.regionSummary, geometrySnapshot)
    }
    regionSavedTask.value = null
    regionPreviewVisible.value = true
    ElMessage.success(result.answerMeta?.llmSuccess ? '区域养护建议已由 AI 生成' : '区域养护建议已生成（未使用 AI）')
  } catch (error: any) {
    if (regionSolutionRequestGuard.isCurrent(requestToken)) {
      ElMessage.error(error?.message || '生成区域养护建议失败')
    }
  } finally {
    if (regionSolutionRequestGuard.isCurrent(requestToken)) {
      regionLoading.value = false
    }
  }
}

function isSameRegionSolutionContext(geometry: Record<string, any>, querySnapshot: GisLayerQuery, layerSnapshot: string[]) {
  return regionGeometry.value === geometry &&
    String(query.routeCode || '') === String(querySnapshot.routeCode || '') &&
    String(query.year || '') === String(querySnapshot.year || '') &&
    String(query.indexCode || '') === String(querySnapshot.indexCode || '') &&
    String(query.grade || '') === String(querySnapshot.grade || '') &&
    sameStringList(activeLayerNames(), layerSnapshot)
}

function sameStringList(left: string[], right: string[]) {
  if (left.length !== right.length) return false
  return left.every((item, index) => item === right[index])
}

function activeLayerNames() {
  const result: string[] = []
  if (layers.roadRoute) result.push('ROAD_ROUTE')
  if (layers.roadSection) result.push('ROAD_SECTION')
  if (layers.evaluationUnit) result.push('EVALUATION_UNIT')
  if (layers.disease) result.push('DISEASE')
  if (layers.assessment || layers.assessmentResult) result.push('ASSESSMENT_RESULT')
  return result
}

async function saveRegionDraft() {
  if (!regionSolution.value || !regionGeometry.value) return
  regionDraftSaving.value = true
  try {
    const templateMeta = regionSolution.value.templateMeta || {}
    const saved = await saveMapRegionSolutionDraft({
      originType: 'MAP_REGION',
      objectType: 'MAP_REGION',
      objectId: regionSolution.value.trace?.traceId || '',
      solutionType: regionSolution.value.solutionType,
      title: regionSolution.value.title,
      markdown: regionSolution.value.markdown,
      routeCode: String(query.routeCode || ''),
      year: Number(query.year) || undefined,
      mapObject: {
        objectType: 'MAP_REGION',
        geometry: regionGeometry.value,
        drawingMode: regionGeometryType.value
      },
      regionSummary: regionSolution.value.regionSummary || {},
      qualityCheck: regionSolution.value.qualityCheck || {},
      trace: regionSolution.value.trace || {},
      templateId: templateMeta.templateId || templateMeta.template_id || '',
      templateVersion: templateMeta.templateVersion || templateMeta.template_version || '',
      templateName: templateMeta.templateName || templateMeta.template_name || '',
      templateMeta,
      sourceSummaries: regionSolution.value.sourceSummaries || [],
      requestContext: { query: { ...query, indexCode: query.indexCode, grade: query.grade }, layers: activeLayerNames(), metric: getMetricMeta(query.indexCode) }
    })
    regionSavedTask.value = saved
    ElMessage.success('区域方案草稿已保存')
  } catch (error: any) {
    ElMessage.error(error?.message || '保存区域方案草稿失败')
  } finally {
    regionDraftSaving.value = false
  }
}

function currentViewport() {
  if (mapDisposed || typeof map === 'undefined' || !map) return null
  const bounds = map.getBounds()
  return {
    zoom: map.getZoom(),
    center: { lat: map.getCenter().lat, lng: map.getCenter().lng },
    bbox: [bounds.getWest(), bounds.getSouth(), bounds.getEast(), bounds.getNorth()]
  }
}

function clearAiSourceHighlight() {
  if (aiSourceHighlightLayer && map) {
    map.removeLayer(aiSourceHighlightLayer)
    aiSourceHighlightLayer = null
  }
}

function locateMapTarget(target: GisSourceMapTarget) {
  if (!target) return false
  clearAiSourceHighlight()
  if (target.geometry) {
    const layer = L.geoJSON(target.geometry as any, {
      style: { color: '#f97316', weight: 4, fillOpacity: 0.15 },
      pointToLayer: (_feature, latlng) => L.circleMarker(latlng, { radius: 8, color: '#f97316', weight: 3, fillColor: '#fed7aa', fillOpacity: 0.8 })
    })
    aiSourceHighlightLayer = layer.addTo(map)
    const bounds = layer.getBounds()
    if (bounds.isValid()) map.fitBounds(bounds, { padding: [80, 80], maxZoom: 16 })
    return true
  }
  const bboxBounds = bboxToBounds(target.bbox)
  if (bboxBounds) {
    aiSourceHighlightLayer = L.rectangle(bboxBounds, { color: '#f97316', weight: 3, fillOpacity: 0.1 }).addTo(map)
    map.fitBounds(bboxBounds, { padding: [80, 80], maxZoom: 16 })
    return true
  }
  const matched = findLayerByTarget(target)
  if (!matched) return false
  const props = { ...(matched.feature?.properties || {}), layerKey: matched.layerKey, objectType: matched.feature?.properties?.objectType || matched.feature?.properties?.object_type || layerKeyToObjectType(matched.layerKey) }
  selectedFeatureProperties.value = props
  selectedDetail.value = props
  highlightLayer(matched.layer)
  zoomToLayer(matched.layer)
  loadObjectDetail(props)
  return true
}

function bboxToBounds(bbox: any) {
  if (!Array.isArray(bbox) || bbox.length !== 4) return null
  const nums = bbox.map((it: any) => Number(it))
  if (nums.some((it: number) => !Number.isFinite(it))) return null
  const [a, b, c, d] = nums
  const looksLatLng = Math.abs(a) <= 90 && Math.abs(b) > 90
  const south = looksLatLng ? a : b
  const west = looksLatLng ? b : a
  const north = looksLatLng ? c : d
  const east = looksLatLng ? d : c
  const bounds = L.latLngBounds([south, west], [north, east])
  return bounds.isValid() ? bounds : null
}

function findLayerByTarget(target: GisSourceMapTarget): { layer: L.Layer; layerKey: string; feature: any } | null {
  let result: { layer: L.Layer; layerKey: string; feature: any } | null = null
  layerMap.forEach((geoLayer, layerKey) => {
    if (result) return
    geoLayer.eachLayer((layer: any) => {
      if (result) return
      const feature = layer.feature || {}
      if (featureMatchesTarget(layerKey, feature, target)) result = { layer, layerKey, feature }
    })
  })
  return result
}

function featureMatchesTarget(layerKey: string, feature: any, target: GisSourceMapTarget) {
  const props = feature?.properties || {}
  const featureType = normalizeObjectType(firstValue(props.objectType, props.object_type, props.type, props.layerType, layerKeyToObjectType(layerKey)))
  const targetType = normalizeObjectType(target.objectType)
  const targetId = firstValue(target.objectId, target.id)
  const featureId = firstValue(props.objectId, props.object_id, props.id, props.featureId, props.sourceId)
  if (targetId && featureId && String(targetId) === String(featureId)) return true

  if (isConcreteMapObjectType(targetType) && featureType && targetType !== featureType) return false

  const targetRoute = target.routeCode
  const featureRoute = firstValue(props.routeCode, props.route_code, query.routeCode)
  if (targetRoute && featureRoute && String(targetRoute) !== String(featureRoute)) return false

  const targetStart = stakeToNumber(target.startStake)
  const targetEnd = stakeToNumber(target.endStake)
  const featureStart = stakeToNumber(firstValue(props.startStake, props.start_stake))
  const featureEnd = stakeToNumber(firstValue(props.endStake, props.end_stake))

  if (Number.isFinite(targetStart) && Number.isFinite(featureStart)) {
    const tEnd = Number.isFinite(targetEnd) ? targetEnd : targetStart
    const fEnd = Number.isFinite(featureEnd) ? featureEnd : featureStart
    return Math.max(targetStart, featureStart) <= Math.min(tEnd, fEnd) + 0.0001
  }

  if (targetRoute && !Number.isFinite(targetStart) && !Number.isFinite(targetEnd)) {
    return targetType === 'ROAD_ROUTE' ? featureType === 'ROAD_ROUTE' : Boolean(featureRoute)
  }

  return false
}

function isConcreteMapObjectType(type: string) {
  return ['ROAD_ROUTE', 'ROAD_SECTION', 'EVALUATION_UNIT', 'DISEASE', 'ASSESSMENT_RESULT'].includes(type)
}

function stakeToNumber(value: any) {
  if (value === undefined || value === null || value === '') return Number.NaN
  const text = String(value).replace(/^K/i, '').replace(/\s+/g, '')
  const normalized = text.includes('+') ? text.replace('+', '.') : text
  const num = Number(normalized)
  return Number.isFinite(num) ? num : Number.NaN
}

function zoomToLayer(layer: L.Layer) {
  const anyLayer: any = layer
  if (anyLayer.getBounds && anyLayer.getBounds().isValid()) {
    map.fitBounds(anyLayer.getBounds(), { padding: [80, 80], maxZoom: 17 })
    return
  }
  if (anyLayer.getLatLng) map.setView(anyLayer.getLatLng(), Math.max(map.getZoom(), 16))
}

function zoomInMap() {
  if (map) map.zoomIn()
}

function zoomOutMap() {
  if (map) map.zoomOut()
}

async function loadStatistics() {
  if (mapDisposed) return
  try {
    const viewport = currentViewport()
    const selectedLayers = activeLayerNames()
    const nextStatistics = await getMapStatistics({
      ...layerQuery(),
      bbox: viewport?.bbox,
      viewport,
      layers: selectedLayers,
      enabledLayers: selectedLayers
    } as any)
    if (!mapDisposed) statistics.value = nextStatistics
  } catch {
    if (!mapDisposed) statistics.value = {}
  }
}

function handleFitAll() {
  if (!map) return

  const bounds = new LatLngBounds([])
  layerMap.forEach((layer) => {
    const layerBounds = layer.getBounds()
    if (layerBounds.isValid()) bounds.extend(layerBounds)
  })

  if (bounds.isValid()) {
    map.fitBounds(bounds, {
      paddingTopLeft: [340, 120],
      paddingBottomRight: [430, 140],
      maxZoom: 15
    })
  } else {
    map.setView([26.65, 106.63], 11)
  }
}
</script>

<style scoped>
.one-map-page {
  position: relative;
  width: 100%;
  height: 100vh;
  min-height: 640px;
  overflow: hidden;
  background: #e5e7eb;
}

.map {
  position: absolute;
  inset: 0;
  z-index: 1;
}

.top-toolbar {
  position: absolute;
  top: 16px;
  left: 16px;
  z-index: 930;
  width: min(960px, calc(100vw - 592px));
  min-width: 760px;
}

.left-map-stack {
  position: absolute;
  top: 88px;
  left: 18px;
  bottom: 32px;
  z-index: 920;
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 292px;
  min-height: 0;
}

.left-map-stack :deep(.gis-left-workbench) {
  position: relative;
  top: auto;
  left: auto;
  bottom: auto;
  flex: 1 1 auto;
  width: 100%;
  min-height: 0;
  max-height: none;
}

.map-legend-fixed {
  position: relative;
  left: auto;
  bottom: auto;
  z-index: auto;
  flex: 0 0 auto;
  width: 100%;
}

.left-map-stack:has(.gis-left-workbench.collapsed) {
  width: 62px;
}

.left-map-stack:has(.gis-left-workbench.collapsed) .map-legend-fixed,
.left-map-stack:has(.gis-left-workbench.collapsed) .map-zoom-panel {
  display: none;
}

.map-zoom-panel {
  display: grid;
  grid-template-columns: 32px 1fr 32px;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 8px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.12);
}

.map-zoom-panel button {
  width: 32px;
  height: 30px;
  border: 1px solid #dbeafe;
  border-radius: 9px;
  color: #1d4ed8;
  background: #eff6ff;
  font-size: 18px;
  font-weight: 800;
  line-height: 1;
  cursor: pointer;
}

.map-zoom-panel span {
  min-width: 0;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
  text-align: center;
  white-space: nowrap;
}

:deep(.leaflet-control-attribution) {
  display: none !important;
}

:deep(.map-object-popup .leaflet-popup-content-wrapper) {
  border-radius: 14px;
  box-shadow: 0 16px 34px rgba(15, 23, 42, 0.22);
}

:deep(.map-object-popup .leaflet-popup-content) {
  min-width: 230px;
  margin: 12px;
}

:deep(.object-popup-title) {
  margin-bottom: 8px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 800;
}

:deep(.object-popup-grid) {
  display: grid;
  grid-template-columns: 64px 1fr;
  gap: 5px 8px;
}

:deep(.object-popup-grid span) {
  color: #64748b;
  font-size: 12px;
}

:deep(.object-popup-grid strong) {
  min-width: 0;
  color: #0f172a;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.object-popup-tip) {
  margin-top: 8px;
  padding-top: 7px;
  border-top: 1px solid #eef2f7;
  color: #64748b;
  font-size: 12px;
}

.region-draw-tip {
  position: absolute;
  left: 50%;
  top: 88px;
  z-index: 935;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  max-width: min(620px, calc(100vw - 720px));
  padding: 9px 14px;
  border: 1px solid rgba(37, 99, 235, 0.22);
  border-radius: 999px;
  color: #1e3a8a;
  background: rgba(239, 246, 255, 0.94);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.14);
  backdrop-filter: blur(8px);
  transform: translateX(-50%);
  pointer-events: none;
}

.region-draw-tip strong {
  font-size: 13px;
}

.region-draw-tip span,
.region-draw-tip em {
  overflow: hidden;
  font-size: 12px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.region-draw-tip em {
  color: #64748b;
  font-style: normal;
}

:deep(.leaflet-container.region-drawing) {
  cursor: crosshair;
}

.ai-float-button {
  position: absolute;
  right: 28px;
  bottom: 44px;
  z-index: 940;
  width: 56px;
  height: 56px;
  border: none;
  border-radius: 18px;
  color: #fff;
  font-weight: 800;
  font-size: 16px;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  box-shadow: 0 16px 32px rgba(37, 99, 235, 0.34);
  cursor: pointer;
}


.loading-mask {
  position: absolute;
  inset: 0;
  z-index: 960;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #2563eb;
  background: rgba(248, 250, 252, 0.52);
  backdrop-filter: blur(2px);
}

:deep(.leaflet-bottom.leaflet-right) {
  right: 18px;
  bottom: 50px;
}

.one-map-page.agent-open :deep(.leaflet-bottom.leaflet-right) {
  right: 548px;
}

.one-map-page.agent-open .ai-float-button {
  display: none;
}


@media (max-width: 1280px) {
  .top-toolbar {
    width: min(900px, calc(100vw - 560px));
    min-width: 700px;
  }

  .left-map-stack {
    top: 92px;
    bottom: 24px;
  }

  .region-draw-tip {
    max-width: min(520px, calc(100vw - 640px));
  }

  .one-map-page.agent-open :deep(.leaflet-bottom.leaflet-right) {
    right: 18px;
    bottom: 220px;
  }
}


@media (max-width: 960px) {
  .top-toolbar {
    left: 10px;
    width: calc(100vw - 20px);
    min-width: 0;
  }

  .left-map-stack {
    top: 92px;
    left: 10px;
    bottom: 24px;
    width: min(292px, calc(100vw - 20px));
  }

  .region-draw-tip {
    left: 10px;
    right: 10px;
    top: 86px;
    max-width: none;
    transform: none;
  }
}
</style>
