<template>
  <AgentPageShell
    title="RAG 质量评测"
    description="验证 knowledge.retrieve、sources、关键词命中和 vectorUsed，形成可持续回归的 AI 评测闭环。"
  >
    <template #actions>
      <el-button @click="loadDefaultCases">加载默认用例</el-button>
      <el-button type="primary" :loading="running" @click="runEval">执行评测</el-button>
    </template>

    <el-card class="block-card">
      <template #header>评测参数</template>
      <el-form :inline="true">
        <el-form-item label="TopK">
          <el-input-number v-model="topK" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="要求 vectorUsed=true">
          <el-switch v-model="requireVectorUsed" />
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="block-card">
      <template #header>评测用例 JSON</template>
      <el-input v-model="casesJson" type="textarea" :autosize="{ minRows: 10, maxRows: 22 }" />
    </el-card>

    <el-card v-if="summary" class="block-card">
      <template #header>评测结果</template>
      <el-row :gutter="16">
        <el-col :span="6"><div class="metric"><span>总数</span><strong>{{ summary.total }}</strong></div></el-col>
        <el-col :span="6"><div class="metric"><span>通过</span><strong>{{ summary.passed }}</strong></div></el-col>
        <el-col :span="6"><div class="metric"><span>通过率</span><strong>{{ percent(summary.passRate) }}</strong></div></el-col>
        <el-col :span="6"><div class="metric"><span>vectorUsed</span><strong>{{ summary.vectorUsedCount }}</strong></div></el-col>
      </el-row>
    </el-card>

    <el-card v-if="results.length" class="block-card">
      <template #header>用例明细</template>
      <el-table :data="results" row-key="id" border>
        <el-table-column prop="id" label="ID" width="160" />
        <el-table-column prop="question" label="问题" min-width="260" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.passed ? 'success' : 'danger'">{{ row.passed ? '通过' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关键词" width="120">
          <template #default="{ row }">{{ row.keywordMatchedCount }}/{{ row.keywordTotal }}</template>
        </el-table-column>
        <el-table-column label="关键词组" width="120">
          <template #default="{ row }">{{ row.keywordGroupMatchedCount || 0 }}/{{ row.keywordGroupTotal || 0 }}</template>
        </el-table-column>
        <el-table-column prop="sourceCount" label="来源数" width="100" />
        <el-table-column prop="searchMode" label="检索模式" width="130" />
        <el-table-column label="vector" width="100">
          <template #default="{ row }">
            <el-tag :type="row.vectorUsed ? 'success' : 'warning'">{{ row.vectorUsed }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="错误" min-width="360">
          <template #default="{ row }">
            <div>{{ (row.errors || []).join('；') }}</div>
            <div v-if="row.missingKeywords && row.missingKeywords.length" class="muted">缺失关键词：{{ row.missingKeywords.join('，') }}</div>
            <div v-if="row.missingKeywordGroups && row.missingKeywordGroups.length" class="muted">缺失关键词组：{{ JSON.stringify(row.missingKeywordGroups) }}</div>
            <div v-if="row.passed === false && row.answerPreview" class="answer-preview">回答摘要：{{ row.answerPreview }}</div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import { getDefaultRagEvalCases, runRagEval } from '../../api/agent'

const topK = ref(5)
const requireVectorUsed = ref(false)
const running = ref(false)
const casesJson = ref('[]')
const summary = ref<any | null>(null)
const results = ref<any[]>([])

async function loadDefaultCases() {
  const data: any = await getDefaultRagEvalCases()
  const cases = data?.data || data || []
  casesJson.value = JSON.stringify(cases, null, 2)
}

async function runEval() {
  running.value = true
  try {
    const cases = JSON.parse(casesJson.value || '[]')
    const data: any = await runRagEval({ cases, topK: topK.value, requireVectorUsed: requireVectorUsed.value })
    const result = data?.data || data || {}
    summary.value = result
    results.value = result.results || []
    ElMessage.success('评测完成')
  } catch (e: any) {
    ElMessage.error(e?.message || '评测失败')
  } finally {
    running.value = false
  }
}

function percent(v: any) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(1)}%`
}

onMounted(loadDefaultCases)
</script>

<style scoped>
.block-card { margin-bottom: 16px; }
.metric { padding: 12px; background: #f8fafc; border-radius: 10px; }
.metric span { color: #64748b; display: block; margin-bottom: 6px; }
.metric strong { font-size: 24px; }
.muted { color: #64748b; font-size: 12px; margin-top: 4px; }
.answer-preview { color: #475569; font-size: 12px; margin-top: 6px; line-height: 1.5; }
</style>
