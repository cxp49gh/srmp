# 阶段三十二：地图对象一键生成处置建议 / 方案草稿

## 1. 阶段目标

阶段三十二建议建设 **“地图对象一键生成处置建议 / 方案草稿”** 能力。

当前平台已经具备：

```text
GIS 一张图
地图对象详情
AI 养护助手
当前地图上下文
地图对象 AI 分析
知识库 / Outline / 方案模板基础能力
```

阶段三十二的目标，是把“AI 分析当前对象”进一步升级为可落地的业务成果：

```text
点击地图对象
  ↓
AI 识别对象类型
  ↓
选择生成类型
  ↓
生成结构化草稿
  ↓
前端预览
  ↓
复制 / 下载 / 后续保存
```

也就是说，AI 不只是回答问题，而是能围绕当前地图对象生成：

```text
病害复核意见
病害处置建议
低分单元处置建议
路段养护计划草稿
路线技术状况报告草稿
```

---

## 2. 业务价值

### 2.1 降低用户输入成本

用户不需要手动输入路线、桩号、病害类型、评定指标等信息，只需要在地图上点击对象。

例如：

```text
点击 G210 K69.007-K69.034 修补损坏
  ↓
点击“生成处置建议”
  ↓
系统自动生成该病害的复核意见和养护建议
```

### 2.2 提升 AI 输出可用性

普通 AI 聊天回答偏自由文本，而方案草稿需要结构化、可复制、可归档。

阶段三十二应把输出规范为：

```text
对象摘要
问题判断
成因分析
处置建议
优先级
风险提示
质量检查
```

### 2.3 连接 GIS、业务数据、知识库和方案模板

阶段三十二将形成一条更完整的业务链路：

```text
GIS 地图对象
  ↓
对象详情
  ↓
业务数据分析
  ↓
知识库 / Outline 专业知识
  ↓
方案模板
  ↓
结构化草稿
```

---

## 3. 支持的地图对象类型

建议阶段三十二支持以下对象类型：

| 对象类型 | 说明 | 推荐生成内容 |
|---|---|---|
| `DISEASE` / `DISEASE_RECORD` | 单个病害对象 | 病害复核意见、病害处置建议 |
| `ASSESSMENT_RESULT` | 评定结果对象 | 低分单元处置建议 |
| `EVALUATION_UNIT` | 评定单元 | 评定单元养护建议 |
| `ROAD_SECTION` | 路段 | 路段养护计划草稿 |
| `ROAD_ROUTE` | 路线 | 路线技术状况分析报告草稿 |

---

## 4. 后端设计

### 4.1 新增接口

建议新增接口：

```http
POST /api/agent/map-object/solution
```

### 4.2 请求参数

```json
{
  "tenantId": "default",
  "objectType": "DISEASE",
  "objectId": "xxx",
  "routeCode": "G210",
  "year": 2026,
  "solutionType": "DISEASE_REVIEW",
  "mapObject": {
    "objectType": "DISEASE",
    "routeCode": "G210",
    "startStake": 69.007,
    "endStake": 69.034,
    "diseaseName": "修补损坏",
    "severity": "MEDIUM",
    "quantity": 11.02,
    "measureUnit": "m2"
  }
}
```

