<template>
  <div class="toolbar">
    <el-form :inline="true" :model="form" size="small">
      <el-form-item label="路线"><el-input v-model="form.routeCode" clearable placeholder="G210" style="width:120px" /></el-form-item>
      <el-form-item label="年度"><el-input v-model="form.year" clearable placeholder="2026" style="width:90px" /></el-form-item>
      <el-form-item label="指标"><el-select v-model="form.indexCode" clearable style="width:100px"><el-option v-for="i in indexes" :key="i" :label="i" :value="i" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" @click="$emit('search', { ...form })">查询</el-button><el-button @click="reset">重置</el-button><el-button @click="$emit('fit')">全图</el-button></el-form-item>
    </el-form>
  </div>
</template>
<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { GisLayerQuery } from '../../../api/gis'
const props = defineProps<{ query: GisLayerQuery }>()
defineEmits<{ (e: 'search', v: GisLayerQuery): void; (e: 'reset'): void; (e: 'fit'): void }>()
const indexes = ['MQI','PQI','PCI','RQI','RDI','SCI','BCI','TCI']
const form = reactive<GisLayerQuery>({ ...props.query })
watch(() => props.query, v => Object.assign(form, v), { deep: true })
function reset() { form.routeCode=''; form.year=''; form.indexCode='MQI'; form.grade='' }
</script>
<style scoped>.toolbar{padding:8px 12px 0;background:rgba(255,255,255,.96);border-radius:8px;box-shadow:0 4px 16px rgba(15,23,42,.12)}:deep(.el-form-item){margin-bottom:8px}</style>
