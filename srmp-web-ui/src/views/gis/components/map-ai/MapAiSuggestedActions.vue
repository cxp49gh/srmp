<template>
  <div v-if="normalizedActions.length" class="map-ai-suggested-actions">
    <span v-for="item in normalizedActions" :key="item.action + item.label" class="suggested-action-item">
      <el-button
        size="small"
        :type="primaryActionType(item)"
        plain
        :disabled="item.disabled"
        @click="$emit('run-action', item)"
      >
        {{ displayLabel(item) }}
      </el-button>
      <el-button
        v-if="isHeavyAction(item.action)"
        size="small"
        plain
        :disabled="item.disabled"
        @click="$emit('preview-plan', item)"
      >
        查看计划
      </el-button>
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MapAgentAction, MapAgentSuggestedAction } from '../../../../api/agent'
import { assessmentSolutionLabel } from '../../../../utils/mapAssessmentSemantics'

const props = defineProps<{
  actions?: MapAgentSuggestedAction[]
  mapObject?: Record<string, any> | null
}>()
defineEmits<{
  (e: 'run-action', action: MapAgentSuggestedAction): void
  (e: 'preview-plan', action: MapAgentSuggestedAction): void
}>()

const normalizedActions = computed(() => props.actions || [])
const objectSolutionLabel = computed(() => {
  const type = normalizeObjectType(props.mapObject)
  if (type === 'DISEASE' || type === 'DISEASE_RECORD') return '生成病害处置建议'
  if (type === 'ASSESSMENT' || type === 'ASSESSMENT_RESULT') return assessmentSolutionLabel(props.mapObject)
  if (type === 'EVALUATION_UNIT') return '生成评定单元建议'
  if (type === 'ROAD_SECTION') return '生成路段养护计划'
  if (type === 'ROAD_ROUTE') return '生成路线养护报告'
  return '生成对象方案'
})

function displayLabel(item: MapAgentSuggestedAction) {
  const action = String(item.action || '')
  if (action === 'GENERATE_OBJECT_SOLUTION') return objectSolutionLabel.value
  if (action === 'GENERATE_REGION_SOLUTION') return '生成区域建议'
  if (action === 'GENERATE_ROUTE_REPORT') return '生成路线养护报告'
  return item.label || action
}

function primaryActionType(item: MapAgentSuggestedAction) {
  if (item.requiresConfirmation) return 'warning'
  if (isHeavyAction(item.action)) return 'success'
  return 'primary'
}

function normalizeObjectType(obj: any) {
  return String(obj?.objectType || obj?.object_type || obj?.type || obj?.layerType || '').toUpperCase()
}

function isHeavyAction(action: MapAgentAction | string) {
  return ['GENERATE_OBJECT_SOLUTION', 'GENERATE_REGION_SOLUTION', 'GENERATE_ROUTE_REPORT', 'SAVE_SOLUTION_DRAFT'].includes(String(action))
}
</script>

<style scoped>
.map-ai-suggested-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 0;
}

.suggested-action-item {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
</style>
