<template>
  <div class="agent-page">
    <AgentSidebar />
    <router-view />
  </div>
</template>

<script setup lang="ts">
import AgentSidebar from './components/AgentSidebar.vue'
</script>

<style scoped>
.agent-page {
  width: 100%;
  height: 100%;
  display: flex;
  background: #f1f5f9;
  color: #0f172a;
}

@media (max-width: 900px) {
  .agent-page {
    flex-direction: column;
  }
}
</style>
