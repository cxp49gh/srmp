<template>
  <div class="one-map-page">
    <div id="map" class="map" />
    <div class="top-toolbar"><MapToolbar :query="query" @search="handleSearch" @reset="handleReset" @fit="handleFitAll" /></div>
    <MapFloatTools class="float-tools" @toggle-layer="layerDrawerVisible = !layerDrawerVisible" @toggle-legend="legendVisible = !legendVisible" @fit="handleFitAll" />
    <LayerDrawer v-model:visible="layerDrawerVisible" v-model:layers="layers" @change="reloadLayers" />
    <LegendPopover v-model:visible="legendVisible" />
    <ObjectDetailDrawer v-model:visible="detailVisible" :detail="selectedDetail" @ai-analyze="openAiForSelected" />
    <MapStatisticsBar v-model:collapsed="statisticsCollapsed" :value="statistics" />
    <AgentChatFloat v-model:visible="agentVisible" :context="agentContext" :map-object="selectedMapObject" :auto-question="pendingAiQuestion" @auto-question-consumed="pendingAiQuestion = ''" />
    <button v-if="!agentVisible" class="ai-float-button" type="button" title="AI 助手" @click="agentVisible = true">AI</button>
    <div v-if="loading" class="loading-mask"><el-icon class="is-loading"><Loading /></el-icon><span>图层加载中...</span></div>
  </div>
</template>

<script setup lang="ts">
import L, { GeoJSON, LatLngBounds, Map as LeafletMap } from 'leaflet'
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import MapToolbar from './components/MapToolbar.vue'
import { getAssessmentResults, getDiseases, getEvaluationUnits, getMapStatistics, getObjectDetail, getRoadRoutes, getRoadSections, type GisLayerQuery } from '../../api/gis'
import { layerStyle } from '../../utils/leafletStyle'
import type { GeoJsonFeatureCollection } from '../../types/geojson'
import MapFloatTools from './components/MapFloatTools.vue'
import LayerDrawer, { type LayerState } from './components/LayerDrawer.vue'
import LegendPopover from './components/LegendPopover.vue'
import ObjectDetailDrawer from './components/ObjectDetailDrawer.vue'
import MapStatisticsBar from './components/MapStatisticsBar.vue'
import AgentChatFloat from './components/AgentChatFloat.vue'

