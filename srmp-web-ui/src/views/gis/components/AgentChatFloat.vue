<template>
  <transition name="chat">
    <div v-if="visible" class="agent-chat-float srmp-card">
      <div class="chat-header">
        <div>
          <strong>AI 养护助手</strong>
          <p>{{ contextText }}</p>
        </div>
        <button type="button" @click="$emit('update:visible', false)">×</button>
      </div>

      <div class="option-row">
        <el-checkbox v-model="options.useBusinessData">业务数据</el-checkbox>
        <el-checkbox v-model="options.useKnowledge">知识库</el-checkbox>
        <el-checkbox v-model="options.useOutline">Outline</el-checkbox>
      </div>

      <div class="quick-list">
        <button type="button" @click="quickAsk('分析当前路线整体路况')">分析路线</button>
        <button type="button" @click="quickAsk('找出次差路段')">次差路段</button>
        <button type="button" @click="quickAsk('解释 PCI 指标')">解释 PCI</button>
        <button type="button" @click="quickAsk('生成评定报告草稿')">报告草稿</button>
      </div>

      <div class="message-list">
        <div v-for="(item, index) in messages" :key="index" :class="['message', item.role]">
          <div class="role">{{ item.role === 'user' ? '我' : 'AI' }}</div>
          <div class="content">{{ item.content }}</div>
        </div>
      </div>

      <div v-if="sourceSummary" class="source-summary">
        {{ sourceSummary }}
      </div>

      <div class="input-row">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          placeholder="请输入问题，Ctrl + Enter 发送"
          @keydown.ctrl.enter="send"
        />
        <el-button type="primary" :loading="loading" @click="send">发送</el-button>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { chat } from '../../../api/agent'

const props = defineProps<{
  visible: boolean
  context: Record<string, any>
  mapObject?: Record<string, any>
}>()

defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const input = ref('')
const loading = ref(false)
const messages = ref<Array<{ role: 'user' | 'assistant'; content: string }>>([])
const knowledgeSources = ref<any[]>([])
const outlineSources = ref<any[]>([])

const options = reactive({
  useBusinessData: true,
  useKnowledge: true,
  useOutline: false,
  topK: 5
})

const contextText = computed(() => {
  const query = props.context?.query || {}
  const selected = props.context?.selected
  const route = query.routeCode || '全部路线'
  const year = query.year || '全部年度'
  const selectedText = selected?.objectType ? `｜已选 ${selected.objectType}` : ''
  return `${route}｜${year}${selectedText}`
})

const sourceSummary = computed(() => {
  const k = knowledgeSources.value.length
  const o = outlineSources.value.length
  if (k === 0 && o === 0) return ''
  return `引用来源：知识库 ${k} 条，Outline ${o} 条`
})

function quickAsk(text: string) {
  input.value = text
  send()
}

async function send() {
  const text = input.value.trim()
  if (!text) return

  messages.value.push({ role: 'user', content: text })
  input.value = ''
  loading.value = true

  try {
    const result = await chat({
      message: text,
      context: props.context,
      options,
      mapObject: props.mapObject
    })
    messages.value.push({
      role: 'assistant',
      content: result?.answer || JSON.stringify(result)
    })
    knowledgeSources.value = result?.data?.knowledgeSources || []
    outlineSources.value = result?.data?.outlineSources || []
  } catch (error: any) {
    messages.value.push({
      role: 'assistant',
      content: error?.message || 'AI 分析请求失败'
    })
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.agent-chat-float {
  position: absolute;
  right: 24px;
  bottom: 84px;
  z-index: 940;
  display: flex;
  flex-direction: column;
  width: 380px;
  height: 520px;
  padding: 12px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.97);
}

.chat-header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.chat-header p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 12px;
}

.chat-header button {
  border: none;
  background: transparent;
  font-size: 22px;
  color: #64748b;
  cursor: pointer;
}

.option-row {
  display: flex;
  gap: 10px;
  margin-bottom: 8px;
}

.quick-list {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
  margin-bottom: 8px;
}

.quick-list button {
  border: 1px solid #dbeafe;
  border-radius: 999px;
  padding: 4px 6px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  cursor: pointer;
}

.message-list {
  flex: 1;
  overflow: auto;
  padding-right: 4px;
}

.message {
  margin-bottom: 10px;
}

.role {
  color: #64748b;
  font-size: 12px;
  margin-bottom: 2px;
}

.content {
  background: #f1f5f9;
  border-radius: 8px;
  padding: 8px;
  white-space: pre-wrap;
  line-height: 1.5;
  font-size: 13px;
}

.message.user .content {
  background: #dbeafe;
}

.source-summary {
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
}

.input-row {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  align-items: flex-end;
}
</style>