<template>
  <section v-if="visibleResult" class="map-ai-action-result">
    <div class="result-head">
      <strong>{{ visibleResult.title || visibleResult.type }}</strong>
      <el-tag size="small" :type="tagType">{{ visibleResult.status }}</el-tag>
    </div>
    <p v-if="visibleResult.errorMessage" class="error">{{ visibleResult.errorMessage }}</p>
    <article v-if="visibleResult.markdown" class="markdown">{{ visibleResult.markdown }}</article>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MapAgentActionResult } from '../../../../api/agent'

const props = defineProps<{ result?: MapAgentActionResult | null }>()

const visibleResult = computed(() => {
  if (!props.result) return null
  if (!props.result.markdown && !props.result.errorMessage) return null
  return props.result
})

const tagType = computed(() => {
  if (visibleResult.value?.status === 'SUCCESS') return 'success'
  if (visibleResult.value?.status === 'NEEDS_CONFIRMATION') return 'warning'
  return 'danger'
})
</script>

<style scoped>
.map-ai-action-result {
  margin: 10px 14px 0;
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}
.result-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}
.error {
  color: #dc2626;
}
.markdown {
  max-height: 180px;
  overflow: auto;
  white-space: pre-wrap;
  line-height: 1.6;
}
</style>
