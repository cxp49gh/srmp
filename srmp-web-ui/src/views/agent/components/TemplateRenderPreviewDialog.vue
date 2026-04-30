<template>
  <el-dialog
    :model-value="visible"
    title="模板生效验证"
    width="760px"
    append-to-body
    @update:model-value="emit('update:visible', $event)"
  >
    <TemplateMetaCard :meta="normalizedResult.templateMeta || normalizedResult.template_meta || normalizedResult || null" />
    <TemplateVariableCheckPanel
      :variables="normalizedResult.variables || {}"
      :missing-variables="normalizedResult.missingVariables || normalizedResult.missing_variables || []"
      :unused-variables="normalizedResult.unusedVariables || normalizedResult.unused_variables || []"
    />

    <pre v-if="renderedMarkdown" class="markdown-preview">{{ renderedMarkdown }}</pre>
    <el-empty v-else description="暂无渲染内容" />

    <template #footer>
      <el-button @click="emit('update:visible', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import TemplateMetaCard from './TemplateMetaCard.vue'
import TemplateVariableCheckPanel from './TemplateVariableCheckPanel.vue'

defineOptions({ name: 'TemplateRenderPreviewDialog' })

const props = defineProps<{
  visible: boolean
  result?: Record<string, any> | null
}>()

const emit = defineEmits<{ (e: 'update:visible', value: boolean): void }>()

const normalizedResult = computed(() => props.result || {})
const renderedMarkdown = computed(() => normalizedResult.value.renderedMarkdown || normalizedResult.value.rendered_markdown || '')
</script>

<style scoped>
.markdown-preview {
  margin-top: 12px;
  max-height: 360px;
  overflow: auto;
  padding: 12px;
  border-radius: 8px;
  background: #0f172a;
  color: #e2e8f0;
  line-height: 1.6;
  white-space: pre-wrap;
}
</style>
