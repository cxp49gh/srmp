<template>
  <aside class="sidebar">
    <div class="brand">智路养护平台</div>
    <div class="subtitle">AI 知识库增强</div>

    <nav ref="navRef" class="sidebar-nav" @scroll="onNavScroll">
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
        <router-link to="/agent/knowledge-feedback">AI 知识反馈</router-link>
      <router-link to="/agent/knowledge-vector">向量知识库验证</router-link>
      <router-link to="/agent/rag-eval">RAG 质量评测</router-link>
      <router-link to="/agent/ai-health">AI 健康检查</router-link>
      <div class="nav-group">
        <button
          type="button"
          class="nav-group-toggle"
          :class="{ expanded: outlineNavExpanded, active: isOutlineRoute }"
          :aria-expanded="outlineNavExpanded"
          @click="toggleOutlineNav"
        >
          <span class="nav-group-label">Outline 文档同步</span>
          <span class="nav-group-chevron" aria-hidden="true" />
        </button>
        <div v-show="outlineNavExpanded" class="nav-group-children">
          <router-link to="/agent/outline/status">连接状态</router-link>
          <router-link to="/agent/outline/search">文档搜索</router-link>
          <router-link to="/agent/outline/sync">同步入库</router-link>
          <router-link to="/agent/outline/tasks">同步任务</router-link>
          <router-link to="/agent/outline/auto-sync">自动同步</router-link>
          <router-link to="/agent/outline/runs">运行监控</router-link>
          <router-link to="/agent/outline/governance">内容治理</router-link>
        </div>
      </div>
    </nav>
  </aside>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

const NAV_SCROLL_KEY = 'srmp.agent.sidebar.scrollTop'

const route = useRoute()
const navRef = ref<HTMLElement | null>(null)
const outlineNavExpanded = ref(false)
const isOutlineRoute = computed(() => route.path.startsWith('/agent/outline'))

function toggleOutlineNav() {
  outlineNavExpanded.value = !outlineNavExpanded.value
}

function onNavScroll() {
  const el = navRef.value
  if (!el) return
  sessionStorage.setItem(NAV_SCROLL_KEY, String(el.scrollTop))
}

function restoreNavScroll() {
  const el = navRef.value
  if (!el) return
  const saved = Number(sessionStorage.getItem(NAV_SCROLL_KEY) || 0)
  if (saved > 0) {
    el.scrollTop = saved
  }
}

function scrollActiveLinkIntoView() {
  const el = navRef.value
  if (!el) return
  const active = el.querySelector('.router-link-active') as HTMLElement | null
  if (!active) return
  const navRect = el.getBoundingClientRect()
  const linkRect = active.getBoundingClientRect()
  if (linkRect.top < navRect.top || linkRect.bottom > navRect.bottom) {
    active.scrollIntoView({ block: 'nearest', behavior: 'auto' })
    onNavScroll()
  }
}

watch(
  () => route.path,
  (path) => {
    if (path.startsWith('/agent/outline')) {
      outlineNavExpanded.value = true
    }
    nextTick(scrollActiveLinkIntoView)
  },
  { immediate: true }
)

onMounted(() => {
  nextTick(restoreNavScroll)
})
</script>

<style scoped>
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
  flex-shrink: 0;
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
  overflow-anchor: none;
  padding-right: 6px;
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

.nav-group {
  margin-top: 4px;
  padding-top: 8px;
  border-top: 1px solid #1e293b;
  flex: 0 0 auto;
}

.nav-group-toggle {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #cbd5e1;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  text-align: left;
}

.nav-group-toggle:hover,
.nav-group-toggle.expanded,
.nav-group-toggle.active {
  background: #1e293b;
  color: #fff;
}

.nav-group-label {
  flex: 1;
  min-width: 0;
}

.nav-group-chevron {
  width: 8px;
  height: 8px;
  border-right: 2px solid currentColor;
  border-bottom: 2px solid currentColor;
  transform: rotate(-45deg);
  transition: transform 0.2s ease;
  flex-shrink: 0;
  margin-top: -2px;
}

.nav-group-toggle.expanded .nav-group-chevron {
  transform: rotate(45deg);
  margin-top: 2px;
}

.nav-group-children {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 4px;
  padding-left: 4px;
}

.nav-group-children a {
  padding-left: 20px;
  font-size: 13px;
}

@media (max-width: 900px) {
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
  }

  .sidebar-nav::-webkit-scrollbar {
    width: auto;
    height: 6px;
  }

  .sidebar-nav a,
  .nav-group-toggle {
    white-space: nowrap;
  }

  .nav-group {
    display: flex;
    flex-direction: row;
    align-items: center;
    border-top: none;
    padding-top: 0;
    flex: 0 0 auto;
  }

  .nav-group-children {
    flex-direction: row;
    margin-top: 0;
    padding-left: 0;
    gap: 8px;
  }

  .nav-group-children a {
    padding-left: 12px;
  }
}
</style>
