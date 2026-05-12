<template>
  <div class="layer-drawer srmp-card" :class="{ collapsed }">
    <div class="drawer-header">
      <span>{{ collapsed ? '图' : '图层控制' }}</span>
      <button type="button" class="collapse-btn" @click="collapsed = !collapsed">
        {{ collapsed ? '›' : '‹' }}
      </button>
    </div>

    <div v-show="!collapsed" class="drawer-content">
      <div class="drawer-actions">
        <el-button size="small" plain :loading="loading" @click="emit('refresh')">刷新图层</el-button>
        <el-button size="small" plain @click="emit('fit')">全图</el-button>
      </div>

      <div class="group-title">资产与业务</div>
      <layer-row label="路网" layer-key="roadRoute" :layers="localLayers" :status="statusMap.roadRoute" @change="emitChange" />
      <layer-row label="路段" layer-key="roadSection" :layers="localLayers" :status="statusMap.roadSection" @change="emitChange" />
      <layer-row label="病害" layer-key="disease" :layers="localLayers" :status="statusMap.disease" @change="emitChange" />

      <layer-row label="评定" layer-key="assessment" :layers="localLayers" :status="statusMap.assessment" @change="emitChange" />

      <p class="tier-hint">路段粒度由工具栏「路段专题」与「查询」决定；勾选「评定」才在地图上展示指标专题数据。</p>

      <div v-if="errorItems.length" class="layer-errors">
        <div class="error-title">加载异常</div>
        <div v-for="item in errorItems" :key="item.key" class="error-item">
          <span>{{ item.label }}</span>
          <em>{{ item.error }}</em>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, reactive, ref, watch, type PropType } from 'vue'

export interface LayerState {
  roadRoute?: boolean
  disease?: boolean
  /** 兼容旧版一张图勾选态 */
  roadSection?: boolean
  evaluationUnit?: boolean
  assessment?: boolean
  assessmentResult?: boolean
}

export interface LayerLoadStatus {
  key: string
  label?: string
  count?: number
  loading?: boolean
  error?: string
  loadedAt?: string
}

const layerLabels: Record<string, string> = {
  roadRoute: '路网',
  roadSection: '路段',
  evaluationUnit: '评定单元',
  disease: '病害',
  assessment: '评定'
}

const LayerRow = defineComponent({
  name: 'LayerRow',
  props: {
    label: { type: String, required: true },
    layerKey: { type: String, required: true },
    layers: { type: Object as PropType<LayerState>, required: true },
    status: { type: Object as PropType<LayerLoadStatus>, default: () => ({}) }
  },
  emits: ['change'],
  setup(props, { emit }) {
    return () => {
      const layerKey = props.layerKey as keyof LayerState
      const status = props.status || {}
      const checked = Boolean(props.layers[layerKey])
      const count = typeof status.count === 'number' ? status.count : 0
      const tagType = status.error ? 'danger' : checked && count > 0 ? 'success' : 'info'
      const tagText = status.loading
        ? '加载中'
        : status.error
          ? '异常'
          : checked
            ? `${count} 条`
            : '隐藏'

      return h('div', { class: 'layer-row' }, [
        h('label', { class: 'layer-check' }, [
          h('input', {
            type: 'checkbox',
            checked,
            onChange: (event: Event) => {
              props.layers[layerKey] = (event.target as HTMLInputElement).checked
              emit('change')
            }
          }),
          h('span', props.label)
        ]),
        h('span', { class: ['layer-status', tagType] }, tagText)
      ])
    }
  }
})

const props = defineProps<{
  layers: LayerState
  status?: Record<string, LayerLoadStatus>
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:layers', value: LayerState): void
  (e: 'change', value: LayerState): void
  (e: 'refresh'): void
  (e: 'fit'): void
}>()

const collapsed = ref(false)
const localLayers = reactive<LayerState>({ ...props.layers })

const statusMap = computed<Record<string, LayerLoadStatus>>(() => props.status || {})

const errorItems = computed(() => {
  return Object.entries(statusMap.value)
    .filter(([, status]) => !!status?.error)
    .map(([key, status]) => ({
      key,
      label: status.label || layerLabels[key] || key,
      error: status.error || ''
    }))
})

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
  width: 270px;
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

.drawer-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 10px;
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

.tier-hint {
  margin: 8px 0 0;
  font-size: 11px;
  line-height: 1.45;
  color: #64748b;
}

.layer-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-height: 30px;
}

.layer-check {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  cursor: pointer;
  color: #334155;
  font-size: 14px;
}

.layer-check input {
  width: 14px;
  height: 14px;
  margin: 0;
}

.layer-status {
  flex-shrink: 0;
  min-width: 52px;
  border-radius: 999px;
  padding: 2px 8px;
  text-align: center;
  font-size: 12px;
  line-height: 18px;
}

.layer-status.success {
  background: #dcfce7;
  color: #15803d;
}

.layer-status.info {
  background: #f1f5f9;
  color: #64748b;
}

.layer-status.danger {
  background: #fee2e2;
  color: #b91c1c;
}

.layer-errors {
  margin-top: 10px;
  padding: 8px;
  border-radius: 10px;
  background: #fff7ed;
  color: #9a3412;
  font-size: 12px;
}

.error-title {
  margin-bottom: 4px;
  font-weight: 700;
}

.error-item {
  display: flex;
  gap: 6px;
  margin-top: 4px;
}

.error-item span {
  flex-shrink: 0;
  font-weight: 700;
}

.error-item em {
  font-style: normal;
  color: #c2410c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.el-divider--horizontal) {
  margin: 12px 0;
}

@media (max-width: 640px) {
  .layer-drawer {
    top: 132px;
    left: 10px;
    width: min(270px, calc(100vw - 20px));
  }

  .layer-drawer.collapsed {
    width: 56px;
  }
}
</style>
