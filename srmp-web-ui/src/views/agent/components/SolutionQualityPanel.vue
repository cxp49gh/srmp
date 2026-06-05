<template>
  <div class="quality-panel">
    <el-empty v-if="!quality || Object.keys(quality).length === 0" description="暂无质量校验结果" />
    <template v-else>
      <el-alert
        class="mb"
        :type="quality.passed ? 'success' : 'warning'"
        show-icon
        :closable="false"
        :title="quality.summary || title"
      />

      <el-descriptions :column="3" border size="small" class="mb">
        <el-descriptions-item label="是否通过">
          <el-tag :type="quality.passed ? 'success' : 'danger'">
            {{ quality.passed ? '通过' : '未通过' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="评分">{{ quality.score }}</el-descriptions-item>
        <el-descriptions-item label="等级">{{ quality.level }}</el-descriptions-item>
      </el-descriptions>

      <section class="quality-snapshot mb">
        <div class="section-title">质量快照</div>
        <div class="dimension-grid">
          <div
            v-for="dimension in visibleDimensions"
            :key="dimension.code"
            class="dimension-card"
          >
            <div class="dimension-head">
              <span>{{ dimension.label }}</span>
              <el-tag size="small" :type="dimensionTagType(dimension.level)">{{ dimension.level }}</el-tag>
            </div>
            <p>{{ dimension.summary }}</p>
            <div v-if="dimension.details && Object.keys(dimension.details).length" class="dimension-details">
              <span v-for="(value, key) in dimension.details" :key="key">
                {{ detailLabel(String(key)) }}：{{ formatDetailValue(value) }}
              </span>
            </div>
          </div>
        </div>
      </section>

      <div v-for="(item, index) in quality.items || []" :key="index" class="quality-item">
        <div class="quality-title">
          <el-tag size="small" :type="tagType(item.level)">{{ item.level }}</el-tag>
          <strong>{{ item.code }}</strong>
          <span v-if="item.penalty">-{{ item.penalty }} 分</span>
        </div>
        <p>{{ item.message }}</p>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  quality?: Record<string, any> | null
}>()

const title = '质量校验完成'

const dimensionFallbacks = [
  { code: 'template', label: '模板命中', level: 'INFO', summary: '暂无模板质量快照。' },
  { code: 'businessEvidence', label: '业务证据', level: 'INFO', summary: '暂无业务证据快照。' },
  { code: 'mapBinding', label: '地图关联', level: 'INFO', summary: '暂无地图关联快照。' },
  { code: 'llm', label: '大模型', level: 'INFO', summary: '暂无大模型元信息快照。' },
  { code: 'scenario', label: '场景匹配', level: 'INFO', summary: '暂无场景匹配快照。' }
]

const visibleDimensions = computed(() => {
  const dimensions = Array.isArray(props.quality?.dimensions) ? props.quality?.dimensions : []
  if (dimensions.length) return dimensions
  return dimensionFallbacks
})

function tagType(level: string) {
  if (level === 'OK') return 'success'
  if (level === 'ERROR') return 'danger'
  if (level === 'WARN') return 'warning'
  return 'info'
}

function dimensionTagType(level: string) {
  return tagType(level)
}

function detailLabel(key: string) {
  const labels: Record<string, string> = {
    templateCode: '模板',
    templateName: '模板名称',
    fallbackReason: '降级原因',
    missingVariables: '缺变量',
    toolSuccessCount: '成功工具',
    businessHitCount: '业务命中',
    sourceCount: '来源数',
    objectId: '对象',
    routeCode: '路线',
    llmStatus: '模型状态',
    answerSource: '回答来源',
    solutionType: '方案类型',
    originType: '来源',
    objectType: '对象类型'
  }
  return labels[key] || key
}

function formatDetailValue(value: any) {
  if (Array.isArray(value)) return value.join('、')
  if (value === undefined || value === null || value === '') return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
</script>

<style scoped>
.mb {
  margin-bottom: 12px;
}

.section-title {
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 8px;
}

.dimension-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 8px;
}

.dimension-card {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px;
  background: #fff;
}

.dimension-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-weight: 700;
  color: #334155;
}

.dimension-card p {
  margin: 8px 0;
  color: #475569;
  line-height: 1.5;
}

.dimension-details {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.dimension-details span {
  max-width: 100%;
  border-radius: 6px;
  background: #f1f5f9;
  padding: 2px 6px;
  color: #64748b;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.quality-item {
  padding: 10px;
  border-radius: 10px;
  background: #f8fafc;
  margin-bottom: 8px;
}

.quality-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.quality-title span {
  color: #ef4444;
  font-size: 12px;
}

.quality-item p {
  margin: 6px 0 0;
  color: #475569;
}
</style>
