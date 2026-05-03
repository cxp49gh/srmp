<template>
  <div class="map-toolbar srmp-card">
    <el-form :inline="true" :model="localQuery" size="small" class="toolbar-form" @submit.prevent>
      <el-form-item label="路线">
        <el-input v-model="localQuery.routeCode" placeholder="如 G210" clearable class="route-input" />
      </el-form-item>

      <el-form-item label="年度">
        <el-input v-model="localQuery.year" placeholder="2026" clearable class="year-input" />
      </el-form-item>

      <el-form-item label="指标">
        <el-select v-model="localQuery.indexCode" clearable placeholder="指标" class="metric-select">
          <el-option
            v-for="item in metricOptions"
            :key="item.code"
            :label="`${item.code} ${item.name}`"
            :value="item.code"
          >
            <div class="metric-option">
              <strong>{{ item.code }}</strong>
              <span>{{ item.name }}</span>
              <em>{{ item.dimension }}</em>
            </div>
          </el-option>
        </el-select>
      </el-form-item>

      <el-form-item label="等级">
        <el-select v-model="localQuery.grade" clearable placeholder="全部" class="grade-select">
          <el-option label="全部" value="" />
          <el-option v-for="grade in gradeOptions" :key="grade.code" :label="`${grade.label}（${grade.rangeText}）`" :value="grade.code">
            <span class="grade-option-dot" :style="{ background: grade.color }" />
            {{ grade.label }} <small>{{ grade.rangeText }}</small>
          </el-option>
        </el-select>
      </el-form-item>

      <el-form-item class="toolbar-actions">
        <el-button type="primary" @click="emitSearch">查询</el-button>
        <el-button @click="emitReset">重置</el-button>
        <div class="region-icon-actions" aria-label="区域框选工具">
          <el-button
            :type="regionMode === 'RECTANGLE' ? 'primary' : ''"
            plain
            class="region-icon-btn"
            title="矩形框选"
            aria-label="矩形框选"
            @click="emitRegion('RECTANGLE')"
          >
            <span class="region-icon region-rect">□</span>
          </el-button>
          <el-button
            :type="regionMode === 'POLYGON' ? 'primary' : ''"
            plain
            class="region-icon-btn"
            title="多边形框选"
            aria-label="多边形框选"
            @click="emitRegion('POLYGON')"
          >
            <span class="region-icon region-poly">◇</span>
          </el-button>
          <el-button
            plain
            class="region-icon-btn"
            title="清除框选"
            aria-label="清除框选"
            :disabled="!hasRegion && regionMode === 'NONE'"
            @click="emitClearRegion"
          >
            <span class="region-icon region-clear">×</span>
          </el-button>
        </div>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { GisLayerQuery } from '../../../api/gis'
import { ROAD_CONDITION_GRADES, ROAD_CONDITION_METRICS } from '../../../utils/roadConditionMetrics'

const props = defineProps<{
  query: GisLayerQuery
  regionMode?: 'NONE' | 'RECTANGLE' | 'POLYGON'
  hasRegion?: boolean
}>()

const emit = defineEmits<{
  (e: 'search', value: GisLayerQuery): void
  (e: 'reset'): void
  (e: 'fit'): void
  (e: 'start-region', value: 'RECTANGLE' | 'POLYGON'): void
  (e: 'clear-region'): void
}>()

const localQuery = reactive<GisLayerQuery>({ ...props.query })
const metricOptions = ROAD_CONDITION_METRICS
const gradeOptions = ROAD_CONDITION_GRADES

watch(
  () => props.query,
  (value) => Object.assign(localQuery, value),
  { deep: true }
)

function emitSearch() {
  emit('search', { ...localQuery })
}

function emitReset() {
  emit('reset')
}

function emitRegion(mode: 'RECTANGLE' | 'POLYGON') {
  emit('start-region', mode)
}

function emitClearRegion() {
  emit('clear-region')
}
</script>

<style scoped>
.map-toolbar {
  position: relative;
  padding: 8px 10px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(226, 232, 240, 0.82);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.1);
}

.toolbar-form {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  column-gap: 6px;
  row-gap: 2px;
}

.route-input {
  width: 104px;
}

.year-input {
  width: 78px;
}

.metric-select {
  width: 198px;
}

.grade-select {
  width: 104px;
}

.metric-option {
  display: grid;
  grid-template-columns: 42px 1fr auto;
  align-items: center;
  gap: 8px;
  min-width: 260px;
}

.metric-option strong {
  color: #2563eb;
}

.metric-option span {
  color: #0f172a;
}

.metric-option em {
  color: #94a3b8;
  font-style: normal;
  font-size: 12px;
}

.grade-option-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 6px;
  border-radius: 999px;
}

.grade-option-dot + small,
.grade-select small {
  color: #94a3b8;
  margin-left: 4px;
}

.toolbar-actions :deep(.el-form-item__content) {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: nowrap;
}

.region-icon-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding-left: 2px;
  border-left: 1px solid rgba(148, 163, 184, 0.32);
}

.region-icon-btn {
  width: 30px;
  height: 30px;
  padding: 0 !important;
  border-radius: 9px;
}

.region-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  font-size: 18px;
  line-height: 1;
  font-weight: 800;
}

.region-poly {
  transform: rotate(45deg);
  font-size: 16px;
}

.region-clear {
  font-size: 20px;
}

.toolbar-actions :deep(.el-button) {
  padding-left: 10px;
  padding-right: 10px;
}

.toolbar-actions {
  margin-left: auto;
}

:deep(.el-form-item) {
  margin-right: 0;
  margin-bottom: 6px;
}

:deep(.el-form-item__label) {
  padding-right: 7px;
  color: #334155;
  font-weight: 600;
}

@media (max-width: 1320px) {
  .toolbar-actions {
    margin-left: 0;
  }

  .metric-select {
    width: 188px;
  }

}

@media (max-width: 1180px) {
  .toolbar-actions :deep(.el-form-item__content) {
    gap: 4px;
  }

  .map-toolbar {
    overflow-x: auto;
    overflow-y: hidden;
  }
}

@media (max-width: 960px) {
  .toolbar-form {
    flex-wrap: wrap;
  }

  .route-input,
  .year-input,
  .metric-select,
  .grade-select {
    width: 100%;
  }

  :deep(.el-form-item) {
    min-width: 136px;
  }
}
</style>
