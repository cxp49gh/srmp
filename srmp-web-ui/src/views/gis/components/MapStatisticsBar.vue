<template>
  <div class="statistics-bar srmp-card" :class="{ collapsed }">
    <button class="collapse-btn" type="button" @click="$emit('update:collapsed', !collapsed)">
      {{ collapsed ? '展开统计' : '收起' }}
    </button>

    <div v-if="!collapsed" class="stat-list">
      <div class="stat-item">
        <span>总里程</span>
        <strong>{{ format(value.totalLengthKm) }}</strong>
      </div>
      <div class="stat-item">
        <span>病害数</span>
        <strong>{{ format(value.diseaseCount) }}</strong>
      </div>
      <div class="stat-item">
        <span>平均 MQI</span>
        <strong>{{ format(value.avgMqi) }}</strong>
      </div>
      <div class="stat-item">
        <span>优良率</span>
        <strong>{{ format(value.excellentGoodRate) }}</strong>
      </div>
      <div class="stat-item">
        <span>次差率</span>
        <strong>{{ format(value.poorBadRate) }}</strong>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  value: Record<string, any>
  collapsed: boolean
}>()

defineEmits<{
  (e: 'update:collapsed', value: boolean): void
}>()

function format(value: any) {
  return value === null || typeof value === 'undefined' || value === '' ? '-' : value
}
</script>

<style scoped>
.statistics-bar {
  position: absolute;
  left: 50%;
  bottom: 18px;
  z-index: 910;
  min-width: 560px;
  max-width: min(720px, calc(100vw - 420px));
  transform: translateX(-50%);
  padding: 8px 12px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.95);
}

.statistics-bar.collapsed {
  min-width: auto;
}

.collapse-btn {
  position: absolute;
  right: 10px;
  top: -28px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 999px;
  padding: 4px 10px;
  background: rgba(255, 255, 255, 0.95);
  color: #475569;
  font-size: 12px;
  cursor: pointer;
}

.stat-list {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
}

.stat-item {
  text-align: center;
}

.stat-item span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.stat-item strong {
  display: block;
  margin-top: 2px;
  color: #0f172a;
  font-size: 16px;
}
</style>
