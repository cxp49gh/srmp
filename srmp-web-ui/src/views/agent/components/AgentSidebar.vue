<template>
  <aside class="sidebar">
    <div class="brand">智路养护平台</div>
    <div class="subtitle">养护业务与 AI 治理</div>

    <nav ref="navRef" class="sidebar-nav" @scroll="onNavScroll">
      <section v-for="section in menuSections" :key="section.title" class="nav-section">
        <div class="nav-section-title">{{ section.title }}</div>
        <template v-for="entry in section.items" :key="entryKey(entry)">
          <router-link v-if="entry.type === 'link'" class="nav-item" :to="entry.to">{{ entry.label }}</router-link>
          <div v-else class="nav-group">
            <button
              type="button"
              class="nav-group-toggle"
              :class="{ expanded: isGroupExpanded(entry.key), active: isGroupActive(entry) }"
              :aria-expanded="isGroupExpanded(entry.key)"
              @click="toggleGroup(entry.key)"
            >
              <span class="nav-group-label">{{ entry.label }}</span>
              <span class="nav-group-chevron" aria-hidden="true" />
            </button>
            <div v-show="isGroupExpanded(entry.key)" class="nav-group-children">
              <router-link v-for="child in entry.items" :key="child.to" class="sub-nav-item" :to="child.to">
                {{ child.label }}
              </router-link>
            </div>
          </div>
        </template>
      </section>
    </nav>
  </aside>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

type NavLink = {
  type: 'link'
  label: string
  to: string
}

type NavGroup = {
  type: 'group'
  key: string
  label: string
  activePrefixes?: string[]
  items: NavLink[]
}

type NavEntry = NavLink | NavGroup

type NavSection = {
  title: string
  items: NavEntry[]
}

const NAV_SCROLL_KEY = 'srmp.agent.sidebar.scrollTop'

const menuSections: NavSection[] = [
  {
    title: '工作台',
    items: [
      { type: 'link', label: 'GIS 一张图', to: '/gis/one-map' },
      { type: 'link', label: 'AI 问答', to: '/agent/chat' },
      { type: 'link', label: '方案生成', to: '/agent/solution-generate' },
      { type: 'link', label: '方案任务', to: '/agent/solution-tasks' }
    ]
  },
  {
    title: '数据资产',
    items: [
      {
        type: 'group',
        key: 'data-assets',
        label: '数据管理',
        activePrefixes: ['/admin/data-management'],
        items: [
          { type: 'link', label: '项目总览', to: '/admin/data-management/projects' },
          { type: 'link', label: '项目导入', to: '/admin/data-management/import' },
          { type: 'link', label: '导入记录', to: '/admin/data-management/import-records' },
          { type: 'link', label: '数据质量', to: '/admin/data-management/quality' },
          { type: 'link', label: '模板与规范', to: '/admin/data-management/templates' },
          { type: 'link', label: '清除与归档', to: '/admin/data-management/maintenance' },
          { type: 'link', label: '操作审计', to: '/admin/data-management/audit' }
        ]
      }
    ]
  },
  {
    title: '知识库',
    items: [
      {
        type: 'group',
        key: 'knowledge-base',
        label: '本地知识库',
        activePrefixes: ['/agent/knowledge', '/agent/rag-eval'],
        items: [
          { type: 'link', label: '知识库文档', to: '/agent/knowledge-documents' },
          { type: 'link', label: '知识库检索', to: '/agent/knowledge-search' },
          { type: 'link', label: '向量验证', to: '/agent/knowledge-vector' },
          { type: 'link', label: '知识反馈', to: '/agent/knowledge-feedback' },
          { type: 'link', label: 'RAG 质量评测', to: '/agent/rag-eval' }
        ]
      },
      {
        type: 'group',
        key: 'outline-sync',
        label: 'Outline 同步',
        activePrefixes: ['/agent/outline'],
        items: [
          { type: 'link', label: '连接状态', to: '/agent/outline/status' },
          { type: 'link', label: '文档搜索', to: '/agent/outline/search' },
          { type: 'link', label: '同步入库', to: '/agent/outline/sync' },
          { type: 'link', label: '同步任务', to: '/agent/outline/tasks' },
          { type: 'link', label: '自动同步', to: '/agent/outline/auto-sync' },
          { type: 'link', label: '运行监控', to: '/agent/outline/runs' },
          { type: 'link', label: '内容治理', to: '/agent/outline/governance' }
        ]
      }
    ]
  },
  {
    title: 'AI 治理',
    items: [
      {
        type: 'group',
        key: 'ai-governance',
        label: '能力与模板',
        activePrefixes: ['/agent/ai-governance', '/agent/solution-templates', '/agent/solution-eval'],
        items: [
          { type: 'link', label: 'AI 能力治理', to: '/agent/ai-governance' },
          { type: 'link', label: '方案模板', to: '/agent/solution-templates' },
          { type: 'link', label: '方案回归评测', to: '/agent/solution-eval' }
        ]
      }
    ]
  },
  {
    title: '系统监控',
    items: [
      {
        type: 'group',
        key: 'system-monitoring',
        label: '运行与排障',
        activePrefixes: ['/agent/ai-ops', '/agent/ai-traces', '/agent/langgraph-ops', '/agent/ai-health'],
        items: [
          { type: 'link', label: 'AI 运维总览', to: '/agent/ai-ops' },
          { type: 'link', label: 'AI 调用监控', to: '/agent/ai-traces' },
          { type: 'link', label: 'LangGraph 编排', to: '/agent/langgraph-ops' },
          { type: 'link', label: 'AI 健康检查', to: '/agent/ai-health' }
        ]
      }
    ]
  }
]

