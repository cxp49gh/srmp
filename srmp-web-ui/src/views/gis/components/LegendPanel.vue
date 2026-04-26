<template>
  <div class="legend-panel srmp-card">
    <div class="title">路况等级</div>
    <div v-for="item in legends" :key="item.code" class="legend-row">
      <span class="color" :style="{ background: item.color }" />
      <span>{{ item.name }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
const legends = [
  { code: 'EXCELLENT', name: '优', color: '#16a34a' },
  { code: 'GOOD', name: '良', color: '#2563eb' },
  { code: 'MEDIUM', name: '中', color: '#eab308' },
  { code: 'POOR', name: '次', color: '#f97316' },
  { code: 'BAD', name: '差', color: '#dc2626' }
]
</script>

<style scoped>
.legend-panel {
  padding: 10px 12px;
}

.title {
  font-weight: 700;
  margin-bottom: 8px;
}

.legend-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  line-height: 22px;
}

.color {
  width: 22px;
  height: 4px;
  display: inline-block;
  border-radius: 4px;
}
</style>