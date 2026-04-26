# 阶段六：一期演示闭环与验收说明

## 1. 阶段目标

阶段六用于把前五个阶段形成的能力整理成一个可演示、可验收、可快速启动的一期版本。

核心闭环：

```text
初始化演示数据
  ↓
启动后端
  ↓
启动前端 GIS 一张图
  ↓
展示 G210 路线、路段、评定单元
  ↓
展示病害分布
  ↓
展示评定专题图
  ↓
调用 AI 分析路线、病害和评定结果
  ↓
生成评定分析报告草稿
```

---

## 2. 新增文件

```text
srmp-admin/src/main/resources/db/demo_data.sql
scripts/init-db.sh
scripts/start-backend.sh
scripts/start-frontend.sh
scripts/smoke-test.sh
docs/api-smoke-test.http
docs/phase6-demo-acceptance.md
docs/readme-phase6-update.md
```

---

## 3. 初始化演示环境

### 3.1 启动基础依赖

```bash
docker compose up -d
```

### 3.2 初始化数据库和演示数据

```bash
chmod +x scripts/*.sh
./scripts/init-db.sh
```

等价于依次执行：

```bash
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/schema.sql
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/init_dict.sql
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/init_admin.sql
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/demo_data.sql
```

---

## 4. 启动系统

### 4.1 启动后端

```bash
./scripts/start-backend.sh
```

或者：

```bash
mvn clean package -DskipTests
java -jar srmp-admin/target/srmp-admin-1.0.0.jar
```

### 4.2 启动前端

```bash
./scripts/start-frontend.sh
```

或者：

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

## 5. 演示数据说明

演示数据包含：

| 类型 | 示例 |
|---|---|
| 路线 | G210 演示路线 |
| 路段 | G210_K0_K10 |
| 评定单元 | G210_BOTH_K0_K1 至 G210_BOTH_K9_K10 |
| 巡检任务 | DEMO_INSPECTION_G210_2026 |
| 病害 | 裂缝、坑槽、车辙、沉陷 |
| 评定结果 | 10 个评定单元的 MQI/PQI/PCI 等 |
| 指标结果 | MQI、PQI、PCI、RQI、RDI |

演示路线坐标范围：

```text
贵州贵阳附近坐标
起点：106.630, 26.650
终点：106.720, 26.710
```

---

## 6. 冒烟测试

执行：

```bash
./scripts/smoke-test.sh
```

也可以使用：

```text
docs/api-smoke-test.http
```

在 IntelliJ IDEA 或 VS Code REST Client 中逐条执行。

---

## 7. 验收项

### 7.1 后端验收

| 验收项 | 接口 | 标准 |
|---|---|---|
| 健康检查 | GET /api/health | code=0 |
| 路线图层 | GET /api/gis/road-routes?routeCode=G210 | 返回 FeatureCollection |
| 路段图层 | GET /api/gis/road-sections?routeCode=G210 | 返回 FeatureCollection |
| 评定单元图层 | GET /api/gis/evaluation-units?routeCode=G210 | 返回 10 个左右单元 |
| 病害图层 | GET /api/gis/diseases?routeCode=G210 | 返回点/线/面病害 |
| 评定图层 | GET /api/gis/assessment-results?routeCode=G210&year=2026 | 返回带等级/颜色对象 |
| AI 分析 | POST /api/agent/analyze/route | 返回路线分析文本 |
| 报告草稿 | POST /api/agent/report/assessment | 返回 Markdown 报告 |

### 7.2 前端验收

| 验收项 | 标准 |
|---|---|
| 页面访问 | http://localhost:5173 可打开 |
| 图层树 | 可勾选路线、路段、评定单元、病害、评定专题 |
| 地图展示 | 可看到 G210 路线 |
| 病害展示 | 可看到病害点、线、面 |
| 评定专题 | 可按颜色显示优、良、中、次、差 |
| 对象详情 | 点击地图对象可显示属性 |
| AI 浮窗 | 可输入问题并返回分析 |

### 7.3 AI 演示问题

可在前端 AI 面板或接口中提问：

```text
分析 G210 2026 年整体路况
找出 G210 病害最严重的路段
哪些评定单元是次差路段
根据当前病害和评定结果给出优先养护建议
生成 G210 2026 年技术状况评定报告草稿
```

---

## 8. 一期演示流程

建议对外演示时按以下顺序：

```text
1. 打开首页或 GIS 一张图
2. 输入路线 G210
3. 打开路线、路段、评定单元图层
4. 打开病害图层，展示裂缝、坑槽、车辙、沉陷
5. 打开评定专题图，展示优良中次差
6. 点击某个病害查看详情
7. 点击某个次差评定单元查看 MQI/PQI/PCI
8. 在 AI 面板提问：分析 G210 2026 年整体路况
9. 调用报告生成，展示 Markdown 报告草稿
```

---

## 9. 常见问题

### 9.1 demo_data.sql 重复执行会重复插入吗？

不会。脚本会先清理 G210 演示数据，然后重新插入。

### 9.2 前端地图没有数据？

检查：

```text
1. 后端是否启动
2. demo_data.sql 是否执行
3. 请求是否带 X-Tenant-Id: default
4. 浏览器 Network 中 /api/gis/road-routes 是否返回 features
```

### 9.3 AI 没有调用真实大模型？

检查：

```yaml
srmp:
  llm:
    base-url: http://127.0.0.1:8000/v1
    api-key: your-api-key
    model: gpt-4o-mini
```

如果 `api-key` 仍是 `your-api-key`，系统会使用本地规则兜底分析。

---

## 10. 阶段六完成标准

完成阶段六后，项目应达到：

```text
git clone 后可以初始化演示数据
后端可以启动
前端可以启动
GIS 一张图可以看到道路资产、病害和评定专题
AI 可以基于演示数据完成分析
文档可以指导别人完成启动和验收
```
