<template>
  <el-tabs v-model="activeTab" class="solution-task-tabs">
    <el-tab-pane label="方案正文" name="content"><slot name="content" /></el-tab-pane>
    <el-tab-pane label="AI 分析依据" name="ai"><slot name="ai" /></el-tab-pane>
    <el-tab-pane label="引用来源" name="sources"><slot name="sources" /></el-tab-pane>
    <el-tab-pane label="质量检查" name="quality"><slot name="quality" /></el-tab-pane>
    <el-tab-pane label="版本历史" name="versions"><slot name="versions" /></el-tab-pane>
    <el-tab-pane label="状态流转" name="timeline"><slot name="timeline" /></el-tab-pane>
  </el-tabs>
</template>

<script setup lang="ts">
import { ref } from 'vue'
const activeTab = ref('content')
</script>

<style scoped>
.solution-task-tabs {
  margin-top: 12px;
}
</style>
