<template>
  <div class="demo-page">
    <header class="header">
      <div>
        <h1>演示数据联调看板</h1>
        <p>用于检查 Phase 1-22 演示数据是否已正确接入 GIS、AI 问答和方案生成。</p>
      </div>
      <div class="actions">
        <el-input v-model="tenantId" style="width: 140px" placeholder="tenantId" />
        <el-input-number v-model="year" :min="2000" :max="2100" />
        <el-button type="primary" @click="load">刷新</el-button>
      </div>
    </header>

    <el-alert
      v-if="status?.health"
      class="mb"
      :type="status.health.ready ? 'success' : 'warning'"
      show-icon
      :closable="false"
      :title="status.health.ready ? '演示数据已就绪' : '演示数据未完全就绪，请先导入 phase1-22 fixed 数据包'"
    />

    <section class="stat-grid">
      <el-card v-for="item in statCards" :key="item.label">
        <div class="stat-value">{{ item.value }}</div>
        <div class="stat-label">{{ item.label }}</div>
      </el-card>
    </section>

    <section class="main-grid">
      <el-card>
        <template #header>路线评定概览</template>
        <el-table :data="dashboard?.routeRanking || []" height="420" size="small">
          <el-table-column prop="route_code" label="路线" width="80" />
          <el-table-column prop="route_name" label="名称" min-width="180" />
          <el-table-column prop="length_km" label="里程(km)" width="90" />
          <el-table-column prop="assessment_count" label="评定" width="80" />
          <el-table-column prop="disease_count" label="病害" width="80" />
          <el-table-column prop="avg_mqi" label="MQI" width="80" />
          <el-table-column prop="avg_pqi" label="PQI" width="80" />
          <el-table-column prop="avg_pci" label="PCI" width="80" />
        </el-table>
      </el-card>

      <el-card>
        <template #header>等级分布</template>
        <div class="tag-list">
          <el-tag v-for="item in dashboard?.gradeDistribution || []" :key="item.grade" size="large">
            {{ gradeLabel(item.grade) }}：{{ item.count }}
          </el-tag>
        </div>
      </el-card>

      <el-card>
        <template #header>病害 TOP</template>
        <el-table :data="dashboard?.diseaseTop || []" height="300" size="small">
          <el-table-column prop="disease_name" label="病害" />
          <el-table-column prop="severity" label="程度" width="90" />
          <el-table-column prop="count" label="数量" width="80" />
        </el-table>
      </el-card>
    </section>

    <section class="main-grid">
      <el-card>
        <template #header>低分评定单元 TOP10</template>
        <el-table :data="dashboard?.lowScoreUnits || []" height="360" size="small">
          <el-table-column prop="route_code" label="路线" width="80" />
          <el-table-column prop="start_stake" label="起点" width="90" />
          <el-table-column prop="end_stake" label="终点" width="90" />
          <el-table-column prop="mqi" label="MQI" width="80" />
          <el-table-column prop="pqi" label="PQI" width="80" />
          <el-table-column prop="pci" label="PCI" width="80" />
          <el-table-column prop="grade" label="等级" width="90" />
        </el-table>
      </el-card>

      <el-card>
        <template #header>AI 演示快捷问题</template>
        <div class="question-list">
          <div v-for="item in dashboard?.quickQuestions || []" :key="item.text" class="question">
            <span>{{ item.text }}</span>
            <div>
              <el-button size="small" @click="copyQuestion(item.text)">复制</el-button>
              <router-link :to="`/agent/chat?routeCode=${item.routeCode}&year=${item.year}&q=${encodeURIComponent(item.text)}`">
                <el-button size="small" type="primary">去问答</el-button>
              </router-link>
            </div>
          </div>
        </div>
      </el-card>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getDemoDashboard, getDemoStatus } from '../../api/demo'

const tenantId = ref('default')
const year = ref(2026)
const status = ref<Record<string, any> | null>(null)
const dashboard = ref<Record<string, any> | null>(null)

onMounted(load)

const statCards = computed(() => {
  const s = dashboard.value?.summary || {}
  return [
    { label: '路线数', value: s.route_count ?? '-' },
    { label: '总里程 km', value: s.total_length_km ?? '-' },
    { label: '评定单元', value: s.unit_count ?? '-' },
    { label: '评定结果', value: s.assessment_count ?? '-' },
    { label: '病害记录', value: s.disease_count ?? '-' },
    { label: '平均 MQI', value: s.avg_mqi ?? '-' },
    { label: '平均 PQI', value: s.avg_pqi ?? '-' },
    { label: '平均 PCI', value: s.avg_pci ?? '-' }
  ]
})

async function load() {
  status.value = await getDemoStatus({ tenantId: tenantId.value, year: year.value })
  dashboard.value = await getDemoDashboard({ tenantId: tenantId.value, year: year.value })
}

async function copyQuestion(text: string) {
  await navigator.clipboard.writeText(text)
  ElMessage.success('已复制问题')
}

function gradeLabel(grade: string) {
  const map: Record<string, string> = {
    EXCELLENT: '优',
    GOOD: '良',
    MEDIUM: '中',
    POOR: '次',
    BAD: '差'
  }
  return map[grade] || grade
}
</script>

<style scoped>
.demo-page {
  min-height: 100vh;
  background: #f1f5f9;
  color: #0f172a;
  padding: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  margin-bottom: 16px;
}

h1 {
  margin: 0;
  font-size: 24px;
}

p {
  margin: 6px 0 0;
  color: #64748b;
}

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mb {
  margin-bottom: 16px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.stat-value {
  font-size: 26px;
  font-weight: 800;
}

.stat-label {
  color: #64748b;
  margin-top: 6px;
}

.main-grid {
  display: grid;
  grid-template-columns: minmax(520px, 1.3fr) minmax(360px, 0.7fr);
  gap: 16px;
  margin-bottom: 16px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 18px;
}

.question-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.question {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  background: #f8fafc;
  border-radius: 10px;
  padding: 12px;
}

.question a {
  margin-left: 6px;
}
</style>