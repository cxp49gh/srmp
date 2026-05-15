<template>
  <section class="map-ai-workbench">
    <MapAiContextPanel :scope="contextScope" :context="context" :map-object="mapObject" />
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
      @generate-default-solution="$emit('generate-default-solution')"
    >
      <template #message-tail>
        <MapAiActionResultPanel :result="latestActionResult" />
        <MapAiSuggestedActions
          :actions="latestSuggestedActions"
          @run-action="$emit('run-action', $event)"
          @preview-plan="$emit('preview-plan', $event)"
        />
      </template>
    </MapAiConversation>
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
  input: string
  loading?: boolean
  solutionLoading?: boolean
  latestActionResult?: MapAgentActionResult | null
  latestSuggestedActions?: MapAgentSuggestedAction[]
}>()
defineEmits<{
  (e: 'update:input', value: string): void
  (e: 'send', text: string): void
  (e: 'open-trace', message: Record<string, any>): void
  (e: 'locate-source', source: any): void
  (e: 'ask-with-source', source: any): void
  (e: 'generate-default-solution'): void
  (e: 'run-action', action: MapAgentSuggestedAction): void
  (e: 'preview-plan', action: MapAgentSuggestedAction): void
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
