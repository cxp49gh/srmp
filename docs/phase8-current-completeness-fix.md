# 阶段八：一期功能完整性修复说明

本 Patch 基于当前 GitHub 主分支审查结果，修复一期演示闭环中的关键缺口。

## 修复内容

1. `/api/agent/chat` 从阶段一占位改为正式调用 `AgentChatService`。
2. `/api/gis/map-statistics` 从固定 0 改为真实统计。
3. 新增 `/api/gis/object-detail`，支撑前端点击对象查看详情。
4. 新增 Dashboard 真实统计接口：`/api/dashboard/overview`、`/api/dashboard/disease-summary`、`/api/dashboard/assessment-summary`。
5. 修复前端 TypeScript 文件被压缩成单行导致的构建问题。
6. 补齐 GIS 一张图页面和组件空文件。
7. 补齐 `demo_data.sql`，让文档中的演示数据初始化命令可执行。

## 应用后检查

```bash
grep -R "阶段一基础骨架已接通" -n srmp-agent
mvn clean package -DskipTests
cd srmp-web-ui && npm install && npm run build
```

## 推荐验收

```bash
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/demo_data.sql
curl -H 'X-Tenant-Id: default' 'http://localhost:8080/api/gis/road-routes?routeCode=G210'
curl -H 'X-Tenant-Id: default' 'http://localhost:8080/api/gis/diseases?routeCode=G210'
curl -H 'X-Tenant-Id: default' 'http://localhost:8080/api/gis/assessment-results?routeCode=G210&year=2026'
```
