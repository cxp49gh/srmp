# AI 证据来源绑定与地图定位治理设计

## 1. 背景

一张图 AI 已能够在路线、路段、病害、评定结果、区域分析及方案生成中返回业务数据与知识资料，并支持来源追问和地图定位。

当前主要风险不再是“没有来源”，而是来源语义不够严格：

- 只要来源携带 `routeCode`，前端就可能将其判断为可定位，但当前项目未必存在对应对象。
- 方案生成阶段可能将当前请求对象的位置补到缺少位置的来源摘要，导致通用知识资料看起来像某个业务对象的直接证据。
- 后端、编排器和前端分别推断定位字段，容易产生不同判断。
- 来源追问没有完全按来源绑定类型约束工具，纯知识资料可能触发不必要的业务查询。
- 管理员能够看到工具和来源，但还不能快速判断绑定来自哪里、是否有效、为何定位失败。

本设计将来源绑定收敛为统一契约，严格区分业务对象、业务范围和纯参考资料。

## 2. 设计目标

1. 所有 AI 来源使用同一份来源绑定契约。
2. 只有明确、合法的定位信息才能显示地图定位操作。
3. 通用知识资料不得继承当前地图对象的位置。
4. 点击定位时验证来源在当前项目中的有效性。
5. 来源追问根据绑定类型选择能力和工具。
6. 用户界面明确表达来源可定位程度和失效原因。
7. 管理员执行过程可以查看绑定来源、状态和验证结果。
8. 路线、路段、病害、评定、区域及六类方案生成形成统一回归。

## 3. 非目标

- 本阶段不建设独立的证据管理中心。
- 本阶段不为所有历史执行记录离线补齐来源绑定。
- 本阶段不将方案模板默认绑定到地图对象。
- 本阶段不通过模糊路线名称、标题关键词或当前视窗猜测定位对象。
- 本阶段不保留“缺少定位信息时回退到当前对象”的老逻辑。

## 4. 已确认的业务边界

### 4.1 知识资料定位

知识文档只有显式配置以下任意一组元数据时才允许定位：

- `objectType + objectId`
- `routeCode + startStake/endStake`
- `geometry`
- `bbox`

否则统一作为“仅参考资料”。

### 4.2 禁止继承当前对象

方案模板、知识文档和普通来源摘要即使在分析某个对象时被引用，也不能自动继承当前对象的位置。

只有业务工具的真实查询结果可以绑定：

- 返回的具体业务对象；
- 工具实际使用的路线桩号范围；
- 工具实际使用的空间区域。

### 4.3 失效来源

来源定位字段合法，但当前项目中不存在对应对象时：

- 不回退到当前对象或当前地图范围；
- 保留来源内容供查看和复制；
- 状态标记为 `NOT_FOUND`；
- 用户界面显示“对象已变更或不存在”。

## 5. 统一来源契约

```json
{
  "sourceId": "string",
  "sourceType": "BUSINESS_DATA | KNOWLEDGE | OUTLINE | TEMPLATE",
  "sourceTitle": "string",
  "contentExcerpt": "string",
  "score": 0.92,
  "bindingType": "OBJECT | RANGE | NONE",
  "bindingOrigin": "BUSINESS_QUERY | EXPLICIT_METADATA | NONE",
  "bindingStatus": "VALID | NOT_FOUND | INVALID | UNVERIFIED",
  "bindingReason": "string",
  "mapTarget": {
    "objectType": "ROAD_ROUTE | ROAD_SECTION | EVALUATION_UNIT | DISEASE | ASSESSMENT_RESULT | MAP_REGION",
    "objectId": "string",
    "routeCode": "string",
    "startStake": 0,
    "endStake": 1.2,
    "geometry": {},
    "bbox": []
  },
  "followupContext": {
    "sourceId": "string",
    "sourceType": "string",
    "sourceTitle": "string",
    "contentExcerpt": "string",
    "bindingType": "OBJECT | RANGE | NONE",
    "bindingStatus": "VALID | NOT_FOUND | INVALID | UNVERIFIED",
    "mapTarget": {}
  },
  "metadata": {}
}
```

