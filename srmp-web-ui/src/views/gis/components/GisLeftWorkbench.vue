<template>
  <aside class="gis-left-workbench srmp-card" :class="{ collapsed }">
    <div class="workbench-header">
      <div>
        <strong>{{ collapsed ? '一张图' : '一张图资源' }}</strong>
        <p v-if="!collapsed">图层、图例、统计、区域工具统一入口</p>
      </div>
      <button type="button" class="collapse-btn" @click="collapsed = !collapsed">
        {{ collapsed ? '›' : '‹' }}
      </button>
    </div>

    <el-tabs v-if="!collapsed" v-model="activeTab" class="workbench-tabs">
      <el-tab-pane label="图层" name="layers">
        <div class="toolbar-row">
          <el-button size="small" :loading="loading" @click="$emit('reload')">刷新图层</el-button>
          <el-button size="small" plain @click="$emit('fit')">全图</el-button>
        </div>

        <div class="layer-group">
          <div class="group-title">道路资产</div>
          <label v-for="item in assetLayerItems" :key="item.key" class="layer-item">
            <el-checkbox v-model="localLayers[item.key]" @change="emitLayerChange">{{ item.label }}</el-checkbox>
            <span class="layer-count">{{ layerCount(item.key) }}</span>
          </label>
        </div>

        <div class="layer-group">
          <div class="group-title">业务图层</div>
          <label v-for="item in businessLayerItems" :key="item.key" class="layer-item">
            <el-checkbox v-model="localLayers[item.key]" @change="emitLayerChange">{{ item.label }}</el-checkbox>
            <span class="layer-count">{{ layerCount(item.key) }}</span>
          </label>
        </div>

        <div class="layer-tip">
          已启用 {{ enabledLayerCount }} 个图层；关闭不必要图层可减少地图拥挤。
        </div>
      </el-tab-pane>

      <el-tab-pane label="图例" name="legend">
        <div class="legend-list">
          <div v-for="item in legends" :key="item.name" class="legend-row">
            <span class="legend-color" :style="{ background: item.color }" />
            <span>{{ item.name }}</span>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="统计" name="stats">
        <div class="stat-grid">
          <div v-for="item in statItems" :key="item.key" class="stat-card">
            <span>{{ item.label }}</span>
            <strong>{{ format(item.value) }}</strong>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="区域" name="region">
        <div class="region-tools">
          <el-button
            size="small"
            :type="regionMode === 'RECTANGLE' ? 'primary' : undefined"
            @click="$emit('start-region', 'RECTANGLE')"
          >矩形框选</el-button>
          <el-button
            size="small"
            :type="regionMode === 'POLYGON' ? 'primary' : undefined"
            @click="$emit('start-region', 'POLYGON')"
          >多边形</el-button>
          <el-button size="small" plain :disabled="!hasRegion" @click="$emit('clear-region')">清除</el-button>
        </div>
        <div class="layer-tip">
          区域框选会进入右侧“区域分析”页签，并统一携带线路、路段、病害、评定结果上下文给 AI。
        </div>
      </el-tab-pane>
    </el-tabs>
  </aside>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { LayerState } from './LayerDrawer.vue'

const props = defineProps<{
  layers: LayerState
  statistics: Record<string, any>
  layerCounts?: Record<string, number>
  loading?: boolean
  regionMode: 'NONE' | 'RECTANGLE' | 'POLYGON'
  hasRegion?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:layers', value: LayerState): void
  (e: 'change', value: LayerState): void
  (e: 'reload'): void
  (e: 'fit'): void
  (e: 'start-region', value: 'RECTANGLE' | 'POLYGON'): void
  (e: 'clear-region'): void
}>()

const collapsed = ref(false)
const activeTab = ref('layers')
const localLayers = reactive<LayerState>({ ...props.layers })

watch(
  () => props.layers,
  (value) => Object.assign(localLayers, value || {}),
  { deep: true }
)

