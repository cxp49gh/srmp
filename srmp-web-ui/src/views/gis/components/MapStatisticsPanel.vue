<template>
  <div class="map-statistics srmp-card">
    <div class="stat-item">
      <div class="label">路线里程</div>
      <div class="value">{{ value.totalLengthKm ?? '-' }}</div>
    </div>
    <div class="stat-item">
      <div class="label">病害数</div>
      <div class="value">{{ value.diseaseCount ?? '-' }}</div>
    </div>
    <div class="stat-item">
      <div class="label">平均 MQI</div>
      <div class="value">{{ value.avgMqi ?? '-' }}</div>
    </div>
    <div class="stat-item">
      <div class="label">优良率</div>
      <div class="value">{{ value.excellentGoodRate ?? '-' }}</div>
    </div>
    <div class="stat-item">
      <div class="label">次差率</div>
      <div class="value">{{ value.poorBadRate ?? '-' }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  value: Record<string, any>
}>()
</script>

<style scoped>
.map-statistics {
  display: flex;
  gap: 12px;
  padding: 10px 14px;
}

.stat-item {
  min-width: 110px;
}

.label {
  color: #64748b;
  font-size: 12px;
}

.value {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}
</style>