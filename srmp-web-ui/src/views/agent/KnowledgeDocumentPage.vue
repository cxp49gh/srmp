<template>
  <AgentPageShell
    title="知识库文档"
    description="录入 Markdown/TXT 文档，后端会自动切片进入知识库。"
  >
    <el-row :gutter="16">
      <el-col :span="10">
        <el-card>
          <template #header>新增文档</template>

          <el-form label-width="90px">
            <el-form-item label="标题">
              <el-input v-model="form.title" placeholder="例如：病害复核流程" />
            </el-form-item>
            <el-form-item label="分类">
              <el-select v-model="form.category" placeholder="选择分类">
                <el-option label="系统手册" value="SYSTEM_MANUAL" />
                <el-option label="公路标准" value="ROAD_STANDARD" />
                <el-option label="养护流程" value="MAINTENANCE_FLOW" />
                <el-option label="导入模板" value="IMPORT_TEMPLATE" />
                <el-option label="FAQ" value="FAQ" />
              </el-select>
            </el-form-item>
            <el-form-item label="来源类型">
              <el-select v-model="form.sourceType">
                <el-option label="LOCAL" value="LOCAL" />
                <el-option label="STANDARD" value="STANDARD" />
                <el-option label="OUTLINE" value="OUTLINE" />
              </el-select>
            </el-form-item>
            <el-form-item label="文档类型">
              <el-select v-model="form.docType">
                <el-option label="MARKDOWN" value="MARKDOWN" />
                <el-option label="TEXT" value="TEXT" />
              </el-select>
            </el-form-item>
            <el-form-item label="原文链接">
              <el-input v-model="form.url" placeholder="可选" />
            </el-form-item>
            <el-form-item label="内容">
              <el-input
                v-model="form.content"
                type="textarea"
                :rows="18"
                placeholder="# 文档标题\n请输入 Markdown 或纯文本内容"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading" @click="submit">保存并切片</el-button>
              <el-button @click="fillDemo">填充示例</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="14">
        <el-card>
          <template #header>操作结果</template>
          <el-empty v-if="!result" description="暂无操作结果" />
          <pre v-else>{{ result }}</pre>
        </el-card>
      </el-col>
    </el-row>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import { createKnowledgeDocument } from '../../api/knowledge'

const loading = ref(false)
const result = ref('')

const form = reactive({
  title: '',
  category: 'SYSTEM_MANUAL',
  sourceType: 'LOCAL',
  docType: 'MARKDOWN',
  url: '',
  content: ''
})

function fillDemo() {
  form.title = '病害复核流程'
  form.category = 'MAINTENANCE_FLOW'
  form.content = `# 病害复核流程

病害数据导入后，应由管理人员进行复核。复核内容包括病害位置、病害类型、严重程度、几何信息、照片资料和关联评定单元。

复核通过后，病害可以进入统计分析和 AI 研判。复核不通过时，应退回数据来源重新修正。`
}

async function submit() {
  if (!form.title || !form.content) {
    ElMessage.warning('请填写标题和内容')
    return
  }

  loading.value = true
  try {
    const data = await createKnowledgeDocument(form)
    result.value = JSON.stringify(data, null, 2)
    ElMessage.success('文档已保存并切片')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
pre {
  white-space: pre-wrap;
  background: #0f172a;
  color: #e2e8f0;
  padding: 16px;
  border-radius: 10px;
}
</style>