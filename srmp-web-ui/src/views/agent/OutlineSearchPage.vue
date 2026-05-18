<template>
  <AgentPageShell
    title="Outline 文档搜索"
    description="Outline 实时在线检索，结果不会写入本地知识库。"
  >
    <el-alert
      type="info"
      show-icon
      :closable="false"
      class="notice"
      title="此为 Outline 实时在线检索，不会写入本地知识库；只有已同步并完成向量化的文档才能稳定参与 AI/RAG。"
    />

    <el-card class="search-card">
      <el-form :inline="true">
        <el-form-item label="关键词">
          <el-input v-model="query" placeholder="例如：病害复核流程" style="width: 360px" @keyup.enter="doSearch" />
        </el-form-item>
        <el-form-item label="Limit">
          <el-input-number v-model="limit" :min="1" :max="20" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="doSearch">搜索</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card>
      <template #header>搜索结果（Outline 在线）</template>
      <el-empty v-if="searched && results.length === 0" description="暂无结果" />
      <el-empty v-else-if="!searched" description="输入关键词后搜索" />
      <div v-for="item in results" :key="item.id || item.title" class="result-item">
        <div class="title-row">
          <strong>{{ item.title || '未命名文档' }}</strong>
          <div class="actions">
            <el-tag v-if="item.score != null" size="small" type="info">分数 {{ formatScore(item.score) }}</el-tag>
            <el-button v-if="item.id" size="small" @click="loadDoc(item.id)">查看详情</el-button>
            <el-link v-if="item.url" :href="item.url" target="_blank" type="primary">原文</el-link>
          </div>
        </div>
        <p class="meta">文档 ID：{{ item.id || '-' }}</p>
        <p v-if="item.url" class="url">{{ item.url }}</p>
        <div class="snippet">{{ item.text || '（无摘要）' }}</div>
      </div>
    </el-card>

    <el-dialog v-model="docVisible" title="Outline 文档详情" width="760px">
      <pre>{{ currentDoc }}</pre>
    </el-dialog>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import { getOutlineDocument, searchOutline, type OutlineSearchResult } from '../../api/outline'

const query = ref('')
const limit = ref(10)
const loading = ref(false)
const searched = ref(false)
const results = ref<OutlineSearchResult[]>([])
const docVisible = ref(false)
const currentDoc = ref('')

function formatScore(score?: number) {
  if (score == null) return '-'
  return Number(score).toFixed(3)
}

async function doSearch() {
  const q = query.value.trim()
  if (!q) {
    ElMessage.warning('请输入搜索关键词')
    return
  }
  loading.value = true
  searched.value = true
  try {
    results.value = await searchOutline({ query: q, limit: limit.value })
  } finally {
    loading.value = false
  }
}

async function loadDoc(id: string) {
  const doc = await getOutlineDocument(id)
  currentDoc.value = JSON.stringify(doc, null, 2)
  docVisible.value = true
}
</script>

<style scoped>
.notice {
  margin-bottom: 16px;
}

.search-card {
  margin-bottom: 16px;
}

.result-item {
  padding: 12px;
  background: #f8fafc;
  border-radius: 10px;
  margin-bottom: 12px;
}

.title-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.meta,
.url {
  color: #64748b;
  margin: 4px 0;
  font-size: 13px;
  word-break: break-all;
}

.snippet {
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.5;
}

pre {
  white-space: pre-wrap;
}
</style>
