<template>
  <AgentPageShell title="AI 调用监控" description="查看 AI 问答 traceId、总耗时、降级状态和每一步耗时。">
    <div class="page-grid">
      <el-card class="left-card"><template #header><div class="card-header"><span>调用列表</span><el-button size="small" @click="loadTraces">刷新</el-button></div></template>
        <el-form :inline="true" class="query-form"><el-form-item><el-select v-model="query.status" clearable placeholder="状态" style="width:130px"><el-option label="SUCCESS" value="SUCCESS"/><el-option label="FAILED" value="FAILED"/><el-option label="TIMEOUT" value="TIMEOUT"/></el-select></el-form-item><el-form-item><el-input v-model="query.keyword" clearable placeholder="traceId / 问题" style="width:220px"/></el-form-item><el-form-item><el-button type="primary" @click="loadTraces">查询</el-button></el-form-item></el-form>
        <el-empty v-if="traces.length===0" description="暂无 trace" />
        <div v-for="item in traces" :key="item.trace_id" :class="['trace-item', selected?.trace_id===item.trace_id?'active':'']" @click="selectTrace(item)"><div class="row"><strong>{{ item.trace_id }}</strong><el-tag size="small" :type="tagType(item.status)">{{ item.status }}</el-tag></div><p>{{ item.user_message }}</p><div class="meta">{{ item.mode }} / {{ item.total_cost_ms }}ms / fallback={{ item.fallback }}</div></div>
      </el-card>
      <el-card class="middle-card"><template #header><div class="card-header"><span>调用详情</span><el-button v-if="detail?.trace_id" size="small" @click="copyTraceId">复制 traceId</el-button></div></template>
        <el-empty v-if="!detail" description="请选择 trace" />
        <template v-else><el-descriptions :column="2" border size="small" class="mb"><el-descriptions-item label="traceId">{{ detail.trace_id }}</el-descriptions-item><el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item><el-descriptions-item label="模式">{{ detail.mode }}</el-descriptions-item><el-descriptions-item label="总耗时">{{ detail.total_cost_ms }} ms</el-descriptions-item><el-descriptions-item label="降级">{{ detail.fallback }}</el-descriptions-item><el-descriptions-item label="时间">{{ detail.created_at }}</el-descriptions-item></el-descriptions><el-alert v-if="detail.error_message" type="error" :title="detail.error_message" show-icon class="mb"/><h3>用户问题</h3><pre>{{ detail.user_message }}</pre><h3>调用链路</h3><el-timeline><el-timeline-item v-for="step in steps" :key="step.id" :type="timelineType(step.status)" :timestamp="`${step.cost_ms || 0}ms`"><div class="step-title"><strong>{{ step.step_label || step.step_name }}</strong><el-tag size="small" :type="tagType(step.status)">{{ step.status }}</el-tag></div><div class="step-meta">count={{ step.hit_count ?? '-' }}</div><div v-if="step.error_message" class="error">{{ step.error_message }}</div></el-timeline-item></el-timeline></template>
      </el-card>
    </div>
  </AgentPageShell>
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import { getAiTrace, listAiTraces } from '../../api/trace'
const query = reactive({ status: '', keyword: '', limit: 50 })
const traces = ref<Record<string, any>[]>([]); const selected = ref<Record<string, any>|null>(null); const detail = ref<Record<string, any>|null>(null); const steps = ref<Record<string, any>[]>([])
onMounted(loadTraces)
async function loadTraces(){ traces.value = await listAiTraces(query) }
async function selectTrace(item: Record<string, any>){ selected.value=item; detail.value=await getAiTrace(item.trace_id); steps.value=detail.value?.steps || [] }
async function copyTraceId(){ await navigator.clipboard.writeText(detail.value?.trace_id || ''); ElMessage.success('traceId 已复制') }
function tagType(status:string){ if(status==='SUCCESS') return 'success'; if(status==='FAILED') return 'danger'; if(status==='TIMEOUT') return 'warning'; if(status==='SKIPPED') return 'info'; return 'info' }
function timelineType(status:string){ if(status==='SUCCESS') return 'success'; if(status==='FAILED') return 'danger'; if(status==='TIMEOUT') return 'warning'; return 'info' }
</script>
<style scoped>
.page-grid{display:grid;grid-template-columns:430px minmax(520px,1fr);gap:16px}.left-card,.middle-card{min-height:calc(100vh - 130px)}.card-header,.row,.step-title{display:flex;justify-content:space-between;gap:8px}.query-form{margin-bottom:12px}.trace-item{padding:12px;border-radius:10px;background:#f8fafc;margin-bottom:10px;cursor:pointer;font-size:13px}.trace-item.active{background:#dbeafe}.trace-item p{color:#64748b;margin:4px 0;word-break:break-all}.meta,.step-meta{color:#64748b;font-size:12px}.mb{margin-bottom:12px}h3{margin:18px 0 10px;font-size:15px}pre{white-space:pre-wrap;background:#0f172a;color:#e2e8f0;border-radius:10px;padding:12px;line-height:1.6}.error{margin-top:6px;color:#dc2626;word-break:break-all}
</style>
