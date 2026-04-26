<template>
  <transition name="slide">
    <div v-if="visible" class="layer-drawer srmp-card">
      <div class="drawer-header">
        <span>图层控制</span>
        <button type="button" @click="$emit('update:visible', false)">×</button>
      </div>

      <div class="group-title">道路资产</div>
      <el-checkbox v-model="localLayers.roadRoute" @change="emitChange">路线</el-checkbox>
      <el-checkbox v-model="localLayers.roadSection" @change="emitChange">路段</el-checkbox>
      <el-checkbox v-model="localLayers.evaluationUnit" @change="emitChange">评定单元</el-checkbox>

      <el-divider />

      <div class="group-title">业务图层</div>
      <el-checkbox v-model="localLayers.disease" @change="emitChange">病害</el-checkbox>
      <el-checkbox v-model="localLayers.assessment" @change="emitChange">评定专题</el-checkbox>
    </div>
  </transition>
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
  visible: boolean
  layers: LayerState
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'update:layers', value: LayerState): void
  (e: 'change', value: LayerState): void
}>()

const localLayers = reactive<LayerState>({ ...props.layers })

watch(
  () => props.layers,
  (value) => Object.assign(localLayers, value),
  { deep: true }
)

function emitChange() {
  const next = { ...localLayers }
  emit('update:layers', next)
  emit('change', next)
}
</script>

<style scoped>
.layer-drawer {
  position: absolute;
  top: 84px;
  left: 68px;
  z-index: 920;
  width: 260px;
  max-height: calc(100vh - 140px);
  padding: 14px;
  overflow: auto;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.96);
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-weight: 700;
}

.drawer-header button {
  border: none;
  background: transparent;
  font-size: 22px;
  cursor: pointer;
  color: #64748b;
}

.group-title {
  margin: 10px 0 8px;
  font-size: 12px;
  font-weight: 700;
  color: #475569;
}

:deep(.el-checkbox) {
  display: flex;
  margin-right: 0;
  margin-bottom: 8px;
}
</style>
