<template>
  <AgentPageShell
    title="知识库检索测试"
    description="测试 knowledge_chunk 关键词检索和知识库问答效果。"
  >
    <el-card class="search-card">
      <el-form :inline="true">
        <el-form-item label="问题">
          <el-input v-model="query" placeholder="例如：PCI 指标是什么意思" style="width: 360px" />
        </el-form-item>
        <el-form-item label="TopK">
          <el-input-number v-model="topK" :min="1" :max="20" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="searching" @click="doSearch">搜索</el-button>
          <el-button :loading="asking" @click="doAsk">问答</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card>
          <template #header>检索结果</template>
          <el-empty v-if="results.length === 0" description="暂无结果" />
          <div v-for="item in results" :key="item.chunkId" class="result-item">
            <strong>{{ item.title }}</strong>
            <p>{{ item.heading || item.sourceType }}</p>
            <div>{{ item.content }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card>
          <template #header>知识库问答</template>
          <el-empty v-if="!answer" description="暂无回答" />
          <pre v-else>{{ answer }}</pre>
        </el-card>
      </el-col>
    </el-row>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import AgentPageShell from './components/AgentPageShell.vue'
import { askKnowledge, searchKnowledge, type KnowledgeSearchResult } from '../../api/knowledge'

const query = ref('PCI 指标是什么意思？')
const topK = ref(5)
const searching = ref(false)
const asking = ref(false)
const results = ref<KnowledgeSearchResult[]>([])
const answer = ref('')

async function doSearch() {
  searching.value = true
  try {
    results.value = await searchKnowledge({
      query: query.value,
      topK: topK.value
    })
  } finally {
    searching.value = false
  }
}

async function doAsk() {
  asking.value = true
  try {
    const data = await askKnowledge({
      query: query.value,
      topK: topK.value
    })
    answer.value = data?.answer || JSON.stringify(data, null, 2)
    results.value = data?.sources || results.value
  } finally {
    asking.value = false
  }
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

.result-item p {
  color: #64748b;
  margin: 4px 0;
}

pre {
  white-space: pre-wrap;
  line-height: 1.6;
}
</style>