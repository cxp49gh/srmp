<template>
  <div class="stats">
    <div v-for="item in items" :key="item.key" class="item">
      <div class="label">{{ item.label }}</div>
      <div class="value">{{ value[item.key] ?? '-' }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ value: Record<string, any> }>()

const items = [
  { key: 'totalLengthKm', label: '里程' },
  { key: 'diseaseCount', label: '病害' },
  { key: 'avgMqi', label: 'MQI' },
  { key: 'excellentGoodRate', label: '优良率' },
  { key: 'poorBadRate', label: '次差率' }
]
</script>

<style scoped>
.stats {
  display: flex;
  gap: 12px;
  padding: 10px 14px;
  background: rgba(255, 255, 255, 0.96);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.12);
}
.item {
  min-width: 90px;
}
.label {
  font-size: 12px;
  color: #64748b;
}
.value {
  font-weight: 700;
  font-size: 18px;
}
</style>