### 5.1 `bindingType`

| 类型 | 判定条件 | 用户含义 |
| --- | --- | --- |
| `OBJECT` | 同时存在合法 `objectType` 与 `objectId` | 定位具体对象 |
| `RANGE` | 存在 `routeCode + 桩号范围`，或 `geometry/bbox` | 定位业务范围 |
| `NONE` | 不满足对象或范围条件 | 仅参考资料 |

对象绑定优先于范围绑定。若同时存在对象编号和范围信息，`bindingType` 为 `OBJECT`，范围字段作为定位与校验辅助条件保留。

### 5.2 `bindingOrigin`

| 类型 | 来源 |
| --- | --- |
| `BUSINESS_QUERY` | GIS 业务工具真实查询结果或实际查询范围 |
| `EXPLICIT_METADATA` | Outline/知识文档自身显式配置的结构化元数据 |
| `NONE` | 未绑定地图 |

模板不因被某个对象使用而改变 `bindingOrigin`。模板只有自身包含显式定位元数据时才可使用 `EXPLICIT_METADATA`。

### 5.3 `bindingStatus`

| 状态 | 含义 |
| --- | --- |
| `UNVERIFIED` | 字段结构合法，尚未在当前项目验证 |
| `VALID` | 已在当前项目验证并可以定位 |
| `NOT_FOUND` | 字段合法，但当前项目中未找到对象或范围数据 |
| `INVALID` | 字段组合或值不合法 |

初次回答默认不为每条来源查询数据库。合法绑定返回 `UNVERIFIED`，用户点击定位时再验证。

## 6. 后端标准化设计

### 6.1 单一标准化组件

在编排器侧新增 `SourceBindingNormalizer`，职责仅包括：

1. 识别来源类别。
2. 读取来源自身和业务工具返回的定位字段。
3. 生成 `bindingType`、`bindingOrigin`、`bindingStatus` 和 `mapTarget`。
4. 从标准化来源生成 `followupContext`。
5. 输出明确的无效原因。

工作流、方案生成和执行记录统一调用该组件，不再分别推断来源绑定。

### 6.2 业务来源

业务工具来源允许读取：

- 工具结果项中的对象编号、对象类型、路线、桩号和空间字段；
- 工具请求实际使用的 `queryScope`；
- 区域工具实际使用的 `geometry/bbox`。

具体规则：

- 返回具体对象编号时生成 `OBJECT + BUSINESS_QUERY + UNVERIFIED`。
- 只有路线桩号或空间范围时生成 `RANGE + BUSINESS_QUERY + UNVERIFIED`。
- 业务统计摘要没有对象或范围时生成 `NONE`，不得使用当前请求上下文补齐。

### 6.3 知识与 Outline 来源

知识来源只读取文档入库时保存的显式元数据：

- `objectType/objectId`
- `routeCode/startStake/endStake`
- `geometry/bbox`

不得读取以下内容作为定位依据：

- 当前请求 `mapContext`；
- 当前选中对象；
- 当前路线筛选；
- 标题或正文中出现的路线、桩号文本；
- 生成方案使用的对象。

没有显式元数据时生成：

```text
bindingType = NONE
bindingOrigin = NONE
bindingStatus = VALID
```

这里的 `VALID` 表示“作为纯资料来源合法”，不是“可以地图定位”。

### 6.4 模板来源

模板蓝本和方案模板默认：

```text
sourceType = TEMPLATE
bindingType = NONE
bindingOrigin = NONE
bindingStatus = VALID
```

不得用生成对象的位置补齐模板来源。

### 6.5 清理现有隐式补齐

移除方案生成中对缺失定位来源执行 `_enrich_source_summary` 并补入请求 `mapTarget` 的行为。

