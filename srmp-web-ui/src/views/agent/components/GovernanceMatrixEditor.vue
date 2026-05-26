<template>
  <section class="matrix-editor">
    <div class="matrix-head">
      <div>
        <strong>能力-工具矩阵</strong>
        <span>编辑每个能力允许和禁止调用的工具</span>
      </div>
      <el-input v-model="keyword" size="small" clearable placeholder="搜索能力或工具" />
    </div>

    <el-table :data="filteredRows" border stripe>
      <el-table-column prop="name" label="能力" min-width="170">
        <template #default="{ row }">
          <div class="capability-cell">
            <strong>{{ row.name }}</strong>
            <span>{{ row.id }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-for="block in policyBlocks" :key="block.key" :label="block.label" min-width="230">
        <template #default="{ row }">
          <el-select
            :model-value="row[block.key]"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            clearable
            size="small"
            @update:model-value="(value: string[]) => updatePolicy(row.id, block.key, value)"
          >
            <el-option
              v-for="tool in toolOptions"
              :key="tool.name"
              :label="tool.label ? `${tool.label} (${tool.name})` : tool.name"
              :value="tool.name"
              :disabled="tool.enabled === false"
            />
          </el-select>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { buildCapabilityMatrixRows, updateCapabilityToolPolicy, type ToolPolicyKey } from './governanceDraft'

const props = defineProps<{
  capabilitiesConfig: Record<string, any>
  toolsConfig: Record<string, any>
}>()

const emit = defineEmits<{
  (event: 'update:capabilitiesConfig', value: Record<string, any>): void
}>()

const keyword = ref('')
const policyBlocks: Array<{ key: ToolPolicyKey, label: string }> = [
  { key: 'required', label: 'Required' },
  { key: 'optional', label: 'Optional' },
  { key: 'adaptive', label: 'Adaptive' },
  { key: 'prohibited', label: 'Prohibited' }
]

const rows = computed(() => buildCapabilityMatrixRows(props.capabilitiesConfig || {}))
const toolOptions = computed(() => Array.isArray(props.toolsConfig?.tools) ? props.toolsConfig.tools : [])
const filteredRows = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  if (!text) return rows.value
  return rows.value.filter((row) => JSON.stringify(row).toLowerCase().includes(text))
})

function updatePolicy(capabilityId: string, key: ToolPolicyKey, value: string[]) {
  emit('update:capabilitiesConfig', updateCapabilityToolPolicy(props.capabilitiesConfig || {}, capabilityId, key, value))
}
</script>

<style scoped>
.matrix-editor {
  display: grid;
  gap: 12px;
}

.matrix-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.matrix-head > div,
.capability-cell {
  display: grid;
  gap: 4px;
}

.matrix-head span,
.capability-cell span {
  color: #64748b;
  font-size: 12px;
}
</style>
