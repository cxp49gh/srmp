# 阶段七：基于当前已提交代码的一期功能完善 Patch

## 1. 修复重点

当前 GitHub 主分支中的 `/api/agent/chat` 仍是阶段一占位实现，会返回：

```text
阶段一基础骨架已接通，后续在 srmp-agent 中接入大模型 API 与业务工具。
```

本 Patch 将其替换为正式的 AI 分析入口，并新增以下接口：

```text
POST /api/agent/chat
POST /api/agent/analyze/route
POST /api/agent/analyze/disease
POST /api/agent/analyze/assessment
POST /api/agent/map-query
POST /api/agent/report/assessment
```

## 2. 能力说明

本 Patch 支持：

```text
1. 智能问答
2. 路线综合分析
3. 病害热点分析
4. 评定结果分析
5. 地图联动查询
6. 评定分析报告草稿生成
7. OpenAI-Compatible 大模型接入
8. 大模型未配置时，本地规则兜底分析
```

## 3. 配置

沿用已有配置：

```yaml
srmp:
  llm:
    provider: openai-compatible
    base-url: http://127.0.0.1:8000/v1
    api-key: your-api-key
    model: gpt-4o-mini
```

当 `api-key` 为空或仍为 `your-api-key` 时，系统不会调用真实大模型，会使用本地规则兜底。

## 4. 测试

### 4.1 智能问答

```http
POST http://localhost:8080/api/agent/chat
Content-Type: application/json
X-Tenant-Id: default

{
  "message": "分析 G210 2026 年整体路况",
  "context": {
    "routeCode": "G210",
    "year": 2026
  }
}
```

### 4.2 路线分析

```http
POST http://localhost:8080/api/agent/analyze/route
Content-Type: application/json
X-Tenant-Id: default

{
  "routeCode": "G210",
  "year": 2026
}
```

### 4.3 病害分析

```http
POST http://localhost:8080/api/agent/analyze/disease
Content-Type: application/json
X-Tenant-Id: default

{
  "routeCode": "G210",
  "year": 2026
}
```

### 4.4 评定分析

```http
POST http://localhost:8080/api/agent/analyze/assessment
Content-Type: application/json
X-Tenant-Id: default

{
  "routeCode": "G210",
  "year": 2026,
  "indexCode": "PCI"
}
```

## 5. 应用 Patch 后检查

```bash
grep -R "阶段一基础骨架已接通" -n srmp-agent
```

如果没有输出，说明占位实现已移除。

然后执行：

```bash
mvn clean package -DskipTests
```
