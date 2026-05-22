<template>
  <AgentPageShell title="数据质量" description="按项目查看数据完整性、关联质量与分类分布。">
    <template #actions>
      <div class="header-actions">
        <el-select v-model="projectId" filterable placeholder="选择项目" style="width: 280px" @change="load">
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-button type="primary" :disabled="!projectId" :loading="loading" @click="load">刷新报告</el-button>
        <el-button :disabled="!report" @click="exportCsv">导出 CSV</el-button>
      </div>
    </template>

    <div class="quality-page">
      <el-empty v-if="!report" description="请选择项目并查看报告" />

      <template v-else>
        <section class="metric-grid">
          <div class="metric-card">
            <div class="metric-head">
              <span>路线</span>
              <el-tag size="small" type="info">ROAD</el-tag>
            </div>
            <strong>{{ formatNumber(report.routeCount) }}</strong>
            <p>路网基础对象总量</p>
          </div>

          <div class="metric-card">
            <div class="metric-head">
              <span>路段</span>
              <el-tag size="small" type="success">SECTION</el-tag>
            </div>
            <strong>{{ formatNumber(report.sectionCount) }}</strong>
            <div class="mini-list">
              <span v-for="item in sectionRows" :key="item.label">{{ item.label }} {{ formatNumber(item.value) }}</span>
            </div>
          </div>

          <div class="metric-card">
            <div class="metric-head">
              <span>病害</span>
              <el-tag size="small" type="warning">DISEASE</el-tag>
            </div>
            <strong>{{ formatNumber(report.diseaseCount) }}</strong>
            <p>未分类 {{ formatNumber(report.unclassifiedDiseaseCount) }} 条</p>
          </div>

          <div class="metric-card" :class="qualityLevelClass">
            <div class="metric-head">
              <span>路线匹配率</span>
              <el-tag size="small" :type="qualityTagType">{{ qualityTagText }}</el-tag>
            </div>
            <strong>{{ report.routeMatchRate == null ? '-' : `${report.routeMatchRate}%` }}</strong>
            <p>未关联路线 {{ formatNumber(report.unmatchedRouteCount) }} 条</p>
          </div>
        </section>

        <section class="chart-grid">
          <div class="panel large">
            <div class="panel-header">
              <div>
                <h3>路段层级分布</h3>
                <p>总量 {{ formatNumber(report.sectionCount) }}，按线路级、台账级、公里级、百米级拆分。</p>
              </div>
            </div>
            <div ref="sectionChartRef" class="chart"></div>
          </div>

          <div class="panel">
            <div class="panel-header">
              <div>
                <h3>路线关联质量</h3>
                <p>统计路段和病害是否已关联路网路线。</p>
              </div>
            </div>
            <div ref="qualityChartRef" class="chart"></div>
          </div>
        </section>

        <section class="chart-grid">
          <div class="panel">
            <div class="panel-header">
              <div>
                <h3>空几何分布</h3>
                <p>定位路线、路段、病害中缺少空间几何的数据。</p>
              </div>
            </div>
            <div ref="geometryChartRef" class="chart"></div>
          </div>

          <div class="panel">
            <div class="panel-header">
              <div>
                <h3>病害分类</h3>
                <p>按病害大类聚合，帮助判断导入分类是否完整。</p>
              </div>
            </div>
            <div ref="diseaseCategoryChartRef" class="chart"></div>
          </div>
        </section>

        <section class="content-grid">
          <div class="panel">
            <div class="panel-header">
              <div>
                <h3>病害名称分布</h3>
                <p>按病害名称聚合病害记录。</p>
              </div>
            </div>
            <div ref="diseaseNameChartRef" class="chart compact"></div>
          </div>

          <div class="panel">
            <div class="panel-header">
              <div>
                <h3>异常清单</h3>
                <p>需要复核或补录的数据问题。</p>
              </div>
            </div>
            <el-table :data="issueRows" size="small" stripe height="280">
              <el-table-column prop="issueType" label="异常类型" width="170" />
              <el-table-column prop="count" label="数量" width="100">
                <template #default="{ row }">{{ formatNumber(row.count) }}</template>
              </el-table-column>
              <el-table-column prop="suggestion" label="建议" min-width="240" />
            </el-table>
          </div>
        </section>
      </template>
    </div>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import AgentPageShell from '../../agent/components/AgentPageShell.vue'
