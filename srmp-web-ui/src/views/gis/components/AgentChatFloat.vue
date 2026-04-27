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
      <div v-if="activeMapObject" class="map-context-banner">
        <div><strong>当前地图上下文</strong><span>{{ mapContextLabel }}</span></div>
        <div class="map-context-actions">
          <el-button size="small" type="primary" plain :loading="loading" @click="analyzeCurrentObject">重新分析当前对象</el-button>
          <el-button size="small" plain :loading="loading" @click="suggestForCurrentObject">生成处置建议</el-button>
        </div>
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
          <div v-if="item.meta" class="message-meta">
            <el-tag v-if="item.meta.mapObjectUsed" size="small" type="success">地图上下文</el-tag>
            <el-tag v-if="item.meta.answerSourceLabel" size="small">{{ item.meta.answerSourceLabel }}</el-tag>
            <el-tag v-if="item.meta.fallback" size="small" type="warning">降级</el-tag>
          </div>
        </div>
      </div>
      <div v-if="sourceSummary" class="source-summary">{{ sourceSummary }}</div>
      <div class="input-row">
        <el-input v-model="input" type="textarea" :rows="2" placeholder="请输入问题，Ctrl + Enter 发送" @keydown.ctrl.enter="send" />
        <el-button type="primary" :loading="loading" @click="send">发送</el-button>
      </div>
    </div>
  </transition>
</template>
<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { chat } from '../../../api/agent'
const props = defineProps<{ visible: boolean; context: Record<string, any>; mapObject?: Record<string, any> | null; autoQuestion?: string }>()
const emit = defineEmits<{ (e: 'update:visible', value: boolean): void; (e: 'auto-question-consumed'): void }>()
const input = ref('')
const loading = ref(false)
const messages = ref<Array<{ role: 'user' | 'assistant'; content: string; meta?: any }>>([])
const knowledgeSources = ref<any[]>([])
const outlineSources = ref<any[]>([])
const options = reactive({ useBusinessData: true, useKnowledge: true, useOutline: false, topK: 5 })
const activeMapObject = computed(() => props.mapObject || props.context?.mapObject || props.context?.selectedMapObject || props.context?.selected || null)
const mapContextLabel = computed(() => { const obj: any = activeMapObject.value || {}; return obj.routeCode || obj.route_code || obj.objectType || obj.object_type || '已选中对象' })
const contextText = computed(() => { const query = props.context?.query || {}; const selected: any = activeMapObject.value; const route = query.routeCode || selected?.routeCode || selected?.route_code || '全部路线'; const year = query.year || selected?.year || '全部年度'; const selectedText = selected ? `｜已选 ${selected.objectType || selected.object_type || selected.routeCode || selected.route_code || '地图对象'}` : ''; return `${route}｜${year}${selectedText}` })
const sourceSummary = computed(() => { const k = knowledgeSources.value.length; const o = outlineSources.value.length; if (k === 0 && o === 0) return ''; return `引用来源：知识库 ${k} 条，Outline ${o} 条` })
watch(() => props.autoQuestion, async (question) => { const text = String(question || '').trim(); if (!props.visible || !text || loading.value) return; input.value = text; await nextTick(); await send(); emit('auto-question-consumed') }, { immediate: true })
function analyzeCurrentObject() { if (!activeMapObject.value) return; quickAsk('分析当前地图选中对象，说明主要问题、成因判断，并给出养护处置建议') }
function suggestForCurrentObject() { if (!activeMapObject.value) return; quickAsk('基于当前地图选中对象，生成养护处置建议和优先级判断') }
function quickAsk(text: string) { input.value = text; send() }
async function send() { const text = input.value.trim(); if (!text) return; messages.value.push({ role: 'user', content: text }); input.value = ''; loading.value = true; try { const result = await chat({ message: text, context: { ...(props.context || {}), mapObject: activeMapObject.value, selectedMapObject: activeMapObject.value }, options, mapObject: activeMapObject.value }); const meta = { mapObjectUsed: result?.data?.mapObjectUsed || result?.mapObjectUsed, answerSourceLabel: result?.data?.answerSourceLabel || result?.data?.answerMeta?.answerSourceLabel, fallback: result?.data?.fallback || result?.data?.answerMeta?.fallback }; messages.value.push({ role: 'assistant', content: result?.answer || JSON.stringify(result), meta }); knowledgeSources.value = result?.data?.knowledgeSources || []; outlineSources.value = result?.data?.outlineSources || [] } catch (error: any) { messages.value.push({ role: 'assistant', content: error?.message || 'AI 分析请求失败' }) } finally { loading.value = false } }
</script>
<style scoped>
.agent-chat-float { position: absolute; right: 24px; bottom: 84px; z-index: 940; display: flex; flex-direction: column; width: 420px; height: 560px; padding: 12px; border: 1px solid rgba(226, 232, 240, 0.9); background: rgba(255,255,255,.97); }
.chat-header { display: flex; justify-content: space-between; gap: 8px; margin-bottom: 8px; }
.chat-header p { margin: 4px 0 0; color: #64748b; font-size: 12px; }
.chat-header button { border: none; background: transparent; font-size: 22px; color: #64748b; cursor: pointer; }
.map-context-banner { display: flex; justify-content: space-between; align-items: center; gap: 10px; margin-bottom: 8px; padding: 8px 10px; border-radius: 10px; background: #eff6ff; color: #1d4ed8; font-size: 12px; }
.map-context-banner strong { margin-right: 8px; }
.map-context-actions { display: flex; gap: 6px; flex-shrink: 0; }
.option-row { display: flex; gap: 10px; margin-bottom: 8px; }
.quick-list { display: grid; grid-template-columns: repeat(4, 1fr); gap: 4px; margin-bottom: 8px; }
.quick-list button { border: 1px solid #dbeafe; border-radius: 999px; padding: 4px 6px; background: #eff6ff; color: #2563eb; font-size: 12px; cursor: pointer; }
.message-list { flex: 1; overflow: auto; padding-right: 4px; }
.message { margin-bottom: 10px; }
.role { color: #64748b; font-size: 12px; margin-bottom: 2px; }
.content { background: #f1f5f9; border-radius: 8px; padding: 8px; white-space: pre-wrap; line-height: 1.5; font-size: 13px; }
.message.user .content { background: #dbeafe; }
.message-meta { display: flex; gap: 6px; margin-top: 4px; }
.source-summary { margin-top: 6px; color: #64748b; font-size: 12px; }
.input-row { display: flex; gap: 8px; margin-top: 8px; align-items: flex-end; }
</style>
