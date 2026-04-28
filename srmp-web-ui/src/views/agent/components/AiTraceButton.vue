<template>
  <el-button v-if="traceId" size="small" plain @click="openTrace">
    查看 Trace
  </el-button>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  trace?: Record<string, any> | null
}>()

const emit = defineEmits<{
  (e: 'open', value: Record<string, any>): void
}>()

const traceId = computed(() => props.trace?.traceId || props.trace?.trace_id || '')

function openTrace() {
  if (props.trace) {
    emit('open', props.trace)
  }
}
</script>
