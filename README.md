# SmartRoad Maintenance Platform（SRMP）

智路养护平台 — 公路养护 GIS 智能分析平台

![](https://img.shields.io/badge/Java-8%2B-blue)
![](https://img.shields.io/badge/Spring%20Boot-2.7.x-green)
![](https://img.shields.io/badge/PostgreSQL-15+-orange)
![](https://img.shields.io/badge/PostGIS-3.x-red)
![](https://img.shields.io/badge/Vue-3-blueviolet)

---

## 项目简介

智路养护平台（SRMP）一期聚焦 **GIS 一张图 + 数据接入 + 道路资产展示 + 病害分析 + 巡检评定 + AI 大模型辅助分析**，为公路养护管理人员提供可接入数据、可展示路网、可分析病害、可辅助决策的智能化平台。

**核心链路：**

```
数据导入 → 道路资产入库 → GIS一张图展示 → 病害/评定结果上图 → 统计分析 → 大模型智能分析
```

---

## 技术栈

| 层次 | 技术 |
|------|------|
| 后端 | Spring Boot 2.7.x、MyBatis Plus、Java 8/17 |
| 数据库 | PostgreSQL 15+、PostGIS 3.x |
| 缓存 | Redis |
| 文件存储 | MinIO |
| 前端 | Vue 3、Vite、Element Plus、Leaflet、ECharts |
| AI | RAG + 工具调用 + 受控 Text2SQL |

---

## 项目结构

```
srmp-parent/
├── srmp-common              # 公共基础：统一响应、分页、异常、工具类
├── srmp-web                 # Web通用：跨域、参数校验、Swagger
├── srmp-security            # 认证授权：JWT、用户、角色
├── srmp-tenant              # 多租户：字段级租户隔离
├── srmp-base                # 基础数据：字典、行政区划、组织机构
├── srmp-road-asset          # 道路资产：路线、路段、评定单元
├── srmp-inspection          # 巡检任务：巡检任务、巡检轨迹
├── srmp-disease             # 病害管理：病害类型、病害记录、复核
├── srmp-assessment          # 评定结果：MQI/PQI/PCI 等指标
├── srmp-gis                 # GIS一张图：图层服务、空间查询、桩号定位
├── srmp-import              # 数据导入：Excel/CSV/GeoJSON 解析
├── srmp-file                # 文件资料：MinIO 对象存储
├── srmp-agent               # AI大模型：智能问答、路况分析、报告生成
├── srmp-dashboard           # 首页驾驶舱：统计概览、趋势分析
└── srmp-admin               # 启动模块：Spring Boot 主入口
```

---

## 环境变量

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-1.8.jdk/Contents/Home
MAVEN_HOME=/Users/cxp/workspace/apache-maven-3.6.3/
HTTP_PROXY=http://127.0.0.1:7890
HTTPS_PROXY=http://127.0.0.1:7890
```

---

## 快速开始

### 环境要求

- JDK 8+
- Maven 3.6+
- PostgreSQL 15+（需启用 PostGIS 扩展）
- Redis 6+
- MinIO（可选，用于文件存储）

### 1. 启动基础依赖

```bash
docker compose up -d
```

### 2. 初始化数据库

```bash
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/schema.sql
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/init_dict.sql
psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/init_admin.sql
```

> 默认数据库：`srmp`，用户名：`srmp`，密码：`srmp123`

### 3. 编译项目

```bash
mvn clean package -DskipTests
```

### 4. 启动后端

```bash
java -jar srmp-admin/target/srmp-admin-1.0.0.jar
```

### 5. 验证服务

```bash
curl http://localhost:8080/api/health
```

期望返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "system": "SmartRoad Maintenance Platform",
    "status": "UP",
    "phase": "phase1-skeleton"
  }
}
```

---

## 核心功能

### GIS 一张图

- 路线、路段、评定单元空间展示
- 病害点、线、面分布展示
- 巡检轨迹上图
- 按 MQI/PQI/PCI 等渲染路况专题图
- 空间查询（框选、圈选、多边形）
- 桩号定位

### 道路资产

- 路线、路段、评定单元三级管理
- 空间线形（geom）维护
- 多格式数据导入（Excel、GeoJSON、WKT）

### 数据导入

- Excel / CSV / GeoJSON / WKT / Shapefile
- 字段映射配置
- 数据校验与错误日志
- 导入预览与确认入库

### AI 大模型分析

- 自然语言路况问答
- 病害热点分析
- 养护建议生成
- 自动报告生成
- 地图联动高亮

### 指标体系

| 指标 | 名称 |
|------|------|
| MQI | 公路技术状况指数 |
| PQI | 路面技术状况指数 |
| PCI | 路面损坏状况指数 |
| RQI | 路面行驶质量指数 |
| RDI | 路面车辙深度指数 |
| SCI | 路基技术状况指数 |
| BCI | 桥隧构造物技术状况指数 |
| TCI | 沿线设施技术状况指数 |

---

## API 概览

| 模块 | 路由前缀 |
|------|---------|
| 登录认证 | `/api/auth/**` |
| 道路资产 | `/api/road-routes/**`、`/api/road-sections/**`、`/api/evaluation-units/**` |
| 巡检任务 | `/api/inspection-tasks/**`、`/api/inspection-tracks/**` |
| 病害管理 | `/api/disease-types/**`、`/api/diseases/**` |
| 评定结果 | `/api/assessment-results/**`、`/api/index-results/**` |
| GIS 一张图 | `/api/gis/**` |
| 数据导入 | `/api/import/**` |
| 文件资料 | `/api/files/**` |
| AI 大模型 | `/api/agent/**` |
| 首页驾驶舱 | `/api/dashboard/**` |

---

## 实施阶段

| 阶段 | 内容 | 状态 |
|------|------|------|
| 阶段一 | 基础骨架搭建 | ✅ 已完成 |
| 阶段二 | 基础数据与道路资产落地 | 🚧 进行中 |
| 阶段三 | 数据导入模块 | 📋 待开始 |
| 阶段四 | GIS 一张图 | 📋 待开始 |
| 阶段五 | AI 大模型接入 | 📋 待开始 |

---

## 文档

更多设计文档位于 `docs/` 目录：

- `smartroad_phase1_gis_ai_design.md` — 一期可行设计方案
- `smartroad_phase1_module_skeleton.md` — 系统模块拆分与骨架设计
- `smartroad_phase1_database_design.md` — 一期数据库设计
- `smartroad_next_step_phase2_plan.md` — 下一步实施计划
- `SRMP项目概述.md` — 项目完整概述（综合所有文档）

---

## 常见问题

### 骨架通不通？

验证服务：

```bash
curl http://localhost:8080/api/health
```

期望返回 `phase1-skeleton` 的健康响应。

### Lombok 编译失败

**问题：** 程序包lombok不存在、找不到符号（Data、EqualsAndHashCode等）

**原因：** 父 pom 的 lombok 仅在 `dependencyManagement` 中声明版本，但缺少 `annotationProcessorPaths` 配置，且子模块未显式声明依赖。

**修复：**

1. 父 pom `pom.xml` 的 `maven-compiler-plugin` 添加注解处理器路径：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <annotationProcessorPath>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </annotationProcessorPath>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

2. 各子模块 `pom.xml` 显式声明 lombok 依赖（若通过 `srmp-common` 传递不生效）：

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
</dependency>
```

### JAR 无主清单属性

**问题：** `xxx.jar中没有主清单属性`

**原因：** `spring-boot-maven-plugin` 未配置 `executions`，默认不执行 `repackage`。

**修复：** `srmp-admin/pom.xml` 添加 executions：

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <version>${spring.boot.version}</version>
    <executions>
        <execution>
            <goals>
                <goal>repackage</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Maven 镜像 502 Bad Gateway

**问题：** `Could not transfer artifact ... 502 Bad Gateway`

**原因：** `nexus.kuembang.com` 镜像已下线。

**修复：** 编辑 `~/.m2/settings.xml`，将 `nexus.kuembang.com` 相关 URL 替换为可用的镜像（如 aliyun），并确保代理配置正确：

```xml
<proxy>
    <id>http-proxy</id>
    <active>true</active>
    <protocol>http</protocol>
    <host>127.0.0.1</host>
    <port>7890</port>
</proxy>
<proxy>
    <id>https-proxy</id>
    <active>true</active>
    <protocol>https</protocol>
    <host>127.0.0.1</host>
    <port>7890</port>
</proxy>
```

---

## 许可证

Private - All Rights Reserved
