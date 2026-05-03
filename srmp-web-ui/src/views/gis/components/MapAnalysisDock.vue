<template>
  <section class="map-analysis-dock srmp-card" :class="{ 'with-agent': agentVisible }">
    <div class="dock-top-row">
      <div class="dock-main">
        <div class="dock-title">
          <strong>一张图分析</strong>
          <el-tag size="small" :type="scopeTagType">{{ scopeLabel }}</el-tag>
        </div>
        <div class="dock-summary">
          {{ summaryText }}
        </div>
      </div>

      <div class="dock-metrics" v-if="metricItems.length">
        <span v-for="item in metricItems" :key="item.key">
          {{ item.label }} <strong>{{ item.value }}</strong>
        </span>
      </div>
    </div>

    <div class="dock-actions">
      <el-button size="small" type="primary" :disabled="!detail" @click="$emit('ai-analyze-object')">分析对象</el-button>
      <el-button size="small" plain :disabled="!hasRegion" :loading="regionLoading" @click="$emit('ai-analyze-region')">分析区域</el-button>
      <el-button size="small" plain :disabled="!hasRegion" :loading="regionLoading" @click="$emit('generate-region')">生成区域建议</el-button>
      <el-button v-if="hasTrace" size="small" plain @click="$emit('trace')">Trace</el-button>
      <el-button size="small" plain @click="$emit('open-agent')">打开 AI</el-button>
      <el-button v-if="detail" size="small" link @click="$emit('close-detail')">取消对象</el-button>
      <el-button v-if="hasRegion" size="small" link @click="$emit('clear-region')">清除区域</el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  detail: Record<string, any> | null
  regionSummary: Record<string, any> | null
  regionLoading?: boolean
  regionTrace?: Record<string, any> | null
  geometryType: 'RECTANGLE' | 'POLYGON'
  hasRegion?: boolean
  agentVisible?: boolean
}>()

defineEmits<{
  (e: 'close-detail'): void
  (e: 'ai-analyze-object'): void
  (e: 'ai-analyze-region'): void
  (e: 'generate-region'): void
  (e: 'trace'): void
  (e: 'clear-region'): void
  (e: 'open-agent'): void
}>()

const hasTrace = computed(() => Boolean(props.regionTrace?.traceId || props.regionTrace?.trace_id))

const scopeLabel = computed(() => {
  if (props.detail) return objectTypeLabel(props.detail.objectType)
  if (props.hasRegion) return props.geometryType === 'RECTANGLE' ? '矩形区域' : '多边形区域'
  return '路线范围'
})

const scopeTagType = computed(() => {
  if (props.detail) return 'primary'
  if (props.hasRegion) return 'success'
  return 'info'
})

const summaryText = computed(() => {
  if (props.detail) {
    const detail = props.detail
    const title = detail.diseaseName || detail.routeName || detail.sectionName || detail.unitCode || detail.routeCode || '当前对象'
    const route = detail.routeCode ? ` / ${detail.routeCode}` : ''
    const stake = formatStake(detail.startStake ?? detail.start_stake, detail.endStake ?? detail.end_stake)
    const severity = detail.severity ? ` / ${detail.severity}` : ''
    return `${objectTypeLabel(detail.objectType)}：${title}${route}${stake ? ` / ${stake}` : ''}${severity}`
  }
  if (props.hasRegion) {
    return `${props.geometryType === 'RECTANGLE' ? '矩形' : '多边形'}框选区域，已统一线路、路段、病害、评定结果上下文，可直接分析或生成区域建议。`
  }
  return '请选择地图对象，或在顶部工具栏进行矩形/多边形框选后开展分析。'
})

