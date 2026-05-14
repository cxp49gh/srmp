<template>
  <div v-if="normalizedActions.length" class="map-ai-suggested-actions">
    <span v-for="item in normalizedActions" :key="item.action + item.label" class="suggested-action-item">
      <el-button
        size="small"
        :type="item.requiresConfirmation ? 'warning' : 'primary'"
        plain
        :disabled="item.disabled"
        @click="$emit('run-action', item)"
      >
        {{ item.label }}
      </el-button>
      <el-button
        v-if="isHeavyAction(item.action)"
        size="small"
        text
        :disabled="item.disabled"
        @click="$emit('preview-plan', item)"
      >
        计划
      </el-button>
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MapAgentAction, MapAgentSuggestedAction } from '../../../../api/agent'

const props = defineProps<{ actions?: MapAgentSuggestedAction[] }>()
defineEmits<{
  (e: 'run-action', action: MapAgentSuggestedAction): void
  (e: 'preview-plan', action: MapAgentSuggestedAction): void
}>()

const normalizedActions = computed(() => props.actions || [])

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
