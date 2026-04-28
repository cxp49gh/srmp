<template>
  <div class="one-map-page">
    <div id="map" class="map"></div>

    <div class="top-toolbar">
      <MapToolbar
        :query="query"
        @search="handleSearch"
        @reset="handleReset"
        @fit="handleFitAll"
      />
    </div>

    <MapFloatTools
      class="float-tools"
      @toggle-layer="layerDrawerVisible = !layerDrawerVisible"
      @fit="handleFitAll"
    />

    <LayerDrawer
      v-model:visible="layerDrawerVisible"
      v-model:layers="layers"
      @change="reloadLayers"
    />

    <LegendPanel class="fixed-legend" />

    <ObjectDetailDrawer
      v-model:visible="detailVisible"
      :detail="selectedDetail"
      @ai-analyze="openAiForSelected"
    />

    <MapStatisticsBar
      v-model:collapsed="statisticsCollapsed"
      :value="statistics"
      :class="{ 'with-agent': agentVisible }"
    />

    <AgentChatFloat
      v-model:visible="agentVisible"
      :context="agentContext"
      :map-object="selectedMapObject"
      :auto-question="pendingAiQuestion"
      @auto-question-consumed="pendingAiQuestion = ''"
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

    <div v-if="loading" class="loading-mask">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>图层加载中...</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import L, { GeoJSON, LatLngBounds, Map as LeafletMap } from 'leaflet'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import MapToolbar from './components/MapToolbar.vue'
import {
  getAssessmentResults,
  getDiseases,
  getEvaluationUnits,
  getMapStatistics,
  getObjectDetail,
  getRoadRoutes,
  getRoadSections,
  type GisLayerQuery
} from '../../api/gis'
import { layerStyle } from '../../utils/leafletStyle'
import type { GeoJsonFeatureCollection } from '../../types/geojson'
import MapFloatTools from './components/MapFloatTools.vue'
import LayerDrawer, { type LayerState } from './components/LayerDrawer.vue'
import LegendPanel from './components/LegendPanel.vue'
import ObjectDetailDrawer from './components/ObjectDetailDrawer.vue'
import MapStatisticsBar from './components/MapStatisticsBar.vue'
import AgentChatFloat from './components/AgentChatFloat.vue'

const query = reactive<GisLayerQuery>({
  routeCode: 'G210',
  year: '2026',
  indexCode: 'MQI',
  grade: ''
})

const layers = reactive<LayerState>({
  roadRoute: true,
  roadSection: true,
  evaluationUnit: false,
  disease: true,
  assessment: true
})

const statistics = ref<Record<string, any>>({})
const selectedDetail = ref<Record<string, any> | null>(null)
const selectedFeatureProperties = ref<Record<string, any> | null>(null)
const loading = ref(false)
const layerDrawerVisible = ref(true)
const detailVisible = ref(false)
const agentVisible = ref(false)
const pendingAiQuestion = ref('')
const statisticsCollapsed = ref(false)

let map: LeafletMap
const layerMap = new Map<string, GeoJSON>()
let selectedLayer: L.Layer | null = null

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
    raw: props
  }
})

const agentContext = computed(() => ({
  query: { ...query },
  selected: selectedDetail.value,
  mapObject: selectedMapObject.value,
  selectedMapObject: selectedMapObject.value
}))

onMounted(async () => {
  map = L.map('map', {
    center: [26.65, 106.63],
    zoom: 11,
    preferCanvas: true,
    zoomControl: false
  })

  L.control.zoom({ position: 'bottomright' }).addTo(map)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap contributors'
  }).addTo(map)

  map.on('moveend', loadStatistics)

  await nextTick()
  map.invalidateSize(true)
  await reloadLayers()
  await loadStatistics()
  handleFitAll()
})

function layerQuery(): GisLayerQuery {
  return { ...query }
}

async function handleSearch() {
  await reloadLayers()
  await loadStatistics()
  handleFitAll()
}

async function handleReset() {
  query.routeCode = 'G210'
  query.year = '2026'
  query.indexCode = 'MQI'
  query.grade = ''
  await handleSearch()
}

