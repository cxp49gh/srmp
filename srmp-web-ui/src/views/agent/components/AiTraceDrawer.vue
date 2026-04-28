<template>
  <el-drawer
    :model-value="visible"
    title="AI Trace"
    size="560px"
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-empty v-if="!trace" description="暂无 Trace" />
    <template v-else>
      <el-descriptions :column="1" border size="small" class="trace-meta">
        <el-descriptions-item label="TraceId">{{ trace.traceId || trace.trace_id }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ trace.requestType || trace.request_type }}</el-descriptions-item>
        <el-descriptions-item label="模式">{{ trace.mode }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ trace.status }}</el-descriptions-item>
        <el-descriptions-item label="总耗时">{{ trace.totalCostMs || trace.total_cost_ms || 0 }} ms</el-descriptions-item>
        <el-descriptions-item label="降级">{{ trace.fallback }}</el-descriptions-item>
      </el-descriptions>

      <el-timeline>
        <el-timeline-item
          v-for="(step, index) in normalizedSteps"
          :key="step.id || `${step.step_name || step.name}-${index}`"
          :type="timelineType(step.status)"
          :timestamp="`${step.cost_ms ?? step.costMs ?? 0}ms`"
        >
          <div class="step-title">
            <strong>{{ step.step_label || step.label || step.step_name || step.name }}</strong>
            <el-tag size="small" :type="tagType(step.status)">{{ step.status }}</el-tag>
          </div>
          <div class="step-meta">count={{ step.hit_count ?? step.count ?? '-' }}</div>
          <div v-if="step.error_message || step.error" class="error">{{ step.error_message || step.error }}</div>
        </el-timeline-item>
      </el-timeline>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  visible: boolean
  trace?: Record<string, any> | null
}>()

defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const normalizedSteps = computed(() => {
  const steps = props.trace?.steps
  return Array.isArray(steps) ? steps : []
})

function tagType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED' || status === 'TIMEOUT') return 'danger'
  if (status === 'SKIPPED') return 'info'
  return 'warning'
}

function timelineType(status: string) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED' || status === 'TIMEOUT') return 'danger'
  return 'info'
}
</script>

<style scoped>
.trace-meta {
  margin-bottom: 16px;
}

.step-title {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.step-meta {
  color: #64748b;
  font-size: 12px;
}

.error {
  margin-top: 6px;
  color: #dc2626;
  word-break: break-all;
}
</style>
