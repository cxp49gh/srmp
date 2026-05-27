<template>
  <section class="tool-catalog-editor">
    <div class="catalog-head">
      <div>
        <strong>工具目录编辑</strong>
        <span>维护工具元数据、可见性和编排策略，工具名保持运行时绑定不可改</span>
      </div>
      <el-input v-model="keyword" size="small" clearable placeholder="搜索工具、分类或说明" />
    </div>

    <section class="catalog-summary">
      <div><span>工具数</span><strong>{{ rows.length }}</strong></div>
      <div><span>启用</span><strong>{{ enabledCount }}</strong></div>
      <div><span>写风险</span><strong>{{ writeRiskCount }}</strong></div>
      <div><span>客户可见</span><strong>{{ customerVisibleCount }}</strong></div>
    </section>

    <el-table :data="filteredRows" border stripe row-key="name">
      <el-table-column label="工具" min-width="230">
        <template #default="{ row }">
          <div class="tool-cell">
            <strong>{{ row.label || row.name }}</strong>
            <span>{{ row.name }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="category" label="分类" width="140" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="风险" width="100">
        <template #default="{ row }">
          <el-tag :type="row.writeRisk ? 'danger' : 'success'">{{ row.writeRisk ? '写风险' : '只读' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="编排策略" min-width="260">
        <template #default="{ row }">
          <div class="tag-list">
            <el-tag v-if="row.adaptiveAllowed" size="small" type="warning" effect="plain">自适应可选</el-tag>
            <el-tag v-if="row.allowUnboundUsage" size="small" type="info" effect="plain">允许独立使用</el-tag>
            <el-tag v-if="row.customerVisible" size="small" type="success" effect="plain">客户可见</el-tag>
            <el-tag v-if="row.adminVisible" size="small" effect="plain">管理员可见</el-tag>
            <span v-if="!hasPolicyTag(row)" class="muted">未开放</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="300" show-overflow-tooltip />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button link type="primary" @click="openEditor(row)">编辑</el-button>
            <el-button link type="info" @click="emit('openRuntimeDetail', row.name)">运行详情</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="editorVisible" size="44%" title="编辑工具">
      <el-form label-width="110px">
        <el-form-item label="工具名">
          <el-input v-model="editingTool.name" disabled />
        </el-form-item>
        <el-form-item label="展示名">
          <el-input v-model="editingTool.label" clearable />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="editingTool.category" clearable />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="editingTool.description" type="textarea" :rows="4" maxlength="240" show-word-limit />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="editingTool.enabled" />
        </el-form-item>
        <el-form-item label="读写风险">
          <div class="switch-grid">
            <el-switch v-model="editingTool.readOnly" active-text="只读" />
            <el-switch v-model="editingTool.writeRisk" active-text="写风险" />
          </div>
        </el-form-item>
        <el-form-item label="编排策略">
          <div class="switch-grid">
            <el-switch v-model="editingTool.adaptiveAllowed" active-text="允许自适应选择" />
            <el-switch v-model="editingTool.allowUnboundUsage" active-text="允许无能力绑定使用" />
          </div>
        </el-form-item>
        <el-form-item label="可见性">
          <div class="switch-grid">
            <el-switch v-model="editingTool.customerVisible" active-text="客户可见" />
            <el-switch v-model="editingTool.adminVisible" active-text="管理员可见" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEditor">保存到草稿</el-button>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  buildToolCatalogRows,
  updateToolCatalogEntry,
  type ToolCatalogRow
} from './governanceDraft'

const props = defineProps<{
  toolsConfig: Record<string, any>
}>()

const emit = defineEmits<{
  (event: 'update:toolsConfig', value: Record<string, any>): void
  (event: 'openRuntimeDetail', toolName: string): void
}>()

const keyword = ref('')
const editorVisible = ref(false)
const editingTool = reactive<ToolCatalogRow>({
  name: '',
  label: '',
  category: '',
  description: '',
  enabled: true,
  readOnly: true,
  writeRisk: false,
  adaptiveAllowed: false,
  customerVisible: false,
  adminVisible: true,
  allowUnboundUsage: false,
  raw: {}
})

const rows = computed(() => buildToolCatalogRows(props.toolsConfig || {}))
const enabledCount = computed(() => rows.value.filter((row) => row.enabled).length)
const writeRiskCount = computed(() => rows.value.filter((row) => row.writeRisk).length)
const customerVisibleCount = computed(() => rows.value.filter((row) => row.customerVisible).length)
const filteredRows = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  if (!text) return rows.value
  return rows.value.filter((row) => JSON.stringify(row).toLowerCase().includes(text))
})

function openEditor(row: ToolCatalogRow) {
  Object.assign(editingTool, {
    ...row,
    raw: row.raw || {}
  })
  editorVisible.value = true
}

function saveEditor() {
  const toolName = editingTool.name.trim()
  if (!toolName) {
    ElMessage.error('工具名不能为空')
    return
  }
  emit('update:toolsConfig', updateToolCatalogEntry(props.toolsConfig || {}, toolName, editingTool))
  editorVisible.value = false
}

function hasPolicyTag(row: ToolCatalogRow): boolean {
  return row.adaptiveAllowed || row.allowUnboundUsage || row.customerVisible || row.adminVisible
}
</script>

<style scoped>
.tool-catalog-editor {
  display: grid;
  gap: 12px;
}

.catalog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.catalog-head > div,
.tool-cell {
  display: grid;
  gap: 4px;
}

.catalog-head span,
.tool-cell span,
.muted {
  color: #64748b;
  font-size: 12px;
}

.catalog-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.catalog-summary div {
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f8fafc;
}

.catalog-summary span {
  color: #64748b;
  font-size: 12px;
}

.catalog-summary strong {
  display: block;
  margin-top: 6px;
}

.tag-list,
.row-actions,
.switch-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
}

.switch-grid {
  align-items: center;
}

@media (max-width: 900px) {
  .catalog-head,
  .catalog-summary {
    grid-template-columns: 1fr;
  }

  .catalog-head {
    align-items: stretch;
  }
}
</style>
