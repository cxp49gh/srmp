# 阶段三十六：一张图 AI Agent 与向量知识库增强设计方案

## 1. 结论

经过推理，阶段三十六继续在 AI 方向增强是可行的，而且建议优先做 **“一张图 AI Agent 与向量知识库增强”**。

不建议第一步就全量接入 LangGraph，也不建议把当前 Java/Spring Boot 主链路整体迁移到 Python Agent 服务。更稳妥的路线是：

```text
短期：Spring Boot 原生 Agent 工具层 + pgvector 向量知识库
中期：抽象 Agent Orchestrator 标准接口
长期：可选接入 LangGraph 独立编排服务
```

阶段三十六第一版应形成最小闭环：

```text
知识入库
  ↓
向量检索
  ↓
一张图 AI 调用 knowledge.retrieve 工具
  ↓
结合地图对象 / 框选区域上下文生成回答
  ↓
回答展示引用来源
  ↓
Trace 展示知识检索与工具调用过程
```

---

## 2. 为什么这个阶段可行

当前系统已经具备：

```text
GIS 一张图
地图对象 AI 分析
框选区域 AI 分析
方案草稿生成
方案任务保存
历史版本管理
状态流转
模板生效验证
AI Trace
```

这些能力说明，平台已经完成了从“地图数据展示”到“AI 生成方案”的基础闭环。下一步如果继续增强 AI，最自然的方向就是让 AI 具备：

```text
1. 更完整的一张图上下文感知；
2. 可调用业务工具；
3. 可检索专业知识；
4. 可解释的工具调用 Trace；
5. 可引用来源的 RAG 回答。
```

因此阶段三十六不是重复做方案生成，而是在现有基础上补齐：

```text
地图上下文 + 工具调用 + 知识库检索 + Trace
```

---

## 3. 为什么暂不直接全量接入 LangGraph

LangGraph 适合多步骤 Agent、状态持久化、人工介入、任务恢复和复杂编排。但当前项目主栈是 Java/Spring Boot，直接引入 LangGraph 会产生：

```text
1. 新增 Python Orchestrator 服务；
2. Java 与 Python DTO 同步成本；
3. 认证、租户、日志、Trace、超时、重试需要重新打通；
4. 当前已有方案生成、模板、任务、Trace 链路需要重新适配；
5. 短期复杂度高于收益。
```

所以建议：

```text
阶段三十六：先用 Java 原生实现 Agent 工具层和向量检索
阶段三十七：抽象 Agent Orchestrator 和评测体系
阶段三十八：再试点 LangGraph 独立服务
```

---

## 4. 阶段目标

阶段三十六的目标是让一张图 AI 从当前的：

```text
地图对象 / 框选区域
  ↓
生成分析或方案
```

升级为：

```text
地图上下文感知
  ↓
AI 判断用户意图
  ↓
调用 GIS / 业务 / 知识库 / 模板 / 方案工具
  ↓
向量知识检索
  ↓
生成带来源、带 Trace 的回答或方案
```

核心目标：

```text
1. 构建 MapAiContext 一张图上下文包；
2. 新增 AiTool 工具抽象；
3. 新增 AiToolRegistry 工具注册器；
4. 新增向量知识库表；
5. 支持 Markdown / Outline / 模板知识入库；
6. 支持知识检索接口；
7. 新增 KnowledgeRetrieveTool；
8. 新增 MapAiAgentService；
9. AI Trace 展示工具调用和知识检索；
10. 前端展示引用来源。
```

---

## 5. 阶段非目标

阶段三十六第一版不做：

```text
1. 全量接入 LangGraph；
2. 多 Agent 协作；
3. 自动复杂工具规划；
4. 多轮长期记忆；
5. Human-in-the-loop；
6. 自动转工单；
7. 复杂知识图谱；
8. 文档权限细粒度模型。
```

这些内容放到后续阶段。

---

## 6. 总体架构

推荐架构：