若方案生成需要表达“方案针对当前对象”，应在方案结果的 `targetContext` 中表示，而不是伪造为来源绑定。

## 7. 定位验证接口

### 7.1 接口

```http
POST /api/gis/source-binding/verify
```

请求：

```json
{
  "projectId": "string",
  "bindingType": "OBJECT | RANGE",
  "mapTarget": {}
}
```

响应：

```json
{
  "bindingStatus": "VALID | NOT_FOUND | INVALID",
  "bindingReason": "string",
  "resolvedTarget": {},
  "recommendedLayer": "roadRoute | roadSection | disease | assessment",
  "matchedCount": 1
}
```

### 7.2 验证规则

`OBJECT`：

- 按 `projectId + objectType + objectId` 查询。
- 路线和桩号仅用于交叉校验，不作为对象编号不存在时的模糊回退。
- 对象存在但路线或桩号明显冲突时返回 `INVALID`。

`RANGE`：

- `geometry/bbox` 校验结构合法性。
- 路线范围按 `projectId + routeCode + startStake/endStake` 验证。
- 合法范围但没有业务数据时返回 `NOT_FOUND`。

`NONE`：

- 前端不调用验证接口。

### 7.3 成本控制

- 仅在用户点击定位时验证。
- 同一会话按 `projectId + sourceId + mapTarget` 缓存验证结果。
- 项目切换后清理缓存。
- 不对初次回答中的所有来源批量验证。

## 8. 前端交互设计

### 8.1 来源状态与动作

| 绑定与状态 | 展示 | 可用动作 |
| --- | --- | --- |
| `OBJECT + UNVERIFIED/VALID` | 地图对象信息 | 定位对象、追问来源、复制来源 |
| `RANGE + UNVERIFIED/VALID` | 路线桩号或区域范围 | 定位范围、追问来源、复制来源 |
| `NONE + VALID` | 仅参考资料 | 追问资料、复制来源 |
| `NOT_FOUND` | 对象已变更或不存在 | 追问资料、复制来源 |
| `INVALID` | 定位信息异常 | 追问资料、复制来源 |

### 8.2 定位流程

1. 根据 `bindingType` 判断是否可发起定位。
2. `UNVERIFIED` 时调用验证接口。
3. 验证为 `VALID` 后自动开启 `recommendedLayer`。
4. 使用 `resolvedTarget` 定向加载图层。
5. 定位并高亮地图对象或范围。
6. 具体对象定位成功后同步为当前选中对象。
7. 验证或加载失败时更新来源状态并显示明确原因。

不允许：

- 定位失败后回退到当前对象；
- 因当前图层筛选条件排除来源对象；
- 使用当前视窗猜测来源范围。

### 8.3 当前自动加载逻辑调整

现有“按来源对象类型自动开启图层并定向加载”的能力保留，但改为在验证成功后执行。

定向查询继续忽略当前页面的：

- 等级筛选；
- 病害类型和严重程度筛选；
- 当前视窗 bbox；
- 旧桩号筛选。

保留 `projectId`、指标与专题粒度等识别数据来源所需条件。

## 9. 来源追问设计

### 9.1 请求上下文

前端只发送标准化 `followupContext`，不重新猜测定位字段。

### 9.2 工具规划

| 绑定类型 | 规划规则 |
| --- | --- |
| `OBJECT` | 根据 `objectType` 选择对象分析能力和对应 GIS 工具 |
| `RANGE` | 根据路线桩号或 geometry 选择范围查询工具 |
| `NONE` | 只使用知识检索；除非用户问题明确要求分析当前地图对象 |
| `NOT_FOUND/INVALID` | 来源内容可用于知识解释，不作为当前业务事实触发对象查询 |

示例：

- 追问病害业务来源：允许 `gis.queryNearbyObjects + knowledge.retrieve`。
- 追问评定业务来源：允许评定和桩号范围病害工具。
- 追问路线范围来源：允许路线评定、病害和统计工具。
- 追问 PCI 规范文档：只允许 `knowledge.retrieve`。

