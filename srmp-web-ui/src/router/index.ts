import { createRouter, createWebHistory } from 'vue-router'
import OneMap from '../views/gis/OneMap.vue'
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
import OutlineAutoSyncPage from '../views/agent/OutlineAutoSyncPage.vue'
import SolutionTemplatesPage from '../views/agent/SolutionTemplatesPage.vue'
import SolutionGeneratePage from '../views/agent/SolutionGeneratePage.vue'
import SolutionTasksPage from '../views/agent/SolutionTasksPage.vue'
import DemoDashboardPage from '../views/demo/DemoDashboardPage.vue'
import DataManagementPage from '../views/admin/DataManagementPage.vue'
import DataMgmtProjectImportPage from '../views/admin/DataMgmtProjectImportPage.vue'

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
      path: '/demo/dashboard',
      component: DemoDashboardPage,
      meta: {
        title: '演示数据联调看板'
      }
    },
    {
      path: '/admin/data-management',
      component: DataManagementPage,
      meta: {
        title: '数据管理'
      }
    },
    {
      path: '/admin/data-management/:projectId/import',
      component: DataMgmtProjectImportPage,
      meta: {
        title: '项目导入'
      }
    },
    {
      path: '/agent/ai-ops',
      component: AiOpsDashboardPage,
      meta: {
        title: 'AI 运维总览'
      }
    },
    {
      path: '/agent/langgraph-ops',
      component: LangGraphOpsPage,
      meta: {
        title: 'LangGraph 编排观测'
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
      path: '/agent/knowledge-vector',
      component: KnowledgeVectorPage,
      meta: {
        title: '向量知识库验证'
      }
    },
    {
      path: '/agent/rag-eval',
      component: RagEvalPage,
      meta: {
        title: 'RAG 质量评测'
      }
    },
    {
      path: '/agent/ai-health',
      component: AiHealthPage,
      meta: {
        title: 'AI 健康检查'
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
      path: '/agent/outline-auto-sync',
      component: OutlineAutoSyncPage,
      meta: {
        title: 'Outline 自动同步'
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