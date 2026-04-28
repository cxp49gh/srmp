<template>
  <div class="layer-drawer srmp-card" :class="{ collapsed }">
    <div class="drawer-header">
      <span>{{ collapsed ? '图' : '图层控制' }}</span>
      <button type="button" class="collapse-btn" @click="collapsed = !collapsed">
        {{ collapsed ? '›' : '‹' }}
      </button>
    </div>

    <div v-show="!collapsed" class="drawer-content">
      <div class="group-title">道路资产</div>
      <el-checkbox v-model="localLayers.roadRoute" @change="emitChange">路线</el-checkbox>
      <el-checkbox v-model="localLayers.roadSection" @change="emitChange">路段</el-checkbox>
      <el-checkbox v-model="localLayers.evaluationUnit" @change="emitChange">评定单元</el-checkbox>

      <el-divider />

      <div class="group-title">业务图层</div>
      <el-checkbox v-model="localLayers.disease" @change="emitChange">病害</el-checkbox>
      <el-checkbox v-model="localLayers.assessment" @change="emitChange">评定专题</el-checkbox>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'

export interface LayerState {
  roadRoute?: boolean
  roadSection?: boolean
  evaluationUnit?: boolean
  disease?: boolean
  assessment?: boolean
  assessmentResult?: boolean
}

const props = defineProps<{
  layers: LayerState
}>()

const emit = defineEmits<{
  (e: 'update:layers', value: LayerState): void
  (e: 'change', value: LayerState): void
}>()

const collapsed = ref(false)
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
  top: 92px;
  left: 18px;
  z-index: 920;
  width: 236px;
  padding: 14px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.14);
  transition: width 0.18s ease, padding 0.18s ease;
  overflow: hidden;
}

.layer-drawer.collapsed {
  width: 56px;
  padding: 8px;
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-weight: 700;
}

.layer-drawer.collapsed .drawer-header {
  margin-bottom: 0;
  justify-content: center;
}

.drawer-content {
  display: flex;
  flex-direction: column;
}

.collapse-btn {
  border: none;
  background: transparent;
  font-size: 18px;
  cursor: pointer;
  color: #64748b;
  padding: 0 4px;
  line-height: 1;
}

.collapse-btn:hover {
  color: #2563eb;
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
  height: 28px;
}

:deep(.el-divider--horizontal) {
  margin: 12px 0;
}
</style>