```text
OneMap.vue
  ↓
AgentChatFloat.vue
  ↓
/api/agent/map-agent/chat
  ↓
MapAiAgentService
  ↓
MapContextBuilder
  ↓
IntentRecognizer
  ↓
AiToolRegistry
  ├── MapDiseaseQueryTool
  ├── MapAssessmentQueryTool
  ├── MapRegionSummaryTool
  ├── MapNearbyObjectTool
  ├── KnowledgeRetrieveTool
  ├── TemplateMatchTool
  ├── SolutionGenerateTool
  └── SolutionSaveTool
  ↓
AiKnowledgeRetrieverService
  ↓
pgvector / ai_knowledge_chunk
  ↓
LLM
  ↓
answer + sources + trace
```

---

## 7. 一张图 AI 上下文包设计

### 7.1 MapAiContext

建议新增：

```java
public class MapAiContext {
    private String tenantId;
    private String mode; // OBJECT / REGION / VIEWPORT / ROUTE / FREE
    private String routeCode;
    private Integer year;

    private Map<String, Object> mapObject;
    private Map<String, Object> regionSummary;
    private Map<String, Object> viewport;

    private List<String> selectedLayers;
    private List<Map<String, Object>> nearbyObjects;

    private String userQuestion;
    private Map<String, Object> extra;
}
```

### 7.2 mode 说明

```text
OBJECT
  当前选中了一个地图对象，例如病害、评定结果、路段、路线。

REGION
  当前存在框选区域。

VIEWPORT
  当前没有明确对象，但有地图视野范围。

ROUTE
  当前按路线 / 年度查询。

FREE
  没有明显地图上下文，普通 AI 问答。
```

### 7.3 前端请求示例

```json
{
  "message": "这个区域主要问题是什么？",
  "mapContext": {
    "mode": "REGION",
    "routeCode": "G210",
    "year": 2026,
    "selectedLayers": ["disease", "assessment"],
    "regionSummary": {
      "diseaseCount": 52,
      "avgMqi": 86.3,
      "lowScoreCount": 4
    },
    "viewport": {
      "bbox": [106.1, 26.1, 106.2, 26.2]
    }
  },
  "options": {
    "useKnowledge": true,
    "useBusinessData": true,
    "useTools": true,
    "topK": 5
  }
}
```

---

## 8. AI Agent 工具层设计

### 8.1 包结构

```text
srmp-agent/src/main/java/com/smartroad/srmp/agent/tool
```

### 8.2 AiTool 接口

```java
public interface AiTool {
    String name();
    String description();
    boolean supports(AiToolContext context);
    AiToolResult execute(AiToolContext context, Map<String, Object> args);
}
```

### 8.3 AiToolContext

```java
public class AiToolContext {
    private String tenantId;
    private String traceId;
    private String userQuestion;
    private MapAiContext mapContext;
    private Map<String, Object> options;
}
```

### 8.4 AiToolResult

```java
public class AiToolResult {
    private String toolName;
    private boolean success;
    private String summary;
    private Object data;
    private Integer count;
    private String errorMessage;
    private Long costMs;
}
```

### 8.5 AiToolRegistry

```java
public class AiToolRegistry {
    private final Map<String, AiTool> tools = new LinkedHashMap<>();

    public void register(AiTool tool) {
        tools.put(tool.name(), tool);
    }

    public AiTool get(String name) {
        return tools.get(name);
    }

    public List<AiTool> list() {
        return new ArrayList<>(tools.values());
    }
}
```

---

## 9. 第一版内置工具

### 9.1 MapDiseaseQueryTool

```text
工具名：gis.queryDiseases
用途：查询当前对象、路线、区域或视野内的病害。
```

输入：

```json
{
  "routeCode": "G210",
  "year": 2026,
  "geometry": {},
  "severity": "MEDIUM",
  "limit": 20
}
```

输出：

```json
{
  "count": 16,
  "items": [
    {
      "routeCode": "G210",
      "startStake": 69.007,
      "endStake": 69.034,
      "diseaseName": "修补损坏",
      "severity": "MEDIUM",
      "quantity": 11.02,
      "measureUnit": "m2"
    }
  ]
}
```

### 9.2 MapAssessmentQueryTool

```text
工具名：gis.queryAssessmentResults
用途：查询评定结果、低分单元、指标异常。
```

