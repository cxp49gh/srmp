import { createRouter, createWebHistory } from 'vue-router'
import OneMap from '../views/gis/OneMap.vue'
import AiChatPage from '../views/agent/AiChatPage.vue'
import KnowledgeDocumentPage from '../views/agent/KnowledgeDocumentPage.vue'
import KnowledgeSearchPage from '../views/agent/KnowledgeSearchPage.vue'
import OutlineSearchPage from '../views/agent/OutlineSearchPage.vue'
import OutlineStatusPage from '../views/agent/OutlineStatusPage.vue'

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
      path: '/agent/outline-status',
      component: OutlineStatusPage,
      meta: {
        title: 'Outline 连接状态'
      }
    }
  ]
})

export default router