<template>
  <AgentPageShell
    title="Outline 文档搜索"
    description="在线调用 Outline 搜索接口，测试团队文档检索效果。"
  >
    <el-card class="search-card">
      <el-form :inline="true">
        <el-form-item label="关键词">
          <el-input v-model="query" placeholder="例如：病害复核流程" style="width: 360px" />
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
      <template #header>搜索结果</template>
      <el-empty v-if="results.length === 0" description="暂无结果" />
      <div v-for="item in results" :key="item.id || item.title" class="result-item">
        <div class="title-row">
          <strong>{{ item.title }}</strong>
          <el-button v-if="item.id" size="small" @click="loadDoc(item.id)">读取文档</el-button>
        </div>
        <p v-if="item.url">{{ item.url }}</p>
        <div>{{ item.text }}</div>
      </div>
    </el-card>

    <el-dialog v-model="docVisible" title="Outline 文档" width="760px">
      <pre>{{ currentDoc }}</pre>
    </el-dialog>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import AgentPageShell from './components/AgentPageShell.vue'
import { getOutlineDocument, searchOutline, type OutlineSearchResult } from '../../api/outline'

const query = ref('病害复核流程')
const limit = ref(5)
const loading = ref(false)
const results = ref<OutlineSearchResult[]>([])
const docVisible = ref(false)
const currentDoc = ref('')

async function doSearch() {
  loading.value = true
  try {
    results.value = await searchOutline({
      query: query.value,
      limit: limit.value
    })
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
}

.result-item p {
  color: #64748b;
  margin: 4px 0;
}

pre {
  white-space: pre-wrap;
}
</style>