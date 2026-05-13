<template>
  <el-drawer
    :model-value="visible"
    title="AI 执行计划"
    size="420px"
    append-to-body
    class="map-ai-plan-drawer"
    @update:model-value="$emit('update:visible', $event)"
    @close="$emit('close')"
  >
    <div class="plan-drawer-body">
      <el-alert
        v-if="error"
        type="error"
        show-icon
        :closable="false"
        :title="error"
      />

      <section v-if="loading" class="plan-loading">
        <el-skeleton animated :rows="5" />
      </section>

      <template v-else-if="plan">
        <section class="plan-section">
          <div class="plan-section-head">
            <strong>识别结果</strong>
            <el-tag size="small" :type="plan.status === 'SUCCESS' ? 'success' : 'danger'">{{ plan.status }}</el-tag>
          </div>
          <div class="plan-tags">
            <el-tag v-if="plan.action" size="small" effect="plain">{{ plan.action }}</el-tag>
            <el-tag v-if="plan.intent" size="small" type="info" effect="plain">{{ plan.intent }}</el-tag>
            <el-tag v-if="plan.traceId" size="small" type="info" effect="plain">{{ plan.traceId }}</el-tag>
          </div>
          <div v-if="plan.contextChips.length" class="plan-context">
            <span v-for="item in plan.contextChips" :key="item">{{ item }}</span>
          </div>
        </section>

        <section v-if="planExecution" class="plan-section">
          <div class="plan-section-head">
            <strong>执行对比</strong>
            <el-tag size="small" :type="planExecutionStatusType(planExecution.status)">
              {{ planExecution.status }}
            </el-tag>
          </div>
          <div class="compare-grid">
            <span>
              <em>Action</em>
              <strong>{{ comparePair(planExecution.plannedAction, planExecution.actualAction) }}</strong>
            </span>
            <span>
              <em>Intent</em>
              <strong>{{ comparePair(planExecution.plannedIntent, planExecution.actualIntent) }}</strong>
            </span>
            <span>
              <em>计划工具</em>
              <strong>{{ planExecution.plannedToolNames.length }}</strong>
            </span>
            <span>
              <em>实际工具</em>
              <strong>{{ planExecution.actualToolNames.length }}</strong>
            </span>
          </div>
          <div v-if="planExecution.missingToolNames.length || planExecution.extraToolNames.length" class="diff-list">
            <div v-if="planExecution.missingToolNames.length" class="diff-item">
              <el-tag size="small" type="danger" effect="plain">缺失工具</el-tag>
              <span>{{ planExecution.missingToolNames.join('、') }}</span>
            </div>
            <div v-if="planExecution.adaptiveExtraToolNames.length" class="diff-item">
              <el-tag size="small" type="success" effect="plain">自适应追加</el-tag>
              <span>{{ planExecution.adaptiveExtraToolNames.join('、') }}</span>
            </div>
            <div v-if="unexplainedExtraTools(planExecution).length" class="diff-item">
              <el-tag size="small" type="warning" effect="plain">额外工具</el-tag>
              <span>{{ unexplainedExtraTools(planExecution).join('、') }}</span>
            </div>
            <div v-if="planExecution.adaptiveReason" class="diff-item">
              <el-tag size="small" type="info" effect="plain">追加原因</el-tag>
              <span>{{ planExecution.adaptiveReason }}</span>
            </div>
          </div>
          <div v-if="planExecution.plannedSourceTypes.length || planExecution.actualSourceTypes.length" class="diff-list">
            <div class="diff-item">
              <el-tag size="small" effect="plain">计划来源</el-tag>
              <span>{{ displayList(planExecution.plannedSourceTypes) }}</span>
            </div>
            <div class="diff-item">
              <el-tag size="small" type="success" effect="plain">实际来源</el-tag>
              <span>{{ displayList(planExecution.actualSourceTypes) }}</span>
            </div>
            <div v-if="planExecution.missingSourceTypes.length" class="diff-item">
              <el-tag size="small" type="warning" effect="plain">未命中来源</el-tag>
              <span>{{ planExecution.missingSourceTypes.join('、') }}</span>
            </div>
          </div>
        </section>

        <section v-if="plan.warnings.length" class="plan-section">
          <div class="plan-section-head">
            <strong>风险提示</strong>
            <span>{{ plan.warnings.length }}</span>
          </div>
          <div class="warning-list">
            <div v-for="item in plan.warnings" :key="item.code" class="warning-item">
              <el-tag size="small" :type="item.level === 'ERROR' ? 'danger' : item.level === 'INFO' ? 'info' : 'warning'">
                {{ item.level }}
              </el-tag>
              <span>{{ item.message }}</span>
            </div>
          </div>
        </section>

        <section class="plan-section">
          <div class="plan-section-head">
            <strong>计划工具</strong>
            <span>{{ plan.toolPlan.length }}</span>
          </div>
          <div v-if="plan.toolPlan.length" class="tool-list">
            <div v-for="tool in plan.toolPlan" :key="tool.name + tool.reason" class="tool-item">
              <div class="tool-title">
                <strong>{{ tool.label }}</strong>
                <el-tag size="small" :type="tool.writeRisk ? 'warning' : 'success'" effect="plain">
                  {{ tool.writeRisk ? '写入' : '只读' }}
                </el-tag>
              </div>
              <p v-if="tool.reason">{{ tool.reason }}</p>
              <div v-if="Object.keys(tool.argsSummary).length" class="arg-grid">
                <span v-for="(value, key) in tool.argsSummary" :key="key">
                  <em>{{ key }}</em>
                  <strong>{{ displayValue(value) }}</strong>
                </span>
              </div>
            </div>
          </div>
          <el-empty v-else description="未规划工具" :image-size="72" />
        </section>

        <section v-if="plan.sourceHints.length" class="plan-section">
          <div class="plan-section-head">
            <strong>预计来源</strong>
            <span>{{ plan.sourceHints.length }}</span>
          </div>
          <div class="source-list">
            <div v-for="item in plan.sourceHints" :key="item.sourceType" class="source-item">
              <el-tag size="small" effect="plain">{{ item.label }}</el-tag>
              <span>{{ item.reason }}</span>
            </div>
          </div>
        </section>

        <section v-if="plan.steps.length" class="plan-section">
          <div class="plan-section-head">
            <strong>规划步骤</strong>
            <span>{{ plan.steps.length }}</span>
          </div>
          <div class="step-list">
            <div v-for="step in plan.steps" :key="step.name + step.label" class="step-item">
              <span>{{ step.label }}</span>
              <el-tag v-if="step.status" size="small" effect="plain">{{ step.status }}</el-tag>
            </div>
          </div>
        </section>
      </template>

      <el-empty v-else description="暂无执行计划" :image-size="96" />
    </div>

    <template #footer>
      <div class="plan-footer">
        <el-button :disabled="loading" @click="$emit('refresh')">刷新计划</el-button>
        <el-button type="primary" :disabled="loading || !plan" @click="$emit('execute')">按计划执行</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import type { MapAiPlanExecution, MapAiPlanPreview } from '../../../utils/mapAiPlanPreview'

