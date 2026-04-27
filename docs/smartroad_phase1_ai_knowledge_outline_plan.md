# 智路养护平台一期 AI 知识库增强与 Outline 接入规划

系统名称：智路养护平台  
英文名称：SmartRoad Maintenance Platform  
简称：SRMP  
文档版本：V1.0  
建设主题：一期 AI 能力增强、知识库问答、Outline 文档库接入  

---

# 1. 文档说明

## 1.1 文档目的

本文档用于规划智路养护平台一期 AI 能力增强方案。

当前一期 AI 能力已经具备：

```text
路线分析
病害分析
评定分析
报告草稿生成
基于数据库统计的问答能力
```

但当前 AI 主要依赖业务数据库，尚未接入标准规范、系统文档、内部流程文档、知识库和 Outline 团队文档。

因此，本阶段建议在一期范围内增强 AI 能力，建设：

```text
业务数据问答 + 本地知识库问答 + Outline 文档问答 + 混合 RAG 问答
```

## 1.2 建设目标

本次增强目标是让 AI 从“只会查业务数据”升级为：

```text
会查业务数据
会查系统文档
会查标准规范
会查内部流程
会查 Outline 文档
能综合数据和知识库生成回答
```

---

# 2. 当前 AI 能力现状

## 2.1 已具备能力

当前 `srmp-agent` 已支持：

```text
POST /api/agent/chat
POST /api/agent/analyze/route
POST /api/agent/analyze/disease
POST /api/agent/analyze/assessment
POST /api/agent/report/assessment
POST /api/agent/map-query
```

主要能力包括：

| 能力 | 说明 |
|---|---|
| 路线综合分析 | 基于道路资产、病害、评定结果进行分析 |
| 病害热点分析 | 统计病害数量、严重程度、热点单元 |
| 评定结果分析 | 统计 MQI、PQI、PCI、等级分布 |
| 报告草稿生成 | 生成技术状况评定报告草稿 |
| 地图联动查询 | 返回地图高亮对象 ID |
| 本地规则兜底 | 大模型不可用时使用规则分析 |

## 2.2 当前不足

当前 AI 能力不足主要包括：

```text
1. 无法查询系统操作手册；
2. 无法查询公路养护标准规范；
3. 无法查询内部流程文档；
4. 无法接入 Outline 团队知识库；
5. 无法引用文档来源；
6. 无法进行知识库检索增强生成；
7. 无法基于文档 + 业务数据做混合回答。
```

---

# 3. 一期 AI 增强总体思路

## 3.1 能力定位

一期 AI 增强定位为：

```text
RAG 知识库问答 + Outline 文档接入 + 业务数据工具调用
```

增强后的 AI 能力：

```text
业务数据问题 → 调用数据库查询工具
知识文档问题 → 调用知识库检索
Outline 文档问题 → 调用 Outline 搜索
混合问题 → 数据库查询 + 知识库检索 + 大模型综合回答
```

## 3.2 问答类型

增强后应支持以下问题：

```text
G210 2026 年整体路况如何？
PCI 指标低于 70 的路段有哪些？
根据《公路技术状况评定标准》，MQI 是怎么计算的？
内部养护流程文档里，病害复核流程怎么走？
一期系统数据导入模板怎么用？
根据标准解释 G210 PCI 偏低的原因。
```

## 3.3 总体架构

```mermaid
flowchart TB

    A[用户问题] --> B[/api/agent/chat]

    B --> C[意图识别]

    C --> D1[业务数据查询]
    C --> D2[本地知识库检索]
    C --> D3[Outline 在线搜索]
    C --> D4[混合检索]

    D1 --> E1[道路资产工具]
    D1 --> E2[病害查询工具]
    D1 --> E3[评定结果工具]
    D1 --> E4[GIS统计工具]

    D2 --> F1[knowledge_document]
    D2 --> F2[knowledge_chunk]

    D3 --> G1[Outline Search API]
    D3 --> G2[Outline Document API]

    D4 --> H[综合上下文]

    E1 --> H
    E2 --> H
    E3 --> H
    E4 --> H
    F1 --> H
    F2 --> H
    G1 --> H
    G2 --> H

    H --> I[Prompt 构造]
    I --> J[大模型回答]
    J --> K[返回答案与引用来源]
```

