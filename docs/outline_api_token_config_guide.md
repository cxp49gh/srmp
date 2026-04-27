# Outline 未启用问题处理说明

文档版本：V1.0  
适用系统：智路养护平台 SRMP  
问题现象：`Outline 未启用（需配置 OUTLINE_API_TOKEN）`

---

# 1. 问题现象

在访问以下页面或接口时：

```text
http://localhost:5173/agent/outline-status
```

或：

```bash
curl -s http://localhost:8080/api/outline/status \
  -H "X-Tenant-Id: default"
```

返回结果中可能出现：

```json
{
  "enabled": false,
  "usable": false
}
```

或者前端提示：

```text
Outline 未启用（需配置 OUTLINE_API_TOKEN）
```

这说明 **SRMP 后端没有拿到可用的 Outline 配置**，尤其是缺少：

```text
OUTLINE_API_TOKEN
```

---

# 2. 原因说明

SRMP 后端的 Outline 接入依赖以下三个环境变量：

```bash
OUTLINE_ENABLED=true
OUTLINE_BASE_URL=http://localhost:3000
OUTLINE_API_TOKEN=你的_Outline_API_Token
```

其中：

| 配置项 | 说明 |
|---|---|
| `OUTLINE_ENABLED` | 是否启用 Outline |
| `OUTLINE_BASE_URL` | Outline 服务地址 |
| `OUTLINE_API_TOKEN` | Outline API Token |
| `OUTLINE_DEFAULT_COLLECTION_ID` | 可选，默认 Collection |
| `OUTLINE_SEARCH_LIMIT` | 可选，默认搜索数量 |

如果没有配置 `OUTLINE_API_TOKEN`，或者 Token 仍然是占位值，后端会判断 Outline 不可用。

---

# 3. 处理步骤

## 3.1 确认本地 Outline 已启动

如果你使用项目中的本地 Outline Docker 部署方式，执行：

```bash
docker ps | grep srmp-outline
```

或者：

```bash
docker compose -f docker-compose.outline.yml ps
```

如果未启动，执行：

```bash
./scripts/start-outline.sh
```

然后访问：

```text
http://localhost:3000
```

默认本地测试账号：

```text
admin@example.com
password
```

---

## 3.2 登录 Outline 创建 API Token

登录 Outline 后，在页面中进入：

```text
Settings → API Tokens → New token
```

复制生成的 Token。

注意：

```text
1. Token 只展示一次，请及时复制保存；
2. 不要把 Token 提交到 Git；
3. 生产环境建议使用专用账号创建 Token。
```

---

## 3.3 配置 SRMP 后端环境变量

在启动 `srmp-admin` 之前执行：

```bash
export OUTLINE_ENABLED=true
export OUTLINE_BASE_URL=http://localhost:3000
export OUTLINE_API_TOKEN=你复制的真实Token
```

然后重新启动 SRMP 后端：

```bash
java -jar srmp-admin/target/srmp-admin-1.0.0.jar --spring.profiles.active=demo
```

---

# 4. IDE 启动时的配置方式

如果你不是通过命令行启动，而是通过 IntelliJ IDEA 启动后端，需要在 Run Configuration 中配置环境变量。

## 4.1 IntelliJ IDEA 配置

进入：

```text
Run/Debug Configurations
  ↓
Spring Boot 启动项
  ↓
Environment variables
```

添加：

```text
OUTLINE_ENABLED=true
OUTLINE_BASE_URL=http://localhost:3000
OUTLINE_API_TOKEN=你的真实Token
```

然后重新启动后端服务。

---

# 5. application-demo.yml 推荐配置

建议在 `application-demo.yml` 中使用环境变量占位：

```yaml
srmp:
  outline:
    enabled: ${OUTLINE_ENABLED:false}
    base-url: ${OUTLINE_BASE_URL:}
    api-token: ${OUTLINE_API_TOKEN:}
    sync-enabled: false
    default-collection-id: ${OUTLINE_DEFAULT_COLLECTION_ID:}
    search-limit: ${OUTLINE_SEARCH_LIMIT:5}
```

这样可以避免把真实 Token 写入配置文件。

---

# 6. 验证方式

## 6.1 验证 Outline 状态接口

执行：

