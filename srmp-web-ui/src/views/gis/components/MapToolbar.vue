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
        <el-button
          :type="regionMode === 'RECTANGLE' ? 'primary' : undefined"
          plain
          @click="$emit('start-region', 'RECTANGLE')"
        >矩形框选</el-button>
        <el-button
          :type="regionMode === 'POLYGON' ? 'primary' : undefined"
          plain
          @click="$emit('start-region', 'POLYGON')"
        >多边形框选</el-button>
        <el-button plain :disabled="!hasRegion" @click="$emit('clear-region')">清除框选</el-button>
      </el-form-item>
    </el-form>

    <div class="metric-hint">
      <span class="metric-pill">{{ activeMetric.shortName }}</span>
      <span class="metric-desc">{{ activeMetric.description }}</span>
      <span v-if="activeGradeText" class="grade-filter">当前等级：{{ activeGradeText }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
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
</script>

<style scoped>
.map-toolbar {
  padding: 9px 12px 8px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(226, 232, 240, 0.82);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.1);
}

.toolbar-form {
  display: flex;
  flex-wrap: wrap;
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
  width: 218px;
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
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 20px;
  margin-top: -2px;
  padding-left: 40px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
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

.metric-desc {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
    width: 168px;
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
    max-height: 132px;
    overflow-y: auto;
  }
}

@media (max-width: 960px) {
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
