<template>
  <el-alert
    v-if="meta"
    class="answer-source-alert"
    :type="meta.llmSuccess ? 'success' : 'warning'"
    show-icon
    :closable="false"
  >
    <template #title>{{ meta.answerSourceLabel || sourceLabel }}</template>
    <div class="notice">{{ meta.notice || meta.answerNotice || notice }}</div>
  </el-alert>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ meta?: Record<string, any> | null }>()

const sourceLabel = computed(() => {
  const source = props.meta?.answerSource
  if (source === 'LLM') return '大模型返回'
  if (source === 'LOCAL_FALLBACK') return '本地知识库/业务数据降级返回'
  if (source === 'BUSINESS_ANALYSIS_FALLBACK') return '业务分析结果降级返回'
  return '回答来源未知'
})

const notice = computed(() => {
  if (props.meta?.answerSource === 'LLM') return '本次 answer 由大模型成功生成。'
  const reason = props.meta?.fallbackReason || '大模型未返回有效内容'
  return `本次 answer 不是大模型成功返回，而是系统降级生成。原因：${reason}`
})
</script>

<style scoped>
.answer-source-alert { margin-bottom: 10px; }
.notice { margin-top: 4px; line-height: 1.5; }
</style>