### 9.3 MapRegionSummaryTool

```text
工具名：gis.queryRegionSummary
用途：查询框选区域内路线、路段、病害、评定结果统计。
```

### 9.4 MapNearbyObjectTool

```text
工具名：gis.queryNearbyObjects
用途：查询当前对象周边一定范围内的病害、评定单元、低分区段。
```

典型问题：

```text
这个病害周边还有没有类似问题？
这个位置是不是低分集中区？
```

### 9.5 KnowledgeRetrieveTool

```text
工具名：knowledge.retrieve
用途：根据用户问题、地图对象、病害类型、方案类型检索向量知识库。
```

输入：

```json
{
  "query": "修补损坏 中度 病害 处置建议",
  "topK": 5,
  "filters": {
    "sourceType": ["OUTLINE", "MANUAL", "TEMPLATE"],
    "tenantId": "default"
  }
}
```

输出：

```json
{
  "hits": [
    {
      "title": "沥青路面局部修补技术指南",
      "section": "修补损坏处置",
      "score": 0.87,
      "content": "...",
      "sourceType": "OUTLINE"
    }
  ]
}
```

### 9.6 TemplateMatchTool

```text
工具名：template.match
用途：匹配方案模板，验证模板是否生效。
```

### 9.7 SolutionGenerateTool

```text
工具名：solution.generateDraft
用途：调用现有方案生成能力，生成地图对象或区域方案草稿。
```

### 9.8 SolutionSaveTool

```text
工具名：solution.saveTask
用途：调用阶段三十三能力，把方案保存为 ai_solution_task。
```

---

## 10. 意图识别设计

第一版不需要复杂 LLM 意图识别，可以先规则驱动。

### 10.1 Intent 类型

```java
public enum MapAiIntent {
    OBJECT_ANALYSIS,
    REGION_ANALYSIS,
    NEARBY_ANALYSIS,
    KNOWLEDGE_QA,
    SOLUTION_GENERATE,
    TEMPLATE_VERIFY,
    TASK_SAVE,
    GENERAL_CHAT
}
```

### 10.2 规则示例

```text
包含 “周边 / 附近 / 相邻”
  → NEARBY_ANALYSIS

包含 “区域 / 框选 / 范围”
  → REGION_ANALYSIS

包含 “规范 / 怎么处理 / 工艺 / 标准”
  → KNOWLEDGE_QA

包含 “生成方案 / 处置建议 / 报告草稿”
  → SOLUTION_GENERATE

当前有 mapObject
  → OBJECT_ANALYSIS

当前有 regionSummary
  → REGION_ANALYSIS
```

---

## 11. Agent 编排流程

### 11.1 MapAiAgentService

```java
public interface MapAiAgentService {
    MapAiAgentResponse chat(MapAiAgentRequest request);
}
```

执行流程：

```text
1. 创建 AiTraceContext；
2. 构建 MapAiContext；
3. 识别用户意图；
4. 选择工具；
5. 执行工具；
6. 检索知识库；
7. 组装 Prompt；
8. 调用 LLM；
9. 清洗 answer；
10. 返回 answer + sources + trace + toolResults。
```

### 11.2 示例：点击病害后问“这个病害怎么处理？”

```text
intent_recognize
  → KNOWLEDGE_QA + OBJECT_ANALYSIS

map_context_build
  → 当前对象：DISEASE，修补损坏，G210 K69.007-K69.034

tool_execute
  → gis.queryNearbyObjects
  → knowledge.retrieve

prompt_build
  → 当前对象 + 周边对象 + 知识命中

llm_answer
  → 生成处置建议

trace_return
  → 展示工具调用和知识来源
```

---

## 12. 向量知识库设计

### 12.1 推荐选型

第一版建议使用：

```text
PostgreSQL + pgvector
```

原因：

```text
1. 当前项目已经使用 PostgreSQL；
2. 部署简单；
3. 可复用租户字段；
4. 与业务数据、任务数据在同一体系内；
5. 后续可迁移到 Milvus / Elasticsearch。
```

### 12.2 ai_knowledge_document

