<template>
  <transition name="drawer-up">
    <div v-if="visible" class="object-detail-drawer srmp-card">
      <div class="drawer-header">
        <div class="title-box">
          <strong>{{ title }}</strong>
          <div class="subtitle">{{ subtitle }}</div>
          <el-tag v-if="detail?.objectType" size="small" class="type-tag">{{ objectTypeLabel }}</el-tag>
        </div>
        <button type="button" @click="$emit('update:visible', false)">×</button>
      </div>

      <el-empty v-if="!detail" description="点击地图对象查看详情" />

      <template v-else>
        <div class="context-card">
          <div>
            <span>AI 上下文</span>
            <strong>{{ contextLabel }}</strong>
          </div>
          <el-tag v-if="hasStableId" size="small" type="success" effect="plain">可追溯</el-tag>
          <el-tag v-else size="small" type="warning" effect="plain">临时对象</el-tag>
        </div>

        <div class="detail-grid">
          <div v-for="item in displayItems" :key="item.key" class="detail-item">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>

        <div class="actions">
          <el-button size="small" plain @click="$emit('copy-context')">复制上下文</el-button>
          <el-button size="small" plain @click="$emit('locate')">定位对象</el-button>
          <el-button size="small" type="primary" @click="$emit('ai-analyze')">用此对象问 AI</el-button>
        </div>
      </template>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  visible: boolean
  detail: Record<string, any> | null
}>()

defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'ai-analyze'): void
  (e: 'copy-context'): void
  (e: 'locate'): void
}>()

function firstValue(...values: any[]) {
  return values.find((it) => it !== undefined && it !== null && it !== '')
}

function objectTypeCn(value: any) {
  const type = String(value || '').toUpperCase()
  const map: Record<string, string> = {
    ROAD_ROUTE: '路线',
    ROAD_SECTION: '路段',
    EVALUATION_UNIT: '评定单元',
    DISEASE: '病害',
    DISEASE_RECORD: '病害',
    ASSESSMENT: '评定结果',
    ASSESSMENT_RESULT: '评定结果'
  }
  return map[type] || type || '地图对象'
}

function formatStake(start: any, end?: any) {
  if (start === undefined || start === null || start === '') return ''
  const startText = String(start).startsWith('K') ? String(start) : `K${start}`
  if (end === undefined || end === null || end === '') return startText
  const endText = String(end).startsWith('K') ? String(end) : `K${end}`
  return `${startText} ~ ${endText}`
}

function formatValue(value: any) {
  if (value === null || typeof value === 'undefined' || value === '') return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

const title = computed(() => {
  const detail = props.detail || {}
  return firstValue(
    detail.name,
    detail.routeName,
    detail.route_name,
    detail.sectionName,
    detail.section_name,
    detail.diseaseName,
    detail.disease_name,
    detail.unitCode,
    detail.unit_code,
    detail.routeCode,
    detail.route_code,
    '对象详情'
  )
})

const objectTypeLabel = computed(() => objectTypeCn(props.detail?.objectType || props.detail?.object_type))

const subtitle = computed(() => {
  const detail = props.detail || {}
  const route = firstValue(detail.routeCode, detail.route_code)
  const stake = formatStake(firstValue(detail.startStake, detail.start_stake), firstValue(detail.endStake, detail.end_stake))
  return [route, stake].filter(Boolean).join('｜') || '地图对象详情与 AI 分析入口'
})

const contextLabel = computed(() => {
  const detail = props.detail || {}
  const type = objectTypeLabel.value
  const route = firstValue(detail.routeCode, detail.route_code)
  const stake = formatStake(firstValue(detail.startStake, detail.start_stake), firstValue(detail.endStake, detail.end_stake))
  const disease = firstValue(detail.diseaseName, detail.disease_name, detail.diseaseType, detail.disease_type)
  const severity = firstValue(detail.severity)
  const score = firstValue(detail.mqi, detail.pqi, detail.pci)
  return [type, disease, severity, route, stake, score !== undefined ? `评分 ${score}` : ''].filter(Boolean).join('｜')
})

const hasStableId = computed(() => {
  const detail = props.detail || {}
  return Boolean(firstValue(detail.objectId, detail.object_id, detail.id))
})

const displayItems = computed(() => {
  const detail = props.detail || {}
  const keys = [
    ['objectId', '对象ID'],
    ['routeCode', '路线'],
    ['routeName', '路线名称'],
    ['sectionCode', '路段编码'],
    ['sectionName', '路段名称'],
    ['unitCode', '评定单元'],
    ['diseaseName', '病害名称'],
    ['diseaseType', '病害类型'],
    ['severity', '严重程度'],
    ['quantity', '数量'],
    ['measureUnit', '单位'],
    ['startStake', '起点桩号'],
    ['endStake', '终点桩号'],
    ['mqi', 'MQI'],
    ['pqi', 'PQI'],
    ['pci', 'PCI'],
    ['grade', '等级'],
    ['status', '状态']
  ]

  const result = keys
    .map(([key, label]) => {
      const snakeKey = key.replace(/[A-Z]/g, (it) => `_${it.toLowerCase()}`)
      const value = firstValue(detail[key], detail[snakeKey])
      return { key, label, value: formatValue(value) }
    })
    .filter((item) => item.value !== '-')

  if (result.length > 0) return result

  return Object.entries(detail)
    .filter(([key]) => !['raw', 'geometry', 'properties'].includes(key))
    .slice(0, 12)
    .map(([key, value]) => ({ key, label: key, value: formatValue(value) }))
})
</script>

<style scoped>
.object-detail-drawer {
  position: absolute;
  left: 50%;
  bottom: 88px;
  z-index: 930;
  width: min(840px, calc(100vw - 360px));
  max-height: 340px;
  padding: 14px;
  transform: translateX(-50%);
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.97);
  overflow: auto;
}

.drawer-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}

.title-box {
  min-width: 0;
}

.subtitle {
  margin-top: 2px;
  color: #64748b;
  font-size: 12px;
}

.drawer-header button {
  border: none;
  background: transparent;
  font-size: 22px;
  color: #64748b;
  cursor: pointer;
}

.type-tag {
  margin-top: 6px;
}

.context-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
  padding: 10px;
  border-radius: 12px;
  background: #eff6ff;
  color: #1d4ed8;
}

.context-card span {
  display: block;
  margin-bottom: 3px;
  font-size: 12px;
  color: #64748b;
}

.context-card strong {
  color: #1e3a8a;
  font-size: 13px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 8px;
}

.detail-item {
  padding: 8px;
  border-radius: 8px;
  background: #f8fafc;
}

.detail-item span {
  display: block;
  margin-bottom: 4px;
  color: #64748b;
  font-size: 12px;
}

.detail-item strong {
  color: #0f172a;
  font-size: 13px;
  word-break: break-all;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
  flex-wrap: wrap;
}

@media (max-width: 960px) {
  .object-detail-drawer {
    left: 12px;
    right: 12px;
    bottom: 88px;
    width: auto;
    transform: none;
  }

  .detail-grid {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }
}
</style>
