<template>
  <div class="one-map-page">
    <div class="top">
      <MapToolbar
        :query="query"
        @search="handleSearch"
        @reset="handleReset"
        @fit="fitAll"
      />
    </div>

    <div class="left">
      <LayerTree v-model="layers" @change="reloadLayers" />
    </div>

    <div id="map" class="map" />

    <div class="right">
      <ObjectDetailPanel :detail="selectedDetail" />
      <div class="agent-wrap">
        <AgentChatPanel :context="agentContext" />
      </div>
    </div>

    <div class="legend">
      <LegendPanel />
    </div>

    <div class="bottom">
      <MapStatisticsPanel :value="statistics" />
    </div>
  </div>
</template>

<script setup lang="ts">
import L, { GeoJSON, Map as LeafletMap } from 'leaflet'
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
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
  routeCode: '',
  year: '',
  indexCode: 'MQI',
  grade: ''
})

const layers = reactive<LayerState>({
  roadRoute: true,
  roadSection: false,
  evaluationUnit: false,
  disease: true,
  assessment: true
})

const statistics = ref<Record<string, any>>({})
const selectedDetail = ref<Record<string, any> | null>(null)

const agentContext = computed(() => ({
  query: { ...query },
  selected: selectedDetail.value
}))

let map: LeafletMap
const layerMap = new Map<string, GeoJSON>()

onMounted(async () => {
  map = L.map('map', {
    center: [26.65, 106.63],
    zoom: 9,
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
    routeCode: '',
    year: '',
    indexCode: 'MQI',
    grade: ''
  })
  await reloadLayers()
}

async function reloadLayers() {
  if (!map) return
  clearAllLayers()

  const tasks: Array<Promise<void>> = []

  if (layers.roadRoute) tasks.push(loadLayer('roadRoute', () => getRoadRoutes(query)))
  if (layers.roadSection) tasks.push(loadLayer('roadSection', () => getRoadSections(query)))
  if (layers.evaluationUnit) tasks.push(loadLayer('evaluationUnit', () => getEvaluationUnits(query)))
  if (layers.disease) tasks.push(loadLayer('disease', () => getDiseases(query)))
  if (layers.assessment) tasks.push(loadLayer('assessment', () => getAssessmentResults(query)))

  await Promise.allSettled(tasks)
  fitAll()
  await loadStatistics()
}

function clearAllLayers() {
  layerMap.forEach((layer) => {
    map.removeLayer(layer)
  })
  layerMap.clear()
}

async function loadLayer(key: string, loader: () => Promise<GeoJsonFeatureCollection>) {
  try {
    const data = await loader()
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
    ElMessage.warning(`${key} 图层加载失败：${error.message || error}`)
  }
}

async function loadObjectDetail(properties: Record<string, any>) {
  const objectType = properties.objectType
  const id = properties.id || properties.objectId
  if (!objectType || !id) return

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

function fitAll() {
  const group = L.featureGroup(Array.from(layerMap.values()))
  if (group.getLayers().length === 0) return
  try {
    map.fitBounds(group.getBounds(), {
      padding: [40, 40],
      maxZoom: 15
    })
  } catch {
    // ignore empty bounds
  }
}

function popupHtml(properties: Record<string, any>) {
  const title = properties.name || properties.routeName || properties.diseaseName || properties.objectType || '对象'
  const rows = Object.entries(properties)
    .slice(0, 8)
    .map(([key, value]) => `<div class="srmp-popup-row"><span>${key}</span><strong>${value ?? '-'}</strong></div>`)
    .join('')
  return `<div class="srmp-popup"><div class="srmp-popup-title">${title}</div>${rows}</div>`
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
  left: 300px;
  right: 390px;
  z-index: 900;
}

.left {
  position: absolute;
  top: 16px;
  left: 16px;
  width: 250px;
  z-index: 900;
}

.right {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 340px;
  z-index: 900;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.agent-wrap {
  height: 320px;
}

.legend {
  position: absolute;
  left: 16px;
  bottom: 96px;
  width: 170px;
  z-index: 900;
}

.bottom {
  position: absolute;
  left: 220px;
  right: 390px;
  bottom: 16px;
  z-index: 900;
}
</style>