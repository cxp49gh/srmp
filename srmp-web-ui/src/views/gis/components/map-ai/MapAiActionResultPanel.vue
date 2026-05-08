<template>
  <section v-if="result" class="map-ai-action-result">
    <div class="result-head">
      <strong>{{ result.title || result.type }}</strong>
      <el-tag size="small" :type="tagType">{{ result.status }}</el-tag>
    </div>
    <p v-if="result.errorMessage" class="error">{{ result.errorMessage }}</p>
    <article v-if="result.markdown" class="markdown">{{ result.markdown }}</article>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MapAgentActionResult } from '../../../../api/agent'

const props = defineProps<{ result?: MapAgentActionResult | null }>()

const tagType = computed(() => {
  if (props.result?.status === 'SUCCESS') return 'success'
  if (props.result?.status === 'NEEDS_CONFIRMATION') return 'warning'
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
