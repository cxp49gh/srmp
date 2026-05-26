<template>
  <section class="map-ai-workbench">
    <MapAiConversation
      :messages="messages"
      :input="input"
      :loading="loading"
      :solution-loading="solutionLoading"
      :context-scope="contextScope"
      :map-context="context"
      :map-object="mapObject"
      @update:input="$emit('update:input', $event)"
      @send="$emit('send', $event)"
      @open-trace="$emit('open-trace', $event)"
      @locate-source="$emit('locate-source', $event)"
      @ask-with-source="$emit('ask-with-source', $event)"
    >
      <template #message-tail>
        <MapAiActionResultPanel :result="latestActionResult" />
        <slot name="message-tail" />
      </template>
    </MapAiConversation>
  </section>
</template>

<script setup lang="ts">
import type { MapAgentActionResult } from '../../../../api/agent'
import MapAiActionResultPanel from './MapAiActionResultPanel.vue'
import MapAiConversation from './MapAiConversation.vue'

defineProps<{
  contextScope?: string
  context?: Record<string, any>
  mapObject?: Record<string, any> | null
  messages: Array<Record<string, any>>
  input: string
  loading?: boolean
  solutionLoading?: boolean
  latestActionResult?: MapAgentActionResult | null
}>()
defineEmits<{
  (e: 'update:input', value: string): void
  (e: 'send', text: string): void
  (e: 'open-trace', message: Record<string, any>): void
  (e: 'locate-source', source: any): void
  (e: 'ask-with-source', source: any): void
}>()
</script>

<style scoped>
.map-ai-workbench {
  flex: 1 1 180px;
  min-height: 180px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: hidden;
}
</style>
