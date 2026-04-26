<template>
  <div class="agent-chat srmp-card">
    <div class="panel-header">
      <span>AI 分析助手</span>
      <el-button link size="small" @click="messages = []">清空</el-button>
    </div>

    <div class="message-list">
      <div
        v-for="(item, index) in messages"
        :key="index"
        :class="['message', item.role]"
      >
        <div class="role">{{ item.role === 'user' ? '我' : 'AI' }}</div>
        <div class="content">{{ item.content }}</div>
      </div>
    </div>

    <div class="input-row">
      <el-input
        v-model="input"
        type="textarea"
        :rows="2"
        placeholder="例如：分析当前路线病害情况"
        @keydown.ctrl.enter="send"
      />
      <el-button type="primary" :loading="loading" @click="send">发送</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { chat } from '../../../api/agent'

const props = defineProps<{
  context: Record<string, any>
}>()

const input = ref('')
const loading = ref(false)
const messages = ref<Array<{ role: 'user' | 'assistant'; content: string }>>([])

async function send() {
  const text = input.value.trim()
  if (!text) return
  messages.value.push({ role: 'user', content: text })
  input.value = ''
  loading.value = true
  try {
    const result = await chat({
      message: text,
      context: props.context
    })
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
.agent-chat {
  display: flex;
  flex-direction: column;
  height: 320px;
  padding: 12px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  font-weight: 700;
  margin-bottom: 8px;
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
</style>