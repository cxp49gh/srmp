<template>
  <div class="one-map-page">
    <div id="map" class="map" />

    <div class="top-toolbar">
      <MapToolbar
        :query="query"
        @search="handleSearch"
        @toggle-panel="panelVisible = !panelVisible"
      />
    </div>

    <MapFloatTools
      class="float-tools"
      @toggle-layer="layerDrawerVisible = !layerDrawerVisible"
      @toggle-legend="legendVisible = !legendVisible"
      @fit="handleFitAll"
    />

    <LayerDrawer
      v-model:visible="layerDrawerVisible"
      v-model:layers="layers"
      @change="reloadLayers"
    />

    <LegendPopover v-model:visible="legendVisible" />

    <ObjectDetailDrawer
      v-model:visible="detailVisible"
      :detail="selectedDetail"
      @ai-analyze="openAiForSelected"
    />

    <MapStatisticsBar
      v-model:collapsed="statisticsCollapsed"
      :value="statistics"
    />

    <AgentChatFloat
      v-model:visible="agentVisible"
      :context="agentContext"
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
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import MapToolbar from './components/MapToolbar.vue'
import {
  getAssessmentResults,
  getDiseases,
  getEvaluationUnits,
  getRoadRoutes,
  getRoadSections,
  getMapStatistics
} from '../../api/gis'
import { layerStyle } from '../../utils/leafletStyle'
import type { GeoJsonFeatureCollection } from '../../types/geojson'
import MapFloatTools from './components/MapFloatTools.vue'
import LayerDrawer, { type LayerState } from './components/LayerDrawer.vue'
import LegendPopover from './components/LegendPopover.vue'
import ObjectDetailDrawer from './components/ObjectDetailDrawer.vue'
import MapStatisticsBar from './components/MapStatisticsBar.vue'
import AgentChatFloat from './components/AgentChatFloat.vue'

const query = reactive<GisLayerQuery>({
  routeCode: 'G210',
  year: 2024
})

const panelVisible = ref(false)
const layers = reactive<LayerState>({
  roadRoute: true,
  roadSection: true,
  evaluationUnit: true,
  disease: true,
  assessment: true
})
const statistics = ref<Record<string, any>>({})
const selectedDetail = ref<Record<string, any> | null>(null)
const loading = ref(false)
const layerDrawerVisible = ref(false)
const legendVisible = ref(false)
const detailVisible = ref(false)
const agentVisible = ref(false)
const statisticsCollapsed = ref(false)

const agentContext = computed(() => ({
  query: { ...query },
  selected: selectedDetail.value
}))

let map: LeafletMap
const layerMap = new Map<string, GeoJSON>()
let selectedLayer: L.Layer | null = null

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

  await reloadLayers()
})

function handleSearch() {
  reloadLayers()
}

async function reloadLayers() {
  loading.value = true
  clearAllLayers()

  if (layers.roadRoute) {
    await loadLayer('roadRoute', getRoadRoutes)
  }
  if (layers.roadSection) {
    await loadLayer('roadSection', getRoadSections)
  }
  if (layers.evaluationUnit) {
    await loadLayer('evaluationUnit', getEvaluationUnits)
  }
  if (layers.disease) {
    await loadLayer('disease', getDiseases)
  }
  if (layers.assessment) {
    await loadLayer('assessment', getAssessmentResults)
  }

  loading.value = false

  await nextTick()
  await handleFitAll(false)
  await loadStatistics()
}

function clearAllLayers() {
  layerMap.forEach((layer) => map.removeLayer(layer))
  layerMap.clear()
  selectedLayer = null
}

