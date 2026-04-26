# README 阶段六更新片段

如果 `git apply` 更新 README 冲突，可以手动复制以下内容替换 README 中对应部分。

---

## 快速开始补充

在“初始化数据库”部分增加演示数据脚本：

```bash
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/demo_data.sql
```

或使用：

```bash
chmod +x scripts/*.sh
./scripts/init-db.sh
```

前端启动：

```bash
cd srmp-web-ui
npm install
npm run dev
```

访问：

```text
http://localhost:5173
```

---

## 实施阶段

| 阶段 | 内容 | 状态 |
|---|---|---|
| 阶段一 | 基础骨架搭建 | ✅ 已完成 |
| 阶段二 | 道路资产 CRUD + GIS 图层接口 | ✅ 已完成 |
| 阶段二（续） | 病害 GIS 图层 + 评定结果 GIS 图层 | ✅ 已完成 |
| 阶段三 | 数据导入模块 | ✅ 已完成 |
| 阶段四 | GIS 一张图前端完善 | ✅ 已完成 |
| 阶段五 | AI 大模型接入 | ✅ 已完成 |
| 阶段六 | 一期演示闭环与验收 | ✅ 已完成 |

---

## 一期演示闭环

```text
docker compose up -d
./scripts/init-db.sh
./scripts/start-backend.sh
./scripts/start-frontend.sh
```

浏览器访问：

```text
http://localhost:5173
```

演示路线：

```text
G210
```

演示问题：

```text
分析 G210 2026 年整体路况
找出 G210 病害最严重的路段
哪些评定单元是次差路段
生成 G210 2026 年技术状况评定报告草稿
```

---

## 演示验收文档

```text
docs/phase6-demo-acceptance.md
docs/api-smoke-test.http
```
