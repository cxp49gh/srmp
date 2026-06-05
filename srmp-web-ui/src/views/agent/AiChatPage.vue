<template>
  <AgentPageShell
    title="AI 问答"
    description="支持业务数据、知识库、Outline 的混合问答。"
  >
    <div class="chat-page">
      <el-card class="config-card">
        <template #header>问答配置</template>

        <el-form label-width="100px" size="default">
          <el-form-item label="路线编号">
            <el-input v-model="context.routeCode" placeholder="G210" />
          </el-form-item>
          <el-form-item label="指标">
            <el-select v-model="context.indexCode" clearable placeholder="选择指标">
              <el-option label="MQI" value="MQI" />
              <el-option label="PQI" value="PQI" />
              <el-option label="PCI" value="PCI" />
              <el-option label="RQI" value="RQI" />
              <el-option label="RDI" value="RDI" />
            </el-select>
          </el-form-item>
          <el-form-item label="能力开关">
            <div class="switch-list">
              <el-checkbox v-model="options.useBusinessData">业务数据</el-checkbox>
              <el-checkbox v-model="options.useKnowledge">知识库</el-checkbox>
              <el-checkbox v-model="options.useOutline">Outline</el-checkbox>
            </div>
          </el-form-item>
          <el-form-item label="TopK">
            <el-input-number v-model="options.topK" :min="1" :max="20" />
          </el-form-item>
        </el-form>

        <div class="quick-list">
          <el-button size="small" @click="quickAsk('分析当前项目 G210 整体路况')">分析路线</el-button>
          <el-button size="small" @click="quickAsk('PCI 指标是什么意思？')">解释 PCI</el-button>
          <el-button size="small" @click="quickAsk('数据导入模板怎么使用？')">导入模板</el-button>
          <el-button size="small" @click="quickAsk('根据知识库解释 PCI 指标，并结合当前项目 G210 情况给出建议')">混合问答</el-button>
        </div>
      </el-card>

      <el-card class="chat-card">
        <template #header>对话</template>

        <div class="messages">
          <div v-for="(item, index) in messages" :key="index" :class="['message', item.role]">
            <div class="role">{{ item.role === 'user' ? '我' : 'AI' }}</div>
            <div class="content">{{ item.content }}</div>
            <AiTraceButton
              v-if="item.role === 'assistant'"
              :trace="item.trace"
              :execution="{ trace: item.trace, answerMeta: item.answerMeta, toolResults: item.toolResults, sources: item.sources }"
              class="trace-button"
              @open="openTrace"
            />
          </div>
        </div>

        <div class="input-row">
          <el-input
            v-model="message"
            type="textarea"
            :rows="3"
            placeholder="请输入问题，Ctrl + Enter 发送"
            @keydown.ctrl.enter="send"
          />
          <el-button type="primary" :loading="loading" @click="send">发送</el-button>
        </div>
      </el-card>

      <el-card class="source-card">
        <template #header>引用来源</template>
        <AiSourceList
          :sources="activeSources"
          :tool-results="activeToolResults"
          :outline-sources="activeOutlineSources"
          :question="lastQuestion"
          :business-context="businessContext"
        />
      </el-card>
    </div>
    <AiTraceDrawer
      v-model:visible="traceDrawerVisible"
      :trace="activeExecution?.trace || null"
      :answer-meta="activeExecution?.answerMeta || null"
      :tool-results="activeExecution?.toolResults || []"
      :sources="activeExecution?.sources || []"
    />
  </AgentPageShell>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import AgentPageShell from './components/AgentPageShell.vue'
import AiSourceList from './components/AiSourceList.vue'
import AiTraceButton from './components/AiTraceButton.vue'
import AiTraceDrawer from './components/AiTraceDrawer.vue'
import { mapAgentRun } from '../../api/agent'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  trace?: Record<string, any> | null
  answerMeta?: Record<string, any> | null
  toolResults?: any[]
  sources?: any[]
}

const context = ref<Record<string, any>>({
  routeCode: 'G210',
  indexCode: 'PCI'
})

const options = ref<Record<string, any>>({
  useBusinessData: true,
  useKnowledge: true,
  useOutline: false,
  topK: 5
})

const message = ref('')
const loading = ref(false)
const messages = ref<ChatMessage[]>([])
const activeSources = ref<any[]>([])
const activeToolResults = ref<any[]>([])
const activeOutlineSources = ref<any[]>([])
const lastQuestion = ref('')
const traceDrawerVisible = ref(false)
const activeExecution = ref<Record<string, any> | null>(null)

const businessContext = computed(() => ({
  routeCode: context.value.routeCode,
  indexCode: context.value.indexCode,
  scope: 'AI_CHAT'
}))

function quickAsk(text: string) {
  message.value = text
  send()
}

async function send() {
  const text = message.value.trim()
  if (!text) return

  messages.value.push({ role: 'user', content: text })
  lastQuestion.value = text
  message.value = ''
  loading.value = true

  try {
    const result = await mapAgentRun({
      action: 'CHAT',
      message: text,
      mapContext: {
        mode: 'ROUTE',
        routeCode: context.value.routeCode,
        extra: {
          indexCode: context.value.indexCode
        }
      },
      options: options.value
    })

    const data = result?.data || {}
    const actionResult = (result?.actionResult || {}) as Record<string, any>
    const sources = result?.sources || result?.knowledgeSources || data.sources || data.knowledgeSources || []
    messages.value.push({
      role: 'assistant',
      content: result?.answer || actionResult.markdown || JSON.stringify(result),
      trace: result?.trace || data.trace || null,
      answerMeta: result?.answerMeta || data.answerMeta || null,
      toolResults: result?.toolResults || data.toolResults || [],
      sources
    })

    activeSources.value = sources
    activeToolResults.value = result?.toolResults || data.toolResults || []
    activeOutlineSources.value = data.outlineSources || (result as any)?.outlineSources || []
  } catch (error: any) {
    messages.value.push({
      role: 'assistant',
      content: error?.message || 'AI 问答请求失败'
    })
  } finally {
    loading.value = false
  }
}

function openTrace(execution: Record<string, any>) {
  activeExecution.value = execution
  traceDrawerVisible.value = true
}
</script>

<style scoped>
.chat-page {
  display: grid;
  grid-template-columns: 320px minmax(420px, 1fr) 360px;
  gap: 16px;
  align-items: stretch;
}

.config-card,
.chat-card,
.source-card {
  min-height: calc(100vh - 130px);
}

.switch-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.quick-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.messages {
  height: calc(100vh - 310px);
  overflow: auto;
  padding-right: 8px;
}

.message {
  margin-bottom: 12px;
}

.role {
  color: #64748b;
  font-size: 12px;
  margin-bottom: 4px;
}

.content {
  white-space: pre-wrap;
  background: #f1f5f9;
  padding: 10px;
  border-radius: 10px;
  line-height: 1.6;
}

.trace-button {
  margin-top: 6px;
}

.message.user .content {
  background: #dbeafe;
}

.input-row {
  margin-top: 12px;
  display: flex;
  align-items: flex-end;
  gap: 8px;
}
</style>
