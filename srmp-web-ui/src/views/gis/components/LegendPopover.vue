<template>
  <transition name="fade">
    <div v-if="visible" class="legend-popover srmp-card">
      <div class="popover-header">
        <span>路况等级</span>
        <button type="button" @click="$emit('update:visible', false)">×</button>
      </div>
      <div v-for="item in legends" :key="item.code" class="legend-row">
        <span class="color" :style="{ background: item.color }" />
        <span>{{ item.name }}</span>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
defineProps<{ visible: boolean }>()

defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const legends = [
  { code: 'EXCELLENT', name: '优', color: '#16a34a' },
  { code: 'GOOD', name: '良', color: '#2563eb' },
  { code: 'MEDIUM', name: '中', color: '#eab308' },
  { code: 'POOR', name: '次', color: '#f97316' },
  { code: 'BAD', name: '差', color: '#dc2626' }
]
</script>

<style scoped>
.legend-popover {
  position: absolute;
  left: 18px;
  top: 92px;
  z-index: 925;
  width: 150px;
  padding: 12px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.14);
}

.popover-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-weight: 700;
}

.popover-header button {
  border: none;
  background: transparent;
  font-size: 18px;
  color: #64748b;
  cursor: pointer;
}

.legend-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  line-height: 24px;
}

.color {
  width: 22px;
  height: 4px;
  display: inline-block;
  border-radius: 4px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