---

# 4. 知识库设计

## 4.1 知识库来源

一期增强建议支持以下知识来源：

```text
1. 本地 Markdown 文档
2. 本地 TXT 文档
3. 系统内置标准规范说明
4. 系统操作手册
5. 数据导入模板说明
6. Outline 团队文档
```

## 4.2 知识库类型

建议定义知识库类型：

```text
ROAD_STANDARD       公路标准规范
SYSTEM_MANUAL       系统操作手册
MAINTENANCE_FLOW    养护业务流程
IMPORT_TEMPLATE     数据导入模板说明
PROJECT_DOC         项目文档
OUTLINE_DOC         Outline 文档
FAQ                 常见问题
```

## 4.3 知识库数据模型

### 4.3.1 knowledge_document

用于保存文档元数据。

```sql
CREATE TABLE knowledge_document (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    source_type     VARCHAR(50) NOT NULL,
    source_id       VARCHAR(200),
    title           VARCHAR(500) NOT NULL,
    doc_type        VARCHAR(50),
    category        VARCHAR(100),
    content_hash    VARCHAR(128),
    version         VARCHAR(50),
    url             VARCHAR(1000),
    status          VARCHAR(30) DEFAULT 'ENABLED',
    synced_at       TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE
);
```

字段说明：

| 字段 | 说明 |
|---|---|
| id | 主键 |
| tenant_id | 租户 ID |
| source_type | 来源类型：LOCAL、UPLOAD、OUTLINE、STANDARD |
| source_id | 外部来源 ID，例如 Outline documentId |
| title | 文档标题 |
| doc_type | 文档类型：MARKDOWN、PDF、WORD、TEXT |
| category | 文档分类 |
| content_hash | 内容 hash，用于判断内容是否变化 |
| version | 文档版本 |
| url | 原文链接 |
| status | 状态 |
| synced_at | 最近同步时间 |
| deleted | 逻辑删除 |

---

### 4.3.2 knowledge_chunk

用于保存文档切片内容。

```sql
CREATE TABLE knowledge_chunk (
    id              VARCHAR(64) PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    document_id     VARCHAR(64) NOT NULL,
    chunk_no        INTEGER NOT NULL,
    heading         VARCHAR(500),
    content         TEXT NOT NULL,
    content_tokens  INTEGER,
    source_type     VARCHAR(50),
    source_url      VARCHAR(1000),
    metadata        JSONB,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN DEFAULT FALSE
);
```

字段说明：

| 字段 | 说明 |
|---|---|
| document_id | 关联知识文档 |
| chunk_no | 切片序号 |
| heading | 当前切片标题 |
| content | 切片正文 |
| content_tokens | token 估算数 |
| source_type | 来源类型 |
| source_url | 来源链接 |
| metadata | 额外元数据 |
| deleted | 逻辑删除 |

> 说明：一期建议先不接 pgvector，先使用 PostgreSQL 关键词检索。后续可增加 `embedding VECTOR` 字段。

---

### 4.3.3 推荐索引

```sql
CREATE INDEX idx_knowledge_document_tenant
ON knowledge_document(tenant_id, source_type, category);

CREATE INDEX idx_knowledge_chunk_doc
ON knowledge_chunk(tenant_id, document_id);

CREATE INDEX idx_knowledge_chunk_text
ON knowledge_chunk USING gin(to_tsvector('simple', content));
```

---

# 5. Outline 接入设计

## 5.1 接入方式

Outline 接入建议分为两种方式：

```text
在线检索模式
同步入库模式
```

---

## 5.2 在线检索模式

在线检索模式是一期优先推荐方案。

流程：

