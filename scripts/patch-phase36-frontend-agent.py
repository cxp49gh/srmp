#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

AGENT_TS = ROOT / "srmp-web-ui/src/api/agent.ts"
FLOAT_VUE = ROOT / "srmp-web-ui/src/views/gis/components/AgentChatFloat.vue"


def fail(msg: str):
    print("[FAIL] " + msg)
    sys.exit(1)


def backup(path: Path):
    if not path.exists():
        fail(f"文件不存在：{path}")
    bak = path.with_suffix(path.suffix + ".phase36.bak")
    if not bak.exists():
        bak.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")


def patch_agent_ts():
    path = AGENT_TS
    backup(path)
    s = path.read_text(encoding="utf-8")

    if "aiRequest" not in s:
        fail("agent.ts 未发现 aiRequest，请确认当前工程请求工具命名")

    if "export interface MapAiContext" not in s:
        marker = "export type MapObjectSolutionType ="
        if marker not in s:
            fail("agent.ts 未找到 MapObjectSolutionType 锚点")
        insert = """
export interface MapAiContext {
  tenantId?: string
  mode?: 'OBJECT' | 'REGION' | 'VIEWPORT' | 'ROUTE' | 'FREE' | string
  routeCode?: string
  year?: number
  mapObject?: Record<string, any> | null
  regionSummary?: Record<string, any> | null
  viewport?: Record<string, any> | null
  selectedLayers?: string[]
  nearbyObjects?: Array<Record<string, any>>
  userQuestion?: string
  extra?: Record<string, any>
}

export interface MapAgentChatRequest extends AgentChatRequest {
  mapContext?: MapAiContext
}

export interface AiKnowledgeMarkdownIngestRequest {
  tenantId?: string
  title: string
  sourceType?: string
  sourceId?: string
  content: string
  metadata?: Record<string, any>
}

export interface AiKnowledgeSearchRequest {
  tenantId?: string
  query: string
  topK?: number
  filters?: Record<string, any>
}

"""
        s = s.replace(marker, insert + marker, 1)

    if "export function mapAgentChat" not in s:
        marker = """export function chat(data: AgentChatRequest): Promise<any> {
  return aiRequest.post('/api/agent/chat', data)
}
"""
        if marker not in s:
            fail("agent.ts 未找到 chat() 锚点，无法插入 mapAgentChat")
        insert = marker + """
/**
 * Phase36：一张图 AI Agent 聊天接口。
 * 检查关键字：mapAgentChat
 */
export function mapAgentChat(data: MapAgentChatRequest): Promise<any> {
  return aiRequest.post('/api/agent/map-agent/chat', data)
}
"""
        s = s.replace(marker, insert, 1)

    if "export function ingestKnowledgeMarkdown" not in s:
        s += """

/**
 * Phase36：Markdown 知识入库。
 * 检查关键字：ingestKnowledgeMarkdown
 */
export function ingestKnowledgeMarkdown(data: AiKnowledgeMarkdownIngestRequest): Promise<any> {
  return aiRequest.post('/api/ai/knowledge/ingest/markdown', data)
}

/**
 * Phase36：AI 知识库检索。
 * 检查关键字：searchAiKnowledge
 */
export function searchAiKnowledge(data: AiKnowledgeSearchRequest): Promise<any> {
  return aiRequest.post('/api/ai/knowledge/search', data)
}

/**
 * 兼容别名。
 */
export function searchKnowledge(data: AiKnowledgeSearchRequest): Promise<any> {
  return searchAiKnowledge(data)
}
"""

    path.write_text(s, encoding="utf-8")
    print(f"[OK] patched {path}")


