<template>
  <div class="map-float-tools">
    <button type="button" title="图层" @click="$emit('toggle-layer')">层</button>
    <button type="button" title="图例" @click="$emit('toggle-legend')">例</button>
    <button type="button" title="全图" @click="$emit('fit')">全</button>
  </div>
</template>

<script setup lang="ts">
defineEmits<{
  (e: 'toggle-layer'): void
  (e: 'toggle-legend'): void
  (e: 'fit'): void
}>()
</script>

<style scoped>
.map-float-tools {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

button {
  width: 40px;
  height: 40px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.96);
  color: #0f172a;
  font-weight: 700;
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.12);
  cursor: pointer;
}

button:hover {
  color: #2563eb;
  border-color: #93c5fd;
}
</style>
