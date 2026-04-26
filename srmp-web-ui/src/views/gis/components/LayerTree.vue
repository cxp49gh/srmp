<template>
  <div class="panel">
    <div class="title">图层控制</div>
    <el-checkbox v-model="state.roadRoute" @change="emitChange">路线</el-checkbox>
    <el-checkbox v-model="state.roadSection" @change="emitChange">路段</el-checkbox>
    <el-checkbox v-model="state.evaluationUnit" @change="emitChange">评定单元</el-checkbox>
    <el-checkbox v-model="state.disease" @change="emitChange">病害</el-checkbox>
    <el-checkbox v-model="state.assessment" @change="emitChange">评定专题</el-checkbox>
  </div>
</template>
<script setup lang="ts">
import { reactive, watch } from 'vue'
export interface LayerState { roadRoute: boolean; roadSection: boolean; evaluationUnit: boolean; disease: boolean; assessment: boolean }
const props = defineProps<{ modelValue: LayerState }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: LayerState): void; (e: 'change', v: LayerState): void }>()
const state = reactive<LayerState>({ ...props.modelValue })
watch(() => props.modelValue, v => Object.assign(state, v), { deep: true })
function emitChange() { const v = { ...state }; emit('update:modelValue', v); emit('change', v) }
</script>
<style scoped>.panel{padding:12px}.title{font-weight:700;margin-bottom:10px}:deep(.el-checkbox){display:flex;margin-bottom:8px}</style>