import { getQualityReport, pageDataMgmtProjects, type DataMgmtProjectVO, type DataMgmtQualityReportVO } from '../../../api/dataMgmt'

type MetricItem = { label: string; value: number }

const projects = ref<DataMgmtProjectVO[]>([])
const projectId = ref('')
const loading = ref(false)
const report = ref<DataMgmtQualityReportVO | null>(null)
const sectionChartRef = ref<HTMLDivElement | null>(null)
const qualityChartRef = ref<HTMLDivElement | null>(null)
const geometryChartRef = ref<HTMLDivElement | null>(null)
const diseaseCategoryChartRef = ref<HTMLDivElement | null>(null)
const diseaseNameChartRef = ref<HTMLDivElement | null>(null)
const charts: echarts.ECharts[] = []

const sectionRows = computed(() => {
  const rows = metricRows(report.value?.sectionBreakdown)
  if (hasPositiveValue(rows) || !report.value) return rows
  return [{ label: '路段总量', value: Number(report.value.sectionCount || 0) }]
})
const qualityRows = computed(() => {
  const rows = metricRows(report.value?.routeMatchBreakdown)
  if (hasPositiveValue(rows) || !report.value) return rows
  const unmatched = Number(report.value.unmatchedRouteCount || 0)
  const base = Number(report.value.sectionCount || 0) + Number(report.value.diseaseCount || 0)
  return [
    { label: '已关联路线', value: Math.max(base - unmatched, 0) },
    { label: '未关联路线', value: unmatched }
  ]
})
const geometryRows = computed(() => {
  const rows = metricRows(report.value?.geometryBreakdown)
  if (hasPositiveValue(rows) || !report.value) return rows
  return [
    { label: '路线空几何', value: Number(report.value.emptyRouteGeometryCount || 0) },
    { label: '路段空几何', value: Number(report.value.emptySectionGeometryCount || 0) },
    { label: '病害空几何', value: Number(report.value.emptyDiseaseGeometryCount || report.value.emptyGeometryCount || 0) }
  ]
})
const diseaseCategoryRows = computed(() => metricRows(report.value?.diseaseCategoryBreakdown))
const diseaseNameRows = computed(() => metricRows(report.value?.diseaseNameBreakdown || report.value?.diseaseSeverityBreakdown))

const issueRows = computed(() => {
  const r = report.value
  if (!r) return []
  return r.issues || []
})

const qualityTagText = computed(() => {
  const rate = report.value?.routeMatchRate
  if (rate == null) return 'NO DATA'
  if (rate >= 98) return 'GOOD'
  if (rate >= 90) return 'CHECK'
  return 'RISK'
})

const qualityTagType = computed(() => {
  const rate = report.value?.routeMatchRate
  if (rate == null || rate >= 98) return 'success'
  if (rate >= 90) return 'warning'
  return 'danger'
})

const qualityLevelClass = computed(() => {
  const rate = report.value?.routeMatchRate
  if (rate == null || rate >= 98) return 'ok'
  if (rate >= 90) return 'warn'
  return 'risk'
})

async function load() {
  if (!projectId.value) return
  loading.value = true
  try {
    report.value = await getQualityReport(projectId.value)
    await nextTick()
    renderCharts()
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  disposeCharts()
  renderBar(sectionChartRef.value, sectionRows.value, '数量')
  renderPie(qualityChartRef.value, qualityRows.value)
  renderPie(geometryChartRef.value, geometryRows.value)
  renderHorizontalBar(diseaseCategoryChartRef.value, diseaseCategoryRows.value.slice(0, 8))
  renderBar(diseaseNameChartRef.value, diseaseNameRows.value.slice(0, 10), '病害数')
}

function renderBar(el: HTMLDivElement | null, rows: MetricItem[], seriesName: string) {
  if (!el) return
  const chart = echarts.init(el)
  charts.push(chart)
  chart.setOption({
    color: ['#2563eb'],
    tooltip: { trigger: 'axis' },
    grid: { left: 44, right: 20, top: 28, bottom: 36 },
    xAxis: { type: 'category', data: rows.map((item) => item.label), axisLabel: { interval: 0 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ name: seriesName, type: 'bar', data: rows.map((item) => item.value), barMaxWidth: 42 }]
  })
}

function renderHorizontalBar(el: HTMLDivElement | null, rows: MetricItem[]) {
  if (!el) return
  const chart = echarts.init(el)
  charts.push(chart)
  const data = rows.length ? rows : [{ label: '暂无数据', value: 0 }]
  chart.setOption({
    color: ['#16a34a'],
    tooltip: { trigger: 'axis' },
    grid: { left: 90, right: 20, top: 20, bottom: 24 },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: { type: 'category', data: data.map((item) => item.label) },
    series: [{ name: '病害数', type: 'bar', data: data.map((item) => item.value), barMaxWidth: 24 }]
  })
}

function renderPie(el: HTMLDivElement | null, rows: MetricItem[]) {
  if (!el) return
  const chart = echarts.init(el)
  charts.push(chart)
  const data = rows.length ? rows.map((item) => ({ name: item.label, value: item.value })) : [{ name: '暂无数据', value: 0 }]
  chart.setOption({
    color: ['#2563eb', '#dc2626', '#f59e0b', '#16a34a', '#7c3aed'],
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      type: 'pie',
      radius: ['48%', '72%'],
      center: ['50%', '42%'],
      avoidLabelOverlap: true,
      label: { formatter: '{b}: {c}' },
      data
    }]
  })
}