### 4.3 返回结果

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "solutionType": "DISEASE_REVIEW",
    "title": "G210 K69.007-K69.034 修补损坏复核意见",
    "markdown": "...",
    "objectSummary": {
      "objectType": "DISEASE",
      "routeCode": "G210",
      "stakeRange": "K69.007-K69.034",
      "diseaseName": "修补损坏",
      "severity": "MEDIUM"
    },
    "qualityCheck": {
      "passed": true,
      "warnings": [],
      "items": [
        {
          "name": "对象位置",
          "passed": true
        },
        {
          "name": "处置建议",
          "passed": true
        },
        {
          "name": "优先级",
          "passed": true
        }
      ]
    }
  }
}
```

---

## 5. 后端模块建议

### 5.1 Controller

```text
MapObjectSolutionController
```

职责：

```text
接收地图对象方案生成请求
校验请求参数
调用 MapObjectSolutionService
返回结构化草稿
```

### 5.2 Service

```text
MapObjectSolutionService
MapObjectSolutionServiceImpl
```

职责：

```text
识别对象类型
补齐对象详情
选择生成模板
组合业务数据、地图上下文、知识库内容
生成 Markdown 草稿
执行质量检查
```

### 5.3 DTO / VO

建议新增：

```text
MapObjectSolutionRequest
MapObjectSolutionResponse
MapObjectSolutionType
MapObjectSolutionQualityCheck
MapObjectSolutionQualityItem
```

### 5.4 质量检查器

建议新增：

```text
MapObjectSolutionQualityChecker
```

检查项：

```text
是否包含对象位置
是否包含病害/指标信息
是否包含成因判断
是否包含处置建议
是否包含优先级
是否包含风险提示
```

---

## 6. 生成类型设计

建议定义枚举：

```java
public enum MapObjectSolutionType {
    DISEASE_REVIEW,
    DISEASE_TREATMENT,
    LOW_SCORE_TREATMENT,
    EVALUATION_UNIT_ADVICE,
    SECTION_PLAN,
    ROUTE_REPORT,
    GENERAL_ADVICE
}
```

说明：

| 枚举 | 名称 | 适用对象 |
|---|---|---|
| `DISEASE_REVIEW` | 病害复核意见 | `DISEASE` |
| `DISEASE_TREATMENT` | 病害处置建议 | `DISEASE` |
| `LOW_SCORE_TREATMENT` | 低分单元处置建议 | `ASSESSMENT_RESULT` |
| `EVALUATION_UNIT_ADVICE` | 评定单元养护建议 | `EVALUATION_UNIT` |
| `SECTION_PLAN` | 路段养护计划草稿 | `ROAD_SECTION` |
| `ROUTE_REPORT` | 路线技术状况报告草稿 | `ROAD_ROUTE` |
| `GENERAL_ADVICE` | 通用养护建议 | 兜底类型 |

---

## 7. 不同对象的输出模板

### 7.1 病害复核意见

适用于：

```text
DISEASE
DISEASE_RECORD
```

推荐结构：

```markdown
# 病害复核意见

## 1. 当前病害对象

- 路线：
- 桩号：
- 病害类型：
- 严重程度：
- 数量：
- 单位：

## 2. 问题判断

说明当前病害的主要表现、影响范围和风险等级。

## 3. 现场复核重点

1. 复核病害边界；
2. 复核损坏深度；
3. 检查基层是否松散；
4. 检查排水情况；
5. 检查周边是否存在连续病害。

## 4. 成因分析

从材料、结构、施工、荷载、排水等方面分析可能原因。

## 5. 处置建议

说明推荐工艺、处置范围、施工注意事项。

## 6. 优先级判断

给出 P1 / P2 / P3 等优先级建议。

## 7. 风险提示

说明若不处置可能带来的风险。
```

### 7.2 病害处置建议

推荐结构：

```markdown
# 病害处置建议

## 1. 对象概况

## 2. 病害影响分析

## 3. 推荐处置工艺

## 4. 工程量估算

## 5. 实施优先级

## 6. 后续跟踪建议
```

### 7.3 低分单元处置建议

适用于：

```text
ASSESSMENT_RESULT
```

推荐结构：

```markdown
# 低分单元处置建议

## 1. 评定对象概况

- 路线：
- 桩号：
- 年度：
- MQI：
- PQI：
- PCI：
- 等级：

## 2. 低分原因判断

结合指标和病害记录说明主要扣分原因。

## 3. 周边病害分析

分析周边病害类型、严重程度和集中程度。

## 4. 处置策略

提出局部修补、罩面、铣刨重铺、排水修复等策略。

## 5. 优先级

给出近期、中期或计划处置建议。

## 6. 风险提示

说明若继续劣化的影响。
```

### 7.4 路段养护计划草稿

适用于：

```text
ROAD_SECTION
```

推荐结构：

```markdown
# 路段养护计划草稿

## 1. 路段概况

## 2. 技术状况

## 3. 主要问题

## 4. 病害分布

## 5. 养护目标

## 6. 建议工程措施

## 7. 实施计划

## 8. 资源需求

## 9. 风险与保障措施
```

### 7.5 路线技术状况报告草稿

适用于：

```text
ROAD_ROUTE
```

推荐结构：

```markdown
# 路线技术状况分析报告草稿

## 1. 路线概况

