# Outline 本地 Docker 部署说明

## 1. 说明

本文档用于在本地通过 Docker 部署 Outline，供智路养护平台一期 AI 知识库增强功能联调使用。

本地部署组件：

```text
Outline
PostgreSQL
Redis
MinIO
Dex OIDC 登录服务
```

默认端口：

| 服务 | 地址 |
|---|---|
| Outline | http://localhost:3000 |
| Dex | http://localhost:5556 |
| MinIO API | http://localhost:19000 |
| MinIO Console | http://localhost:19001 |
| PostgreSQL | localhost:15432 |
| Redis | localhost:16379 |

---

## 2. 启动方式

### 2.1 创建环境配置

```bash
cp deploy/outline/outline.env.example deploy/outline/outline.env
```

必须添加 `PGSSLMODE=disable`（Outline 镜像内 Sequelize 默认要求 SSL）：

```bash
echo "PGSSLMODE=disable" >> deploy/outline/outline.env
```

建议重新生成密钥：

```bash
openssl rand -hex 32
openssl rand -hex 32
```

分别替换：

```text
SECRET_KEY（64字符十六进制）
UTILS_SECRET（64字符十六进制）
```

### 2.2 启动 Outline

方式一：

```bash
chmod +x scripts/*.sh
./scripts/start-outline.sh
```

方式二：

```bash
docker compose -f docker-compose.outline.yml up -d
```

查看日志：

```bash
docker logs -f srmp-outline
```

访问：

```text
http://localhost:3000
```

本地测试账号：

```text
admin@example.com
password
```

---

## 3. 创建 Outline API Token

登录 Outline 后，在页面中进入：

```text
Settings → API Tokens → New token
```

复制生成的 token。

然后配置智路养护平台后端环境变量：

```bash
export OUTLINE_ENABLED=true
export OUTLINE_BASE_URL=http://localhost:3000
export OUTLINE_API_TOKEN=你的_Outline_API_Token
```

启动 SRMP 后端：

```bash
java -jar srmp-admin/target/srmp-admin-1.0.0.jar --spring.profiles.active=demo
```

---

## 4. SRMP 联调验证

### 4.1 后端接口验证

```http
GET http://localhost:8080/api/outline/status
X-Tenant-Id: default
```

正常应返回：

```json
{
  "enabled": true,
  "usable": true,
  "baseUrl": "http://localhost:3000"
}
```

### 4.2 Outline 搜索验证

```http
POST http://localhost:8080/api/outline/search
Content-Type: application/json
X-Tenant-Id: default

{
  "query": "病害复核流程",
  "limit": 5
}
```

### 4.3 前端页面验证

访问：

```text
http://localhost:5173/agent/outline-status
http://localhost:5173/agent/outline-search
```

---

## 5. 智路养护平台配置示例

如果使用 `application-demo.yml`，建议增加：

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

---

## 6. 常见问题

### 6.1 Outline 登录失败

检查 Dex 是否启动：

```bash
docker logs -f srmp-outline-dex
```

检查 Outline 环境变量：

```text
OIDC_CLIENT_ID=outline
OIDC_CLIENT_SECRET=outline-secret
OIDC_AUTH_URI=http://localhost:5556/auth
OIDC_TOKEN_URI=http://outline-dex:5556/token
OIDC_USERINFO_URI=http://outline-dex:5556/userinfo
```

### 6.2 MinIO 上传失败

检查 bucket 是否创建：

```bash
docker logs srmp-outline-createbuckets
```

访问 MinIO 控制台：

```text
http://localhost:19001
```

账号：

```text
outline
outlinepass
```

### 6.3 SRMP 显示 Outline usable=false

检查后端环境变量：

```bash
echo $OUTLINE_ENABLED
echo $OUTLINE_BASE_URL
echo $OUTLINE_API_TOKEN
```

必须满足：

```text
OUTLINE_ENABLED=true
OUTLINE_BASE_URL=http://localhost:3000
OUTLINE_API_TOKEN=真实 token
```

### 6.4 重置本地 Outline 数据

停止并删除本地数据：

```bash
docker compose -f docker-compose.outline.yml down
rm -rf deploy/outline/data/postgres deploy/outline/data/minio
```

重新启动：

```bash
./scripts/start-outline.sh
```

---

## 7. 注意事项

```text
1. 本配置仅用于本地开发联调；
2. 不要把真实 API Token 提交到 Git；
3. 生产环境不要直接使用 latest 镜像；
4. 生产环境需要配置 HTTPS；
5. 生产环境应接入正式 OIDC / SSO；
6. SECRET_KEY 和 UTILS_SECRET 必须使用强随机值。
```