function disposeCharts() {
  while (charts.length) {
    charts.pop()?.dispose()
  }
}

function resizeCharts() {
  charts.forEach((chart) => chart.resize())
}

function metricRows(rows?: MetricItem[]) {
  return (rows || []).map((item) => ({
    label: item.label || '-',
    value: Number(item.value || 0)
  }))
}

function hasPositiveValue(rows: MetricItem[]) {
  return rows.some((item) => Number(item.value || 0) > 0)
}

function formatNumber(value?: number | null) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function exportCsv() {
  const r = report.value
  if (!r) return
  const lines = [
    '指标,值',
    `路线数,${r.routeCount}`,
    `路段数,${r.sectionCount}`,
    ...sectionRows.value.map((item) => `路段-${item.label},${item.value}`),
    `病害数,${r.diseaseCount}`,
    `路线匹配率,${r.routeMatchRate ?? ''}`,
    `未匹配路线,${r.unmatchedRouteCount}`,
    `路段未匹配路线,${r.unmatchedSectionRouteCount ?? ''}`,
    `病害未匹配路线,${r.unmatchedDiseaseRouteCount ?? ''}`,
    `未分类病害,${r.unclassifiedDiseaseCount}`,
    `空几何,${r.emptyGeometryCount}`,
    `路线空几何,${r.emptyRouteGeometryCount ?? ''}`,
    `路段空几何,${r.emptySectionGeometryCount ?? ''}`,
    `病害空几何,${r.emptyDiseaseGeometryCount ?? ''}`
  ]
  const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `quality-${r.projectId}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success('已导出')
}

onMounted(async () => {
  const res = await pageDataMgmtProjects({ pageNo: 1, pageSize: 500 })
  projects.value = res.records || []
  if (!projectId.value && projects.value.length) {
    projectId.value = projects.value[0].id
    await load()
  }
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  disposeCharts()
})
</script>

<style scoped>
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.quality-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(180px, 1fr));
  gap: 12px;
}

.metric-card,
.panel {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.metric-card {
  min-height: 136px;
  padding: 16px;
}

.metric-head,
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.metric-head span {
  color: #475569;
  font-weight: 600;
}

.metric-card strong {
  display: block;
  margin-top: 16px;
  color: #0f172a;
  font-size: 28px;
  line-height: 1;
}

.metric-card p,
.panel-header p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.4;
}

.metric-card.ok {
  border-color: #bbf7d0;
}

.metric-card.warn {
  border-color: #fde68a;
}

.metric-card.risk {
  border-color: #fecaca;
}

.mini-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 10px;
  margin-top: 12px;
  color: #475569;
  font-size: 13px;
}

.chart-grid,
.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(360px, 0.8fr);
  gap: 16px;
}

.panel {
  padding: 16px;
}

.panel.large {
  min-width: 0;
}

.panel-header h3 {
  margin: 0;
  color: #0f172a;
  font-size: 16px;
  line-height: 1.3;
}

.chart {
  width: 100%;
  height: 320px;
  margin-top: 10px;
}

.chart.compact {
  height: 280px;
}

@media (max-width: 1100px) {
  .metric-grid,
  .chart-grid,
  .content-grid {
    grid-template-columns: 1fr;
  }

  .header-actions {
    flex-wrap: wrap;
  }
}
</style>