def patch_agent_chat_float():
    path = FLOAT_VUE
    backup(path)
    s = path.read_text(encoding="utf-8")

    if "AgentChatFloat" not in str(path):
        fail("路径异常")

    if "mapAgentChat" not in s:
        # import block in current code:
        s = s.replace(
            "  chat,\n  generateMapObjectSolution,",
            "  chat,\n  mapAgentChat,\n  generateMapObjectSolution,",
            1,
        )

    if "toolResults?: any[]" not in s:
        s = s.replace(
            "  trace?: Record<string, any> | null\n}",
            "  trace?: Record<string, any> | null\n  sources?: any[]\n  toolResults?: any[]\n}",
            1,
        )

    if "const useAgentTools = ref(true)" not in s:
        s = s.replace(
            "const activeTrace = ref<Record<string, any> | null>(null)\n",
            "const activeTrace = ref<Record<string, any> | null>(null)\nconst useAgentTools = ref(true)\n",
            1,
        )

    if "Agent工具" not in s:
        s = s.replace(
            '        <el-checkbox v-model="options.useOutline">Outline</el-checkbox>\n',
            '        <el-checkbox v-model="options.useOutline">Outline</el-checkbox>\n        <el-checkbox v-model="useAgentTools">Agent工具</el-checkbox>\n',
            1,
        )

    if "const activeRegionSummary = computed" not in s:
        marker = """const activeMapObject = computed(() => {
  return props.mapObject || props.context?.mapObject || props.context?.selectedMapObject || props.context?.selected || null
})
"""
        if marker not in s:
            fail("AgentChatFloat.vue 未找到 activeMapObject computed 锚点")
        insert = marker + """
const activeRegionSummary = computed(() => {
  return props.context?.regionSummary || props.context?.region || null
})

const contextMode = computed(() => {
  if (activeMapObject.value) return 'OBJECT'
  if (activeRegionSummary.value) return 'REGION'
  if (props.context?.viewport || props.context?.bounds) return 'VIEWPORT'
  if (props.context?.query?.routeCode || props.context?.routeCode) return 'ROUTE'
  return 'FREE'
})
"""
        s = s.replace(marker, insert, 1)

    if "if (activeRegionSummary.value) return '框选区域｜区域养护分析'" not in s:
        s = s.replace(
            """const contextText = computed(() => {
  if (activeMapObject.value) return mapContextLabel.value
  const query = props.context?.query || {}
""",
            """const contextText = computed(() => {
  if (activeMapObject.value) return mapContextLabel.value
  if (activeRegionSummary.value) return '框选区域｜区域养护分析'
  const query = props.context?.query || {}
""",
            1,
        )

    if "function buildMapAiContext" not in s:
        marker = "async function generateSolutionDraft(solutionType: MapObjectSolutionType) {"
        if marker not in s:
            fail("AgentChatFloat.vue 未找到 generateSolutionDraft 锚点")
        func = """/**
 * Phase36 关键方法：buildMapAiContext
 * 构建一张图 AI Agent 使用的地图上下文包。
 */
function buildMapAiContext(message: string) {
  const query = props.context?.query || props.context || {}
  return {
    mode: contextMode.value,
    routeCode: query.routeCode || activeMapObject.value?.routeCode || activeMapObject.value?.route_code,
    year: Number(query.year || activeMapObject.value?.year || 2026),
    mapObject: activeMapObject.value,
    regionSummary: activeRegionSummary.value,
    viewport: props.context?.viewport || props.context?.bounds || null,
    selectedLayers: props.context?.selectedLayers || [],
    nearbyObjects: props.context?.nearbyObjects || [],
    userQuestion: message,
    extra: { rawContext: props.context || {} }
  }
}

"""
        s = s.replace(marker, func + marker, 1)

    if "const res: any = useAgentTools.value" not in s:
        old = """    const res: any = await chat({
      message: text,
      context: props.context,
      mapObject: activeMapObject.value,
      options: { ...options }
    })"""
        new = """    const requestPayload = {
      message: text,
      context: props.context,
      mapObject: activeMapObject.value,
      options: { ...options, useTools: useAgentTools.value }
    }

    const res: any = useAgentTools.value
      ? await mapAgentChat({
          ...requestPayload,
          mapContext: buildMapAiContext(text)
        })
      : await chat(requestPayload)"""
        if old not in s:
            fail("AgentChatFloat.vue 未找到 send() 中 chat() 调用锚点")
        s = s.replace(old, new, 1)

    if "sources: payload.data?.sources" not in s:
        s = s.replace(
            "      trace: payload.data?.trace || payload.trace || null,\n      meta: {",
            "      trace: payload.data?.trace || payload.trace || null,\n      sources: payload.data?.sources || payload.sources || payload.data?.knowledgeHits || [],\n      toolResults: payload.data?.toolResults || payload.toolResults || payload.data?.tools || [],\n      meta: {",
            1,
        )

    if "参考资料" not in s:
        marker = """          <AiTraceButton v-if="item.role === 'assistant'" :trace="item.trace" class="trace-button" @open="openTrace" />"""
        if marker not in s:
            fail("AgentChatFloat.vue 未找到 AiTraceButton 模板锚点")
        panel = """          <div v-if="item.role === 'assistant' && item.sources && item.sources.length" class="source-panel">
            <div class="source-title">参考资料</div>
            <div v-for="(source, sIdx) in item.sources" :key="sIdx" class="source-item">
              <span class="source-index">{{ sIdx + 1 }}</span>
              <span class="source-main">
                {{ source.title || source.docTitle || source.documentTitle || '知识片段' }}
                <template v-if="source.sectionTitle || source.section"> / {{ source.sectionTitle || source.section }}</template>
              </span>
              <span v-if="source.score !== undefined && source.score !== null" class="source-score">{{ formatScore(source.score) }}</span>
            </div>
          </div>
          <div v-if="item.role === 'assistant' && item.toolResults && item.toolResults.length" class="tool-panel">
            <div class="source-title">工具调用</div>
            <div v-for="(tool, tIdx) in item.toolResults" :key="tIdx" class="tool-item">
              <span class="tool-name">{{ tool.toolName || tool.name || 'tool' }}</span>
              <el-tag size="small" :type="tool.success === false ? 'danger' : 'success'" effect="plain">
                {{ tool.success === false ? '失败' : '成功' }}
              </el-tag>
              <span class="tool-summary">
                {{ tool.summary || '' }}
                <template v-if="tool.count !== undefined && tool.count !== null">（{{ tool.count }}条）</template>
              </span>
            </div>
          </div>
""" + marker
        s = s.replace(marker, panel, 1)

    if "function formatScore" not in s:
        s = s.replace(
            "function openTrace(trace: Record<string, any>) {",
            """function formatScore(score: any) {
  const num = Number(score)
  return Number.isFinite(num) ? num.toFixed(3) : String(score)
}

function openTrace(trace: Record<string, any>) {""",
            1,
        )

    if ".source-panel" not in s:
        css = """
.source-panel,
.tool-panel {
  margin-top: 6px;
  padding: 8px;
  background: #f9fafb;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  font-size: 12px;
}

.source-title {
  margin-bottom: 6px;
  font-weight: 700;
  color: #334155;
}

.source-item,
.tool-item {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 4px 0;
  color: #475569;
}

.source-index {
  width: 18px;
  height: 18px;
  border-radius: 999px;
  background: #e0f2fe;
  color: #0369a1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.source-main {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-score {
  color: #64748b;
}

.tool-name {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  color: #0f766e;
}

.tool-summary {
  flex: 1;
  color: #64748b;
}

"""
        s = s.replace("\n.source-summary {", css + "\n.source-summary {", 1)

    path.write_text(s, encoding="utf-8")
    print(f"[OK] patched {path}")


def verify():
    checks = {
        AGENT_TS: ["mapAgentChat", "ingestKnowledgeMarkdown", "searchAiKnowledge"],
        FLOAT_VUE: ["Agent工具", "参考资料", "buildMapAiContext"],
    }
    for path, needles in checks.items():
        text = path.read_text(encoding="utf-8")
        for needle in needles:
            if needle not in text:
                fail(f"{path} 缺少 {needle}")
    print("[OK] Phase36 前端接入检查通过")


patch_agent_ts()
patch_agent_chat_float()
verify()
