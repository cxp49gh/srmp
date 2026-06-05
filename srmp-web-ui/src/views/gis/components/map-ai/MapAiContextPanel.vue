<template>
  <section class="map-ai-context-panel">
    <el-tag size="small" type="info">{{ scope || 'ROUTE' }}</el-tag>
    <strong>{{ title }}</strong>
    <span>{{ subtitle }}</span>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  scope?: string
  context?: Record<string, any>
  mapObject?: Record<string, any> | null
}>()

const title = computed(() => {
  if (props.mapObject) return String(props.mapObject.name || props.mapObject.objectName || props.mapObject.disease_name || '当前对象')
  return String(props.context?.routeCode || props.context?.route_code || props.context?.query?.routeCode || '当前地图')
})

const subtitle = computed(() => {
  const project = props.context?.projectName || props.context?.query?.projectName
  return [props.context?.indexCode || props.context?.query?.indexCode, project].filter(Boolean).join(' / ') || '一张图上下文'
})
</script>

<style scoped>
.map-ai-context-panel {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-bottom: 1px solid #e2e8f0;
  color: #475569;
  font-size: 12px;
}
</style>