const metricItems = computed(() => {
  if (!props.hasRegion) return []
  const summary = unwrapSummary(props.regionSummary)
  const disease = pick(summary, 'diseaseSummary', 'disease_summary') || {}
  const assessment = pick(summary, 'assessmentSummary', 'assessment_summary') || {}
  return [
    { key: 'route', label: '路线', value: format(pick(summary, 'routeCount', 'route_count')) },
    { key: 'section', label: '路段', value: format(pick(summary, 'sectionCount', 'section_count')) },
    { key: 'disease', label: '病害', value: format(pick(disease, 'disease_count', 'diseaseCount')) },
    { key: 'mqi', label: 'MQI', value: format(pick(assessment, 'avg_mqi', 'avgMqi')) }
  ].filter((item) => item.value !== '-')
})

function objectTypeLabel(type: any) {
  const map: Record<string, string> = {
    ROAD_ROUTE: '路线',
    ROAD_SECTION: '路段',
    EVALUATION_UNIT: '评定单元',
    DISEASE: '病害',
    DISEASE_RECORD: '病害',
    ASSESSMENT: '评定结果',
    ASSESSMENT_RESULT: '评定结果',
    MAP_REGION: '区域'
  }
  return map[String(type || '').toUpperCase()] || String(type || '地图对象')
}

function unwrapSummary(value: any) {
  if (value && typeof value === 'object' && typeof value.code !== 'undefined' && 'data' in value) {
    return value.data || {}
  }
  return value || {}
}

function pick(source: any, ...keys: string[]) {
  if (!source || typeof source !== 'object') return undefined
  for (const key of keys) {
    if (source[key] !== undefined && source[key] !== null && source[key] !== '') return source[key]
  }
  return undefined
}

function format(value: any) {
  return value === null || value === undefined || value === '' ? '-' : value
}

function formatStake(start: any, end?: any) {
  if (start === undefined || start === null || start === '') return ''
  const s = String(start).startsWith('K') ? String(start) : `K${start}`
  if (end === undefined || end === null || end === '') return s
  const e = String(end).startsWith('K') ? String(end) : `K${end}`
  return `${s}—${e}`
}
</script>

<style scoped>
.map-analysis-dock {
  position: absolute;
  left: 50%;
  bottom: 26px;
  z-index: 930;
  display: flex;
  flex-direction: column;
  gap: 9px;
  width: min(860px, calc(100vw - 720px));
  min-height: 116px;
  max-height: 168px;
  padding: 12px 14px;
  transform: translateX(-50%);
  border: 1px solid rgba(226, 232, 240, 0.92);
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.14);
  overflow: hidden;
}

.map-analysis-dock.with-agent {
  left: calc(50% - 92px);
  width: min(760px, calc(100vw - 930px));
}

.dock-top-row {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) auto;
  align-items: start;
  gap: 12px;
  min-width: 0;
}

.dock-main {
  min-width: 0;
}

.dock-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.dock-title strong {
  color: #0f172a;
}

.dock-summary {
  color: #475569;
  font-size: 12px;
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.dock-metrics {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
  min-width: 176px;
  max-width: 260px;
}

.dock-metrics span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 9px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 12px;
}

.dock-metrics strong {
  color: #0f172a;
}

.dock-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  padding-top: 8px;
  border-top: 1px solid #eef2f7;
}

@media (max-width: 1440px) {
  .map-analysis-dock {
    width: min(760px, calc(100vw - 620px));
  }

  .map-analysis-dock.with-agent {
    left: calc(50% - 86px);
    width: min(680px, calc(100vw - 860px));
  }

  .dock-metrics {
    max-width: 220px;
  }
}

@media (max-width: 1180px) {
  .map-analysis-dock,
  .map-analysis-dock.with-agent {
    left: 50%;
    width: min(680px, calc(100vw - 360px));
    transform: translateX(-50%);
  }

  .dock-metrics {
    display: none;
  }
}

@media (max-width: 960px) {
  .map-analysis-dock,
  .map-analysis-dock.with-agent {
    left: 10px;
    right: 10px;
    bottom: 16px;
    width: auto;
    min-height: 126px;
    max-height: 190px;
    transform: none;
  }

  .dock-top-row {
    grid-template-columns: 1fr;
  }

  .dock-actions {
    justify-content: flex-start;
  }
}
</style>
