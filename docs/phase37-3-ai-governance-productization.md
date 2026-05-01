# Phase37.3：AI 能力治理与产品化收口

## 1. 阶段背景

当前系统已经完成：

```text
Phase36：一张图 AI Agent + 向量知识库 + RAG 端到端链路
Phase37：真实 Embedding + RAG 质量评测
Phase37.1：知识库 Reindex / Re-embedding
Phase37.2：RAG 评测规则增强
```

现在系统已经具备“能用、可评测”的 AI 能力，但从用户角度和产品化角度看，还需要解决：

```text
1. 配置安全：真实 API Key 不应出现在 application-dev.yml；
2. AI 健康状态：Embedding/知识库/pgvector 是否正常，需要页面和接口可见；
3. 默认评测用例：不应硬编码在 Controller 中，应资源化、版本化；
4. 前后端契约：RagEvalCase / RagEvalResult 类型需要对齐；
5. 用户解释：用户需要知道 AI 回答用了哪些知识和是否降级；
6. 运维治理：需要脚本防止敏感 Key、检查 AI 运行状态。
```

本阶段目标是把 AI 能力从“脚本可验收”推进到：

```text
用户可理解
管理员可维护
系统可持续回归
配置更安全
```

---

## 2. 本次实现范围

本实现包交付第一版产品化收口，聚焦 4 个高优先级点：

```text
1. 配置安全收口
   - application-dev.yml 使用环境变量
   - 新增敏感配置检查脚本

2. Embedding 健康检查
   - GET /api/ai/embedding/health
   - 前端新增 /agent/ai-health 页面

3. RAG 默认用例资源化
   - 默认用例移动到 classpath resources
   - Controller 从 JSON 加载，失败时才使用 fallback

4. 前后端评测 DTO 对齐
   - agent.ts 补齐 RagEvalCase / RagEvalResult 类型
   - 支持 expectedKeywordGroups / failReasons / answerPreview
```

---

## 3. 新增后端接口

### 3.1 Embedding 健康检查

```http
GET /api/ai/embedding/health
```

返回示例：

```json
{
  "provider": "openai-compatible",
  "model": "text-embedding-v4",
  "endpoint": "https://dashscope.aliyuncs.com/compatible-mode/v1",
  "expectedDimensions": 1024,
  "actualDimensions": 1024,
  "available": true,
  "costMs": 356,
  "errorType": null,
  "errorMessage": null,
  "suggestion": "Embedding provider 可用"
}
```

如果不可用：

```json
{
  "provider": "openai-compatible",
  "model": "text-embedding-v4",
  "endpoint": "https://dashscope.aliyuncs.com/compatible-mode/v1",
  "expectedDimensions": 1024,
  "actualDimensions": 0,
  "available": false,
  "errorType": "SSLHandshakeException",
  "errorMessage": "Remote host terminated the handshake",
  "suggestion": "请检查网络代理、JDK TLS、API Key、endpoint 或模型名称"
}
```

---

## 4. RAG 默认评测用例资源化

资源文件：

```text
srmp-agent/src/main/resources/eval/phase37-rag/road-maintenance-rag-cases.json
```

Controller：

```text
/api/ai/eval/rag/default-cases
/api/ai/eval/rag/cases
```

优先从 classpath JSON 加载，避免 Controller 中硬编码旧用例导致版本不一致。

---

## 5. 前端新增页面

新增页面：

```text
/agent/ai-health
```

页面内容：

```text
1. Embedding 健康状态
2. 知识库统计
3. 当前 provider/model/dimensions
4. vectorEnabled
5. chunkEmbeddingProviders 分布
6. 一键刷新
```

---

## 6. 配置安全

`application-dev.yml` 改为：

```yaml
srmp:
  llm:
    api-key: ${DASHSCOPE_API_KEY:}
  ai:
    embedding:
      api-key: ${DASHSCOPE_API_KEY:}
```

新增脚本：

```text
scripts/check-sensitive-config.sh
```

用于检查：

```text
sk- 开头的 API Key
DashScope 明文 Key
OpenAI 明文 Key
application*.yml 中的敏感配置
```

---

## 7. 使用方式

```bash
unzip srmp-phase37-3-ai-governance-productization.zip -d /tmp/phase37-3
cp -r /tmp/phase37-3/srmp-phase37-3-ai-governance-productization/* /path/to/srmp/

cd /path/to/srmp
chmod +x scripts/check-sensitive-config.sh

bash scripts/check-sensitive-config.sh

mvn clean package -DskipTests
npm --prefix srmp-web-ui run build
```

设置环境变量：

```bash
export DASHSCOPE_API_KEY="sk-xxx"
```

启动后验证：

```bash
curl -s http://localhost:8080/api/ai/embedding/health | python3 -m json.tool
curl -s http://localhost:8080/api/ai/eval/rag/default-cases | python3 -m json.tool
```

前端访问：

```text
/agent/ai-health
```

---

## 8. 后续建议

Phase37.3 后续可继续增强：

```text
1. LLM health
2. AI 调用成本统计
3. RAG 评测历史记录
4. 知识库文档禁用/删除/版本管理
5. Reindex 任务表和执行历史
6. Query Rewrite + Hybrid Search
7. 一张图 AI 依据面板
```