async function reloadLayers() {
  if (!map) return
  loading.value = true
  try {
    clearLayers()

    const tasks: Promise<void>[] = []
    const params = layerQuery()

    if (layers.roadRoute) tasks.push(loadLayer('roadRoute', () => getRoadRoutes(params)))
    if (layers.roadSection) tasks.push(loadLayer('roadSection', () => getRoadSections(params)))
    if (layers.evaluationUnit) tasks.push(loadLayer('evaluationUnit', () => getEvaluationUnits(params)))
    if (layers.disease) tasks.push(loadLayer('disease', () => getDiseases(params)))
    if (layers.assessment || layers.assessmentResult) {
      tasks.push(loadLayer('assessment', () => getAssessmentResults(params)))
    }

    await Promise.all(tasks)
  } catch (error: any) {
    ElMessage.error(error?.message || '图层加载失败')
  } finally {
    loading.value = false
  }
}

function clearLayers() {
  layerMap.forEach((layer) => {
    map.removeLayer(layer)
  })
  layerMap.clear()
  selectedLayer = null
}

async function loadLayer(layerKey: string, loader: () => Promise<GeoJsonFeatureCollection>) {
  const collection: any = await loader()
  if (!collection || !collection.features || collection.features.length === 0) return

  const geoLayer = L.geoJSON(collection as any, {
    style: (feature: any) => layerStyle(feature?.properties || feature),
    pointToLayer: (feature, latlng) => {
      const style = layerStyle(feature?.properties || feature) as any
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
  const properties = {
    ...(feature?.properties || {}),
    layerKey,
    objectType: feature?.properties?.objectType || feature?.properties?.object_type || layerKeyToObjectType(layerKey)
  }

  selectedFeatureProperties.value = properties
  selectedDetail.value = properties
  detailVisible.value = true
  highlightLayer(layer)

  await loadObjectDetail(properties)
}

function highlightLayer(layer: L.Layer) {
  if (selectedLayer && (selectedLayer as any).setStyle) {
    const feature = (selectedLayer as any).feature
    ;(selectedLayer as any).setStyle(layerStyle(feature?.properties || feature))
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
  const objectType = normalizeObjectType(
    firstValue(properties.objectType, properties.object_type, properties.type, properties.layerType)
  )
  const id = firstValue(properties.objectId, properties.object_id, properties.id)

  if (!objectType || !id) {
    selectedDetail.value = { ...properties, objectType, objectId: id, id }
    return
  }

  try {
    const detail = await getObjectDetail({ objectType, id })
    selectedDetail.value = {
      ...properties,
      ...(detail || {}),
      objectType,
      objectId: id,
      id
    }
  } catch {
    selectedDetail.value = {
      ...properties,
      objectType,
      objectId: id,
      id
    }
  }
}

function openAiForSelected() {
  if (!selectedDetail.value) {
    ElMessage.warning('请先在地图上选择一个对象')
    return
  }
  agentVisible.value = true
  pendingAiQuestion.value = '分析当前地图选中对象，说明主要问题、成因判断，并给出养护处置建议'
}

async function loadStatistics() {
  try {
    statistics.value = await getMapStatistics(layerQuery())
  } catch {
    statistics.value = {}
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
      paddingTopLeft: [320, 110],
      paddingBottomRight: agentVisible.value ? [470, 120] : [120, 120],
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
  right: 104px;
  z-index: 930;
}

.float-tools {
  position: absolute;
  top: 156px;
  right: 18px;
  z-index: 935;
}

.fixed-legend {
  position: absolute;
  bottom: 16px;
  left: 18px;
  z-index: 920;
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
  bottom: 36px;
}

:deep(.with-agent.statistics-bar) {
  max-width: min(640px, calc(100vw - 620px));
}

@media (max-width: 1280px) {
  .top-toolbar {
    right: 76px;
  }

  :deep(.with-agent.statistics-bar) {
    display: none;
  }
}

@media (max-width: 960px) {
  .top-toolbar {
    left: 10px;
    right: 10px;
  }

  .float-tools {
    top: 122px;
    right: 12px;
  }
}
</style>
