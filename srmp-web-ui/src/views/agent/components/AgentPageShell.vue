<template>
  <div class="agent-page">
    <aside class="sidebar">
      <div class="brand">智路养护平台</div>
      <div class="subtitle">AI 知识库增强</div>

      <nav class="sidebar-nav">
        <router-link to="/gis/one-map">GIS 一张图</router-link>
        <router-link to="/admin/data-management">数据管理</router-link>
        <router-link to="/agent/ai-ops">AI 运维总览</router-link>
        <router-link to="/agent/langgraph-ops">LangGraph 编排</router-link>
        <router-link to="/agent/chat">AI 问答</router-link>
        <router-link to="/agent/ai-traces">AI 调用监控</router-link>
        <router-link to="/agent/solution-templates">方案模板</router-link>
        <router-link to="/agent/solution-generate">方案生成</router-link>
        <router-link to="/agent/solution-tasks">方案任务</router-link>
        <router-link to="/agent/knowledge-documents">知识库文档</router-link>
        <router-link to="/agent/knowledge-search">知识库检索</router-link>
        <router-link to="/agent/knowledge-vector">向量知识库验证</router-link>
        <router-link to="/agent/rag-eval">RAG 质量评测</router-link>
        <router-link to="/agent/ai-health">AI 健康检查</router-link>
        <router-link to="/agent/outline-search">Outline 搜索</router-link>
        <router-link to="/agent/outline-sync">Outline 同步</router-link>
        <router-link to="/agent/outline-auto-sync">Outline 自动同步</router-link>
        <router-link to="/agent/outline-status">Outline 状态</router-link>
      </nav>
    </aside>

    <main class="main">
      <header class="page-header">
        <div>
          <h1>{{ title }}</h1>
          <p>{{ description }}</p>
        </div>
        <slot name="actions" />
      </header>

      <section class="content">
        <slot />
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<style scoped>
.agent-page {
  width: 100%;
  height: 100%;
  display: flex;
  background: #f1f5f9;
  color: #0f172a;
}

.sidebar {
  width: 220px;
  padding: 20px 16px;
  background: #0f172a;
  color: #fff;
  height: 100vh;
  box-sizing: border-box;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.brand {
  font-size: 18px;
  font-weight: 800;
}

.subtitle {
  margin-top: 4px;
  color: #94a3b8;
  font-size: 12px;
}

.sidebar-nav {
  margin-top: 28px;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 6px;
  scroll-behavior: smooth;
  scrollbar-width: thin;
  scrollbar-color: #475569 transparent;
}

.sidebar-nav::-webkit-scrollbar {
  width: 6px;
}

.sidebar-nav::-webkit-scrollbar-track {
  background: transparent;
  border-radius: 6px;
}

.sidebar-nav::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.35);
  border-radius: 6px;
  border: 2px solid transparent;
  background-clip: padding-box;
}

.sidebar-nav::-webkit-scrollbar-thumb:hover {
  background: rgba(148, 163, 184, 0.55);
  background-clip: padding-box;
}

.sidebar-nav a {
  color: #cbd5e1;
  text-decoration: none;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 14px;
  flex: 0 0 auto;
}

.sidebar-nav a.router-link-active,
.sidebar-nav a:hover {
  background: #1e293b;
  color: #fff;
}

.main {
  flex: 1;
  min-width: 0;
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
  .agent-page {
    flex-direction: column;
  }

  .sidebar {
    width: 100%;
    height: auto;
    max-height: 190px;
  }

  .sidebar-nav {
    margin-top: 14px;
    flex: none;
    min-height: 0;
    flex-direction: row;
    overflow-x: auto;
    overflow-y: hidden;
    padding-bottom: 8px;
    margin-right: 0;
    padding-right: 4px;
    scrollbar-width: thin;
    scrollbar-color: #475569 transparent;
  }

  .sidebar-nav::-webkit-scrollbar {
    width: auto;
    height: 6px;
  }

  .sidebar-nav::-webkit-scrollbar-thumb {
    background: rgba(148, 163, 184, 0.35);
    border-radius: 6px;
    border: 2px solid transparent;
    background-clip: padding-box;
  }

  .sidebar-nav a {
    white-space: nowrap;
  }

  .main {
    padding: 14px;
  }

  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }
}

</style>