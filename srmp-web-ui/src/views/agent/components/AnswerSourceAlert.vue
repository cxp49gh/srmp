<template>
  <el-alert
    v-if="visible"
    class="answer-source-alert"
    :type="alertType"
    show-icon
    :closable="false"
  >
    <template #title>{{ title }}</template>
    <div class="notice">{{ notice }}</div>
    <div class="meta-row">
      <el-tag v-if="meta.answerSource" size="small">{{ meta.answerSource }}</el-tag>
      <el-tag v-if="meta.llmStatus" size="small" :type="meta.llmSuccess ? 'success' : 'warning'">LLM {{ meta.llmStatus }}</el-tag>
      <el-tag v-if="meta.llmModel" size="small" type="info">{{ meta.llmModel }}</el-tag>
      <el-tag v-if="meta.retriedWithCompactPrompt" size="small" type="warning">压缩重试</el-tag>
      <el-tag v-if="meta.qualityFallback" size="small" type="warning">质量兜底</el-tag>
    </div>
  </el-alert>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ meta?: Record<string, any> | null; allowEmpty?: boolean }>()

const meta = computed(() => props.meta || {})
const hasMeta = computed(() => Object.keys(meta.value).length > 0)
const visible = computed(() => props.allowEmpty || hasMeta.value)

const alertType = computed(() => {
  if (!hasMeta.value) return 'info'
  if (meta.value.qualityFallback) return 'warning'
  if (meta.value.llmSuccess && meta.value.answerSource === 'LLM') return 'success'
  return 'warning'
})

const title = computed(() => {
  if (!hasMeta.value) return '未返回 answerMeta'
  return meta.value.answerSourceLabel || sourceLabel(meta.value.answerSource)
})

const notice = computed(() => {
  if (!hasMeta.value) return '当前结果缺少 answerMeta，可能是旧任务、旧接口或没有经过 LangGraph 管线。'
  if (meta.value.notice || meta.value.answerNotice) return meta.value.notice || meta.value.answerNotice
  if (meta.value.llmSuccess && meta.value.answerSource === 'LLM') return '本次回答由大模型成功生成。'
  const reason = meta.value.fallbackReason || meta.value.llmStatus || '大模型未返回有效内容'
  return `本次回答经过降级或兜底处理。原因：${reason}`
})

function sourceLabel(source?: string) {
  if (source === 'LLM') return '大模型返回'
  if (source === 'LOCAL_FALLBACK') return '本地知识库/业务数据降级返回'
  if (source === 'BUSINESS_ANALYSIS_FALLBACK') return '业务分析结果降级返回'
  if (source === 'FALLBACK') return '系统降级返回'
  return '回答来源未知'
}
</script>

<style scoped>
.answer-source-alert {
  margin-bottom: 10px;
}

.notice {
  margin-top: 4px;
  line-height: 1.5;
}

.meta-row {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
