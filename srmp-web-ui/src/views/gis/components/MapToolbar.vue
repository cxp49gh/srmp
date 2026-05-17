<template>
  <div class="map-toolbar srmp-card">
    <el-form
        :model="localQuery"
        size="small"
        class="toolbar-form"
        :inline="true"
        label-position="right"
        @submit.prevent
    >
      <div class="toolbar-main-layout">
        <div class="query-primary-row">
          <div class="fields-grid">
            <el-form-item label="项目" required class="uniform-item">
              <el-select v-model="localQuery.projectId" filterable placeholder="请选择项目">
                <el-option v-for="p in projectOptions" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
            </el-form-item>

            <el-form-item label="路网编码" class="uniform-item">
              <el-input v-model="localQuery.routeCode" placeholder="如 G210" clearable />
            </el-form-item>

            <el-form-item label="路段专题" class="uniform-item">
              <el-select v-model="localTier" placeholder="路线级">
                <el-option label="路线级" value="LINE" />
                <el-option label="台账级" value="LEDGER" />
                <el-option label="公里级" value="KM" />
                <el-option label="百米级" value="HM" />
              </el-select>
            </el-form-item>
          </div>

          <div class="action-group">
            <el-button type="primary" @click="emitSearch">查询</el-button>
            <el-button @click="emitReset">重置</el-button>
            <el-button link class="expand-toggle" @click="isExpanded = !isExpanded">
              {{ isExpanded ? '收起' : '展开' }}
              <i :class="['el-icon-arrow-down', 'arrow-icon', { 'is-reverse': isExpanded }]"></i>
            </el-button>
          </div>

          <div class="v-divider" />

          <div class="draw-section" aria-label="区域框选工具">
            <span class="draw-section-label">框选</span>
            <div class="draw-btn-group">
              <el-button
                  class="draw-tool-button"
                  :class="{ 'is-active': regionMode === 'RECTANGLE' }"
                  :aria-pressed="regionMode === 'RECTANGLE'"
                  aria-label="矩形框选"
                  title="矩形框选"
                  @click="emitRegion('RECTANGLE')"
              >
                <svg class="icon-svg" aria-hidden="true" focusable="false" viewBox="0 0 24 24"><rect x="5" y="6" width="14" height="12" rx="2" /></svg>
              </el-button>
              <el-button
                  class="draw-tool-button"
                  :class="{ 'is-active': regionMode === 'POLYGON' }"
                  :aria-pressed="regionMode === 'POLYGON'"
                  aria-label="多边形框选"
                  title="多边形框选"
                  @click="emitRegion('POLYGON')"
              >
                <svg class="icon-svg" aria-hidden="true" focusable="false" viewBox="0 0 24 24"><path d="M7.5 4.5 17 6.2l3 8.2-6.3 5.1-8.8-3.2-1-7.5Z" /></svg>
              </el-button>
              <el-button
                  class="draw-tool-button draw-clear-button"
                  :disabled="!hasRegion"
                  aria-label="清除框选"
                  title="清除框选"
                  @click="emitClearRegion"
              >
                <svg class="icon-svg" aria-hidden="true" focusable="false" viewBox="0 0 24 24"><path d="m7 17 10-10M7 7l10 10" /></svg>
              </el-button>
            </div>
          </div>
        </div>

        <el-collapse-transition>
          <div v-show="isExpanded" class="query-extended-row">
            <div class="fields-grid">
              <el-form-item label="指标" class="uniform-item">
                <el-select v-model="localQuery.indexCode" clearable placeholder="请选择指标">
                  <el-option
                      v-for="item in metricOptions"
                      :key="item.code"
                      :label="`${item.code} ${item.name}`"
                      :value="item.code"
                  >
                    <div class="metric-option-layout">
                      <strong class="m-code">{{ item.code }}</strong>
                      <span class="m-name">{{ item.name }}</span>
                      <em class="m-dim">{{ item.dimension }}</em>
                    </div>
                  </el-option>
                </el-select>
              </el-form-item>

              <el-form-item label="等级" class="uniform-item">
                <el-select v-model="localQuery.grade" clearable placeholder="全部">
                  <el-option label="全部" value="" />
                  <el-option
                      v-for="grade in gradeOptions"
                      :key="grade.code"
                      :label="`${grade.label}（${grade.rangeText}）`"
                      :value="grade.code"
                  >
                    <div class="grade-option-layout">
                      <span class="grade-dot" :style="{ background: grade.color }" />
                      <span class="grade-label">{{ grade.label }}</span>
                      <small class="grade-range">{{ grade.rangeText }}</small>
                    </div>
                  </el-option>
                </el-select>
              </el-form-item>

              <div class="uniform-item-placeholder" />
            </div>
          </div>
        </el-collapse-transition>
      </div>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import type { GisLayerQuery, GisSectionTier } from '../../../api/gis'
