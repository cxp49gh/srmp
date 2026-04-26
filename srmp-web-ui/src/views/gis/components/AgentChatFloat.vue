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

      <div class="quick-list">
        <button type="button" @click="quickAsk('分析当前路线整体路况')">分析路线</button>
        <button type="button" @click="quickAsk('找出次差路段')">次差路段</button>
        <button type="button" @click="quickAsk('分析病害热点')">病害热点</button>
        <button type="button" @click="quickAsk('生成评定报告草稿')">报告草稿</button>
      </div>

      <div class="message-list">
        <div v-for="(item, index) in messages" :key="index" :class="['message', item.role]">
          <div class="role">{{ item.role === 'user' ? '我' : 'AI' }}</div>
          <div class="content">{{ item.content }}</div>
        </div>
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
import { computed, ref } from 'vue'
import { chat } from '../../../api/agent'

const props = defineProps<{
  visible: boolean
  context: Record<string, any>
}>()

defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const input = ref('')
const loading = ref(false)
const messages = ref<Array<{ role: 'user' | 'assistant'; content: string }>>([])

const contextText = computed(() => {
  const query = props.context?.query || {}
  const selected = props.context?.selected
  const route = query.routeCode || '全部路线'
  const year = query.year || '全部年度'
  const selectedText = selected?.objectType ? `｜已选 ${selected.objectType}` : ''
  return `${route}｜${year}${selectedText}`
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
    const result = await chat({ message: text, context: props.context })
    messages.value.push({
      role: 'assistant',
      content: result?.answer || JSON.stringify(result)
    })
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
  width: 360px;
  height: 480px;
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

.input-row {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  align-items: flex-end;
}

.chat-enter-active,
.chat-leave-active {
  transition: all 0.2s ease;
}

.chat-enter-from,
.chat-leave-to {
  transform: translateY(10px);
  opacity: 0;
}
</style>