async function loadLayer(key: string, loader: () => Promise<GeoJsonFeatureCollection>) {
  try {
    const data = await loader()
    const style = layerStyle(key)
    const layer = L.geoJSON(data, {
      style: (feature) => style(feature?.properties),
      onEachFeature: (feature, layer) => {
        const properties = feature.properties || {}
        layer.bindPopup(popupHtml(properties), { className: 'srmp-popup' })
        layer.on('click', async () => {
          highlightLayer(layer)
          selectedDetail.value = properties
          detailVisible.value = true
          await loadObjectDetail(properties)
        })
      }
    })
    layer.addTo(map)
    layerMap.set(key, layer)
  } catch (error: any) {
    ElMessage.error(`图层 ${layerName(key)} 加载失败: ${error.message}`)
  }
}

function highlightLayer(layer: L.Layer) {
  if (selectedLayer && 'setStyle' in selectedLayer) {
    ;(selectedLayer as any).setStyle({ weight: 4, opacity: 0.85 })
  }
  selectedLayer = layer
  if ('setStyle' in layer) {
    ;(layer as any).setStyle({ weight: 7, opacity: 1 })
  }
}

async function loadObjectDetail(properties: Record<string, any>) {
  const objectType = properties.objectType
  const id = properties.id || properties.objectId
  if (!objectType || !id) return

  if (objectType === 'disease') {
    const data = await getDiseases()
    const feature = data.features.find((f) => f.properties?.id === id || f.properties?.objectId === id)
    if (feature) {
      selectedDetail.value = feature.properties
    }
  }
}

async function loadStatistics() {
  try {
    statistics.value = await getStatistics({
      routeCode: query.routeCode,
      year: query.year
    })
  } catch (error: any) {
    console.warn('统计接口不可用:', error.message)
  }
}

async function handleFitAll(showMessage = true) {
  if (!map) return

  const bounds = collectVisibleBounds()
  if (!bounds || !bounds.isValid()) {
    if (showMessage) ElMessage.warning('当前无可见图层，无法执行全图')
    return
  }

  map.fitBounds(bounds, {
    paddingTopLeft: [80, 96],
    paddingBottomRight: [420, 120],
    maxZoom: 15,
    animate: true
  })
}

function collectVisibleBounds(): LatLngBounds | null {
  let bounds: LatLngBounds | null = null

  layerMap.forEach((layer) => {
    const layerBounds = layer.getBounds()
    if (!layerBounds || !layerBounds.isValid()) return
    bounds = bounds ? bounds.extend(layerBounds) : layerBounds
  })

  return bounds
}

function openAiForSelected() {
  agentVisible.value = true
}

function popupHtml(properties: Record<string, any>) {
  const title = properties.name || properties.routeName || properties.diseaseName || properties.objectType || '对象'
  const rows = Object.entries(properties)
    .slice(0, 6)
    .map(([key, value]) => `<div class="srmp-popup-row"><span>${key}</span><strong>${value ?? '-'}</strong></div>`)
    .join('')
  return `<div class="srmp-popup"><div class="srmp-popup-title">${title}</div>${rows}</div>`
}

function layerName(key: string) {
  const map: Record<string, string> = {
    roadRoute: '路线',
    roadSection: '路段',
    evaluationUnit: '评定单元',
    disease: '病害',
    assessment: '评定专题'
  }
  return map[key] || key
}
</script>

<style scoped>
.one-map-page {
  position: relative;
  height: 100%;
}

#map {
  position: absolute;
  inset: 0;
  height: 100%;
}

.top-toolbar {
  position: absolute;
  top: 14px;
  left: 50%;
  z-index: 900;
  width: min(760px, calc(100vw - 260px));
  transform: translateX(-50%);
}

.float-tools {
  position: absolute;
  top: 84px;
  left: 16px;
  z-index: 910;
}

.ai-float-button {
  position: absolute;
  right: 24px;
  bottom: 24px;
  z-index: 930;
  width: 52px;
  height: 52px;
  border: none;
  border-radius: 50%;
  color: #fff;
  font-weight: 800;
  font-size: 16px;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  box-shadow: 0 10px 28px rgba(37, 99, 235, 0.32);
  cursor: pointer;
}

.loading-mask {
  position: absolute;
  left: 50%;
  top: 72px;
  transform: translateX(-50%);
  z-index: 950;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.12);
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}
</style>
