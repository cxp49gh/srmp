<template>
  <aside class="gis-right-workbench srmp-card" :class="{ collapsed }">
    <div class="workbench-header">
      <div>
        <strong>{{ collapsed ? '分析' : '一张图分析' }}</strong>
        <p v-if="!collapsed">对象、区域、AI 和草稿统一操作区</p>
      </div>
      <button type="button" class="collapse-btn" @click="collapsed = !collapsed">
        {{ collapsed ? '‹' : '›' }}
      </button>
    </div>

    <el-tabs v-if="!collapsed" :model-value="activeTab" class="workbench-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="对象详情" name="object">
        <el-empty v-if="!detail" description="点击地图路线、路段、病害或评定对象" />
        <template v-else>
          <div class="context-title">
            <strong>{{ objectTitle }}</strong>
            <el-tag v-if="detail.objectType" size="small">{{ objectTypeLabel(detail.objectType) }}</el-tag>
          </div>
          <div class="detail-grid">
            <div v-for="item in objectItems" :key="item.key" class="detail-item">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
          <div class="action-row">
            <el-button size="small" type="primary" @click="$emit('ai-analyze-object')">AI 分析对象</el-button>
            <el-button size="small" plain @click="$emit('open-agent')">打开 AI</el-button>
            <el-button size="small" plain @click="$emit('close-detail')">取消选择</el-button>
          </div>
        </template>
      </el-tab-pane>

      <el-tab-pane label="区域分析" name="region">
        <el-empty v-if="!hasRegion" description="请在左侧“区域”页签绘制矩形或多边形" />
        <template v-else>
          <div class="context-title">
            <strong>区域摘要</strong>
            <el-tag size="small" type="success">{{ geometryType === 'RECTANGLE' ? '矩形' : '多边形' }}</el-tag>
          </div>
          <div class="detail-grid">
            <div v-for="item in regionItems" :key="item.key" class="detail-item">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
          <div v-if="hotspots.length" class="hotspot-list">
            <strong>热点</strong>
            <span v-for="(item, index) in hotspots" :key="index">{{ hotspotText(item) }}</span>
          </div>
          <div class="action-row">
            <el-button size="small" type="primary" :loading="regionLoading" @click="$emit('generate-region')">生成区域建议</el-button>
            <el-button size="small" plain :loading="regionLoading" @click="$emit('ai-analyze-region')">AI 综合分析</el-button>
            <el-button v-if="hasTrace" size="small" plain @click="$emit('trace')">Trace</el-button>
            <el-button size="small" plain @click="$emit('clear-region')">清除</el-button>
          </div>
        </template>
      </el-tab-pane>

      <el-tab-pane label="AI" name="ai">
        <div class="ai-context-card">
          <span>当前上下文</span>
          <strong>{{ contextLabel }}</strong>
          <p>{{ contextHint }}</p>
        </div>
        <div class="action-row">
          <el-button size="small" type="primary" @click="$emit('open-agent')">打开 AI 助手</el-button>
          <el-button size="small" plain :disabled="!detail" @click="$emit('ai-analyze-object')">分析对象</el-button>
          <el-button size="small" plain :disabled="!hasRegion" @click="$emit('ai-analyze-region')">分析区域</el-button>
        </div>
      </el-tab-pane>
    </el-tabs>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  activeTab: 'object' | 'region' | 'ai'
  detail: Record<string, any> | null
  regionSummary: Record<string, any> | null
  regionLoading?: boolean
  regionTrace?: Record<string, any> | null
  geometryType: 'RECTANGLE' | 'POLYGON'
  hasRegion?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:activeTab', value: 'object' | 'region' | 'ai'): void
  (e: 'close-detail'): void
  (e: 'ai-analyze-object'): void
  (e: 'ai-analyze-region'): void
  (e: 'generate-region'): void
  (e: 'trace'): void
  (e: 'clear-region'): void
  (e: 'open-agent'): void
}>()

const collapsed = ref(false)

watch(
  () => props.hasRegion,
  (value) => {
    if (value) emit('update:activeTab', 'region')
  }
)

watch(
  () => props.detail,
  (value) => {
    if (value) emit('update:activeTab', 'object')
  }
)

const objectTitle = computed(() => {
  const detail = props.detail || {}
  return detail.name || detail.routeName || detail.diseaseName || detail.unitCode || detail.routeCode || '对象详情'
})

