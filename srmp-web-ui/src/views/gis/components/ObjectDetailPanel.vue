<template>
  <div class="object-detail srmp-card">
    <div class="panel-header">
      <span>对象详情</span>
      <el-tag v-if="detail?.objectType" size="small">{{ detail.objectType }}</el-tag>
    </div>

    <el-empty v-if="!detail" description="点击地图对象查看详情" />

    <el-descriptions v-else :column="1" size="small" border>
      <el-descriptions-item
        v-for="(value, key) in detail"
        :key="key"
        :label="String(key)"
      >
        <span>{{ formatValue(value) }}</span>
      </el-descriptions-item>
    </el-descriptions>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  detail: Record<string, any> | null
}>()

function formatValue(value: any) {
  if (value === null || typeof value === 'undefined') return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
</script>

<style scoped>
.object-detail {
  padding: 14px;
  max-height: calc(100vh - 170px);
  overflow: auto;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 700;
  margin-bottom: 12px;
}
</style>