<template>
  <section v-if="hasMeta" class="template-meta">
    <div class="meta-header">
      <strong>{{ matched ? '生成模板' : '模板兜底' }}</strong>
      <el-tag size="small" :type="matched ? 'success' : 'warning'">
        {{ matched ? '已命中' : '兜底' }}
      </el-tag>
    </div>

    <el-descriptions :column="2" border size="small">
      <el-descriptions-item label="名称">{{ field('templateName', 'template_name') || '-' }}</el-descriptions-item>
      <el-descriptions-item label="编码">{{ field('templateCode', 'template_code') || '-' }}</el-descriptions-item>
      <el-descriptions-item label="版本">{{ field('templateVersion', 'template_version') || '-' }}</el-descriptions-item>
      <el-descriptions-item label="类型">{{ field('solutionType', 'solution_type') || '-' }}</el-descriptions-item>
      <el-descriptions-item label="对象">{{ field('objectType', 'object_type') || '-' }}</el-descriptions-item>
      <el-descriptions-item label="来源">{{ field('originType', 'origin_type') || '-' }}</el-descriptions-item>
    </el-descriptions>

    <el-alert v-if="fallbackReason" class="mt" type="warning" :title="fallbackReason" show-icon />
    <el-alert v-else-if="matchReason" class="mt" type="success" :title="matchReason" show-icon />

    <div v-if="missingVariables.length" class="variable-row">
      <span>缺失变量</span>
      <el-tag v-for="item in missingVariables" :key="item" type="warning" size="small">{{ item }}</el-tag>
    </div>

    <div v-if="warnings.length" class="variable-row">
      <span>提示</span>
      <el-tag v-for="item in warnings" :key="item" type="info" size="small">{{ item }}</el-tag>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

defineOptions({ name: 'TemplateMetaCard' })

const props = defineProps<{ meta?: Record<string, any> | null }>()

const meta = computed(() => props.meta || {})
const hasMeta = computed(() => Object.keys(meta.value).length > 0)
const matched = computed(() => meta.value.matched === true || meta.value.fallback === false)
const matchReason = computed(() => field('matchReason', 'match_reason'))
const fallbackReason = computed(() => field('fallbackReason', 'fallback_reason'))
const missingVariables = computed(() => asStringArray(meta.value.missingVariables || meta.value.missing_variables))
const warnings = computed(() => asStringArray(meta.value.warnings || meta.value.warningMessages || meta.value.warning_messages))

function field(camelKey: string, snakeKey: string) {
  return meta.value[camelKey] || meta.value[snakeKey] || ''
}

function asStringArray(value: any) {
  if (!Array.isArray(value)) return []
  return value.map((item) => String(item)).filter(Boolean)
}
</script>

<style scoped>
.template-meta {
  margin-bottom: 12px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.meta-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.mt {
  margin-top: 10px;
}

.variable-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-top: 10px;
  color: #475569;
  font-size: 13px;
}
</style>