```text
用户提问
  ↓
调用 Outline Search API
  ↓
获取相关文档
  ↓
读取文档内容
  ↓
抽取相关片段
  ↓
交给大模型回答
```

优点：

```text
实现快
不需要同步任务
文档实时
适合一期快速接入
```

缺点：

```text
依赖 Outline 服务可用
每次问答都需要远程调用
大文档切片能力较弱
```

---

## 5.3 同步入库模式

同步入库模式适合后续增强。

流程：

```text
Outline Collection
  ↓
Outline Documents
  ↓
Markdown 内容
  ↓
knowledge_document
  ↓
knowledge_chunk
  ↓
关键词检索 / 向量检索
  ↓
RAG 问答
```

优点：

```text
检索速度快
可做向量检索
可做权限过滤
可做离线问答
可做增量更新
```

缺点：

```text
需要同步任务
需要处理内容 hash
需要处理文档删除和更新
```

---

## 5.4 Outline 配置

建议在配置文件中增加：

```yaml
srmp:
  outline:
    enabled: true
    base-url: http://outline.example.com
    api-token: your-outline-token
    sync-enabled: false
    default-collection-id:
    search-limit: 5
```

生产环境建议通过环境变量注入：

```yaml
srmp:
  outline:
    enabled: ${OUTLINE_ENABLED:false}
    base-url: ${OUTLINE_BASE_URL:}
    api-token: ${OUTLINE_API_TOKEN:}
    sync-enabled: ${OUTLINE_SYNC_ENABLED:false}
    default-collection-id: ${OUTLINE_DEFAULT_COLLECTION_ID:}
    search-limit: ${OUTLINE_SEARCH_LIMIT:5}
```

## 5.5 Outline 配置类

```java
@ConfigurationProperties(prefix = "srmp.outline")
public class OutlineProperties {
    private Boolean enabled = false;
    private String baseUrl;
    private String apiToken;
    private Boolean syncEnabled = false;
    private String defaultCollectionId;
    private Integer searchLimit = 5;
}
```

---

# 6. 后端包结构规划

建议继续在 `srmp-agent` 模块中增强，不新增独立模块。

```text
srmp-agent
└── src/main/java/com/smartroad/srmp/agent
    ├── knowledge
    │   ├── controller
    │   │   └── KnowledgeController.java
    │   ├── entity
    │   │   ├── KnowledgeDocument.java
    │   │   ├── KnowledgeChunk.java
    │   │   └── KnowledgeSource.java
    │   ├── service
    │   │   ├── KnowledgeDocumentService.java
    │   │   ├── KnowledgeIndexService.java
    │   │   ├── KnowledgeSearchService.java
    │   │   └── KnowledgeQaService.java
    │   ├── parser
    │   │   ├── MarkdownParser.java
    │   │   ├── PlainTextParser.java
    │   │   └── PdfParser.java
    │   └── splitter
    │       └── TextChunkSplitter.java
    │
    ├── outline
    │   ├── config
    │   │   └── OutlineProperties.java
    │   ├── client
    │   │   └── OutlineClient.java
    │   ├── dto
    │   │   ├── OutlineSearchRequest.java
    │   │   ├── OutlineSearchResult.java
    │   │   └── OutlineDocumentDTO.java
    │   └── service
    │       ├── OutlineSearchService.java
    │       └── OutlineSyncService.java
    │
    └── rag
        ├── RagContext.java
        ├── RagRetriever.java
        ├── RagPromptBuilder.java
        └── RagAnswerService.java
```

---

# 7. API 规划

## 7.1 知识库管理接口

```text
POST   /api/knowledge/documents
POST   /api/knowledge/documents/upload
POST   /api/knowledge/documents/page
GET    /api/knowledge/documents/{id}
DELETE /api/knowledge/documents/{id}

POST   /api/knowledge/documents/{id}/parse
POST   /api/knowledge/search
POST   /api/knowledge/ask
```

---

## 7.2 Outline 接口

```text
GET  /api/outline/status
POST /api/outline/search
GET  /api/outline/documents/{id}
POST /api/outline/sync
GET  /api/outline/sync-tasks/{id}
```

