<template>
  <transition name="drawer-up">
    <div v-if="visible" class="object-detail-drawer srmp-card">
      <div class="drawer-header">
        <div>
          <strong>{{ title }}</strong>
          <el-tag v-if="detail?.objectType" size="small" class="type-tag">{{ detail.objectType }}</el-tag>
        </div>
        <button type="button" @click="$emit('update:visible', false)">×</button>
      </div>

      <el-empty v-if="!detail" description="点击地图对象查看详情" />

      <template v-else>
        <div class="detail-grid">
          <div v-for="item in displayItems" :key="item.key" class="detail-item">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>

        <div class="actions">
          <el-button size="small" type="primary" @click="$emit('ai-analyze')">AI 分析此对象</el-button>
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
}>()

const title = computed(() => {
  const detail = props.detail || {}
  return detail.name || detail.routeName || detail.diseaseName || detail.unitCode || detail.routeCode || '对象详情'
})

const displayItems = computed(() => {
  const detail = props.detail || {}
  const keys = [
    ['routeCode', '路线'],
    ['routeName', '路线名称'],
    ['unitCode', '评定单元'],
    ['diseaseName', '病害'],
    ['severity', '严重程度'],
    ['startStake', '起点桩号'],
    ['endStake', '终点桩号'],
    ['mqi', 'MQI'],
    ['pqi', 'PQI'],
    ['pci', 'PCI'],
    ['grade', '等级'],
    ['status', '状态']
  ]

  const result = keys
    .filter(([key]) => typeof detail[key] !== 'undefined' && detail[key] !== null && detail[key] !== '')
    .map(([key, label]) => ({ key, label, value: String(detail[key]) }))

  if (result.length > 0) return result

  return Object.entries(detail)
    .slice(0, 10)
    .map(([key, value]) => ({
      key,
      label: key,
      value: value === null || typeof value === 'undefined' ? '-' : String(value)
    }))
})
</script>

<style scoped>
.object-detail-drawer {
  position: absolute;
  left: 50%;
  bottom: 88px;
  z-index: 930;
  width: min(760px, calc(100vw - 360px));
  max-height: 280px;
  padding: 14px;
  transform: translateX(-50%);
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.97);
  overflow: auto;
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.drawer-header button {
  border: none;
  background: transparent;
  font-size: 22px;
  color: #64748b;
  cursor: pointer;
}

.type-tag {
  margin-left: 8px;
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
}

.actions {
  margin-top: 12px;
  text-align: right;
}
</style>
