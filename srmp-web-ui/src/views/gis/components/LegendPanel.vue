<template>
  <div class="legend" :class="{ collapsed }">
    <div class="legend-header" @click="collapsed = !collapsed">
      <div>
        <strong>{{ metricMeta.code }} 等级</strong>
        <span>{{ metricMeta.shortName }}</span>
      </div>
      <button type="button">{{ collapsed ? '展开' : '收起' }}</button>
    </div>

    <template v-if="!collapsed">
      <div v-for="item in list" :key="item.code" class="row">
        <span class="color" :style="{ background: item.color }" />
        <span class="name">{{ item.label }}</span>
        <em>{{ item.rangeText }}</em>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ROAD_CONDITION_GRADES, getMetricMeta } from '../../../utils/roadConditionMetrics'

const props = defineProps<{
  indexCode?: string
}>()

const collapsed = ref(false)
const metricMeta = computed(() => getMetricMeta(props.indexCode))
const list = ROAD_CONDITION_GRADES
</script>

<style scoped>
.legend {
  min-width: 126px;
  padding: 10px 12px;
  border: 1px solid rgba(226, 232, 240, 0.88);
  background: rgba(255, 255, 255, 0.95);
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
}

.legend.collapsed {
  min-width: 104px;
  padding: 8px 10px;
}

.legend-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
  cursor: pointer;
}

.legend-header strong {
  display: block;
  color: #0f172a;
  font-size: 13px;
}

.legend-header span {
  color: #64748b;
  font-size: 11px;
}

.legend-header button {
  border: none;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
  font-size: 12px;
  padding: 0;
}

.legend.collapsed .legend-header {
  margin-bottom: 0;
}

.row {
  display: grid;
  grid-template-columns: 24px 24px 1fr;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  line-height: 22px;
}

.color {
  display: inline-block;
  width: 22px;
  height: 4px;
  border-radius: 4px;
}

.name {
  color: #0f172a;
  font-weight: 700;
}

.row em {
  color: #64748b;
  font-style: normal;
  text-align: right;
}
</style>