const query = reactive<GisLayerQuery>({ routeCode: 'G210', year: '2026', indexCode: 'MQI', grade: '' })
const layers = reactive<LayerState>({ roadRoute: true, roadSection: false, evaluationUnit: false, assessmentResult: true, disease: true })
const statistics = ref<Record<string, any>>({})
const loading = ref(false)
const layerDrawerVisible = ref(false)
const legendVisible = ref(false)
const detailVisible = ref(false)
const statisticsCollapsed = ref(false)
const agentVisible = ref(false)
const pendingAiQuestion = ref('')
const selectedDetail = ref<Record<string, any> | null>(null)
const selectedFeatureProperties = ref<Record<string, any> | null>(null)
let map: LeafletMap | null = null
let selectedLayer: L.Layer | null = null
const layerMap: Record<string, GeoJSON> = {}
function normalizeMapObjectType(rawType: any) { const type = String(rawType || '').toUpperCase().replace('-', '_'); if (type === 'ASSESSMENT') return 'ASSESSMENT_RESULT'; if (type === 'DISEASE_RECORD') return 'DISEASE'; return type }
const selectedMapObject = computed(() => { const raw: any = selectedFeatureProperties.value || {}; const detail: any = selectedDetail.value || {}; const detailProps: any = detail.properties || detail; const props: any = { ...raw, ...detailProps }; if (!props || Object.keys(props).length === 0) return null; const objectType = normalizeMapObjectType(props.objectType || props.object_type || props.type || props.layerType); const objectId = props.objectId || props.object_id || props.id || raw.objectId || raw.id; return { objectType, objectId, id: objectId, routeCode: props.routeCode || props.route_code || raw.routeCode || raw.route_code || query.routeCode, year: Number(props.year || query.year || 2026), startStake: props.startStake ?? props.start_stake ?? raw.startStake ?? raw.start_stake, endStake: props.endStake ?? props.end_stake ?? raw.endStake ?? raw.end_stake, routeName: props.routeName || props.route_name || raw.routeName || raw.route_name, sectionName: props.sectionName || props.section_name || raw.sectionName || raw.section_name, sectionCode: props.sectionCode || props.section_code || raw.sectionCode || raw.section_code, unitCode: props.unitCode || props.unit_code || raw.unitCode || raw.unit_code, diseaseName: props.diseaseName || props.disease_name || raw.diseaseName || raw.disease_name, diseaseType: props.diseaseType || props.disease_type || raw.diseaseType || raw.disease_type, severity: props.severity || raw.severity, quantity: props.quantity ?? raw.quantity, measureUnit: props.measureUnit || props.measure_unit || raw.measureUnit || raw.measure_unit, mqi: props.mqi ?? raw.mqi, pqi: props.pqi ?? raw.pqi, pci: props.pci ?? raw.pci, grade: props.grade || raw.grade, raw: props } })
const agentContext = computed(() => ({ query: { ...query }, selected: selectedDetail.value, mapObject: selectedMapObject.value, selectedMapObject: selectedMapObject.value }))
onMounted(async () => { map = L.map('map', { zoomControl: false }).setView([26.6, 106.7], 8); L.control.zoom({ position: 'bottomright' }).addTo(map); L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19, attribution: '&copy; OpenStreetMap contributors' }).addTo(map); map.on('moveend', loadStatistics); await nextTick(); map.invalidateSize(true); await reloadLayers() })
async function handleSearch(next: GisLayerQuery) { Object.assign(query, next); await reloadLayers() }
async function handleReset() { Object.assign(query, { routeCode: 'G210', year: '2026', indexCode: 'MQI', grade: '', diseaseType: '', severity: '' }); await reloadLayers() }
async function reloadLayers() { if (!map) return; loading.value = true; clearAllLayers(); const tasks: Array<Promise<void>> = []; if (layers.roadRoute) tasks.push(loadLayer('roadRoute', () => getRoadRoutes(query))); if (layers.roadSection) tasks.push(loadLayer('roadSection', () => getRoadSections(query))); if (layers.evaluationUnit) tasks.push(loadLayer('evaluationUnit', () => getEvaluationUnits(query))); if (layers.assessmentResult) tasks.push(loadLayer('assessmentResult', () => getAssessmentResults(query))); if (layers.disease) tasks.push(loadLayer('disease', () => getDiseases(query))); await Promise.allSettled(tasks); await loadStatistics(); handleFitAll(false); loading.value = false }
function clearAllLayers() { Object.values(layerMap).forEach((layer) => layer.remove()); Object.keys(layerMap).forEach((key) => delete layerMap[key]) }
async function loadLayer(key: string, loader: () => Promise<GeoJsonFeatureCollection>) { if (!map) return; try { const data = await loader(); const featureCount = data?.features?.length || 0; if (featureCount === 0) { ElMessage.info(`${layerName(key)}：当前条件无数据`); return } const geoLayer = L.geoJSON(data as any, { style: (feature) => layerStyle(feature?.properties || {}), pointToLayer: (feature, latlng) => L.circleMarker(latlng, { radius: 6, ...layerStyle(feature?.properties || {}) }), onEachFeature: (feature, layer) => { const properties = feature?.properties || {}; layer.bindPopup(popupHtml(properties)); layer.on('click', async () => { selectedFeatureProperties.value = properties; selectedDetail.value = properties; detailVisible.value = true; highlightLayer(layer); await loadObjectDetail(properties) }) } }).addTo(map); layerMap[key] = geoLayer } catch (error: any) { ElMessage.error(`${layerName(key)}加载失败：${error.message || error}`) } }
function highlightLayer(layer: L.Layer) { if (selectedLayer && 'setStyle' in selectedLayer) (selectedLayer as any).setStyle({ weight: 4, opacity: 0.85 }); selectedLayer = layer; if ('setStyle' in layer) (layer as any).setStyle({ weight: 7, opacity: 1 }) }
async function loadObjectDetail(properties: Record<string, any>) { const objectType = properties.objectType || properties.object_type || properties.type; const id = properties.id || properties.objectId || properties.object_id; if (!objectType || !id) { selectedDetail.value = properties; return } try { const detail = await getObjectDetail({ objectType, id }); selectedDetail.value = { ...properties, ...(detail || {}), objectType, objectId: id, id } } catch { selectedDetail.value = { ...properties, objectType, objectId: id, id } } }
async function loadStatistics() { if (!map) return; const bounds = map.getBounds(); const bbox = [bounds.getWest(), bounds.getSouth(), bounds.getEast(), bounds.getNorth()]; try { statistics.value = await getMapStatistics({ ...query, bbox }) } catch { statistics.value = {} } }
async function handleFitAll(showMessage = true) { if (!map) return; await nextTick(); map.invalidateSize(false); const bounds = collectVisibleBounds(); if (!bounds || !bounds.isValid()) { if (showMessage) ElMessage.warning('当前没有可定位的图层，请先勾选图层或查询有空间数据的路线'); return } map.fitBounds(bounds, { paddingTopLeft: [80, 96], paddingBottomRight: [420, 120], maxZoom: 16 }) }
function collectVisibleBounds() { const bounds = new LatLngBounds([] as any); Object.values(layerMap).forEach((layer: any) => { if (layer?.getBounds) { const b = layer.getBounds(); if (b?.isValid?.()) bounds.extend(b) } }); return bounds }
function openAiForSelected() { if (!selectedDetail.value) { ElMessage.warning('请先在地图上选择一个对象'); return } agentVisible.value = true; pendingAiQuestion.value = '分析当前地图选中对象，说明主要问题、成因判断，并给出养护处置建议' }
function popupHtml(properties: Record<string, any>) { const title = properties.name || properties.routeName || properties.route_name || properties.diseaseName || properties.disease_name || properties.objectType || properties.object_type || '对象'; const rows = Object.entries(properties).slice(0, 6).map(([key, value]) => `<div class="srmp-popup-row"><span>${key}</span><strong>${value ?? '-'}</strong></div>`).join(''); return `<div class="srmp-popup"><div class="srmp-popup-title">${title}</div>${rows}</div>` }
function layerName(key: string) { const names: Record<string, string> = { roadRoute: '路线', roadSection: '路段', evaluationUnit: '评定单元', assessmentResult: '评定结果', disease: '病害' }; return names[key] || key }
</script>