```sql
CREATE TABLE IF NOT EXISTS ai_knowledge_document (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128),
    title VARCHAR(300) NOT NULL,
    status VARCHAR(32) DEFAULT 'ACTIVE',
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 12.3 ai_knowledge_chunk

```sql
CREATE TABLE IF NOT EXISTS ai_knowledge_chunk (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    document_id VARCHAR(64) NOT NULL,
    source_type VARCHAR(64),
    source_id VARCHAR(128),
    title VARCHAR(300),
    section_title VARCHAR(300),
    chunk_index INTEGER,
    content TEXT NOT NULL,
    metadata JSONB,
    embedding VECTOR(1536),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 12.4 索引

```sql
CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunk_tenant
ON ai_knowledge_chunk(tenant_id, source_type, document_id);

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunk_embedding
ON ai_knowledge_chunk
USING ivfflat (embedding vector_cosine_ops);
```

说明：

```text
embedding 维度必须和实际模型一致。
如果用 1536 维模型，则 VECTOR(1536)。
如果用 1024 维模型，则 VECTOR(1024)。
```

---

## 13. 知识来源类型

建议支持：

```text
OUTLINE
MARKDOWN
PDF
TEMPLATE
SOLUTION_TASK
MANUAL
BUSINESS_RULE
```

第一版建议先支持：

```text
MARKDOWN
OUTLINE
MANUAL
TEMPLATE
```

---

## 14. 知识切片策略

建议：

```text
1. Markdown 按标题切分；
2. 每个 chunk 500~1000 字；
3. 保留标题层级；
4. 保留 sourceType / sourceId / docTitle / sectionTitle；
5. 每个 chunk 单独生成 embedding；
6. 支持按 document_id 删除重建。
```

---

## 15. EmbeddingClient 设计

### 15.1 接口

```java
public interface EmbeddingClient {
    List<Float> embed(String text);
    List<List<Float>> embedBatch(List<String> texts);
    String model();
    int dimensions();
}
```

### 15.2 实现

第一版可以支持：

```text
OpenAIEmbeddingClient
LocalEmbeddingClient
MockEmbeddingClient
```

OpenAI 配置示例：

```yaml
srmp:
  ai:
    embedding:
      provider: openai
      model: text-embedding-3-small
      dimensions: 1536
      batch-size: 16
```

本地 embedding 服务配置示例：

```yaml
srmp:
  ai:
    embedding:
      provider: local
      endpoint: http://localhost:8002/embed
      model: bge-m3
      dimensions: 1024
```

---

## 16. 知识入库接口

### 16.1 导入 Markdown

```http
POST /api/ai/knowledge/ingest/markdown
```

请求：

```json
{
  "tenantId": "default",
  "title": "沥青路面病害处置指南",
  "sourceType": "MANUAL",
  "sourceId": "manual-001",
  "content": "# 修补损坏\\n..."
}
```

### 16.2 从 Outline 同步

```http
POST /api/ai/knowledge/ingest/outline
```

请求：

```json
{
  "tenantId": "default",
  "collectionId": "xxx",
  "documentIds": ["doc1", "doc2"]
}
```

### 16.3 重建索引

```http
POST /api/ai/knowledge/reindex
```

---

## 17. 知识检索接口

```http
POST /api/ai/knowledge/search
```

请求：

```json
{
  "query": "修补损坏中度病害如何处置",
  "tenantId": "default",
  "topK": 5,
  "filters": {
    "sourceType": ["OUTLINE", "MANUAL", "TEMPLATE"]
  }
}
```

返回：

```json
{
  "code": 0,
  "data": {
    "query": "修补损坏中度病害如何处置",
    "hits": [
      {
        "chunkId": "xxx",
        "documentId": "doc1",
        "title": "沥青路面病害处置指南",
        "sectionTitle": "修补损坏",
        "score": 0.87,
        "content": "...",
        "sourceType": "OUTLINE"
      }
    ]
  }
}
```

---

## 18. RAG Prompt 设计

输入内容：

```text
用户问题
地图上下文
工具调用结果
知识库命中内容
模板信息
业务规则
```

Prompt 示例：

```text
你是道路养护专家，请基于以下信息回答用户问题。

【用户问题】
{{question}}

【地图上下文】
{{mapContext}}

【业务数据】
{{toolResults}}

【知识库资料】
{{knowledgeHits}}

【要求】
1. 优先围绕当前地图对象或框选区域回答；
2. 引用知识库内容时说明来源；
3. 如果数据不足，请明确说明缺失项；
4. 给出可执行的养护建议；
5. 不要输出思考过程。
```

---

## 19. AI Trace 增强

### 19.1 Trace Step

建议新增或统一：

```text
agent_intent_recognize      意图识别
map_context_build           地图上下文构建
tool_plan                   工具规划
tool_execute                工具执行
knowledge_retrieve          知识检索
prompt_build                Prompt 构建
llm_answer                  大模型回答
answer_sanitize             回答清洗
quality_check               质量检查
```

### 19.2 Tool Trace

每个工具执行记录：

```json
{
  "toolName": "knowledge.retrieve",
  "status": "SUCCESS",
  "costMs": 132,
  "count": 5,
  "summary": "命中 5 条知识片段",
  "error": null
}
```

### 19.3 前端展示

`AiTraceDrawer` 增强：

```text
Trace 总览
工具调用
知识来源
模板来源
LLM 调用
错误与降级原因
```

---

## 20. 前端设计

### 20.1 一张图 AI 面板增强

在 `AgentChatFloat.vue` 中新增：

```text
上下文模式：
- 当前对象
- 框选区域
- 当前视野
- 当前路线

快捷操作：
- 分析周边病害
- 查询相关规范
- 生成区域建议
- 对比评定结果
- 生成方案并保存
```

### 20.2 知识来源展示

AI 回答下方展示：

```text
参考资料：
1. 沥青路面病害处置指南 / 修补损坏
2. 公路技术状况评定标准 / PCI 指标
```

### 20.3 工具调用展示

在 Trace 中展示：

```text
gis.queryDiseases
knowledge.retrieve
template.match
solution.generateDraft
```

---

## 21. 后端模块清单

新增包建议：

```text
srmp-agent/src/main/java/com/smartroad/srmp/agent/tool
srmp-agent/src/main/java/com/smartroad/srmp/agent/mapagent
srmp-agent/src/main/java/com/smartroad/srmp/agent/knowledge
srmp-agent/src/main/java/com/smartroad/srmp/agent/embedding
```

### 21.1 tool

```text
AiTool
AiToolContext
AiToolResult
AiToolRegistry
MapDiseaseQueryTool
MapAssessmentQueryTool
MapRegionSummaryTool
MapNearbyObjectTool
KnowledgeRetrieveTool
TemplateMatchTool
SolutionGenerateTool
SolutionSaveTool
```

### 21.2 mapagent

```text
MapAiAgentController
MapAiAgentService
MapAiAgentServiceImpl
MapAiAgentRequest
MapAiAgentResponse
MapAiContext
MapAiIntent
MapAiIntentRecognizer
MapAiPromptBuilder
```

### 21.3 knowledge

```text
AiKnowledgeDocument
AiKnowledgeChunk
AiKnowledgeIngestService
AiKnowledgeIngestServiceImpl
AiKnowledgeRetrieverService
AiKnowledgeRetrieverServiceImpl
AiKnowledgeController
AiKnowledgeSearchRequest
AiKnowledgeSearchResponse
```

### 21.4 embedding

```text
EmbeddingClient
OpenAIEmbeddingClient
LocalEmbeddingClient
MockEmbeddingClient
```

---

## 22. 阶段三十六第一版最小闭环

第一版建议只做：

```text
1. ai_knowledge_document / ai_knowledge_chunk 表；
2. Markdown 知识入库；
3. MockEmbeddingClient 或真实 EmbeddingClient；
4. 知识检索接口；
5. KnowledgeRetrieveTool；
6. MapAiAgentService 调用 knowledge.retrieve；
7. 一张图 AI 回答展示知识来源；
8. AI Trace 展示 knowledge_retrieve。
```

先不做：

```text
1. LangGraph；
2. 复杂多 Agent；
3. 自动工具规划；
4. 多轮记忆；
5. 人工审批；
6. 自动转工单。
```

---

## 23. 与 LangChain / LangGraph 的关系

### 23.1 第一阶段不直接引入

阶段三十六先用 Java 原生方式实现：

```text
Agent 工具抽象
向量检索
RAG 上下文组装
Trace
```

### 23.2 后续可接 LangGraph

阶段三十八可以考虑：

```text
Java Spring Boot
  ↓ HTTP
Python ai-orchestrator
  ↓
LangGraph
  ↓
工具调用 / 多步骤流程 / 持久化
```

适合场景：

```text
多步骤生成
人工确认后继续
任务恢复
复杂 Agent 状态机
多工具动态规划
```

---

## 24. 验收标准

### 24.1 知识入库

```text
能导入 Markdown 文档
能切片
能生成 embedding
能写入 ai_knowledge_chunk
```

### 24.2 知识检索

```text
输入“修补损坏怎么处理”
能返回相关知识片段
有 score
有 title / sectionTitle / sourceType
```

### 24.3 一张图 AI

```text
点击病害
提问“这个病害怎么处理”
AI 返回：
- 当前病害分析
- 处置建议
- 引用知识来源
- Trace 中有 knowledge_retrieve
```

### 24.4 Trace

```text
Trace 显示：
- map_context_build
- knowledge_retrieve
- prompt_build
- llm_answer
```

---

## 25. 验收脚本建议

新增：

```text
scripts/check-phase36-map-ai-agent-knowledge.sh
```

检查：

```text
1. ai_knowledge_document SQL 存在；
2. ai_knowledge_chunk SQL 存在；
3. EmbeddingClient 存在；
4. AiKnowledgeRetrieverService 存在；
5. KnowledgeRetrieveTool 存在；
6. MapAiAgentController 存在；
7. knowledge_retrieve trace 存在；
8. 前端展示知识来源。
```

---

## 26. 推荐实施顺序

```text
1. 新增知识库表结构；
2. 实现 EmbeddingClient；
3. 实现 Markdown 切片；
4. 实现知识入库；
5. 实现向量检索；
6. 实现 KnowledgeRetrieveTool；
7. 实现 MapAiAgentService；
8. 一张图 AI 面板接入 map-agent/chat；
9. 前端展示知识来源；
10. Trace 增加工具调用和知识检索记录；
11. 增加验收脚本和文档。
```

---

## 27. 风险与注意事项

### 27.1 pgvector 环境依赖

PostgreSQL 需要安装 pgvector 扩展：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

如果部署环境暂时不能安装 pgvector，可以先使用：

```text
MockEmbeddingClient + 关键词检索兜底
```

### 27.2 Embedding 维度

`VECTOR(1536)` 必须和 embedding 模型维度一致。

### 27.3 知识质量

RAG 的效果依赖知识库质量。需要控制：

```text
文档来源
切片质量
标题层级
重复内容
过期内容
```

### 27.4 Trace 体积

知识命中内容不宜全部写入 Trace，可只记录：

```text
chunkId
title
sectionTitle
score
sourceType
```

### 27.5 回答来源可信度

AI 回答中应区分：

```text
业务数据
知识库资料
模型推断
```

避免把模型推断说成事实。

---

## 28. 结论

阶段三十六可行，且建议优先实施。

阶段名称：

```text
阶段三十六：一张图 AI Agent 与向量知识库增强
```

推荐路线：

```text
短期：
Spring Boot 原生 Agent 工具层 + pgvector 向量知识库

中期：
Agent Orchestrator 标准接口 + 回归评测

长期：
可选 LangGraph 独立 AI 编排服务
```

第一版最小闭环：

```text
知识入库
  ↓
向量检索
  ↓
一张图 AI 调用 knowledge.retrieve
  ↓
回答展示引用来源
  ↓
Trace 展示知识检索过程
```

完成后，一张图 AI 将从：

```text
业务数据 + 地图上下文
```

升级为：

```text
业务数据 + 地图上下文 + 专业知识库 + 工具调用 Trace
```
