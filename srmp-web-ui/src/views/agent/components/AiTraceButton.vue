<template>
  <el-button v-if="enabled" size="small" plain @click="openTrace">
    {{ label || 'AI 执行过程' }}
  </el-button>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  trace?: Record<string, any> | null
  execution?: Record<string, any> | null
  label?: string
}>()

const emit = defineEmits<{
  (e: 'open', value: Record<string, any>): void
}>()

const enabled = computed(() => hasExecution(props.execution) || Boolean(props.trace?.traceId || props.trace?.trace_id || props.trace?.steps))

function openTrace() {
  emit('open', props.execution || { trace: props.trace })
}

function hasExecution(value?: Record<string, any> | null) {
  if (!value) return false
  const trace = value.trace
  if (trace?.traceId || trace?.trace_id || trace?.steps) return true
  if (value.record || value.replayResult || value.solution) return true
  if (value.answerMeta && Object.keys(value.answerMeta).length) return true
  if (Array.isArray(value.toolResults) && value.toolResults.length) return true
  if (Array.isArray(value.sources) && value.sources.length) return true
  return false
}
</script>
