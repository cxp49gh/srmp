<template>
  <div class="one-map-page">
    <div class="top">
      <MapToolbar
        :query="query"
        @search="handleSearch"
        @reset="handleReset"
        @fit="handleFitAll"
      />
    </div>

    <div id="map" class="map" />

    <div class="left-panel srmp-card">
      <div class="left-panel__scroll">
        <section class="panel-section">
          <LayerTree v-model="layers" @change="reloadLayers" />
        </section>

        <section class="panel-section">
          <ObjectDetailPanel :detail="selectedDetail" />
        </section>

        <section class="panel-section">
          <LegendPanel />
        </section>
      </div>
    </div>

    <div v-if="loading" class="loading-mask">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>图层加载中...</span>
    </div>

    <div class="right-panel">
      <AgentChatPanel :context="agentContext" />
    </div>

    <div class="bottom">
      <MapStatisticsPanel :value="statistics" />
    </div>
  </div>
</template>

<script setup lang="ts">
import L, { GeoJSON, LatLngBounds, Map as LeafletMap } from 'leaflet'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import MapToolbar from './components/MapToolbar.vue'
import LayerTree, { type LayerState } from './components/LayerTree.vue'
import ObjectDetailPanel from './components/ObjectDetailPanel.vue'
import MapStatisticsPanel from './components/MapStatisticsPanel.vue'
import AgentChatPanel from './components/AgentChatPanel.vue'
import LegendPanel from './components/LegendPanel.vue'
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
const loading = ref(false)

const agentContext = computed(() => ({
  query: { ...query },
  selected: selectedDetail.value
}))

let map: LeafletMap
const layerMap = new Map<string, GeoJSON>()

onMounted(async () => {
  map = L.map('map', {
    center: [26.65, 106.63],
    zoom: 11,
    preferCanvas: true
  })

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap contributors'
  }).addTo(map)

  map.on('moveend', loadStatistics)
  await reloadLayers()
})

async function handleSearch(next: GisLayerQuery) {
  Object.assign(query, next)
  await reloadLayers()
}

async function handleReset() {
  Object.assign(query, {
    routeCode: 'G210',
    year: '2026',
    indexCode: 'MQI',
    grade: '',
    diseaseType: '',
    severity: ''
  })
  await reloadLayers()
}

async function reloadLayers() {
  if (!map) return
  loading.value = true
  clearAllLayers()

  const tasks: Array<Promise<void>> = []

  if (layers.roadRoute) tasks.push(loadLayer('roadRoute', () => getRoadRoutes(query)))
  if (layers.roadSection) tasks.push(loadLayer('roadSection', () => getRoadSections(query)))
  if (layers.evaluationUnit) tasks.push(loadLayer('evaluationUnit', () => getEvaluationUnits(query)))
  if (layers.disease) tasks.push(loadLayer('disease', () => getDiseases(query)))
  if (layers.assessment) tasks.push(loadLayer('assessment', () => getAssessmentResults(query)))

  await Promise.allSettled(tasks)
  loading.value = false

  await nextTick()
  handleFitAll(false)
  await loadStatistics()
}

function clearAllLayers() {
  layerMap.forEach((layer) => map.removeLayer(layer))
  layerMap.clear()
}

async function loadLayer(key: string, loader: () => Promise<GeoJsonFeatureCollection>) {
  try {
    const data = await loader()
    const featureCount = data?.features?.length || 0
    if (featureCount === 0) {
      ElMessage.info(`${layerName(key)}：当前条件无数据`)
      return
    }

    const geoLayer = L.geoJSON(data as any, {
      style: (feature) => layerStyle(feature?.properties || {}),
      pointToLayer: (feature, latlng) => {
        return L.circleMarker(latlng, {
          radius: 6,
          ...layerStyle(feature?.properties || {})
        })
      },
      onEachFeature: (feature, layer) => {
        const properties = feature.properties || {}
        layer.bindPopup(popupHtml(properties), {
          className: 'srmp-popup'
        })
        layer.on('click', async () => {
          selectedDetail.value = properties
          await loadObjectDetail(properties)
        })
      }
    })

    geoLayer.addTo(map)
    layerMap.set(key, geoLayer)
  } catch (error: any) {
    ElMessage.error(`${layerName(key)}加载失败：${error.message || error}`)
  }
}

