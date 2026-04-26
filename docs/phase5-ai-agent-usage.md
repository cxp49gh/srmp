# 阶段五：AI 大模型分析模块使用说明

本阶段目标：把已有的道路资产、病害、评定结果、GIS 图层能力接入 AI 大模型分析模块，形成“自然语言问答 + 业务工具查询 + 地图联动 + 报告草稿生成”的最小闭环。

---

## 1. 本阶段新增能力

```text
1. OpenAI-Compatible 大模型客户端
2. 大模型配置读取 srmp.llm.*
3. 业务数据查询服务 AgentDataQueryService
4. 路线综合分析接口
5. 病害热点分析接口
6. 评定结果分析接口
7. 地图联动查询接口
8. 评定分析报告草稿生成接口
9. 智能问答接口增强
10. 大模型不可用时的本地规则兜底分析
```

---

## 2. 配置项

在 `srmp-admin/src/main/resources/application-dev.yml` 中已有配置：

```yaml
srmp:
  llm:
    provider: openai-compatible
    base-url: http://127.0.0.1:8000/v1
    api-key: your-api-key
    model: gpt-4o-mini
```

说明：

| 配置 | 说明 |
|---|---|
| provider | 当前支持 openai-compatible |
| base-url | OpenAI 兼容接口基础地址 |
| api-key | API Key |
| model | 模型名称 |

如果 `api-key` 为空或为 `your-api-key`，系统不会真实调用大模型，会使用本地规则分析兜底返回。

---

## 3. 阶段五接口

### 3.1 智能问答

```http
POST /api/agent/chat
X-Tenant-Id: default
Content-Type: application/json
```

请求：

```json
{
  "message": "分析 G210 的病害情况",
  "context": {
    "routeCode": "G210",
    "year": 2026
  }
}
```

---

### 3.2 路线综合分析

```http
POST /api/agent/analyze/route
```

请求：

```json
{
  "routeCode": "G210",
  "year": 2026
}
```

---

### 3.3 病害热点分析

```http
POST /api/agent/analyze/disease
```

请求：

```json
{
  "routeCode": "G210",
  "diseaseType": "POTHOLE",
  "severity": "HEAVY"
}
```

---

### 3.4 评定结果分析

```http
POST /api/agent/analyze/assessment
```

请求：

```json
{
  "routeCode": "G210",
  "year": 2026,
  "indexCode": "PCI",
  "grade": "POOR"
}
```

---

### 3.5 地图联动查询

```http
POST /api/agent/map-query
```

请求：

```json
{
  "message": "把 PCI 小于 70 的路段显示出来",
  "routeCode": "G210",
  "year": 2026
}
```

返回会包含：

```json
{
  "objectType": "ASSESSMENT",
  "highlightIds": [],
  "queryHint": "..."
}
```

前端可用 `highlightIds` 高亮地图对象。

---

### 3.6 评定分析报告草稿

```http
POST /api/agent/report/assessment
```

请求：

```json
{
  "routeCode": "G210",
  "year": 2026
}
```

返回 Markdown 报告草稿。

---

## 4. 本地兜底策略

为避免大模型服务未配置导致接口不可用，本阶段内置兜底逻辑：

```text
1. 未配置 API Key：直接根据数据库统计结果生成分析文本
2. 大模型调用失败：降级为本地规则分析
3. 数据为空：返回明确提示，建议先导入数据
```

---

## 5. 测试顺序

建议按以下顺序测试：

```text
1. 确认已有路线 G210
2. 确认已有病害数据
3. 确认已有评定结果
4. 调用 /api/agent/analyze/route
5. 调用 /api/agent/analyze/disease
6. 调用 /api/agent/analyze/assessment
7. 调用 /api/agent/chat
8. 在前端 GIS 一张图 AI 问答浮窗测试
```

---

## 6. 注意事项

1. 本阶段默认使用 `JdbcTemplate` 做跨模块统计查询，避免强依赖 disease、assessment、road-asset 模块内部 Service。
2. 所有查询都通过 `TenantContextHolder` 获取当前租户。
3. 生成内容是辅助研判，不直接修改业务数据。
4. 报告接口当前返回 Markdown 草稿，后续可扩展 Word/PDF 导出。
