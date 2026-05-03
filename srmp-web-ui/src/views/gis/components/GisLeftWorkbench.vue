<template>
  <aside class="gis-left-workbench srmp-card" :class="{ collapsed }">
    <div class="workbench-header">
      <div>
        <strong>{{ collapsed ? '一张图' : '一张图资源' }}</strong>
        <p v-if="!collapsed">图层控制、指标专题与统计统一入口</p>
      </div>
      <button type="button" class="collapse-btn" @click="collapsed = !collapsed">
        {{ collapsed ? '›' : '‹' }}
      </button>
    </div>

    <template v-if="!collapsed">
      <div class="toolbar-row">
        <el-button size="small" :loading="loading" @click="$emit('reload')">刷新图层</el-button>
        <el-button size="small" plain @click="$emit('fit')">全图</el-button>
      </div>

      <section class="indicator-card">
        <div class="indicator-title">
          <span>{{ metricMeta.shortName }}</span>
          <el-tag size="small" type="info">{{ metricMeta.dimension }}</el-tag>
        </div>
        <p>{{ metricMeta.description }}</p>
        <div class="indicator-value-row">
          <span>当前均值</span>
          <strong>{{ formatMetricValue(selectedMetricAverage) }}</strong>
          <em v-if="selectedMetricGradeLabel !== '-'">{{ selectedMetricGradeLabel }}</em>
        </div>
      </section>

      <section class="layer-group">
        <div class="group-title">道路资产</div>
        <label v-for="item in assetLayerItems" :key="item.key" class="layer-item">
          <el-checkbox v-model="localLayers[item.key]" @change="emitLayerChange">{{ item.label }}</el-checkbox>
          <span class="layer-count">{{ layerCount(item.key) }}</span>
        </label>
      </section>

      <section class="layer-group">
        <div class="group-title">业务图层</div>
        <label v-for="item in businessLayerItems" :key="item.key" class="layer-item">
          <el-checkbox v-model="localLayers[item.key]" @change="emitLayerChange">{{ item.label }}</el-checkbox>
          <span class="layer-count">{{ layerCount(item.key) }}</span>
        </label>
      </section>

      <section class="stats-section">
        <div class="section-title">图层统计</div>
        <div class="stat-grid">
          <div v-for="item in statItems" :key="item.key" class="stat-card">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </section>

      <div class="layer-tip">
        已启用 {{ enabledLayerCount }} 个图层；当前专题指标为 {{ metricMeta.code }}，统计随查询条件、等级过滤与启用图层更新。
      </div>
    </template>
  </aside>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { GisLayerQuery } from '../../../api/gis'
import type { LayerState } from './LayerDrawer.vue'
import { formatMetricValue, getGradeMeta, getMetricGrade, getMetricMeta, getMetricValue, gradeFromScore, gradeLabel } from '../../../utils/roadConditionMetrics'

const props = defineProps<{
  layers: LayerState
  statistics: Record<string, any>
  layerCounts?: Record<string, number>
  query?: GisLayerQuery
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:layers', value: LayerState): void
  (e: 'change', value: LayerState): void
  (e: 'reload'): void
  (e: 'fit'): void
}>()

const collapsed = ref(false)
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

const metricMeta = computed(() => getMetricMeta(props.query?.indexCode))
const normalizedStats = computed(() => unwrapPayload(props.statistics || {}))
const selectedMetricAverage = computed(() => getMetricValue(normalizedStats.value, metricMeta.value.code))
const selectedMetricGradeLabel = computed(() => {
  const explicit = getMetricGrade(normalizedStats.value, metricMeta.value.code)
  const grade = explicit || gradeFromScore(selectedMetricAverage.value)
  return grade ? gradeLabel(grade) : '-'
})

const enabledLayerCount = computed(() => {
  return [...assetLayerItems, ...businessLayerItems].filter((item) => localLayers[item.key]).length
})