<style scoped>
.one-map-page { position: relative; width: 100%; height: calc(100vh - 56px); overflow: hidden; background: #e2e8f0; }
.map { width: 100%; height: 100%; }
.top-toolbar { position: absolute; top: 14px; left: 14px; right: 96px; z-index: 500; }
.float-tools { position: absolute; right: 16px; top: 92px; z-index: 520; }
.ai-float-button { position: absolute; right: 20px; bottom: 28px; z-index: 700; width: 52px; height: 52px; border-radius: 50%; border: 0; background: #2563eb; color: white; font-weight: 800; box-shadow: 0 12px 28px rgba(37, 99, 235, .35); cursor: pointer; }
.loading-mask { position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%); z-index: 1000; display: flex; align-items: center; gap: 8px; border-radius: 999px; padding: 10px 16px; background: rgba(15, 23, 42, .82); color: white; }
:deep(.srmp-popup) { min-width: 220px; }
:deep(.srmp-popup-title) { font-weight: 700; margin-bottom: 6px; }
:deep(.srmp-popup-row) { display: flex; justify-content: space-between; gap: 12px; font-size: 12px; border-top: 1px solid #e5e7eb; padding: 3px 0; }
:deep(.srmp-popup-row span) { color: #64748b; }
:deep(.srmp-popup-row strong) { color: #0f172a; max-width: 140px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
