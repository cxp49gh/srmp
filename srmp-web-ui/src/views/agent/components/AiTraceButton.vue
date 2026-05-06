<template>
  <el-button v-if="enabled" size="small" plain @click="openTrace">
    AI 执行过程
  </el-button>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  trace?: Record<string, any> | null
  execution?: Record<string, any> | null
}>()

const emit = defineEmits<{
  (e: 'open', value: Record<string, any>): void
}>()

const enabled = computed(() => Boolean(props.execution || props.trace?.traceId || props.trace?.trace_id || props.trace?.steps))

function openTrace() {
  emit('open', props.execution || { trace: props.trace })
}
</script>
