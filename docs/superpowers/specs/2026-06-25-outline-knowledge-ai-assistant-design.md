# Outline 知识库到 AI 助手生效设计

## 背景

当前 Outline 初始化脚本已经具备目录、元数据和模板蓝本能力，但部分知识页仍保留“样例”口吻，项目资料、案例库和 FAQ 的可检索内容偏薄。用户希望完善 Outline 知识库各目录的真实可用数据，并验证从 Outline 到 AI 助手提问的链路生效。

## 目标

1. 补齐 `scripts/init-outline-knowledge-base.py` 中各目录下的知识内容，使其可直接进入 Outline 并参与 RAG。
2. 保持模板蓝本、治理页等非 RAG 内容不误入检索。
3. 增加自动化质量检查，防止后续再次出现占位“样例”内容。
4. 增加一键验收脚本，串起 Outline 状态、同步、知识库检索、`knowledge.retrieve` 工具和地图 AI 助手问答。
5. 不处理当前主工作区中的 `.env.dev.example`。

## 范围

涉及：

- `scripts/init-outline-knowledge-base.py`
- 新增 Outline 知识目录质量测试
- 新增或增强 Outline → AI 助手验收脚本
- 必要的文档说明

不涉及：

- 修改 `.env.dev.example`
- 修改外部 Outline 服务配置
- 引入未经确认的外部规范条文或具体标准条款号

## 知识内容原则

- 使用 SRMP 平台业务口径与通用公路养护实践表达，不冒充具体国家/行业标准原文。
- 每个可参与 RAG 的知识页至少包含：
  - 适用场景
  - 输入数据要求
  - 判断口径
  - AI 可引用表达
  - 需要复核或禁止输出的边界
  - 可追溯实例卡片
- 关键词覆盖 AI 常见提问：MQI、PQI、PCI、RQI、RDI、裂缝、坑槽、车辙、沉陷、灌缝、封层、罩面、铣刨重铺、区域养护、现场复核、来源上下文。

## 打通验证口径

验收链路应至少验证：

1. Outline 可用；
2. 初始化脚本可生成完整目录；
3. `/api/outline/sync` 能把 Outline 文档同步到本地知识库；
4. `/api/ai/knowledge/search` 能命中 `OUTLINE` 来源；
5. `/api/agent/tools/execute` 执行 `knowledge.retrieve` 能命中 Outline 片段；
6. `/api/agent/map-agent/run` 或等价 AI 问答入口返回包含知识库/来源上下文的数据结构。

若目标环境未启动 Outline、后端或 LangGraph，应明确输出跳过/失败原因，而不是静默通过。
