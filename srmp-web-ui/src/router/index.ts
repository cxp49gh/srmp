import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import OneMap from '../views/gis/OneMap.vue'
import AgentLayout from '../views/agent/AgentLayout.vue'
import AiChatPage from '../views/agent/AiChatPage.vue'
import AiTracesPage from '../views/agent/AiTracesPage.vue'
import KnowledgeDocumentPage from '../views/agent/KnowledgeDocumentPage.vue'
import KnowledgeSearchPage from '../views/agent/KnowledgeSearchPage.vue'
import KnowledgeVectorPage from '../views/agent/KnowledgeVectorPage.vue'
import RagEvalPage from '../views/agent/RagEvalPage.vue'
import AiHealthPage from '../views/agent/AiHealthPage.vue'
import AiOpsDashboardPage from '../views/agent/AiOpsDashboardPage.vue'
import LangGraphOpsPage from '../views/agent/LangGraphOpsPage.vue'
import OutlineSearchPage from '../views/agent/OutlineSearchPage.vue'
import OutlineStatusPage from '../views/agent/OutlineStatusPage.vue'
import OutlineSyncPage from '../views/agent/OutlineSyncPage.vue'
import OutlineTasksPage from '../views/agent/OutlineTasksPage.vue'
import OutlineAutoSyncPage from '../views/agent/OutlineAutoSyncPage.vue'
import OutlineRunsPage from '../views/agent/OutlineRunsPage.vue'
import OutlineGovernancePage from '../views/agent/OutlineGovernancePage.vue'
import AiKnowledgeFeedbackPage from '../views/agent/AiKnowledgeFeedbackPage.vue'
import SolutionTemplatesPage from '../views/agent/SolutionTemplatesPage.vue'
import SolutionGeneratePage from '../views/agent/SolutionGeneratePage.vue'
import SolutionTasksPage from '../views/agent/SolutionTasksPage.vue'
import DemoDashboardPage from '../views/demo/DemoDashboardPage.vue'
import DataManagementPage from '../views/admin/DataManagementPage.vue'
import DataMgmtProjectImportPage from '../views/admin/DataMgmtProjectImportPage.vue'

const agentChildren: RouteRecordRaw[] = [
  {
    path: 'ai-ops',
    component: AiOpsDashboardPage,
    meta: { title: 'AI 运维总览' }
  },
  {
    path: 'langgraph-ops',
    component: LangGraphOpsPage,
    meta: { title: 'LangGraph 编排观测' }
  },
  {
    path: 'chat',
    component: AiChatPage,
    meta: { title: 'AI 问答' }
  },
  {
    path: 'ai-traces',
    component: AiTracesPage,
    meta: { title: 'AI 调用监控' }
  },
  {
    path: 'solution-templates',
    component: SolutionTemplatesPage,
    meta: { title: '方案模板管理' }
  },
  {
    path: 'solution-generate',
    component: SolutionGeneratePage,
    meta: { title: '方案生成' }
  },
  {
    path: 'solution-tasks',
    component: SolutionTasksPage,
    meta: { title: '方案任务' }
  },
  {
    path: 'knowledge-documents',
    component: KnowledgeDocumentPage,
    meta: { title: '知识库文档' }
  },
  {
    path: 'knowledge-search',
    component: KnowledgeSearchPage,
    meta: { title: '知识库检索测试' }
  },
  {
    path: 'knowledge-vector',
    component: KnowledgeVectorPage,
    meta: { title: '向量知识库验证' }
  },
  {
    path: 'rag-eval',
    component: RagEvalPage,
    meta: { title: 'RAG 质量评测' }
  },
  {
    path: 'ai-health',
    component: AiHealthPage,
    meta: { title: 'AI 健康检查' }
  },
  {
    path: 'outline/status',
    component: OutlineStatusPage,
    meta: { title: 'Outline 连接状态', permission: 'outline:status:view' }
  },
  {
    path: 'outline/search',
    component: OutlineSearchPage,
    meta: { title: 'Outline 文档搜索', permission: 'outline:search' }
  },
  {
    path: 'outline/sync',
    component: OutlineSyncPage,
    meta: { title: 'Outline 同步入库', permission: 'outline:sync:view' }
  },
  {
    path: 'outline/tasks',
    component: OutlineTasksPage,
    meta: { title: 'Outline 同步任务', permission: 'outline:sync:view' }
  },
  {
    path: 'outline/auto-sync',
    component: OutlineAutoSyncPage,
    meta: { title: 'Outline 自动同步', permission: 'outline:auto-sync:view' }
  },
  {
    path: 'outline/runs',
    component: OutlineRunsPage,
    meta: { title: 'Outline 运行监控', permission: 'outline:auto-sync:view' }
  },
  {
    path: 'outline/governance',
    component: OutlineGovernancePage,
    meta: { title: 'Outline 内容治理', permission: 'outline:governance:view' }
  },
  {
    path: 'knowledge-feedback',
    component: AiKnowledgeFeedbackPage,
    meta: { title: 'AI 知识反馈' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(_to, _from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }
    return { left: 0, top: 0 }
  },
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
      path: '/demo/dashboard',
      component: DemoDashboardPage,
      meta: {
        title: '演示数据联调看板'
      }
    },
    {
      path: '/agent',
      component: AgentLayout,
      children: agentChildren
    },
    {
      path: '/admin',
      component: AgentLayout,
      children: [
        {
          path: 'data-management',
          component: DataManagementPage,
          meta: {
            title: '数据管理'
          }
        },
        {
          path: 'data-management/:projectId/import',
          component: DataMgmtProjectImportPage,
          meta: {
            title: '项目导入'
          }
        }
      ]
    },
    { path: '/agent/outline-status', redirect: '/agent/outline/status' },
    { path: '/agent/outline-search', redirect: '/agent/outline/search' },
    { path: '/agent/outline-sync', redirect: '/agent/outline/sync' },
    { path: '/agent/outline-auto-sync', redirect: '/agent/outline/auto-sync' }
  ]
})

export default router