const statItems = computed(() => {
  const value = normalizedStats.value
  return [
    { key: 'totalLengthKm', label: '总里程', value: format(pick(value, 'totalLengthKm', 'total_length_km', 'mileageKm', 'mileage_km')) },
    { key: 'selectedMetricAvg', label: `平均 ${metricMeta.value.code}`, value: formatMetricValue(selectedMetricAverage.value) },
    { key: 'diseaseCount', label: '病害数', value: format(pick(value, 'diseaseCount', 'disease_count')) },
    { key: 'excellentGoodRate', label: '优良率', value: formatPercent(pick(value, 'excellentGoodRate', 'excellent_good_rate', 'goodRate', 'good_rate')) },
    { key: 'poorBadRate', label: '次差率', value: formatPercent(pick(value, 'poorBadRate', 'poor_bad_rate', 'badRate', 'bad_rate')) },
    { key: 'gradeFilter', label: '等级过滤', value: gradeFilterLabel.value }
  ]
})

const gradeFilterLabel = computed(() => {
  const grade = getGradeMeta(props.query?.grade)
  return grade ? `${grade.label} ${grade.rangeText}` : '全部'
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

function unwrapPayload(value: any) {
  if (value && typeof value === 'object' && typeof value.code !== 'undefined' && 'data' in value) return value.data || {}
  return value || {}
}

function pick(source: any, ...keys: string[]) {
  if (!source || typeof source !== 'object') return undefined
  for (const key of keys) {
    const value = source[key]
    if (value !== undefined && value !== null && value !== '') return value
  }
  return undefined
}

function format(value: any) {
  return value === null || value === undefined || value === '' ? '-' : value
}

function formatPercent(value: any) {
  if (value === null || value === undefined || value === '') return '-'
  const num = Number(value)
  if (!Number.isFinite(num)) return String(value)
  return `${num % 1 === 0 ? num : num.toFixed(1)}%`
}
</script>

<style scoped>
.gis-left-workbench {
  position: absolute;
  top: 108px;
  left: 18px;
  bottom: 252px;
  z-index: 920;
  width: 292px;
  max-height: none;
  padding: 12px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.14);
  overflow-y: auto;
  overflow-x: hidden;
  transition: width 0.18s ease, padding 0.18s ease, bottom 0.18s ease, top 0.18s ease;
  scrollbar-width: thin;
}

.gis-left-workbench.collapsed {
  width: 62px;
  padding: 10px;
  overflow: hidden;
}

.workbench-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
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

.toolbar-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.indicator-card {
  padding: 10px;
  border-radius: 12px;
  background: linear-gradient(135deg, #eff6ff, #f8fafc);
  border: 1px solid #dbeafe;
}

.indicator-title,
.indicator-value-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.indicator-title span {
  font-weight: 800;
  color: #1d4ed8;
}

.indicator-card p {
  margin: 6px 0 8px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.indicator-value-row span {
  color: #64748b;
  font-size: 12px;
}

.indicator-value-row strong {
  color: #0f172a;
  font-size: 18px;
}

.indicator-value-row em {
  padding: 1px 7px;
  border-radius: 999px;
  background: #fff;
  color: #2563eb;
  font-style: normal;
  font-size: 12px;
  font-weight: 700;
}

.layer-group + .layer-group,
.stats-section {
  margin-top: 12px;
}

.group-title,
.section-title {
  margin: 8px 0 6px;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.layer-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 28px;
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

.stat-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.stat-card {
  padding: 6px 8px;
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
  font-size: 15px;
}

.layer-tip {
  margin-top: 8px;
  padding: 7px 8px;
  border-radius: 8px;
  color: #64748b;
  background: #f8fafc;
  font-size: 12px;
  line-height: 1.45;
}

.gis-left-workbench::-webkit-scrollbar {
  width: 6px;
}

.gis-left-workbench::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.62);
}

@media (max-width: 1280px) {
  .gis-left-workbench {
    bottom: 272px;
  }
}

@media (max-width: 960px) {
  .gis-left-workbench {
    top: 150px;
    left: 10px;
    bottom: 220px;
    width: min(296px, calc(100vw - 20px));
    max-height: none;
  }
}
</style>