import { pageDataMgmtProjects } from '../../../api/dataMgmt'
import { ROAD_CONDITION_GRADES, ROAD_CONDITION_METRICS } from '../../../utils/roadConditionMetrics'

const props = withDefaults(
    defineProps<{
      query: GisLayerQuery
      regionMode?: 'NONE' | 'RECTANGLE' | 'POLYGON'
      hasRegion?: boolean
    }>(),
    { regionMode: 'NONE', hasRegion: false }
)

const emit = defineEmits(['search', 'reset', 'start-region', 'clear-region'])

const isExpanded = ref(false)
const localQuery = reactive<GisLayerQuery>({ ...props.query })
const localTier = ref<GisSectionTier>((props.query.sectionTier || 'LINE') as GisSectionTier)

const metricOptions = ROAD_CONDITION_METRICS
const gradeOptions = ROAD_CONDITION_GRADES
const projectOptions = ref<{ id: string; name: string }[]>([])

onMounted(async () => {
  const res = await pageDataMgmtProjects({ pageNo: 1, pageSize: 500 })
  projectOptions.value = (res.records || []).map((p) => ({ id: p.id, name: p.name }))
})

watch(() => props.query, (val) => {
  Object.assign(localQuery, val)
  localTier.value = (val.sectionTier || 'LINE') as GisSectionTier
}, { deep: true })

const emitSearch = () => emit('search', { ...localQuery, sectionTier: localTier.value })
const emitReset = () => emit('reset')
const emitRegion = (mode: any) => emit('start-region', mode)
const emitClearRegion = () => emit('clear-region')
</script>

<style scoped>
.map-toolbar {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
  padding: 10px 16px;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  border: 1px solid #e2e8f0;
}

.toolbar-main-layout {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.query-primary-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

/* 栅格布局 */
.fields-grid {
  display: grid;
  flex: 1 1 714px;
  grid-template-columns: repeat(3, 230px);
  gap: 12px;
}

.uniform-item {
  margin-right: 0 !important;
  margin-bottom: 0 !important;
  display: flex !important;
}

/* ==== 核心：Label 右对齐优化 ==== */
:deep(.uniform-item .el-form-item__label) {
  width: 70px !important;
  display: inline-flex !important;
  justify-content: flex-end !important; /* 强制内容右对齐 */
  align-items: center;
  font-weight: 600;
  color: #475569;
  padding-right: 8px !important;
}

:deep(.uniform-item .el-select),
:deep(.uniform-item .el-input) {
  width: 150px !important;
}

/* 下拉内容布局 */
.metric-option-layout {
  display: grid;
  grid-template-columns: 42px 1fr auto;
  align-items: center;
  gap: 8px;
  min-width: 260px;
}

.m-code { color: #2563eb; }
.m-name { color: #0f172a; }
.m-dim { color: #94a3b8; font-style: normal; font-size: 12px; }

.grade-option-layout {
  display: flex;
  align-items: center;
  min-width: 180px;
}

.grade-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
}

/* 按钮及交互 */
.action-group {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.expand-toggle {
  font-size: 13px;
  color: #2563eb;
  padding: 0 4px;
}

.arrow-icon {
  margin-left: 4px;
  transition: transform 0.3s;
}

.arrow-icon.is-reverse {
  transform: rotate(180deg);
}

.v-divider {
  flex-shrink: 0;
  width: 1px;
  height: 24px;
  background: #e2e8f0;
}

.draw-section {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 6px;
}

.draw-section-label {
  color: #475569;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}

.draw-btn-group {
  display: flex;
  background: #f1f5f9;
  padding: 2px;
  border-radius: 6px;
}

.draw-btn-group :deep(.draw-tool-button) {
  border: none;
  background: transparent;
  color: #334155;
  padding: 6px;
  width: 32px;
  height: 32px;
  margin: 0;
}

.draw-btn-group :deep(.draw-tool-button > span) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.draw-btn-group :deep(.draw-tool-button:hover:not(.is-disabled)) {
  background: #e0edff;
  color: #1d4ed8;
}

.draw-btn-group :deep(.draw-tool-button.is-active) {
  background: white;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
  color: #2563eb;
}

.query-extended-row {
  padding-top: 10px;
  border-top: 1px dashed #e2e8f0;
}

.icon-svg {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

@media (max-width: 1660px) {
  .fields-grid {
    flex-basis: 100%;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  :deep(.uniform-item .el-select),
  :deep(.uniform-item .el-input) {
    width: 130px !important;
  }
}
</style>