---

## 7.3 AI 问答增强接口

现有接口：

```text
POST /api/agent/chat
```

建议增强请求体：

```json
{
  "message": "根据内部文档，病害复核流程怎么走？",
  "context": {
    "routeCode": "G210",
    "year": 2026
  },
  "options": {
    "useBusinessData": true,
    "useKnowledge": true,
    "useOutline": true,
    "topK": 5
  }
}
```

建议增强响应体：

```json
{
  "answer": "根据知识库和业务数据综合分析……",
  "mode": "HYBRID_RAG",
  "data": {
    "businessData": {},
    "knowledgeSources": [
      {
        "title": "病害复核流程",
        "sourceType": "OUTLINE",
        "url": "https://outline.xxx/doc/xxx",
        "score": 0.89
      }
    ]
  }
}
```

---

# 8. 问答策略设计

## 8.1 轻量意图分类

一期建议先使用规则分类。

```text
包含 G210 / 路线 / 病害 / MQI / PCI / 评定 → 业务数据查询
包含 标准 / 规范 / 流程 / 文档 / 怎么操作 / 模板 → 知识库查询
两类都包含 → 混合查询
```

## 8.2 问答策略示例

| 用户问题 | 策略 |
|---|---|
| G210 路况如何 | 业务数据 |
| PCI 是什么 | 知识库 |
| 根据标准解释 G210 PCI 低的原因 | 混合 |
| 数据导入模板怎么填 | 知识库 |
| 内部文档里病害复核流程是什么 | Outline |
| 根据公司流程生成 G210 养护建议 | 混合 |

---

# 9. RAG Prompt 设计

建议 Prompt 结构：

```text
你是智路养护平台 AI 助手。

请基于以下信息回答用户问题：

【业务数据】
...

【知识库片段】
片段1：
标题：
来源：
内容：

片段2：
...

【回答要求】
1. 优先引用业务数据；
2. 知识库内容只作为依据，不要编造；
3. 如果资料不足，请明确说明；
4. 涉及标准规范时，说明来源；
5. 不直接生成正式决策，只生成建议或草稿。

【用户问题】
...
```

---

# 10. 前端页面规划

## 10.1 新增菜单建议

```text
智能分析
├── AI 问答
├── 知识库文档
├── Outline 文档
└── 同步任务
```

## 10.2 GIS 页面 AI 面板增强

在当前 AI 浮窗中增加：

```text
□ 使用业务数据
□ 使用知识库
□ 使用 Outline
```

新增快捷问题：

```text
分析当前路线
解释 PCI 指标
查询病害复核流程
数据导入模板怎么填
根据标准生成养护建议
```

## 10.3 知识库文档页

功能：

```text
1. 文档列表
2. 上传 Markdown / TXT
3. 文档解析
4. 文档切片
5. 搜索测试
6. 删除文档
```

## 10.4 Outline 文档页

功能：

```text
1. Outline 连接状态
2. Outline 搜索
3. 查看文档
4. 同步 Collection
5. 同步任务记录
```

---

# 11. 实施阶段建议

## 11.1 阶段 A：本地知识库最小闭环

优先做：

```text
1. knowledge_document 表
2. knowledge_chunk 表
3. Markdown / TXT 文档入库
4. 简单切片
5. PostgreSQL 关键词检索
6. /api/knowledge/search
7. /api/knowledge/ask
8. /api/agent/chat 接入知识库检索
```

不依赖 Outline，最稳。

---

## 11.2 阶段 B：Outline 在线搜索接入

新增：

```text
1. OutlineProperties
2. OutlineClient
3. /api/outline/status
4. /api/outline/search
5. /api/outline/documents/{id}
6. /api/agent/chat 可调用 Outline 搜索结果
```

先不做同步，降低复杂度。

---

## 11.3 阶段 C：Outline 同步入库

新增：

