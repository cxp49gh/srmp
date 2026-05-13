<template>
  <div v-if="normalizedActions.length" class="map-ai-suggested-actions">
    <el-button
      v-for="item in normalizedActions"
      :key="item.action + item.label"
      size="small"
      :type="item.requiresConfirmation ? 'warning' : 'primary'"
      plain
      :disabled="item.disabled"
      @click="$emit('run-action', item)"
    >
      {{ item.label }}
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MapAgentSuggestedAction } from '../../../../api/agent'

const props = defineProps<{ actions?: MapAgentSuggestedAction[] }>()
defineEmits<{ (e: 'run-action', action: MapAgentSuggestedAction): void }>()

const normalizedActions = computed(() => props.actions || [])
</script>

<style scoped>
.map-ai-suggested-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 0;
}
</style>