## 10. 管理员可观测性

AI 执行过程和 `/agent/ai-traces` 来源详情增加：

- `bindingType`
- `bindingOrigin`
- `bindingStatus`
- `bindingReason`
- `recommendedLayer`
- 验证耗时
- 验证接口结果
- 追问实际采用的能力和工具

管理员应能直接区分：

- 业务工具真实返回的位置；
- 文档显式配置的位置；
- 无定位的纯资料；
- 对象已删除或项目不匹配；
- 定位字段冲突；
- 前端图层加载失败。

## 11. 数据与 Outline 元数据

Outline 文档结构化元数据允许增加可选字段：

```yaml
srmp:
  ragEnabled: true
  objectType: ASSESSMENT_RESULT
  objectId: assessment-1
  routeCode: Y016140727
  startStake: 0
  endStake: 0.1
```

约束：

- 不要求每篇文档配置地图绑定。
- 通用规范、指标解释和处置指南通常不配置地图绑定。
- 项目案例、专项调查记录和现场复核记录可显式配置。
- 同步时只解析明确字段，不从正文抽取路线与桩号作为定位信息。

## 12. 兼容与迁移

本次采用明确契约，不增加复杂兼容层。

迁移策略：

1. 新响应统一输出完整绑定字段。
2. 标准化器可以读取现有 `mapTarget/followupContext`，但必须重新判定 `bindingType` 与来源。
3. 前端以新字段为准；缺少新字段的历史记录按 `NONE + UNVERIFIED` 展示，不执行自动定位。
4. 不修改历史数据库记录；管理员查看历史 Trace 时明确标记“旧记录未标准化”。
5. 移除请求上下文自动补齐知识来源位置的逻辑。

## 13. 测试与验收

### 13.1 单元测试

- 来源类型与绑定类型判定。
- 对象、范围、纯资料和非法字段组合。
- 知识来源不得继承请求地图对象。
- 模板来源不得继承生成目标。
- `followupContext` 必须来自标准化来源。
- 前端状态与操作映射。
- 定向查询过滤隔离。

### 13.2 接口测试

- 五类 GIS 对象验证。
- 路线桩号范围验证。
- geometry/bbox 验证。
- 项目不匹配。
- 对象删除。
- 对象编号与路线冲突。

### 13.3 一张图真实 E2E

覆盖：

- 分析路线；
- 分析路段；
- 分析病害；
- 分析评定结果；
- 分析区域；
- 生成病害处置建议；
- 生成病害复核意见；
- 生成路段养护计划；
- 生成评定养护建议；
- 生成路线养护报告；
- 生成区域建议；
- 纯知识问答；
- 显式绑定 Outline 文档定位；
- 普通 Outline 文档仅参考；
- 来源对象失效；
- 来源追问工具规划。

每个用例至少验证：

- 来源分类正确；
- `bindingOrigin` 正确；
- 可定位来源能在当前项目找到对象或范围；
- 纯知识资料不出现定位操作；
- 追问能力和工具符合绑定类型；
- 回答引用来源与工具结果一致。

## 14. 实施顺序

1. 新增统一来源契约和标准化器。
2. 修正业务工具与知识来源标准化。
3. 移除方案来源隐式位置补齐。
4. 新增定位验证接口。
5. 前端按绑定状态渲染和验证定位。
6. 追问规划按绑定类型约束能力与工具。
7. Trace 增加绑定诊断。
8. 扩展真实 E2E 验收并完成全量回归。

## 15. 完成标准

- 通用知识文档不再显示为当前业务对象的地图来源。
- 所有“定位对象/定位范围”操作都经过严格绑定判定。
- 对象失效时明确提示，不发生模糊回退。
- 来源追问不会因纯资料触发无关 GIS 工具。
- 用户和管理员看到的来源状态一致。
- 分析与方案生成的全部真实 E2E 用例通过。
