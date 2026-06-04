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
        <div class="group-title">资产与业务</div>
        <label v-for="item in assetLayerItems" :key="item.key" class="layer-item">
          <el-checkbox v-model="localLayers[item.key]" @change="emitLayerChange">{{ item.label }}</el-checkbox>
          <span class="layer-count" :class="{ loading: layerLoading(item.key), error: layerError(item.key) }">
            <span v-if="layerLoading(item.key)" class="loading-dot"></span>
            {{ layerStatusText(item.key) }}
          </span>
        </label>
        <p class="tier-hint">「评定」用于开关地图上的指标专题图层；路段粒度仍由顶部工具栏「评定」与「查询」决定。</p>
        <p class="disease-zoom-hint">
          病害图层在 {{ ZOOM_DISEASE_SUMMARY }} 级显示总和；15-{{ ZOOM_DISEASE_SUMMARY_FREEZE_MAX }} 级沿用该结果；{{ ZOOM_DISEASE_DETAIL_MIN }} 级起显示明细。
        </p>
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

      <div v-if="layerErrorItems.length" class="layer-error-box">
        <strong>图层异常</strong>
        <p v-for="item in layerErrorItems" :key="item.key">{{ item.label }}：{{ item.message }}</p>
      </div>

      <div class="layer-tip">
        当前已勾选 {{ enabledLayerCount }} 个图层（路网 / 路段 / 病害 / 评定）。取消「评定」后地图不展示指标着色数据，左侧等级图例一并隐藏；指标均值与图层统计也会排除评定数据。
      </div>
    </template>
  </aside>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { GisLayerQuery } from '../../../api/gis'
import { ZOOM_DISEASE_DETAIL_MIN, ZOOM_DISEASE_SUMMARY, ZOOM_DISEASE_SUMMARY_FREEZE_MAX } from '../../../constants/gisDiseaseLayer'
import type { LayerState } from './LayerDrawer.vue'
import { formatMetricValue, getGradeMeta, getMetricGrade, getMetricMeta, getMetricValue, gradeFromScore, gradeLabel } from '../../../utils/roadConditionMetrics'

const props = defineProps<{
  layers: LayerState
  statistics: Record<string, any>
  layerCounts?: Record<string, number>
  layerErrors?: Record<string, string>
  layerLoading?: Record<string, boolean>
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
  { key: 'roadRoute' as keyof LayerState, label: '路网' },
  { key: 'roadSection' as keyof LayerState, label: '路段' },
  { key: 'disease' as keyof LayerState, label: '病害' },
  { key: 'assessment' as keyof LayerState, label: '评定' }
]

const allLayerItems = computed(() => assetLayerItems)

const layerErrorItems = computed(() => {
  const errors = props.layerErrors || {}
  return allLayerItems.value
    .map((item) => ({ key: String(item.key), label: item.label, message: errors[String(item.key)] }))
    .filter((item) => item.message)
})

const metricMeta = computed(() => getMetricMeta(props.query?.indexCode))
const normalizedStats = computed(() => unwrapPayload(props.statistics || {}))
const selectedMetricAverage = computed(() => getMetricValue(normalizedStats.value, metricMeta.value.code))
const selectedMetricGradeLabel = computed(() => {
  const explicit = getMetricGrade(normalizedStats.value, metricMeta.value.code)
  const grade = explicit || gradeFromScore(selectedMetricAverage.value)
  return grade ? gradeLabel(grade) : '-'
})

const enabledLayerCount = computed(() => {
  return allLayerItems.value.filter((item) => localLayers[item.key]).length
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

function layerLoading(key: keyof LayerState) {
  return Boolean(props.layerLoading?.[String(key)])
}

function layerError(key: keyof LayerState) {
  return Boolean(props.layerErrors?.[String(key)])
}

function layerStatusText(key: keyof LayerState) {
  if (layerLoading(key)) return '查询中'
  if (layerError(key)) return '异常'
  return layerCount(key)
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
  top: 112px;
  left: 18px;
  bottom: 328px;
  z-index: 920;
  width: 276px;
  max-height: none;
  padding: 10px;
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
  margin-bottom: 6px;
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
  padding: 8px;
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
  margin: 5px 0 6px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.42;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
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
  margin-top: 8px;
}

.group-title,
.section-title {
  margin: 6px 0 5px;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.layer-item-wrap {
  margin-bottom: 2px;
}

.layer-item-wrap:last-child {
  margin-bottom: 0;
}

.layer-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 26px;
}

.layer-hint {
  margin: 0 0 6px;
  padding-left: 22px;
  font-size: 11px;
  line-height: 1.38;
  color: #64748b;
}

.layer-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-width: 42px;
  padding: 1px 7px;
  border-radius: 999px;
  text-align: center;
  color: #475569;
  background: #f1f5f9;
  font-size: 12px;
}

.layer-count.loading {
  color: #1d4ed8;
  background: #dbeafe;
}

.layer-count.error {
  color: #b91c1c;
  background: #fee2e2;
}

.loading-dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: currentColor;
  animation: loadingPulse 0.9s ease-in-out infinite;
}

@keyframes loadingPulse {
  0%,
  100% {
    opacity: 0.35;
    transform: scale(0.85);
  }
  50% {
    opacity: 1;
    transform: scale(1);
  }
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 5px;
}

.stat-card {
  padding: 5px 7px;
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

.layer-error-box {
  margin-top: 7px;
  padding: 7px 8px;
  border-radius: 8px;
  color: #b45309;
  background: #fffbeb;
  border: 1px solid #fde68a;
  font-size: 12px;
  line-height: 1.42;
}

.layer-error-box strong {
  display: block;
  margin-bottom: 3px;
  color: #92400e;
}

.layer-error-box p {
  margin: 2px 0;
}

.tier-hint,
.disease-zoom-hint {
  margin: 6px 0 0;
  font-size: 11px;
  line-height: 1.45;
  color: #64748b;
}

.disease-zoom-hint {
  margin-top: 4px;
}

.layer-tip {
  margin-top: 7px;
  padding: 6px 8px;
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
    bottom: 342px;
  }
}

@media (max-width: 960px) {
  .gis-left-workbench {
    top: 150px;
    left: 10px;
    bottom: 260px;
    width: min(296px, calc(100vw - 20px));
    max-height: none;
  }
}
</style>
