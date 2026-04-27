<template>
  <div class="legend">
    <div class="title">路况等级</div>
    <div v-for="i in list" :key="i.name" class="row">
      <span class="color" :style="{background:i.color}" />{{ i.name }}
    </div>
  </div>
</template>

<script setup lang="ts">
const list = [
  { name: '优', color: '#16a34a' },
  { name: '良', color: '#2563eb' },
  { name: '中', color: '#eab308' },
  { name: '次', color: '#f97316' },
  { name: '差', color: '#dc2626' }
]
</script>

<style scoped>
.legend {
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.96);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.12);
}
.title {
  font-weight: 700;
  margin-bottom: 8px;
}
.row {
  font-size: 12px;
  line-height: 22px;
}
.color {
  display: inline-block;
  width: 22px;
  height: 4px;
  margin-right: 8px;
  border-radius: 4px;
  vertical-align: middle;
}
</style>
