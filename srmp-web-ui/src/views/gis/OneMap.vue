<template>
  <div class="one-map-page">
    <div id="map" class="map" />
    <div class="top"><MapToolbar :query="query" @search="search" @reset="reset" @fit="fitAll" /></div>
    <div class="left"><LayerTree v-model="layers" @change="reloadLayers" /></div>
    <div class="right"><ObjectDetailPanel :detail="selectedDetail" /><AgentChatPanel class="agent" :context="agentContext" /></div>
    <div class="legend"><LegendPanel /></div>
    <div class="bottom"><MapStatisticsPanel :value="statistics" /></div>
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
import { getAssessmentResults, getDiseases, getEvaluationUnits, getMapStatistics, getObjectDetail, getRoadRoutes, getRoadSections, type GisLayerQuery } from '../../api/gis'
import { layerStyle } from '../../utils/leafletStyle'
import type { GeoJsonFeatureCollection } from '../../types/geojson'
const query = reactive<GisLayerQuery>({ routeCode: 'G210', year: '2026', indexCode: 'MQI' })
const layers = reactive<LayerState>({ roadRoute: true, roadSection: true, evaluationUnit: true, disease: true, assessment: true })
const statistics = ref<Record<string, any>>({}); const selectedDetail = ref<Record<string, any> | null>(null)
const agentContext = computed(() => ({ ...query, selected: selectedDetail.value }))
let map: LeafletMap; const layerMap = new Map<string, GeoJSON>()
onMounted(async()=>{ map=L.map('map',{center:[26.68,106.67],zoom:12,preferCanvas:true}); L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'&copy; OpenStreetMap'}).addTo(map); map.on('moveend', loadStatistics); await reloadLayers() })
async function search(v:GisLayerQuery){ Object.assign(query,v); await reloadLayers() }
async function reset(){ Object.assign(query,{routeCode:'G210',year:'2026',indexCode:'MQI',grade:''}); await reloadLayers() }
async function reloadLayers(){ if(!map) return; clearLayers(); const jobs:Promise<void>[]=[]; if(layers.roadRoute) jobs.push(loadLayer('roadRoute',()=>getRoadRoutes(query))); if(layers.roadSection) jobs.push(loadLayer('roadSection',()=>getRoadSections(query))); if(layers.evaluationUnit) jobs.push(loadLayer('evaluationUnit',()=>getEvaluationUnits(query))); if(layers.disease) jobs.push(loadLayer('disease',()=>getDiseases(query))); if(layers.assessment) jobs.push(loadLayer('assessment',()=>getAssessmentResults(query))); await Promise.allSettled(jobs); fitAll(); await loadStatistics() }
function clearLayers(){ layerMap.forEach(l=>map.removeLayer(l)); layerMap.clear() }
async function loadLayer(key:string, loader:()=>Promise<GeoJsonFeatureCollection>){ try{ const data=await loader(); const geo=L.geoJSON(data as any,{style:f=>layerStyle(f?.properties||{}),pointToLayer:(f,latlng)=>L.circleMarker(latlng,{radius:6,...layerStyle(f?.properties||{})}),onEachFeature:(feature,layer)=>{ const p=feature.properties||{}; layer.bindPopup(`<b>${p.routeName||p.sectionName||p.unitCode||p.diseaseName||p.objectType||'对象'}</b>`); layer.on('click',async()=>{selectedDetail.value=p; await loadDetail(p)}) }}).addTo(map); layerMap.set(key,geo) }catch(e:any){ ElMessage.warning(`${key} 加载失败：${e.message||e}`) } }
async function loadDetail(p:Record<string,any>){ const objectType=p.objectType; const id=p.id||p.objectId; if(!objectType||!id) return; try{ selectedDetail.value=await getObjectDetail({objectType,id}) }catch{ selectedDetail.value=p } }
async function loadStatistics(){ try{ statistics.value=await getMapStatistics(query) }catch{ statistics.value={} } }
function fitAll(){ const group=L.featureGroup(Array.from(layerMap.values())); if(group.getLayers().length){ try{ map.fitBounds(group.getBounds(),{padding:[40,40],maxZoom:15}) }catch{} } }
</script>
<style scoped>.one-map-page{position:relative;width:100%;height:100%;background:#e2e8f0}.map{width:100%;height:100%}.top{position:absolute;top:16px;left:285px;right:380px;z-index:900}.left{position:absolute;top:16px;left:16px;width:240px;z-index:900;background:rgba(255,255,255,.96);border-radius:8px;box-shadow:0 4px 16px rgba(15,23,42,.12)}.right{position:absolute;top:16px;right:16px;width:340px;z-index:900;display:flex;flex-direction:column;gap:12px}.right>div:first-child{background:rgba(255,255,255,.96);border-radius:8px;box-shadow:0 4px 16px rgba(15,23,42,.12)}.legend{position:absolute;left:16px;bottom:96px;width:160px;z-index:900}.bottom{position:absolute;left:220px;right:380px;bottom:16px;z-index:900}</style>
