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
        <el-button @click="$emit('fit')">全图</el-button>
        <span class="region-divider" />
        <el-dropdown trigger="click" @command="handleRegionCommand">
          <el-button plain>
            区域工具
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="RECTANGLE">{{ regionMode === 'RECTANGLE' ? '✓ ' : '' }}矩形框选</el-dropdown-item>
              <el-dropdown-item command="POLYGON">{{ regionMode === 'POLYGON' ? '✓ ' : '' }}多边形框选</el-dropdown-item>
              <el-dropdown-item command="CLEAR" :disabled="!hasRegion" divided>清除框选</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-form-item>
    </el-form>

    <div class="metric-hint compact">
      <span class="metric-pill">{{ activeMetric.shortName }}</span>
      <span v-if="activeGradeText" class="grade-filter">{{ activeGradeText }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import type { GisLayerQuery } from '../../../api/gis'
import { ROAD_CONDITION_GRADES, ROAD_CONDITION_METRICS, getGradeMeta, getMetricMeta } from '../../../utils/roadConditionMetrics'

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

const activeMetric = computed(() => getMetricMeta(localQuery.indexCode))
const activeGradeText = computed(() => {
  const grade = getGradeMeta(localQuery.grade)
  return grade ? `${grade.label}（${grade.rangeText}）` : ''
})

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

function handleRegionCommand(command: string) {
  if (command === 'CLEAR') {
    emit('clear-region')
    return
  }
  emit('start-region', command as 'RECTANGLE' | 'POLYGON')
}
</script>

<style scoped>
.map-toolbar {
  position: relative;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(226, 232, 240, 0.82);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.1);
}

.toolbar-form {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  column-gap: 8px;
  row-gap: 2px;
}

.route-input {
  width: 132px;
}

.year-input {
  width: 90px;
}

.metric-select {
  width: 236px;
}

.grade-select {
  width: 126px;
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
  flex-wrap: wrap;
}

.toolbar-actions {
  margin-left: auto;
}

.region-divider {
  width: 1px;
  height: 22px;
  margin: 0 2px;
  background: #e2e8f0;
}

.metric-hint {
  position: absolute;
  left: 58px;
  bottom: -25px;
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 20px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
  pointer-events: none;
}

.metric-pill,
.grade-filter {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  white-space: nowrap;
  background: #eff6ff;
  color: #2563eb;
  font-weight: 700;
}

.grade-filter {
  background: #f8fafc;
  color: #475569;
  font-weight: 600;
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

  .metric-hint {
    display: none;
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