const assetLayerItems = [
  { key: 'roadRoute' as keyof LayerState, label: '路线' },
  { key: 'roadSection' as keyof LayerState, label: '路段' },
  { key: 'evaluationUnit' as keyof LayerState, label: '评定单元' }
]

const businessLayerItems = [
  { key: 'disease' as keyof LayerState, label: '病害' },
  { key: 'assessment' as keyof LayerState, label: '评定专题' }
]

const legends = [
  { name: '优', color: '#16a34a' },
  { name: '良', color: '#2563eb' },
  { name: '中', color: '#eab308' },
  { name: '次', color: '#f97316' },
  { name: '差', color: '#dc2626' },
  { name: 'AI / 来源高亮', color: '#f97316' }
]

const enabledLayerCount = computed(() => {
  return [...assetLayerItems, ...businessLayerItems].filter((item) => localLayers[item.key]).length
})

const statItems = computed(() => {
  const value = props.statistics || {}
  return [
    { key: 'totalLengthKm', label: '总里程', value: value.totalLengthKm },
    { key: 'diseaseCount', label: '病害数', value: value.diseaseCount },
    { key: 'avgMqi', label: '平均 MQI', value: value.avgMqi },
    { key: 'excellentGoodRate', label: '优良率', value: value.excellentGoodRate },
    { key: 'poorBadRate', label: '次差率', value: value.poorBadRate }
  ]
})

function emitLayerChange() {
  const next = { ...localLayers }
  emit('update:layers', next)
  emit('change', next)
}

function layerCount(key: keyof LayerState) {
  const count = props.layerCounts?.[String(key)]
  if (count === undefined || count === null) return '-'
  return count
}

function format(value: any) {
  return value === null || value === undefined || value === '' ? '-' : value
}
</script>

<style scoped>
.gis-left-workbench {
  position: absolute;
  top: 92px;
  left: 18px;
  z-index: 920;
  width: 286px;
  max-height: calc(100vh - 126px);
  padding: 14px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.14);
  overflow: auto;
  transition: width 0.18s ease, padding 0.18s ease;
}

.gis-left-workbench.collapsed {
  width: 64px;
  padding: 10px;
  overflow: hidden;
}

.workbench-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.workbench-header strong {
  color: #0f172a;
}

.workbench-header p {
  margin: 3px 0 0;
  color: #64748b;
  font-size: 12px;
}

.collapse-btn {
  border: none;
  background: transparent;
  font-size: 18px;
  cursor: pointer;
  color: #64748b;
  padding: 0 4px;
  line-height: 1;
}

.workbench-tabs :deep(.el-tabs__header) {
  margin-bottom: 10px;
}

.toolbar-row,
.region-tools {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.layer-group + .layer-group {
  margin-top: 12px;
}

.group-title {
  margin: 8px 0 6px;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.layer-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 30px;
}

.layer-count {
  min-width: 34px;
  padding: 1px 7px;
  border-radius: 999px;
  text-align: center;
  color: #475569;
  background: #f1f5f9;
  font-size: 12px;
}

.layer-tip {
  margin-top: 10px;
  padding: 8px;
  border-radius: 8px;
  color: #64748b;
  background: #f8fafc;
  font-size: 12px;
  line-height: 1.45;
}

.legend-list {
  display: grid;
  gap: 8px;
}

.legend-row {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #334155;
  font-size: 13px;
}

.legend-color {
  width: 28px;
  height: 5px;
  border-radius: 999px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.stat-card {
  padding: 8px;
  border-radius: 10px;
  background: #f8fafc;
}

.stat-card span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.stat-card strong {
  display: block;
  margin-top: 3px;
  color: #0f172a;
  font-size: 16px;
}

@media (max-width: 960px) {
  .gis-left-workbench {
    top: 132px;
    left: 10px;
    width: min(286px, calc(100vw - 20px));
    max-height: 45vh;
  }
}
</style>
