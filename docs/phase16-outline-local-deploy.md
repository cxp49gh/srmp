# 阶段十六：Outline 本地 Docker 部署增量说明

## 1. 本阶段目标

为智路养护平台一期 AI 知识库增强功能提供本地 Outline 联调环境。

## 2. 新增文件

```text
docker-compose.outline.yml
deploy/outline/outline.env.example
deploy/outline/dex-config.yaml
scripts/start-outline.sh
scripts/stop-outline.sh
docs/outline-local-deploy.md
docs/phase16-outline-local-deploy.md
```

## 3. 使用方式

```bash
chmod +x scripts/*.sh
./scripts/start-outline.sh
```

访问：

```text
http://localhost:3000
```

测试账号：

```text
admin@example.com
password
```

## 4. 联调 SRMP

```bash
export OUTLINE_ENABLED=true
export OUTLINE_BASE_URL=http://localhost:3000
export OUTLINE_API_TOKEN=你的_Outline_API_Token
```

验证：

```text
http://localhost:5173/agent/outline-status
http://localhost:5173/agent/outline-search
```