const route = useRoute()
const navRef = ref<HTMLElement | null>(null)
const expandedGroups = reactive<Record<string, boolean>>({
  'data-assets': false,
  'knowledge-base': false,
  'outline-sync': false,
  'ai-governance': false,
  'system-monitoring': false
})

function entryKey(entry: NavEntry) {
  return entry.type === 'link' ? entry.to : entry.key
}

function isGroupExpanded(key: string) {
  return Boolean(expandedGroups[key])
}

function toggleGroup(key: string) {
  expandedGroups[key] = !expandedGroups[key]
}

function isGroupActive(group: NavGroup) {
  return isGroupActiveForPath(group, route.path)
}

function isGroupActiveForPath(group: NavGroup, path: string) {
  if (group.activePrefixes?.some((prefix) => path.startsWith(prefix))) return true
  return group.items.some((item) => isRouteMatch(path, item.to))
}

function isRouteMatch(path: string, target: string) {
  return path === target || path.startsWith(`${target}/`)
}

function expandActiveEntry(entry: NavEntry, path: string) {
  if (entry.type === 'group' && isGroupActiveForPath(entry, path)) {
    expandedGroups[entry.key] = true
  }
}

function expandActiveGroups(path = route.path) {
  menuSections.forEach((section) => {
    section.items.forEach((entry) => expandActiveEntry(entry, path))
  })
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
    expandActiveGroups(path)
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
  padding: 20px 14px;
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
  margin-top: 24px;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
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

.nav-section {
  flex: 0 0 auto;
}

.nav-section + .nav-section {
  padding-top: 10px;
  border-top: 1px solid #1e293b;
}

.nav-section-title {
  margin: 0 0 6px 6px;
  color: #64748b;
  font-size: 11px;
  font-weight: 800;
}

.nav-item,
.sub-nav-item {
  display: block;
  color: #cbd5e1;
  text-decoration: none;
  padding: 9px 12px;
  border-radius: 9px;
  font-size: 14px;
  flex: 0 0 auto;
}

.nav-item.router-link-active,
.nav-item:hover,
.sub-nav-item.router-link-active,
.sub-nav-item:hover {
  background: #1e293b;
  color: #fff;
}

.nav-group {
  flex: 0 0 auto;
}

.nav-group + .nav-group {
  margin-top: 4px;
}

.nav-group-toggle {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 9px 12px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: #cbd5e1;
  font-size: 14px;
  font-weight: 700;
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
  gap: 3px;
  margin-top: 3px;
  padding-left: 4px;
}

.sub-nav-item {
  padding-left: 20px;
  font-size: 13px;
}

@media (max-width: 900px) {
  .sidebar {
    width: 100%;
    height: auto;
    max-height: 210px;
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

  .nav-section {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: 0 0 auto;
  }

  .nav-section + .nav-section {
    padding-top: 0;
    padding-left: 10px;
    border-top: none;
    border-left: 1px solid #1e293b;
  }

  .nav-section-title {
    margin: 0;
    white-space: nowrap;
  }

  .nav-item,
  .sub-nav-item,
  .nav-group-toggle {
    white-space: nowrap;
  }

  .nav-group {
    display: flex;
    flex-direction: row;
    align-items: center;
    flex: 0 0 auto;
  }

  .nav-group-children {
    flex-direction: row;
    margin-top: 0;
    padding-left: 0;
    gap: 8px;
  }

  .sub-nav-item {
    padding-left: 12px;
  }
}
</style>
