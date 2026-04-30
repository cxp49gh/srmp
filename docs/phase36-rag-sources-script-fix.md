# Phase36.1：RAG sources 透传与验收脚本收口修复

## 修复目标

当前 Phase36 主链路已经通过：

```text
知识入库 ✅
知识检索 ✅
map-agent/chat 调用 knowledge.retrieve ✅
toolResults 包含 knowledge.retrieve ✅
```

但存在两个收口问题：

```text
1. map-agent/chat 顶层 sources 为空，前端“参考资料”可能不显示；
2. check-phase36-vector-rag-e2e.sh 使用 head 管道，在 set -euo pipefail 下可能因 BrokenPipe 误失败。
```

本补丁用于修复这两个问题。

## 修复内容

### 后端

```text
MapAiAgentResponse 增加 sources 字段；
MapAiAgentServiceImpl 将 knowledgeSources 同步写入：
- response.knowledgeSources
- response.sources
- response.data.sources
- response.data.knowledgeSources

knowledge.retrieve 的 Trace step 优先命名为 knowledge_retrieve；
Trace step data 增加 searchMode、vectorUsed、embeddingProvider、hitCount、topScore、fallback 等字段。
```

### 前端

```text
AgentChatFloat.vue 的 normalizeSources 增强：
1. 优先读取 payload.sources；
2. 其次读取 payload.knowledgeSources；
3. 再读取 payload.data.sources / payload.data.knowledgeSources；
4. 如果仍为空，则从 toolResults 中 knowledge.retrieve 的 data.hits 兜底提取。
```

### 脚本

```text
check-phase36-vector-rag-e2e.sh 去掉 `python3 -m json.tool | head` 管道写法；
改为写入临时文件后用 sed 截断显示，避免 BrokenPipe 误失败。
```

## 使用方式

```bash
unzip srmp-phase36-rag-sources-script-fix.zip -d /tmp/phase36-fix
cp -r /tmp/phase36-fix/srmp-phase36-rag-sources-script-fix/* /path/to/srmp/

cd /path/to/srmp
chmod +x scripts/check-phase36-vector-rag-e2e.sh

mvn clean package -DskipTests
npm --prefix srmp-web-ui run build

BASE_URL=http://localhost:8080 TENANT_ID=default \
bash scripts/check-phase36-vector-rag-e2e.sh
```

## 验收标准

```text
/api/agent/map-agent/chat 返回：
- toolResults 包含 knowledge.retrieve；
- sources 或 knowledgeSources 不为空；
- trace.steps 包含 knowledge_retrieve；
- 前端 AI 面板显示“参考资料”。
```