defineProps<{
  visible: boolean
  loading?: boolean
  plan?: MapAiPlanPreview | null
  planExecution?: MapAiPlanExecution | null
  error?: string
}>()

defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'close'): void
  (e: 'refresh'): void
  (e: 'execute'): void
}>()

function displayValue(value: any) {
  if (value === true) return '是'
  if (value === false) return '否'
  if (value === undefined || value === null || value === '') return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function displayList(values: string[]) {
  return values?.length ? values.join('、') : '-'
}

function comparePair(planned?: string, actual?: string) {
  const left = planned || '-'
  const right = actual || '-'
  return left === right ? left : `${left} / ${right}`
}

function planExecutionStatusType(status: string) {
  if (status === 'MATCHED') return 'success'
  if (status === 'DIVERGED') return 'danger'
  if (status === 'PARTIAL') return 'warning'
  return 'info'
}

function unexplainedExtraTools(planExecution: MapAiPlanExecution) {
  return planExecution.extraToolNames.filter((item) => !planExecution.adaptiveExtraToolNames.includes(item))
}
</script>

<style scoped>
.plan-drawer-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.plan-section {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
  background: #fff;
}

.plan-section-head,
.tool-title,
.step-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.plan-tags,
.plan-context,
.source-list,
.warning-list,
.step-list,
.diff-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.plan-context span {
  padding: 3px 8px;
  border-radius: 6px;
  background: #f3f4f6;
  color: #374151;
  font-size: 12px;
}

.tool-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 10px;
}

.tool-item {
  border: 1px solid #eef2f7;
  border-radius: 6px;
  padding: 10px;
  background: #fafafa;
}

.tool-item p {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 12px;
}

.arg-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  margin-top: 8px;
}

.arg-grid span {
  min-width: 0;
  border-radius: 6px;
  background: #fff;
  padding: 6px;
}

.arg-grid em {
  display: block;
  color: #6b7280;
  font-size: 11px;
  font-style: normal;
}

.arg-grid strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #111827;
  font-size: 12px;
}

.warning-item,
.source-item,
.diff-item {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  width: 100%;
  color: #4b5563;
  font-size: 12px;
}

.compare-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 10px;
}

.compare-grid span {
  min-width: 0;
  border-radius: 6px;
  background: #f9fafb;
  padding: 8px;
}

.compare-grid em {
  display: block;
  color: #6b7280;
  font-size: 11px;
  font-style: normal;
}

.compare-grid strong {
  display: block;
  overflow: hidden;
  color: #111827;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plan-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
