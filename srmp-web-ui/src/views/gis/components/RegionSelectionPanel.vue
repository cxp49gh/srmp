<template>
  <transition name="drawer-up">
    <div v-if="visible" class="region-selection-panel srmp-card">
      <div class="panel-header">
        <div>
          <strong>区域摘要</strong>
          <el-tag size="small" class="type-tag">{{ geometryTypeLabel }}</el-tag>
        </div>
        <button type="button" @click="$emit('clear')">×</button>
      </div>

      <div class="summary-grid">
        <div v-for="item in displayItems" :key="item.key" class="summary-item">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </div>

      <div v-if="hotspots.length" class="hotspots">
        <strong>热点</strong>
        <span v-for="(item, index) in hotspots" :key="index">
          {{ pick(item, 'route_code', 'routeCode') || '区域' }}：病害 {{ format(pick(item, 'disease_count', 'diseaseCount')) }}
        </span>
      </div>

      <div class="actions">
        <el-button size="small" @click="$emit('clear')">清除选区</el-button>
        <el-button size="small" type="primary" :loading="loading" @click="$emit('generate')">生成区域养护建议</el-button>
        <el-button v-if="trace?.traceId || trace?.trace_id" size="small" plain @click="$emit('trace')">查看 AI Trace</el-button>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  visible: boolean
  geometryType: 'RECTANGLE' | 'POLYGON'
  summary?: Record<string, any> | null
  trace?: Record<string, any> | null
  loading?: boolean
}>()

defineEmits<{
  (e: 'clear'): void
  (e: 'generate'): void
  (e: 'trace'): void
}>()

const geometryTypeLabel = computed(() => props.geometryType === 'RECTANGLE' ? '矩形' : '多边形')

const displayItems = computed(() => {
  const summary = unwrapSummary(props.summary)
  const disease = pick(summary, 'diseaseSummary', 'disease_summary') || {}
  const assessment = pick(summary, 'assessmentSummary', 'assessment_summary') || {}
  return [
    { key: 'areaKm2', label: '面积 km2', value: format(pick(summary, 'areaKm2', 'area_km2')) },
    { key: 'routeCount', label: '路线', value: format(pick(summary, 'routeCount', 'route_count')) },
    { key: 'sectionCount', label: '路段', value: format(pick(summary, 'sectionCount', 'section_count')) },
    { key: 'unitCount', label: '评定单元', value: format(pick(summary, 'unitCount', 'unit_count')) },
    { key: 'diseaseCount', label: '病害', value: format(pick(disease, 'disease_count', 'diseaseCount')) },
    { key: 'heavyCount', label: '重度病害', value: format(pick(disease, 'heavy_count', 'heavyCount')) },
    { key: 'avgMqi', label: '平均 MQI', value: format(pick(assessment, 'avg_mqi', 'avgMqi')) },
    { key: 'avgPci', label: '平均 PCI', value: format(pick(assessment, 'avg_pci', 'avgPci')) }
  ]
})

const hotspots = computed(() => {
  const value = pick(unwrapSummary(props.summary), 'hotspots', 'hot_spots')
  return Array.isArray(value) ? value.slice(0, 3) : []
})

function unwrapSummary(value: any) {
  if (value && typeof value === 'object' && typeof value.code !== 'undefined' && 'data' in value) {
    return value.data || {}
  }
  return value || {}
}

function pick(source: any, ...keys: string[]) {
  if (!source || typeof source !== 'object') return undefined
  for (const key of keys) {
    if (source[key] !== undefined && source[key] !== null && source[key] !== '') {
      return source[key]
    }
  }
  return undefined
}

function format(value: any) {
  return value === null || value === undefined || value === '' ? '-' : value
}
</script>

<style scoped>
.region-selection-panel {
  position: absolute;
  left: 50%;
  bottom: 88px;
  z-index: 935;
  width: min(820px, calc(100vw - 360px));
  max-height: 300px;
  padding: 14px;
  transform: translateX(-50%);
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.97);
  overflow: auto;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.panel-header button {
  border: none;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-size: 22px;
}

.type-tag {
  margin-left: 8px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 8px;
}

.summary-item {
  padding: 8px;
  border-radius: 8px;
  background: #f8fafc;
}

.summary-item span {
  display: block;
  margin-bottom: 4px;
  color: #64748b;
  font-size: 12px;
}

.summary-item strong {
  color: #0f172a;
  font-size: 13px;
}

.hotspots {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
  color: #475569;
  font-size: 13px;
}

.actions {
  margin-top: 12px;
  text-align: right;
}

@media (max-width: 960px) {
  .region-selection-panel {
    left: 12px;
    right: 12px;
    bottom: 80px;
    width: auto;
    transform: none;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
