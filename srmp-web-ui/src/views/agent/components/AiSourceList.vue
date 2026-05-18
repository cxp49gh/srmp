<template>
  <div class="ai-source-list">
    <template v-for="section in sections" :key="section.category">
      <div v-if="section.items.length" class="source-section">
        <h3 class="section-title">
          {{ section.label }}
          <el-tag size="small" :type="categoryTagType(section.category)">{{ section.items.length }}</el-tag>
        </h3>
        <div v-for="item in section.items" :key="item.key" class="source-card">
          <div class="source-head">
            <strong class="source-title">{{ item.title }}</strong>
            <el-tag size="small" :type="categoryTagType(item.category)">{{ categoryLabel(item.category) }}</el-tag>
          </div>
          <div class="source-meta">
            <span v-if="item.score != null">相似度 {{ item.score.toFixed(3) }}</span>
            <span v-if="item.category === 'OUTLINE'">
              {{ item.fromLocalKb ? '本地知识库（已同步）' : 'Outline 在线' }}
            </span>
            <span v-if="item.updatedAt">文档更新：{{ item.updatedAt }}</span>
            <span v-if="item.syncedAt">同步时间：{{ item.syncedAt }}</span>
          </div>
          <p v-if="item.excerpt" class="source-excerpt">{{ item.excerpt }}</p>
          <div class="source-actions">
            <el-link v-if="item.url" :href="item.url" target="_blank" type="primary">打开原文</el-link>
          </div>
        </div>
      </div>
    </template>

    <el-empty v-if="normalized.length === 0" :description="emptyText" />

    <div v-if="enableFeedback" class="feedback-bar">
      <el-button size="small" @click="openFeedback('MISSING_KNOWLEDGE')">知识缺失反馈</el-button>
      <el-button size="small" type="warning" plain @click="openFeedback('SOURCE_INACCURATE')">来源不准确反馈</el-button>
    </div>

    <el-dialog v-model="feedbackVisible" :title="feedbackTitle" width="520px" destroy-on-close>
      <el-form label-width="88px">
        <el-form-item label="用户问题">
          <el-input :model-value="question" type="textarea" :rows="2" readonly />
        </el-form-item>
        <el-form-item label="补充说明">
          <el-input v-model="feedbackRemark" type="textarea" :rows="3" placeholder="请描述缺失的资料或来源为何不准确" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feedbackVisible = false">取消</el-button>
        <el-button type="primary" :loading="feedbackSubmitting" @click="submitFeedback">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createAiKnowledgeFeedback, type AiFeedbackType } from '../../../api/knowledgeFeedback'
import {
  categoryLabel,
  categoryTagType,
  groupNormalizedSources,
  mergeAiSources,
  type AiSourceCategory
} from '../../../utils/aiSourceDisplay'

const props = withDefaults(
  defineProps<{
    sources?: Record<string, any>[]
    toolResults?: Record<string, any>[]
    outlineSources?: Record<string, any>[]
    question?: string
    businessContext?: Record<string, any>
    enableFeedback?: boolean
    emptyText?: string
  }>(),
  {
    sources: () => [],
    toolResults: () => [],
    outlineSources: () => [],
    question: '',
    enableFeedback: true,
    emptyText: '暂无引用来源'
  }
)

const normalized = computed(() => mergeAiSources(props.sources, props.toolResults, props.outlineSources))

const sections = computed(() => {
  const groups = groupNormalizedSources(normalized.value)
  return (['BUSINESS', 'KNOWLEDGE', 'OUTLINE'] as AiSourceCategory[]).map((category) => ({
    category,
    label: categoryLabel(category),
    items: groups[category]
  }))
})

const feedbackVisible = ref(false)
const feedbackType = ref<AiFeedbackType>('MISSING_KNOWLEDGE')
const feedbackRemark = ref('')
const feedbackSubmitting = ref(false)

const feedbackTitle = computed(() =>
  feedbackType.value === 'MISSING_KNOWLEDGE' ? '知识缺失反馈' : '答案来源不准确反馈'
)

function openFeedback(type: AiFeedbackType) {
  feedbackType.value = type
  feedbackRemark.value = ''
  feedbackVisible.value = true
}

async function submitFeedback() {
  feedbackSubmitting.value = true
  try {
    await createAiKnowledgeFeedback({
      feedbackType: feedbackType.value,
      question: props.question,
      remark: feedbackRemark.value.trim(),
      businessContext: props.businessContext,
      citedSources: normalized.value.map((item) => item.raw)
    })
    ElMessage.success('反馈已提交，感谢你的帮助')
    feedbackVisible.value = false
  } finally {
    feedbackSubmitting.value = false
  }
}
</script>

<style scoped>
.ai-source-list {
  font-size: 13px;
}

.source-section + .source-section {
  margin-top: 18px;
}

.section-title {
  margin: 0 0 10px;
  font-size: 15px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.source-card {
  padding: 10px 12px;
  margin-bottom: 10px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.source-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.source-title {
  flex: 1;
  min-width: 0;
  word-break: break-word;
}

.source-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
}

.source-excerpt {
  margin: 8px 0 0;
  line-height: 1.5;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-word;
}

.source-actions {
  margin-top: 8px;
}

.feedback-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #e2e8f0;
}
</style>
