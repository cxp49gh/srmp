# README 阶段十更新片段

建议把 README 中本机专属环境变量替换为通用说明：

```bash
cp .env.example .env
docker compose -f docker-compose.demo.yml up -d
chmod +x scripts/*.sh
./scripts/reset-demo.sh
mvn clean package -DskipTests
java -jar srmp-admin/target/srmp-admin-1.0.0.jar --spring.profiles.active=demo
```

前端：

```bash
cd srmp-web-ui
cp .env.development.example .env.development
npm install
npm run dev
```

自动验收：

```bash
./scripts/api-acceptance-test.sh
```

导入模板：

```text
docs/import-templates/
```

一期收口状态：

| 阶段 | 内容 | 状态 |
|---|---|---|
| 阶段一 | 基础骨架搭建 | ✅ 已完成 |
| 阶段二 | 道路资产 CRUD + GIS 图层接口 | ✅ 已完成 |
| 阶段二续 | 病害 GIS 图层 + 评定结果 GIS 图层 | ✅ 已完成 |
| 阶段三 | 数据导入模块 | ✅ 已完成 |
| 阶段四 | GIS 一张图前端完善 | ✅ 已完成 |
| 阶段五 | AI 大模型接入 | ✅ 已完成 |
| 阶段六 | 一期演示闭环与验收 | ✅ 已完成 |
| 阶段九 | AI 统计过滤条件修复 | ✅ 已完成 |
| 阶段十 | 一期工程化收口 | ✅ 已完成 |
```