## 2. 技术状况总体评价

## 3. 评定指标分析

## 4. 病害分布分析

## 5. 低分区段分析

## 6. 养护建议

## 7. 后续工作建议
```

---

## 8. 前端设计

### 8.1 AI 面板按钮增强

当前 AI 浮窗已有：

```text
重新分析当前对象
生成处置建议
```

阶段三十二建议扩展为：

```text
重新分析当前对象
生成处置建议
生成报告草稿
```

不同对象显示不同按钮：

| 对象类型 | 按钮 |
|---|---|
| `DISEASE` | 生成复核意见、生成处置建议 |
| `ASSESSMENT_RESULT` | 生成低分单元处置建议 |
| `EVALUATION_UNIT` | 生成单元养护建议 |
| `ROAD_SECTION` | 生成路段养护计划 |
| `ROAD_ROUTE` | 生成路线报告草稿 |

### 8.2 新增方案预览弹窗

建议新增组件：

```text
SolutionPreviewDialog.vue
```

功能：

```text
展示标题
展示对象摘要
展示 Markdown 正文
展示质量检查结果
支持复制
支持下载 Markdown
预留保存草稿按钮
```

### 8.3 前端 API

新增方法：

```ts
export function generateMapObjectSolution(data: MapObjectSolutionRequest) {
  return request.post('/api/agent/map-object/solution', data)
}
```

---

## 9. 前端交互流程

```text
1. 用户打开 /gis/one-map
2. 点击地图对象
3. AI 浮窗显示“当前地图上下文”
4. 用户点击“生成处置建议”
5. 前端调用 /api/agent/map-object/solution
6. 后端返回 markdown 草稿
7. 前端打开 SolutionPreviewDialog
8. 用户复制或下载 Markdown
```

---

## 10. 第一版最小闭环

阶段三十二第一版不要做保存数据库，不要做审批流，不要一键转工单。

第一版只做：

```text
地图对象 → 方案草稿生成 → 前端预览 → 复制 / 下载
```

这样可以快速演示，且不会破坏现有 AI 问答链路。

---

## 11. 后续阶段规划

### 阶段三十三：方案草稿保存与版本管理

```text
保存方案草稿
方案历史版本
方案模板绑定
方案状态流转
```

### 阶段三十四：方案转工单

```text
方案草稿转养护任务
生成工单
关联地图对象
跟踪处置状态
```

### 阶段三十五：方案模板与知识库联动

```text
Outline 更新模板
知识库同步
方案模板自动刷新
不同业务场景选择不同模板
```

---

## 12. 阶段三十二交付清单

后端：

```text
MapObjectSolutionController
MapObjectSolutionService
MapObjectSolutionServiceImpl
MapObjectSolutionRequest
MapObjectSolutionResponse
MapObjectSolutionType
MapObjectSolutionQualityChecker
```

前端：

```text
AgentChatFloat 增加生成按钮
SolutionPreviewDialog.vue
agent.ts 增加 generateMapObjectSolution()
支持复制 Markdown
支持下载 Markdown
```

脚本：

```text
scripts/check-phase32-map-object-solution.sh
```

文档：

```text
docs/phase32-map-object-solution.md
```

---

## 13. 验收标准

### 13.1 病害对象

```text
点击病害对象
点击生成处置建议
返回内容包含：
- 路线
- 桩号
- 病害类型
- 严重程度
- 成因判断
- 处置建议
- 优先级
```

### 13.2 评定结果对象

```text
点击评定结果
点击生成低分单元处置建议
返回内容包含：
- MQI / PQI / PCI
- 等级
- 低分原因
- 周边病害
- 处置策略
```

### 13.3 路线对象

```text
点击路线
点击生成路线报告草稿
返回内容包含：
- 路线概况
- 技术状况
- 病害分布
- 低分区段
- 养护建议
```

### 13.4 前端体验

```text
弹出预览框
Markdown 可读
支持复制
支持下载 .md
质量检查结果可见
```

---

## 14. 推荐实施顺序

```text
1. 后端 DTO / 枚举
2. 后端 Service 模板生成
3. 后端质量检查
4. Controller 接口
5. 前端 API
6. SolutionPreviewDialog
7. AgentChatFloat 按钮接入
8. 验收脚本
9. 文档
```