const objectItems = computed(() => buildDetailItems(props.detail || {}, [
  ['routeCode', '路线'],
  ['routeName', '路线名称'],
  ['sectionName', '路段'],
  ['unitCode', '评定单元'],
  ['diseaseName', '病害'],
  ['diseaseType', '病害类型'],
  ['severity', '严重程度'],
  ['startStake', '起点桩号'],
  ['endStake', '终点桩号'],
  ['mqi', 'MQI'],
  ['pqi', 'PQI'],
  ['pci', 'PCI'],
  ['grade', '等级'],
  ['status', '状态']
]))

const regionItems = computed(() => {
  const summary = unwrapSummary(props.regionSummary)
  const disease = pick(summary, 'diseaseSummary', 'disease_summary') || {}
  const assessment = pick(summary, 'assessmentSummary', 'assessment_summary') || {}
  return [
    { key: 'areaKm2', label: '面积 km²', value: format(pick(summary, 'areaKm2', 'area_km2')) },
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
  const value = pick(unwrapSummary(props.regionSummary), 'hotspots', 'hot_spots')
  return Array.isArray(value) ? value.slice(0, 4) : []
})

const hasTrace = computed(() => Boolean(props.regionTrace?.traceId || props.regionTrace?.trace_id))

const contextLabel = computed(() => {
  if (props.detail) return `${objectTypeLabel(props.detail.objectType)}｜${objectTitle.value}`
  if (props.hasRegion) return '区域分析｜线路 / 路段 / 病害 / 评定统一上下文'
  return '路线范围｜请先选择地图对象或绘制区域'
})

const contextHint = computed(() => {
  if (props.hasRegion) return '区域分析优先使用框选区域，其次结合当前启用图层和查询条件。'
  if (props.detail) return '对象分析会携带对象类型、路线、桩号、病害或评定指标。'
  return 'AI 助手仍可进行路线级问答，但建议先选择对象或区域。'
})

function handleTabChange(name: any) {
  emit('update:activeTab', String(name) as 'object' | 'region' | 'ai')
}

function buildDetailItems(detail: Record<string, any>, keys: string[][]) {
  const result = keys
    .filter(([key]) => detail[key] !== undefined && detail[key] !== null && detail[key] !== '')
    .map(([key, label]) => ({ key, label, value: String(detail[key]) }))
  if (result.length > 0) return result.slice(0, 12)
  return Object.entries(detail).slice(0, 10).map(([key, value]) => ({
    key,
    label: key,
    value: value === null || value === undefined ? '-' : String(value)
  }))
}

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

function hotspotText(item: any) {
  const route = pick(item, 'route_code', 'routeCode') || '区域'
  const count = format(pick(item, 'disease_count', 'diseaseCount'))
  return `${route}：病害 ${count}`
}

function format(value: any) {
  return value === null || value === undefined || value === '' ? '-' : value
}
</script>

<style scoped>
.gis-right-workbench {
  position: absolute;
  top: 92px;
  right: 18px;
  z-index: 925;
  width: 386px;
  max-height: calc(100vh - 150px);
  padding: 14px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.14);
  overflow: auto;
  transition: width 0.18s ease, padding 0.18s ease;
}

.gis-right-workbench.collapsed {
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

.context-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.detail-item {
  padding: 8px;
  border-radius: 10px;
  background: #f8fafc;
}

.detail-item span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.detail-item strong {
  display: block;
  margin-top: 3px;
  color: #0f172a;
  font-size: 13px;
  word-break: break-word;
}

.action-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.hotspot-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 10px;
  padding: 8px;
  border-radius: 10px;
  background: #f8fafc;
  color: #475569;
  font-size: 12px;
}

.ai-context-card {
  padding: 10px;
  border-radius: 12px;
  background: #eff6ff;
  color: #1e3a8a;
}

.ai-context-card span {
  display: block;
  font-size: 12px;
  color: #64748b;
}

.ai-context-card strong {
  display: block;
  margin-top: 4px;
}

.ai-context-card p {
  margin: 6px 0 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.45;
}

@media (max-width: 1180px) {
  .gis-right-workbench {
    width: 340px;
  }
}

@media (max-width: 960px) {
  .gis-right-workbench {
    top: auto;
    right: 10px;
    left: 10px;
    bottom: 14px;
    width: auto;
    max-height: 46vh;
  }
}
</style>
