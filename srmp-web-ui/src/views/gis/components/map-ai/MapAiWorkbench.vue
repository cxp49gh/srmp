<template>
  <section class="map-ai-workbench">
    <MapAiContextPanel :scope="contextScope" :context="context" :map-object="mapObject" />
    <MapAiConversation :messages="messages" @send="$emit('send', $event)" @open-trace="$emit('open-trace', $event)" />
    <MapAiActionResultPanel :result="latestActionResult" />
    <MapAiSuggestedActions :actions="latestSuggestedActions" @run-action="$emit('run-action', $event)" />
  </section>
</template>

<script setup lang="ts">
import type { MapAgentActionResult, MapAgentSuggestedAction } from '../../../../api/agent'
import MapAiActionResultPanel from './MapAiActionResultPanel.vue'
import MapAiContextPanel from './MapAiContextPanel.vue'
import MapAiConversation from './MapAiConversation.vue'
import MapAiSuggestedActions from './MapAiSuggestedActions.vue'

defineProps<{
  contextScope?: string
  context?: Record<string, any>
  mapObject?: Record<string, any> | null
  messages: Array<Record<string, any>>
  latestActionResult?: MapAgentActionResult | null
  latestSuggestedActions?: MapAgentSuggestedAction[]
}>()
defineEmits<{
  (e: 'send', text: string): void
  (e: 'open-trace', message: Record<string, any>): void
  (e: 'run-action', action: MapAgentSuggestedAction): void
}>()
</script>

<style scoped>
.map-ai-workbench {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
