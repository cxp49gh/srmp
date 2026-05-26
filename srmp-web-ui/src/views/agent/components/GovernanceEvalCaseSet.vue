<template>
  <section class="eval-case-set">
    <div class="eval-head">
      <div>
        <strong>评测用例集</strong>
        <span>维护能力 examples，并用当前草稿运行策略覆盖</span>
      </div>
      <div class="eval-actions">
        <el-button size="small" :disabled="selectedRows.length === 0" :loading="loading" @click="runSelectedCoverage">运行选中</el-button>
        <el-button size="small" type="primary" :disabled="rows.length === 0" :loading="loading" @click="runAllCoverage">运行全部</el-button>
      </div>
    </div>

    <section class="coverage-summary" v-if="coveragePayload.mode">
      <div><span>覆盖模式</span><strong>{{ coveragePayload.mode }}</strong></div>
      <div><span>用例数</span><strong>{{ coveragePayload.caseCount ?? rows.length }}</strong></div>
      <div><span>通过</span><strong>{{ coveragePayload.passedCount ?? 0 }}</strong></div>
      <div><span>失败</span><strong>{{ coveragePayload.failedCount ?? 0 }}</strong></div>
    </section>

    <el-table
      :data="rows"
      border
      stripe
      row-key="id"
      v-loading="loading"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="42" />
      <el-table-column prop="id" label="用例 ID" min-width="220" />
      <el-table-column prop="name" label="场景" min-width="160" />
      <el-table-column label="能力" min-width="230">
        <template #default="{ row }">
          <div class="stacked">
            <strong>{{ row.capabilityName || row.capabilityId || '-' }}</strong>
            <span>{{ row.capabilityId || '-' }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="期望工具" min-width="320">
        <template #default="{ row }">
          <div class="tag-list">
            <el-tag v-for="item in expectedToolTags(row)" :key="item" size="small" effect="plain">{{ item }}</el-tag>
            <span v-if="expectedToolTags(row).length === 0" class="muted">未配置</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="草稿覆盖" min-width="300">
        <template #default="{ row }">
          <div class="stacked">
            <el-tag :type="coverageStatusType(rowCoverage(row).status)">{{ rowCoverage(row).status || '未运行' }}</el-tag>
            <div class="tag-list">
              <el-tag v-for="item in arrayValue(rowCoverage(row).actualToolNames)" :key="item" size="small">{{ item }}</el-tag>
            </div>
            <span v-for="item in arrayValue(rowCoverage(row).warnings)" :key="item.code || item.message" class="error">
              {{ item.message || item.code }}
            </span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEditor(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="editorVisible" size="46%" title="编辑评测用例">
      <el-form label-width="110px">
        <el-form-item label="用例 ID">
          <el-input v-model="editingCase.id" />
        </el-form-item>
        <el-form-item label="场景名称">
          <el-input v-model="editingCase.name" />
        </el-form-item>
        <el-form-item label="所属能力">
          <el-select v-model="editingCase.capabilityId" filterable>
            <el-option
              v-for="capability in capabilityOptions"
              :key="capability.id"
              :label="capability.name ? `${capability.name} (${capability.id})` : capability.id"
              :value="capability.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="期望能力">
          <el-input v-model="editingCase.expectedCapabilityId" />
        </el-form-item>
        <el-form-item label="请求 JSON">
          <el-input v-model="requestText" type="textarea" :rows="8" spellcheck="false" />
        </el-form-item>
        <el-form-item label="Required">
          <el-input v-model="requiredToolsText" placeholder="逗号分隔工具名" />
        </el-form-item>
        <el-form-item label="Prohibited">
          <el-input v-model="prohibitedToolsText" placeholder="逗号分隔工具名" />
        </el-form-item>
        <el-form-item label="Exact">
          <el-input v-model="exactToolNamesText" placeholder="逗号分隔工具名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEditor">保存</el-button>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  extractEvaluationCases,
  writeEvaluationCases,
  type EvaluationCaseRow
} from './governanceDraft'

const props = defineProps<{
  capabilitiesConfig: Record<string, any>
  coveragePayload?: Record<string, any>
  loading?: boolean
}>()

const emit = defineEmits<{
  (event: 'update:capabilitiesConfig', value: Record<string, any>): void
  (event: 'runCoverage', caseIds: string[]): void
}>()

const editorVisible = ref(false)
const editingOriginalId = ref('')
const selectedRows = ref<EvaluationCaseRow[]>([])
const requestText = ref('{}')
const requiredToolsText = ref('')
const prohibitedToolsText = ref('')
const exactToolNamesText = ref('')
const editingCase = reactive({
  id: '',
  name: '',
  capabilityId: '',
  expectedCapabilityId: ''
})

const rows = computed(() => extractEvaluationCases(props.capabilitiesConfig || {}))
const capabilityOptions = computed(() => arrayValue(props.capabilitiesConfig?.capabilities).map((capability) => ({
  id: stringValue(capability.id),
  name: stringValue(capability.name || capability.id)
})).filter((capability) => capability.id))
const coverageCasesById = computed(() => {
  const map = new Map<string, Record<string, any>>()
  for (const item of arrayValue(props.coveragePayload?.cases)) {
    const id = stringValue(item.id)
    if (id) map.set(id, item)
  }
  return map
})

function handleSelectionChange(selection: EvaluationCaseRow[]) {
  selectedRows.value = selection
}

function runSelectedCoverage() {
  emit('runCoverage', selectedRows.value.map((item) => item.id).filter(Boolean))
}

function runAllCoverage() {
  emit('runCoverage', [])
}

function rowCoverage(row: EvaluationCaseRow) {
  return coverageCasesById.value.get(row.id) || {}
}

function expectedToolTags(row: EvaluationCaseRow): string[] {
  return [
    ...row.requiredTools.map((item) => `required:${item}`),
    ...row.prohibitedTools.map((item) => `prohibited:${item}`),
    ...row.exactToolNames.map((item) => `exact:${item}`)
  ]
}

function openEditor(row: EvaluationCaseRow) {
  editingOriginalId.value = row.id
  editingCase.id = row.id
  editingCase.name = row.name
  editingCase.capabilityId = row.capabilityId
  editingCase.expectedCapabilityId = row.expectedCapabilityId || row.capabilityId
  requestText.value = prettyJson(row.request)
  requiredToolsText.value = row.requiredTools.join(', ')
  prohibitedToolsText.value = row.prohibitedTools.join(', ')
  exactToolNamesText.value = row.exactToolNames.join(', ')
  editorVisible.value = true
}

function saveEditor() {
  const request = parseJsonObject(requestText.value)
  if (!request) {
    ElMessage.error('请求 JSON 必须是对象')
    return
  }
  const capability = capabilityOptions.value.find((item) => item.id === editingCase.capabilityId)
  const nextRow: EvaluationCaseRow = {
    id: editingCase.id.trim(),
    name: editingCase.name.trim() || editingCase.id.trim(),
    capabilityId: editingCase.capabilityId,
    capabilityName: capability?.name || editingCase.capabilityId,
    request,
    expect: {},
    expectedCapabilityId: editingCase.expectedCapabilityId.trim() || editingCase.capabilityId,
    requiredTools: splitList(requiredToolsText.value),
    prohibitedTools: splitList(prohibitedToolsText.value),
    exactToolNames: splitList(exactToolNamesText.value)
  }
  if (!nextRow.id || !nextRow.capabilityId) {
    ElMessage.error('用例 ID 和所属能力不能为空')
    return
  }
  const nextRows = rows.value.map((row) => row.id === editingOriginalId.value ? nextRow : row)
  emit('update:capabilitiesConfig', writeEvaluationCases(props.capabilitiesConfig || {}, nextRows))
  editorVisible.value = false
}

function coverageStatusType(status: string) {
  if (status === 'PASS') return 'success'
  if (status === 'FAIL') return 'danger'
  return 'info'
}

function splitList(value: string): string[] {
  return Array.from(new Set(value.split(',').map((item) => item.trim()).filter(Boolean)))
}

function parseJsonObject(text: string): Record<string, any> | null {
  try {
    const parsed = JSON.parse(text || '{}')
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null
  } catch {
    return null
  }
}

function prettyJson(value: any): string {
  return JSON.stringify(value || {}, null, 2)
}

function arrayValue(value: any): any[] {
  return Array.isArray(value) ? value : []
}

function stringValue(value: any): string {
  return value == null ? '' : String(value)
}
</script>

<style scoped>
.eval-case-set {
  display: grid;
  gap: 12px;
}

.eval-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.eval-head > div:first-child,
.stacked {
  display: grid;
  gap: 4px;
}

.eval-actions,
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.eval-head span,
.muted,
.stacked span {
  color: #64748b;
  font-size: 12px;
}

.error {
  color: #dc2626;
  font-size: 12px;
}

.coverage-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.coverage-summary div {
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f8fafc;
}

.coverage-summary span {
  color: #64748b;
  font-size: 12px;
}

.coverage-summary strong {
  display: block;
  margin-top: 6px;
}

@media (max-width: 900px) {
  .eval-head,
  .coverage-summary {
    grid-template-columns: 1fr;
  }

  .eval-head {
    align-items: stretch;
  }
}
</style>
