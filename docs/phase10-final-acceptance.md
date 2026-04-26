# 阶段十：一期工程化收口说明

## 1. 收口目标

阶段十不再新增业务范围，重点补齐一期交付所需的稳定性、可演示性和可验收性。

本次包含：

```text
1. 前端入口文件格式修复
2. GIS 一张图默认加载 G210 / 2026
3. GIS 图层 loading、空数据提示、接口失败提示
4. 自动化接口验收脚本
5. 演示数据重置脚本
6. 导入模板 CSV 示例文件
7. demo 环境配置样例
8. README 手动更新片段
```

## 2. 一键演示流程

```bash
docker compose -f docker-compose.demo.yml up -d
chmod +x scripts/*.sh
./scripts/reset-demo.sh
mvn clean package -DskipTests
java -jar srmp-admin/target/srmp-admin-1.0.0.jar --spring.profiles.active=demo
```

另开终端：

```bash
cd srmp-web-ui
cp .env.development.example .env.development
npm install
npm run dev
```

访问：

```text
http://localhost:5173
```

## 3. 自动验收

```bash
./scripts/api-acceptance-test.sh
```

脚本会验证：

```text
1. /api/health
2. 路线图层 features 非空
3. 病害图层 features 非空
4. 评定专题图 features 非空
5. AI 路线分析不返回 null 指标
6. AI 报告草稿不返回 null 指标
```

## 4. 导入模板

模板位置：

```text
docs/import-templates/
├── road_route.csv
├── road_section.csv
├── evaluation_unit.csv
├── disease.csv
├── assessment.csv
└── index_result.csv
```

## 5. 前端验收点

打开 GIS 一张图后，默认条件为：

```text
routeCode = G210
year = 2026
indexCode = MQI
```

应能看到：

```text
1. G210 路线
2. G210 路段
3. 病害点/线/面
4. 评定专题图
5. 地图统计面板
6. AI 问答面板
```

## 6. 注意事项

如果 `git apply` 在 README 上冲突，本补丁没有直接覆盖 README，请使用：

```text
docs/readme-phase10-update.md
```

手动合并 README 内容。
