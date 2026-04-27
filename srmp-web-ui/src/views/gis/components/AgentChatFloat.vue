<template>
  <transition name="chat">
    <div v-if="visible" class="agent-chat-float srmp-card">
      <div class="chat-header">
        <div><strong>AI 养护助手</strong><p>{{ contextText }}</p></div>
        <button type="button" @click="emit('update:visible', false)">×</button>
      </div>
      <div v-if="activeMapObject" class="map-context-banner">
        <div class="map-context-main"><strong>当前地图上下文</strong><span>{{ mapContextLabel }}</span></div>
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
        <button type="button" @click="quickAsk('生成报告草稿')">报告草稿</button>
      </div>
      <div class="chat-body">
        <div v-for="(item, index) in messages" :key="index" class="msg" :class="item.role">
          <div class="role">{{ item.role === 'user' ? '我' : 'AI' }}</div>
          <div class="content" v-html="renderContent(item.content)"></div>
          <div v-if="item.role === 'assistant' && item.meta" class="answer-meta">
            <span v-if="item.meta.answerSourceLabel">{{ item.meta.answerSourceLabel }}</span>
            <span v-if="item.meta.fallback" class="warn">降级</span>
            <span v-if="item.meta.llmSuccess" class="ok">大模型</span>
          </div>
        </div>
      </div>
      <div v-if="knowledgeSources.length || outlineSources.length" class="sources">
        <span v-for="(item, index) in knowledgeSources" :key="`k-${index}`">知识库：{{ item.title || item.name || item.id }}</span>
        <span v-for="(item, index) in outlineSources" :key="`o-${index}`">Outline：{{ item.title || item.name || item.id }}</span>
      </div>
      <div class="input-row">
        <el-input v-model="input" type="textarea" :rows="2" resize="none" placeholder="例如：分析当前对象，给出养护建议" @keydown.enter.exact.prevent="send" />
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
function mapObjectTypeLabel(type: any) { const value = String(type || '').toUpperCase(); const map: Record<string, string> = { ROAD_ROUTE: '路线', ROAD_SECTION: '路段', EVALUATION_UNIT: '评定单元', ASSESSMENT: '评定结果', ASSESSMENT_RESULT: '评定结果', DISEASE: '病害', DISEASE_RECORD: '病害' }; return map[value] || value || '地图对象' }
function formatStake(start: any, end?: any) { if (start === undefined || start === null || start === '') return ''; const s = `K${start}`; return end !== undefined && end !== null && end !== '' ? `${s}—K${end}` : s }
const mapContextLabel = computed(() => { const obj: any = activeMapObject.value || {}; const type = String(obj.objectType || obj.object_type || '').toUpperCase(); const typeLabel = mapObjectTypeLabel(type); const route = obj.routeCode || obj.route_code || ''; const stake = formatStake(obj.startStake ?? obj.start_stake, obj.endStake ?? obj.end_stake); if (type === 'DISEASE' || type === 'DISEASE_RECORD') { const name = obj.diseaseName || obj.disease_name || obj.diseaseType || obj.disease_type || '病害'; const sev = obj.severity ? `｜${obj.severity}` : ''; return `${typeLabel}｜${name}${sev}${route ? `｜${route}` : ''}${stake ? `｜${stake}` : ''}` } if (type === 'ASSESSMENT' || type === 'ASSESSMENT_RESULT') { const score = obj.mqi !== undefined && obj.mqi !== null ? `｜MQI ${obj.mqi}` : (obj.pci !== undefined && obj.pci !== null ? `｜PCI ${obj.pci}` : ''); return `${typeLabel}${route ? `｜${route}` : ''}${stake ? `｜${stake}` : ''}${score}` } if (type === 'EVALUATION_UNIT') { const unit = obj.unitCode || obj.unit_code || ''; return `${typeLabel}${route ? `｜${route}` : ''}${stake ? `｜${stake}` : ''}${unit ? `｜${unit}` : ''}` } if (type === 'ROAD_SECTION') { const section = obj.sectionName || obj.section_name || obj.sectionCode || obj.section_code || ''; return `${typeLabel}${section ? `｜${section}` : ''}${route ? `｜${route}` : ''}${stake ? `｜${stake}` : ''}` } if (type === 'ROAD_ROUTE') { const name = obj.routeName || obj.route_name || route || '路线'; return `${typeLabel}｜${name}${route && name !== route ? `｜${route}` : ''}` } return `${typeLabel}${route ? `｜${route}` : ''}${stake ? `｜${stake}` : ''}` })
const contextText = computed(() => activeMapObject.value ? mapContextLabel.value : `${props.context?.query?.routeCode || '全部路线'}｜${props.context?.query?.year || '全部年度'}`)
watch(() => props.autoQuestion, async (question) => { const text = String(question || '').trim(); if (!props.visible || !text || loading.value) return; input.value = text; await nextTick(); await send(); emit('auto-question-consumed') }, { immediate: true })
function analyzeCurrentObject() { if (!activeMapObject.value) return; quickAsk('分析当前地图选中对象，说明主要问题、成因判断，并给出养护处置建议') }
function suggestForCurrentObject() { if (!activeMapObject.value) return; quickAsk('基于当前地图选中对象，生成养护处置建议和优先级判断') }
function quickAsk(text: string) { input.value = text; send() }
async function send() { const text = input.value.trim(); if (!text || loading.value) return; messages.value.push({ role: 'user', content: text }); input.value = ''; loading.value = true; try { const resp: any = await chat({ message: text, context: props.context, mapObject: activeMapObject.value, options }); const data = resp?.data || {}; const answer = resp?.answer || data?.answer || '未返回内容'; const meta = data.answerMeta || { answerSourceLabel: data.answerSourceLabel, answerSource: data.answerSource, llmSuccess: data.llmSuccess, fallback: data.fallback, fallbackReason: data.fallbackReason }; messages.value.push({ role: 'assistant', content: answer, meta }); knowledgeSources.value = data.knowledgeSources || []; outlineSources.value = data.outlineSources || [] } catch (e: any) { messages.value.push({ role: 'assistant', content: `请求失败：${e?.message || e}` }) } finally { loading.value = false } }
function escapeHtml(value: string) { return String(value || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;') }
function renderContent(content: string) { let html = escapeHtml(content || ''); html = html.replace(/^### (.*)$/gm, '<h4>$1</h4>'); html = html.replace(/^## (.*)$/gm, '<h3>$1</h3>'); html = html.replace(/^# (.*)$/gm, '<h2>$1</h2>'); html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>'); html = html.replace(/^- (.*)$/gm, '<div class="md-li">• $1</div>'); html = html.replace(/^(\d+)\. (.*)$/gm, '<div class="md-li">$1. $2</div>'); return html.replace(/\n/g, '<br />') }
</script>

<style scoped>
.agent-chat-float { position: absolute; right: 18px; top: 88px; width: 430px; max-height: calc(100vh - 120px); z-index: 900; padding: 12px; display: flex; flex-direction: column; background: #fff; border-radius: 16px; box-shadow: 0 18px 45px rgba(15, 23, 42, .18); }
.chat-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 10px; }
.chat-header strong { font-size: 16px; color: #0f172a; }
.chat-header p { margin: 2px 0 0; color: #64748b; font-size: 12px; }
.chat-header button { border: 0; background: transparent; font-size: 22px; cursor: pointer; color: #64748b; }
.option-row { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
.quick-list { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 10px; }
.quick-list button { border: 1px solid #dbeafe; color: #2563eb; background: #eff6ff; border-radius: 999px; padding: 4px 10px; cursor: pointer; font-size: 12px; }
.map-context-banner { display: flex; justify-content: space-between; align-items: center; gap: 10px; margin-bottom: 8px; padding: 8px 10px; border-radius: 10px; background: #eff6ff; color: #1d4ed8; font-size: 12px; }
.map-context-main { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.map-context-main span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.map-context-actions { display: flex; gap: 6px; flex-shrink: 0; }
.chat-body { overflow: auto; max-height: 430px; padding-right: 4px; display: flex; flex-direction: column; gap: 10px; }
.msg { border-radius: 12px; padding: 9px 10px; background: #f8fafc; color: #0f172a; }
.msg.user { background: #ecfeff; }
.role { font-size: 12px; color: #64748b; margin-bottom: 4px; }
.content { white-space: normal; line-height: 1.65; font-size: 13px; }
.answer-meta { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 8px; color: #64748b; font-size: 12px; }
.answer-meta span { border-radius: 999px; background: #e2e8f0; padding: 2px 7px; }
.answer-meta .warn { background: #fef3c7; color: #92400e; }
.answer-meta .ok { background: #dcfce7; color: #166534; }
.sources { display: flex; flex-direction: column; gap: 4px; margin-top: 8px; color: #64748b; font-size: 12px; }
.input-row { display: flex; gap: 8px; align-items: flex-end; margin-top: 10px; }
.input-row .el-button { height: 54px; }
@media (max-width: 900px) { .agent-chat-float { left: 12px; right: 12px; width: auto; } .map-context-banner { align-items: flex-start; flex-direction: column; } }
</style>
