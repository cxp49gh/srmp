<template>
  <main class="main">
    <header class="page-header">
      <div>
        <h1>{{ title }}</h1>
        <p v-if="description">{{ description }}</p>
      </div>
      <slot name="actions" />
    </header>

    <section class="content">
      <slot />
    </section>
  </main>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<style scoped>
.main {
  flex: 1;
  min-width: 0;
  min-height: 0;
  padding: 20px;
  overflow: auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

h1 {
  margin: 0;
  font-size: 22px;
}

p {
  margin: 6px 0 0;
  color: #64748b;
}

.content {
  min-height: calc(100vh - 110px);
}

@media (max-width: 900px) {
  .main {
    padding: 14px;
  }

  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
