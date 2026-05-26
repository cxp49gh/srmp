<template>
  <section class="draft-review-panel">
    <div class="review-head">
      <div>
        <strong>草稿发布审查</strong>
        <span>汇总配置校验、策略覆盖和发布阻断项</span>
      </div>
      <div class="review-actions">
        <el-button size="small" :loading="validationLoading" @click="$emit('validate')">校验草稿</el-button>
        <el-button size="small" :loading="coverageLoading" @click="$emit('runCoverage')">运行覆盖</el-button>
        <el-button size="small" type="warning" :loading="publishLoading" @click="$emit('submitPublish')">提交发布</el-button>
      </div>
    </div>

    <section class="review-summary">
      <div><span>配置校验</span><strong>{{ validationStatus }}</strong></div>
      <div><span>策略覆盖</span><strong>{{ coverageStatus }}</strong></div>
      <div><span>发布申请</span><strong>{{ publishStatus }}</strong></div>
      <div><span>阻断项</span><strong>{{ blockers.length }}</strong></div>
    </section>

    <el-alert
      v-if="blockers.length > 0"
      type="error"
      show-icon
      :closable="false"
      title="当前草稿存在发布阻断项"
    />

    <div class="blocker-list">
      <el-tag v-for="item in blockers" :key="item" type="danger" effect="plain">{{ item }}</el-tag>
      <el-tag v-if="blockers.length === 0" type="success" effect="plain">未发现阻断项</el-tag>
    </div>

    <section class="review-detail">
      <div>
        <span>草稿 ID</span>
        <strong>{{ validationPayload.draftId || coveragePayload.draftId || '-' }}</strong>
      </div>
      <div>
        <span>配置问题</span>
        <strong>错误 {{ validationErrorCount }} / 告警 {{ validationWarningCount }}</strong>
      </div>
      <div>
        <span>覆盖用例</span>
        <strong>通过 {{ coveragePassedCount }} / 失败 {{ coverageFailedCount }}</strong>
      </div>
      <div>
        <span>申请记录</span>
        <strong>{{ publishRecord.requestId || '-' }}</strong>
      </div>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { derivePublishBlockers } from './governanceDraft'

const props = defineProps<{
  validationPayload: Record<string, any>
  coveragePayload: Record<string, any>
  publishPayload?: Record<string, any>
  validationLoading?: boolean
  coverageLoading?: boolean
  publishLoading?: boolean
}>()

defineEmits<{
  (event: 'validate'): void
  (event: 'runCoverage'): void
  (event: 'submitPublish'): void
}>()

const validation = computed(() => objectValue(props.validationPayload?.validation))
const readiness = computed(() => objectValue(props.validationPayload?.readiness))
const coverage = computed(() => Object.keys(props.coveragePayload || {}).length
  ? props.coveragePayload
  : objectValue(props.validationPayload?.policyCoverage))
const publishRecord = computed(() => objectValue(props.publishPayload?.record))
const blockers = computed(() => derivePublishBlockers(props.validationPayload || {}, props.coveragePayload || {}))
const validationErrorCount = computed(() => Number(validation.value.errorCount || 0))
const validationWarningCount = computed(() => Number(validation.value.warningCount || 0))
const coverageFailedCount = computed(() => Number(coverage.value.failedCount || 0))
const coveragePassedCount = computed(() => Number(coverage.value.passedCount || 0))
const validationStatus = computed(() => {
  if (!props.validationPayload?.mode) return '未校验'
  if (stringValue(readiness.value.status)) return stringValue(readiness.value.status)
  return validationErrorCount.value > 0 ? 'FAIL' : 'PASS'
})
const coverageStatus = computed(() => {
  if (!coverage.value.mode && !coverage.value.caseCount) return '未运行'
  return coverageFailedCount.value > 0 ? 'FAIL' : 'PASS'
})
const publishStatus = computed(() => stringValue(publishRecord.value.status || '未提交'))

function objectValue(value: any): Record<string, any> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
}

function stringValue(value: any): string {
  return value == null ? '' : String(value)
}
</script>

<style scoped>
.draft-review-panel {
  display: grid;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f8fafc;
}

.review-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.review-head > div:first-child {
  display: grid;
  gap: 4px;
}

.review-actions,
.blocker-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.review-head span,
.review-summary span,
.review-detail span {
  color: #64748b;
  font-size: 12px;
}

.review-summary,
.review-detail {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.review-summary div,
.review-detail div {
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
}

.review-summary strong,
.review-detail strong {
  display: block;
  margin-top: 6px;
  word-break: break-all;
}

@media (max-width: 900px) {
  .review-head {
    align-items: stretch;
    flex-direction: column;
  }

  .review-summary,
  .review-detail {
    grid-template-columns: 1fr;
  }
}
</style>
