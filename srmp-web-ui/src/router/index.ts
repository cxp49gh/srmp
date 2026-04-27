import { createRouter, createWebHistory } from 'vue-router'
import OneMap from '../views/gis/OneMap.vue'
import AiChatPage from '../views/agent/AiChatPage.vue'
import AiTracesPage from '../views/agent/AiTracesPage.vue'
import KnowledgeDocumentPage from '../views/agent/KnowledgeDocumentPage.vue'
import KnowledgeSearchPage from '../views/agent/KnowledgeSearchPage.vue'
import OutlineSearchPage from '../views/agent/OutlineSearchPage.vue'
import OutlineStatusPage from '../views/agent/OutlineStatusPage.vue'
import OutlineSyncPage from '../views/agent/OutlineSyncPage.vue'
import SolutionTemplatesPage from '../views/agent/SolutionTemplatesPage.vue'
import SolutionGeneratePage from '../views/agent/SolutionGeneratePage.vue'
import SolutionTasksPage from '../views/agent/SolutionTasksPage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/gis/one-map'
    },
    {
      path: '/gis/one-map',
      component: OneMap,
      meta: {
        title: 'GIS 一张图'
      }
    },
    {
      path: '/agent/chat',
      component: AiChatPage,
      meta: {
        title: 'AI 问答'
      }
    },
    {
      path: '/agent/ai-traces',
      component: AiTracesPage,
      meta: {
        title: 'AI 调用监控'
      }
    },
    {
      path: '/agent/solution-templates',
      component: SolutionTemplatesPage,
      meta: {
        title: '方案模板管理'
      }
    },
    {
      path: '/agent/solution-generate',
      component: SolutionGeneratePage,
      meta: {
        title: '方案生成'
      }
    },
    {
      path: '/agent/solution-tasks',
      component: SolutionTasksPage,
      meta: {
        title: '方案任务'
      }
    },
    {
      path: '/agent/knowledge-documents',
      component: KnowledgeDocumentPage,
      meta: {
        title: '知识库文档'
      }
    },
    {
      path: '/agent/knowledge-search',
      component: KnowledgeSearchPage,
      meta: {
        title: '知识库检索测试'
      }
    },
    {
      path: '/agent/outline-search',
      component: OutlineSearchPage,
      meta: {
        title: 'Outline 文档搜索'
      }
    },
    {
      path: '/agent/outline-sync',
      component: OutlineSyncPage,
      meta: {
        title: 'Outline 同步入库'
      }
    },
    {
      path: '/agent/outline-status',
      component: OutlineStatusPage,
      meta: {
        title: 'Outline 连接状态'
      }
    }
  ]
})

export default router