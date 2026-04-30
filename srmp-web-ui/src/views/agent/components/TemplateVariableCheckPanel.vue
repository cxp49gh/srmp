<template>
  <section class="variable-panel">
    <div class="panel-title">变量检查</div>
    <div class="variable-list">
      <el-tag v-for="item in filledKeys" :key="item" size="small">{{ item }}</el-tag>
      <el-empty v-if="filledKeys.length === 0" description="暂无变量" />
    </div>
    <el-alert v-if="missing.length" class="mt" type="warning" :title="`缺失变量：${missing.join('，')}`" show-icon />
    <el-alert v-if="unused.length" class="mt" type="info" :title="`未使用变量：${unused.join('，')}`" show-icon />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

defineOptions({ name: 'TemplateVariableCheckPanel' })

const props = defineProps<{
  variables?: Record<string, any> | null
  missingVariables?: string[]
  unusedVariables?: string[]
}>()

const filledKeys = computed(() => Object.keys(props.variables || {}))
const missing = computed(() => props.missingVariables || [])
const unused = computed(() => props.unusedVariables || [])
</script>

<style scoped>
.variable-panel {
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
}

.panel-title {
  margin-bottom: 8px;
  font-weight: 600;
}

.variable-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.mt {
  margin-top: 10px;
}
</style>
