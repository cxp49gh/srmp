<template>
  <div class="quality-panel">
    <el-empty v-if="!quality || Object.keys(quality).length === 0" description="暂无质量校验结果" />
    <template v-else>
      <el-alert
        class="mb"
        :type="quality.passed ? 'success' : 'warning'"
        show-icon
        :closable="false"
        :title="quality.summary || title"
      />

      <el-descriptions :column="3" border size="small" class="mb">
        <el-descriptions-item label="是否通过">
          <el-tag :type="quality.passed ? 'success' : 'danger'">
            {{ quality.passed ? '通过' : '未通过' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="评分">{{ quality.score }}</el-descriptions-item>
        <el-descriptions-item label="等级">{{ quality.level }}</el-descriptions-item>
      </el-descriptions>

      <div v-for="(item, index) in quality.items || []" :key="index" class="quality-item">
        <div class="quality-title">
          <el-tag size="small" :type="tagType(item.level)">{{ item.level }}</el-tag>
          <strong>{{ item.code }}</strong>
          <span v-if="item.penalty">-{{ item.penalty }} 分</span>
        </div>
        <p>{{ item.message }}</p>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  quality?: Record<string, any> | null
}>()

const title = '质量校验完成'

function tagType(level: string) {
  if (level === 'OK') return 'success'
  if (level === 'ERROR') return 'danger'
  if (level === 'WARN') return 'warning'
  return 'info'
}
</script>

<style scoped>
.mb {
  margin-bottom: 12px;
}

.quality-item {
  padding: 10px;
  border-radius: 10px;
  background: #f8fafc;
  margin-bottom: 8px;
}

.quality-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.quality-title span {
  color: #ef4444;
  font-size: 12px;
}

.quality-item p {
  margin: 6px 0 0;
  color: #475569;
}
</style>