```text
1. outline_sync_task
2. Outline Collection 同步
3. 文档内容 hash 判断
4. 增量更新
5. 同步后进入 knowledge_document / knowledge_chunk
```

---

## 11.4 阶段 D：向量检索增强

可选增强：

```text
1. 接入 pgvector
2. embedding 字段
3. 文档向量化
4. 混合检索：关键词 + 向量
5. rerank
```

这一阶段建议放到二期。

---

# 12. 一期增强 MVP 范围

## 12.1 MVP 建议包含

```text
1. 知识库表结构
2. Markdown / TXT 文档录入
3. 文档切片
4. PostgreSQL 关键词检索
5. Outline 在线搜索
6. AI 问答融合知识库结果
7. GIS AI 面板增加“使用知识库 / 使用 Outline”开关
```

## 12.2 暂不建议做

```text
1. PDF 复杂解析
2. Word 复杂解析
3. 向量数据库
4. 复杂权限同步
5. 定时同步全量 Outline
6. 自动执行业务操作
```

---

# 13. 推荐开发顺序

## Patch 1：本地知识库 + RAG 问答

包含：

```text
knowledge_document / knowledge_chunk 表
KnowledgeController
KnowledgeSearchService
Markdown/TXT 切片
/api/knowledge/search
/api/knowledge/ask
/api/agent/chat 接入知识库检索
演示知识库 SQL / Markdown
前端 AI 面板增加知识库开关
```

## Patch 2：Outline 在线搜索接入

包含：

```text
OutlineProperties
OutlineClient
/api/outline/status
/api/outline/search
/api/outline/documents/{id}
AI 问答接入 Outline 搜索结果
配置示例
说明文档
```

## Patch 3：Outline 同步入库

包含：

```text
outline_sync_task
Outline Collection 同步
同步任务记录
内容 hash 判断
增量同步
```

---

# 14. 验收标准

## 14.1 本地知识库验收

| 验收项 | 标准 |
|---|---|
| 文档入库 | 可新增 Markdown/TXT 文档 |
| 文档切片 | 可生成 knowledge_chunk |
| 知识检索 | /api/knowledge/search 可返回相关片段 |
| 知识问答 | /api/knowledge/ask 可基于片段回答 |
| AI 融合 | /api/agent/chat 可同时使用业务数据和知识库 |
| 来源引用 | 回答中返回知识来源标题和类型 |

## 14.2 Outline 验收

| 验收项 | 标准 |
|---|---|
| 连接状态 | /api/outline/status 可返回 Outline 状态 |
| 在线搜索 | /api/outline/search 可返回相关文档 |
| 文档读取 | /api/outline/documents/{id} 可返回文档内容 |
| AI 融合 | /api/agent/chat 可使用 Outline 结果回答 |
| 配置安全 | token 不写死在代码或 Git 中 |

---

# 15. 风险与控制

| 风险 | 说明 | 控制措施 |
|---|---|---|
| Outline token 泄露 | API Token 可能被提交到 Git | 使用环境变量 |
| 文档过长 | 大文档直接进入 Prompt 会超长 | 文档切片 + topK |
| 回答幻觉 | 大模型可能编造 | 明确引用来源，资料不足时说明 |
| 权限不同步 | Outline 权限和系统权限不一致 | 一期先按租户配置整体 token |
| 检索不准 | 关键词检索效果有限 | 后续接 pgvector |
| 内容过期 | 文档更新后本地未同步 | 在线搜索优先或手动同步 |

---

# 16. 总结

一期 AI 增强建议优先建设：

```text
本地知识库 + 关键词 RAG + Outline 在线搜索
```

建设顺序：

```text
先本地知识库
再 Outline 在线搜索
最后 Outline 同步入库和向量检索
```

这样可以在不扩大二期业务范围的前提下，让一期 AI 能力从“数据分析助手”升级为：

```text
数据分析助手
系统操作助手
标准规范助手
内部流程助手
综合知识问答助手
```

最终形成：

```text
业务数据 + 知识库 + Outline 文档 + 大模型
```

的混合智能问答能力。