```bash
curl -s http://localhost:8080/api/outline/status \
  -H "X-Tenant-Id: default"
```

正常应看到：

```json
{
  "code": 0,
  "data": {
    "enabled": true,
    "usable": true,
    "baseUrl": "http://localhost:3000"
  }
}
```

如果仍然是：

```json
{
  "usable": false
}
```

说明后端仍未拿到有效 Token。

---

## 6.2 验证 Outline 搜索接口

先在 Outline 中创建一篇测试文档，例如：

```text
病害复核流程
```

然后执行：

```bash
curl -s -X POST http://localhost:8080/api/outline/search \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: default" \
  -d '{
    "query": "病害复核流程",
    "limit": 5
  }'
```

正常应返回相关文档列表。

---

## 6.3 验证前端页面

访问：

```text
http://localhost:5173/agent/outline-status
```

应看到：

```text
enabled = true
usable = true
baseUrl = http://localhost:3000
```

访问：

```text
http://localhost:5173/agent/outline-search
```

输入关键词搜索，确认能返回 Outline 文档。

---

# 7. 常见问题

## 7.1 已经 export 了环境变量，但还是 usable=false

原因可能是：

```text
1. 后端是在 IDE 中启动的，终端 export 不生效；
2. 后端启动后才 export，进程没有读取到；
3. Token 填错；
4. OUTLINE_BASE_URL 不正确；
5. Outline 服务没有启动；
6. application 配置没有读取 srmp.outline.*。
```

处理：

```bash
echo $OUTLINE_ENABLED
echo $OUTLINE_BASE_URL
echo $OUTLINE_API_TOKEN
```

然后重新启动后端。

---

## 7.2 Docker 里能访问 Outline，但 SRMP 访问失败

如果 SRMP 后端也运行在 Docker 中，不能使用：

```text
http://localhost:3000
```

因为容器里的 `localhost` 指的是容器自身。

此时应改成 Docker 网络中的服务名，例如：

```bash
export OUTLINE_BASE_URL=http://srmp-outline:3000
```

或者使用宿主机访问地址。

---

## 7.3 Outline 页面能打开，但搜索接口返回空

可能原因：

```text
1. Outline 中没有文档；
2. API Token 权限不足；
3. 搜索关键词和文档内容不匹配；
4. 文档未发布或当前账号不可访问。
```

建议先创建一篇包含明确关键词的文档，例如：

```text
病害复核流程
PCI 指标说明
数据导入模板
```

---

## 7.4 不想使用 Outline，可以只用本地知识库吗？

可以。

在 AI 问答时关闭 Outline：

```json
{
  "options": {
    "useBusinessData": true,
    "useKnowledge": true,
    "useOutline": false
  }
}
```

或者前端页面不勾选：

```text
使用 Outline
```

本地知识库仍然可以通过以下页面使用：

```text
http://localhost:5173/agent/knowledge-documents
http://localhost:5173/agent/knowledge-search
http://localhost:5173/agent/chat
```

---

# 8. 推荐启动流程

完整本地联调流程：

```bash
# 1. 启动 Outline
./scripts/start-outline.sh

# 2. 登录 Outline 创建 API Token
# http://localhost:3000

# 3. 配置后端环境变量
export OUTLINE_ENABLED=true
export OUTLINE_BASE_URL=http://localhost:3000
export OUTLINE_API_TOKEN=你的真实Token

# 4. 启动 SRMP 后端
java -jar srmp-admin/target/srmp-admin-1.0.0.jar --spring.profiles.active=demo

# 5. 启动前端
cd srmp-web-ui
npm run dev
```

访问：

```text
http://localhost:5173/agent/outline-status
http://localhost:5173/agent/outline-search
http://localhost:5173/agent/outline-sync
```

---

# 9. 总结

出现：

```text
Outline 未启用（需配置 OUTLINE_API_TOKEN）
```

说明 Outline 功能链路尚未完全接通。

最关键的处理是：

```bash
export OUTLINE_ENABLED=true
export OUTLINE_BASE_URL=http://localhost:3000
export OUTLINE_API_TOKEN=真实Token
```

然后 **重新启动 SRMP 后端**。

如果不需要 Outline，也可以继续使用本地知识库能力，不影响：

```text
知识库文档录入
知识库检索
知识库问答
AI 混合问答
```
