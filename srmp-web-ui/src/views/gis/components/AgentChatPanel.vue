<template>
  <div class="chat">
    <div class="title">AI 分析助手</div>
    <div v-if="mapObject && Object.keys(mapObject).length" class="map-context">
      <div class="context-title">📍 当前地图对象</div>
      <div class="context-info">
        <span v-if="mapObject.objectType">类型：{{ mapObject.objectType }}</span>
        <span v-if="mapObject.routeCode">路线：{{ mapObject.routeCode }}</span>
        <span v-if="mapObject.startStake && mapObject.endStake">桩号：{{ mapObject.startStake }} ~ {{ mapObject.endStake }}</span>
        <span v-if="mapObject.grade">等级：{{ mapObject.grade }}</span>
      </div>
    </div>
    <div class="msgs">
      <div v-for="(m,i) in messages" :key="i" :class="['msg',m.role]"><b>{{ m.role==='user'?'我':'AI' }}：</b><span>{{ m.content }}</span></div>
    </div>
    <div class="input">
      <el-input v-model="input" type="textarea" :rows="2" placeholder="分析 G210 2026 年整体路况" /><el-button type="primary" :loading="loading" @click="send">发送</el-button>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { mapAgentRun } from '../../../api/agent'
const props = defineProps<{ context: Record<string, any>; mapObject?: Record<string, any> }>()
const input = ref(''); const loading = ref(false); const messages = ref<Array<{role:'user'|'assistant';content:string}>>([])
async function send(){ const text=input.value.trim(); if(!text) return; messages.value.push({role:'user',content:text}); input.value=''; loading.value=true; try{ const r=await mapAgentRun({action:props.mapObject?'ANALYZE_OBJECT':'CHAT',message:text,mapContext:{mode:props.mapObject?'OBJECT':'FREE',mapObject:props.mapObject,extra:props.context||{}},options:{useKnowledge:true,useBusinessData:true}}); messages.value.push({role:'assistant',content:r?.answer || JSON.stringify(r)}) }catch(e:any){messages.value.push({role:'assistant',content:e?.message||'请求失败'})} finally{loading.value=false} }
</script>
<style scoped>
.chat{height:300px;display:flex;flex-direction:column;padding:12px;background:rgba(255,255,255,.96);border-radius:8px;box-shadow:0 4px 16px rgba(15,23,42,.12)}.title{font-weight:700;margin-bottom:8px}.msgs{flex:1;overflow:auto}.msg{font-size:13px;line-height:1.5;margin-bottom:8px}.input{display:flex;gap:8px;align-items:flex-end}.map-context{background:#f0f9ff;border-radius:6px;padding:8px;margin-bottom:8px;font-size:12px}.context-title{font-weight:600;color:#0369a1;margin-bottom:4px}.context-info{display:flex;flex-wrap:wrap;gap:8px;color:#64748b}
</style>
