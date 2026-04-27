<template>
  <AgentPageShell title="方案任务" description="查看 AI 方案生成历史、结果内容和引用来源。">
    <div class="page-grid">
      <el-card class="left-card">
        <template #header>
          <div class="card-header">
            <span>任务列表</span>
            <el-button size="small" @click="loadTasks">刷新</el-button>
          </div>
        </template>

        <el-form :inline="true" class="query-form">
          <el-form-item>
            <el-input v-model="query.routeCode" clearable placeholder="路线，如 G210" style="width: 150px" />
          </el-form-item>
          <el-form-item>
            <el-input-number v-model="query.year" :min="2000" :max="2100" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadTasks">查询</el-button>
          </el-form-item>
        </el-form>

        <el-empty v-if="tasks.length === 0" description="暂无任务" />
        <div
          v-for="item in tasks"
          :key="item.id"
          :class="['task-item', selected?.id === item.id ? 'active' : '']"
          @click="selectTask(item)"
        >
          <div class="row">
            <strong>{{ item.title }}</strong>
            <el-tag size="small">{{ item.status }}</el-tag>
          </div>
          <p>{{ item.id }}</p>
          <div class="meta">{{ item.route_code }} / {{ item.year }} / {{ item.solution_type }}</div>
        </div>
      </el-card>

      <el-card class="middle-card">
        <template #header>
          <div class="card-header">
            <span>方案结果</span>
            <el-button v-if="detail?.result_content" size="small" @click="copyResult">复制 Markdown</el-button>
          </div>
        </template>
        <el-empty v-if="!detail" description="请选择任务" />
        <template v-else>
          <el-descriptions :column="2" border size="small" class="mb">
            <el-descriptions-item label="任务ID">{{ detail.id }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
            <el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item>
            <el-descriptions-item label="模板版本">{{ detail.template_version }}</el-descriptions-item>
          </el-descriptions>
          <pre>{{ detail.result_content }}</pre>
        </template>
      </el-card>

      <el-card class="right-card">
        <template #header>引用来源</template>
        <el-empty v-if="sources.length === 0" description="暂无来源" />
        <div v-for="item in sources" :key="item.id" class="source-item">
          <div class="source-title">
            <strong>{{ item.source_title }}</strong>
            <el-tag size="small">{{ item.source_type }}</el-tag>
          </div>
          <p>{{ item.source_url || item.source_id }}</p>
          <div>{{ item.content_excerpt }}</div>
        </div>
      </el-card>
    </div>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import { getSolutionTask, getSolutionTaskSources, listSolutionTasks } from '../../api/solution'

const query = reactive({
  routeCode: 'G210',
  year: 2026,
  limit: 50
})

const tasks = ref<Record<string, any>[]>([])
const selected = ref<Record<string, any> | null>(null)
const detail = ref<Record<string, any> | null>(null)
const sources = ref<Record<string, any>[]>([])

onMounted(loadTasks)

async function loadTasks() {
  tasks.value = await listSolutionTasks(query)
}

async function selectTask(item: Record<string, any>) {
  selected.value = item
  detail.value = await getSolutionTask(item.id)
  sources.value = await getSolutionTaskSources(item.id)
}

async function copyResult() {
  await navigator.clipboard.writeText(detail.value?.result_content || '')
  ElMessage.success('已复制')
}
</script>

<style scoped>
.page-grid {
  display: grid;
  grid-template-columns: 360px minmax(520px, 1fr) 360px;
  gap: 16px;
}

.left-card,
.middle-card,
.right-card {
  min-height: calc(100vh - 130px);
}

.card-header,
.row,
.source-title {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.query-form {
  margin-bottom: 12px;
}

.task-item,
.source-item {
  padding: 12px;
  border-radius: 10px;
  background: #f8fafc;
  margin-bottom: 10px;
  cursor: pointer;
  font-size: 13px;
}

.task-item.active {
  background: #dbeafe;
}

.task-item p,
.source-item p {
  color: #64748b;
  margin: 4px 0;
  word-break: break-all;
}

.meta {
  color: #64748b;
  font-size: 12px;
}

.mb {
  margin-bottom: 12px;
}

pre {
  white-space: pre-wrap;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 12px;
  padding: 14px;
  line-height: 1.6;
  max-height: calc(100vh - 250px);
  overflow: auto;
}
</style>