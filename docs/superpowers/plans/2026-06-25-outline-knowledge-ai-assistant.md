# Outline 知识库内容完善与 AI 助手链路打通计划

## 分支与隔离

- 工作分支：`codex/outline-knowledge-ai-assistant`
- 工作目录：`/Users/cxp/workspace/srmp-outline-knowledge-ai-assistant`
- 主工作区的 `.env.dev.example` 不处理。

## Step 1：写失败测试保护知识库质量

新增 Python 单元测试，动态加载 `scripts/init-outline-knowledge-base.py` 的 `build_catalog()`，先断言当前内容存在质量问题：

- catalog 包含 8 个一级目录及预期子文档；
- `ragEnabled: true` 的文档不得包含 `样例：`、`初始化样例`、`应替换为正式规范` 等占位语；
- 各业务目录至少有可检索关键词；
- 模板蓝本必须 `ragEnabled: false`；
- 项目资料、案例库、FAQ 必须包含真实业务场景、判断口径和 AI 输出边界。

预期：新增测试先失败。

## Step 2：完善 Outline 种子知识内容

修改 `scripts/init-outline-knowledge-base.py`：

- 把通用 `ensure_structured_sample()` 生成内容的来源说明从“初始化样例”改为可用的“SRMP 内置种子知识/项目业务口径”；
- 将 `50_项目资料`、`60_案例库`、`70_术语与问答` 从短句样例扩展为可直接检索的结构化知识；
- 让各目录知识页表达更贴近 AI 助手场景：证据、地图对象、GIS 校验、来源上下文、追问；
- 保留治理页和模板蓝本的非 RAG 行为。

预期：Step 1 测试通过。

## Step 3：补打通验收脚本

新增 `scripts/check-outline-ai-assistant.sh`，默认读取：

- `BASE_URL=http://localhost:8080`
- `TENANT_ID=default`
- `OUTLINE_SYNC_LIMIT=50`
- `OUTLINE_SYNC_FORCE=true`

脚本步骤：

1. 检查 `/api/outline/status`；
2. 同步 `/api/outline/sync`；
3. 检查 `/api/outline/knowledge-stats` 和 `/api/ai/knowledge/stats`；
4. 调 `/api/ai/knowledge/search`，过滤 `sourceTypes:["OUTLINE"]`，验证命中标题/内容包含 PCI、裂缝、坑槽、灌缝或铣刨等关键词；
5. 调 `/api/agent/tools/execute` 的 `knowledge.retrieve`，验证工具成功、命中数大于 0、request summary 带 topK/filters；
6. 调 `/api/agent/map-agent/run` 发起地图 AI 问答，允许 LangGraph 不可用时给出明确诊断；若可用则验证 toolResults 或 sources 中包含知识来源。

## Step 4：补必要的链路说明文档

新增简短说明文档，记录：

- 初始化 Outline；
- 同步入库；
- 检索验证；
- AI 助手问答验证；
- 常见失败原因和处理方式。

## Step 5：执行验证

按风险由低到高执行：

1. `python3 scripts/test_init_outline_knowledge_base.py`
2. `mvn -pl srmp-agent -Dtest=OutlineKnowledgeDocumentPreprocessorTest,KnowledgeRetrieveToolTest,AiKnowledgeRetrieverServiceImplTest test`
3. 若本地后端/Outline 可访问，执行 `bash scripts/check-outline-ai-assistant.sh`

如网络或目标服务未启动导致 live smoke 无法完成，保留脚本和失败诊断输出，说明需要在目标环境部署/更新后运行。

## Step 6：提交与交付

- 检查 `git status`；
- 提交本分支；
- 汇报已改文件、验证结果和目标环境验收命令。