async function loadObjectDetail(properties: Record<string, any>) {
  const objectType = properties.objectType
  const id = properties.id || properties.objectId
  if (!objectType || !id) {
    selectedDetail.value = properties
    return
  }

  try {
    selectedDetail.value = await getObjectDetail({ objectType, id })
  } catch {
    selectedDetail.value = properties
  }
}

async function loadStatistics() {
  if (!map) return
  const bounds = map.getBounds()
  const bbox = [
    bounds.getWest(),
    bounds.getSouth(),
    bounds.getEast(),
    bounds.getNorth()
  ]

  try {
    statistics.value = await getMapStatistics({ ...query, bbox })
  } catch {
    statistics.value = {}
  }
}

/**
 * 工具栏"全图"按钮修复：
 * 1. 先 invalidateSize，避免左右面板调整后 Leaflet 尺寸缓存不正确；
 * 2. 汇总所有可见 GeoJSON 图层边界；
 * 3. 只在 bounds 有效时执行 fitBounds；
 * 4. 没有图层时给出明确提示。
 */
async function handleFitAll(showMessage = true) {
  if (!map) return

  await nextTick()
  map.invalidateSize(false)

  const bounds = collectVisibleBounds()
  if (!bounds || !bounds.isValid()) {
    if (showMessage) {
      ElMessage.warning('当前没有可定位的图层，请先勾选图层或查询有空间数据的路线')
    }
    return
  }

  map.fitBounds(bounds, {
    paddingTopLeft: [390, 90],
    paddingBottomRight: [390, 120],
    maxZoom: 15,
    animate: true
  })
}

function collectVisibleBounds(): LatLngBounds | null {
  let bounds: LatLngBounds | null = null

  layerMap.forEach((layer) => {
    const layerBounds = layer.getBounds()
    if (!layerBounds || !layerBounds.isValid()) {
      return
    }
    bounds = bounds ? bounds.extend(layerBounds) : layerBounds
  })

  return bounds
}

function popupHtml(properties: Record<string, any>) {
  const title = properties.name || properties.routeName || properties.diseaseName || properties.objectType || '对象'
  const rows = Object.entries(properties)
    .slice(0, 8)
    .map(([key, value]) => `<div class="srmp-popup-row"><span>${key}</span><strong>${value ?? '-'}</strong></div>`)
    .join('')
  return `<div class="srmp-popup"><div class="srmp-popup-title">${title}</div>${rows}</div>`
}

function layerName(key: string) {
  const names: Record<string, string> = {
    roadRoute: '路线图层',
    roadSection: '路段图层',
    evaluationUnit: '评定单元图层',
    disease: '病害图层',
    assessment: '评定专题图层'
  }
  return names[key] || key
}
</script>

<style scoped>
.one-map-page {
  position: relative;
  width: 100%;
  height: 100%;
  background: #e2e8f0;
}

.map {
  width: 100%;
  height: 100%;
}

.top {
  position: absolute;
  top: 16px;
  left: 386px;
  right: 386px;
  z-index: 900;
}

.left-panel {
  position: absolute;
  top: 16px;
  left: 16px;
  width: 340px;
  bottom: 16px;
  z-index: 900;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.left-panel__scroll {
  flex: 1;
  overflow: auto;
  padding: 12px;
}

.panel-section + .panel-section {
  margin-top: 12px;
}

:deep(.layer-tree),
:deep(.object-detail),
:deep(.legend-panel) {
  box-shadow: none;
  background: transparent;
  border-radius: 0;
  padding: 0;
}

:deep(.object-detail) {
  max-height: 330px;
}

.right-panel {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 340px;
  bottom: 16px;
  z-index: 900;
}

.bottom {
  position: absolute;
  left: 386px;
  right: 386px;
  bottom: 16px;
  z-index: 900;
}

.loading-mask {
  position: absolute;
  left: 50%;
  top: 90px;
  transform: translateX(-50%);
  z-index: 950;
  padding: 10px 16px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.12);
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>