<template>
  <section class="map-ai-conversation">
    <div class="conversation-message-list">
      <div v-for="(item, index) in messages" :key="index" :class="['message', item.role]">
        <div class="role">{{ item.role === 'user' ? '我' : 'AI' }}</div>
        <div class="content" v-html="renderMarkdown(item.content)" />
        <div v-if="item.meta" class="message-meta">
          <el-tag v-if="item.meta.mapObjectUsed" size="small" type="success">对象上下文</el-tag>
          <el-tag v-if="item.meta.regionUsed || item.meta.mapRegionUsed" size="small" type="success">区域上下文</el-tag>
          <el-tag v-if="item.meta.intent" size="small" type="info">{{ item.meta.intent }}</el-tag>
          <el-tag v-if="item.meta.answerSourceLabel" size="small">{{ item.meta.answerSourceLabel }}</el-tag>
          <el-tag
            v-if="item.meta.planExecutionStatus && item.meta.planExecutionStatus !== 'NO_PLAN'"
            size="small"
            :type="planExecutionTagType(item.meta.planExecutionStatus)"
          >计划 {{ item.meta.planExecutionStatus }}</el-tag>
          <el-tag v-if="item.meta.runElapsed" size="small" type="info">耗时 {{ item.meta.runElapsed }}</el-tag>
          <el-tag v-if="item.meta.llmStatus" size="small" :type="item.meta.llmStatus === 'SUCCESS' ? 'success' : 'warning'">LLM {{ item.meta.llmStatus }}</el-tag>
          <el-tag v-if="item.meta.llmModel" size="small" type="info">{{ item.meta.llmModel }}</el-tag>
          <el-tag v-if="item.toolResults?.length" size="small" type="info">工具 {{ successfulTools(item.toolResults) }}/{{ item.toolResults.length }}</el-tag>
          <el-tag v-if="item.sources?.length" size="small" type="info">来源 {{ item.sources.length }}</el-tag>
          <el-tag v-if="item.meta.retriedWithCompactPrompt" size="small" type="warning">压缩重试</el-tag>
          <el-tag v-if="item.meta.fallback" size="small" type="warning">降级</el-tag>
        </div>
        <AiEvidencePanel
          v-if="item.role === 'assistant'"
          :message="item"
          :map-context="mapContext"
          @locate-source="$emit('locate-source', $event)"
          @ask-with-source="$emit('ask-with-source', $event)"
        />
        <div v-if="item.role === 'assistant' && contextScope === 'OBJECT' && mapObject" class="assistant-action-row">
          <el-button
            size="small"
            type="success"
            plain
            :loading="solutionLoading"
            :disabled="loading || solutionLoading"
            @click="$emit('generate-default-solution')"
          >
            生成结构化建议
          </el-button>
        </div>
        <AiTraceButton
          v-if="item.role === 'assistant'"
          :trace="item.trace"
          :execution="{ trace: item.trace, answerMeta: item.meta, toolResults: item.toolResults, sources: item.sources }"
          class="trace-button"
          @open="$emit('open-trace', $event)"
        />
      </div>
    </div>
    <div class="send-row">
      <el-input
        :model-value="input"
        type="textarea"
        :rows="2"
        placeholder="例如：分析当前对象，给出养护建议"
        @update:model-value="$emit('update:input', String($event))"
        @keydown.ctrl.enter="send"
      />
      <el-button type="primary" :loading="loading" @click="send">发送</el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import AiTraceButton from '../../../agent/components/AiTraceButton.vue'
import AiEvidencePanel from '../AiEvidencePanel.vue'

const props = defineProps<{
  messages: Array<Record<string, any>>
  input: string
  loading?: boolean
  solutionLoading?: boolean
  contextScope?: string
  mapContext?: Record<string, any>
  mapObject?: Record<string, any> | null
}>()

const emit = defineEmits<{
  (e: 'update:input', value: string): void
  (e: 'send', text: string): void
  (e: 'open-trace', execution: Record<string, any>): void
  (e: 'locate-source', source: any): void
  (e: 'ask-with-source', source: any): void
  (e: 'generate-default-solution'): void
}>()

function send() {
  const text = props.input.trim()
  if (!text) return
  emit('send', text)
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function renderMarkdown(value: string) {
  const escaped = escapeHtml(value || '')
  return escaped
    .replace(/^### (.*)$/gm, '<h4>$1</h4>')
    .replace(/^## (.*)$/gm, '<h3>$1</h3>')
    .replace(/^# (.*)$/gm, '<h2>$1</h2>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/^\- (.*)$/gm, '<div class="md-list">• $1</div>')
    .replace(/\n/g, '<br />')
}

function successfulTools(tools: any[] = []) {
  return tools.filter((item) => item?.success !== false && String(item?.status || '').toUpperCase() !== 'FAILED').length
}

function planExecutionTagType(status: string) {
  if (status === 'MATCHED') return 'success'
  if (status === 'DIVERGED') return 'danger'
  if (status === 'PARTIAL') return 'warning'
  return 'info'
}
</script>

<style scoped>
.map-ai-conversation {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: hidden;
}

.conversation-message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}

.message {
  margin-bottom: 12px;
  font-size: 13px;
  line-height: 1.5;
}

.role {
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
}

.content {
  border-radius: 12px;
  padding: 10px 12px;
  line-height: 1.65;
  font-size: 13px;
  white-space: normal;
  word-break: break-word;
}

.message.user .content {
  background: #eff6ff;
  color: #1e3a8a;
}

.message.assistant .content {
  background: #f8fafc;
  color: #0f172a;
}

.content :deep(h2),
.content :deep(h3),
.content :deep(h4) {
  margin: 8px 0 4px;
  line-height: 1.35;
}

.content :deep(code) {
  padding: 1px 4px;
  border-radius: 4px;
  background: #e2e8f0;
}

.content :deep(.md-list) {
  margin-left: 4px;
}

.message-meta {
  display: flex;
  gap: 6px;
  margin-top: 6px;
  flex-wrap: wrap;
}

.assistant-action-row {
  margin-top: 8px;
  display: flex;
  justify-content: flex-start;
}

.trace-button {
  margin-top: 6px;
}

.send-row {
  flex-shrink: 0;
  position: sticky;
  bottom: 0;
  z-index: 2;
  display: grid;
  grid-template-columns: 1fr 70px;
  gap: 8px;
  margin-top: 8px;
  padding-top: 8px;
  background: rgba(255, 255, 255, 0.96);
}
</style>
