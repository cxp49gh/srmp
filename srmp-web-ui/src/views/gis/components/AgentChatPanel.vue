<template><div class="chat"><div class="title">AI 分析助手</div><div class="msgs"><div v-for="(m,i) in messages" :key="i" :class="['msg',m.role]"><b>{{ m.role==='user'?'我':'AI' }}：</b><span>{{ m.content }}</span></div></div><div class="input"><el-input v-model="input" type="textarea" :rows="2" placeholder="分析 G210 2026 年整体路况" /><el-button type="primary" :loading="loading" @click="send">发送</el-button></div></div></template>
<script setup lang="ts">
import { ref } from 'vue'
import { chat } from '../../../api/agent'
const props = defineProps<{ context: Record<string, any> }>()
const input = ref(''); const loading = ref(false); const messages = ref<Array<{role:'user'|'assistant';content:string}>>([])
async function send(){ const text=input.value.trim(); if(!text) return; messages.value.push({role:'user',content:text}); input.value=''; loading.value=true; try{ const r=await chat({message:text,context:props.context}); messages.value.push({role:'assistant',content:r?.answer || JSON.stringify(r)}) }catch(e:any){messages.value.push({role:'assistant',content:e?.message||'请求失败'})} finally{loading.value=false} }
</script>
<style scoped>.chat{height:300px;display:flex;flex-direction:column;padding:12px;background:rgba(255,255,255,.96);border-radius:8px;box-shadow:0 4px 16px rgba(15,23,42,.12)}.title{font-weight:700;margin-bottom:8px}.msgs{flex:1;overflow:auto}.msg{font-size:13px;line-height:1.5;margin-bottom:8px}.input{display:flex;gap:8px;align-items:flex-end}</style>
