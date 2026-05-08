<template>
  <section class="map-ai-conversation">
    <div v-for="(item, index) in messages" :key="index" :class="['message', item.role]">
      <div class="role">{{ item.role === 'user' ? '我' : 'AI' }}</div>
      <div class="content">{{ item.content }}</div>
      <el-button v-if="item.role === 'assistant' && item.trace" size="small" text @click="$emit('open-trace', item)">Trace</el-button>
    </div>
    <div class="send-row">
      <el-input v-model="draft" type="textarea" :rows="2" @keydown.ctrl.enter="send" />
      <el-button type="primary" @click="send">发送</el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'

defineProps<{ messages: Array<Record<string, any>> }>()
const emit = defineEmits<{ (e: 'send', text: string): void; (e: 'open-trace', message: Record<string, any>): void }>()
const draft = ref('')

function send() {
  const text = draft.value.trim()
  if (!text) return
  draft.value = ''
  emit('send', text)
}
</script>

<style scoped>
.map-ai-conversation {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.message {
  font-size: 13px;
  line-height: 1.5;
}
.send-row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
</style>
