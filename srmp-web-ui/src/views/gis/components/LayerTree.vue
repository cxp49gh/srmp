<template>
  <div class="layer-tree srmp-card">
    <div class="panel-title">图层控制</div>

    <el-checkbox v-model="localLayers.roadRoute" @change="emitChange">路线</el-checkbox>
    <el-checkbox v-model="localLayers.roadSection" @change="emitChange">路段</el-checkbox>
    <el-checkbox v-model="localLayers.evaluationUnit" @change="emitChange">评定单元</el-checkbox>

    <el-divider />

    <el-checkbox v-model="localLayers.disease" @change="emitChange">病害分布</el-checkbox>
    <el-checkbox v-model="localLayers.assessment" @change="emitChange">评定专题</el-checkbox>

    <el-divider />

    <div class="hint">
      勾选图层后会自动请求后端 GeoJSON 接口。
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'

export interface LayerState {
  roadRoute: boolean
  roadSection: boolean
  evaluationUnit: boolean
  disease: boolean
  assessment: boolean
}

const props = defineProps<{
  modelValue: LayerState
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: LayerState): void
  (e: 'change', value: LayerState): void
}>()

const localLayers = reactive<LayerState>({ ...props.modelValue })

watch(
  () => props.modelValue,
  (value) => Object.assign(localLayers, value),
  { deep: true }
)

function emitChange() {
  const next = { ...localLayers }
  emit('update:modelValue', next)
  emit('change', next)
}
</script>

<style scoped>
.layer-tree {
  padding: 14px;
}

.panel-title {
  font-weight: 700;
  margin-bottom: 12px;
}

:deep(.el-checkbox) {
  display: flex;
  margin-right: 0;
  margin-bottom: 8px;
}

.hint {
  color: #64748b;
  font-size: 12px;
  line-height: 18px;
}
</style>