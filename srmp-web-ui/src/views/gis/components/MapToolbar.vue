<template>
  <div class="map-toolbar srmp-card">
    <el-form :inline="true" :model="localQuery" size="small">
      <el-form-item label="路线">
        <el-input v-model="localQuery.routeCode" placeholder="如 G210" clearable style="width: 140px" />
      </el-form-item>

      <el-form-item label="年度">
        <el-input v-model="localQuery.year" placeholder="2026" clearable style="width: 100px" />
      </el-form-item>

      <el-form-item label="指标">
        <el-select v-model="localQuery.indexCode" clearable placeholder="指标" style="width: 120px">
          <el-option label="MQI" value="MQI" />
          <el-option label="PQI" value="PQI" />
          <el-option label="PCI" value="PCI" />
          <el-option label="RQI" value="RQI" />
          <el-option label="RDI" value="RDI" />
        </el-select>
      </el-form-item>

      <el-form-item label="等级">
        <el-select v-model="localQuery.grade" clearable placeholder="等级" style="width: 120px">
          <el-option label="优" value="EXCELLENT" />
          <el-option label="良" value="GOOD" />
          <el-option label="中" value="MEDIUM" />
          <el-option label="次" value="POOR" />
          <el-option label="差" value="BAD" />
        </el-select>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" @click="emitSearch">查询</el-button>
        <el-button @click="emitReset">重置</el-button>
        <el-button @click="$emit('fit')">全图</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { GisLayerQuery } from '../../../api/gis'

const props = defineProps<{
  query: GisLayerQuery
}>()

const emit = defineEmits<{
  (e: 'search', value: GisLayerQuery): void
  (e: 'reset'): void
  (e: 'fit'): void
}>()

const localQuery = reactive<GisLayerQuery>({ ...props.query })

watch(
  () => props.query,
  (value) => Object.assign(localQuery, value),
  { deep: true }
)

function emitSearch() {
  emit('search', { ...localQuery })
}

function emitReset() {
  Object.assign(localQuery, {})
  emit('reset')
}
</script>

<style scoped>
.map-toolbar {
  padding: 10px 12px 0;
}

:deep(.el-form-item) {
  margin-bottom: 10px;
}
</style>