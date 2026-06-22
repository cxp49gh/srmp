# AI 证据来源 GIS 冒烟闭环修复设计

## 1. 背景

目标环境已经完成更新，并针对以下链路执行了真实冒烟验证：

```text
AI 证据来源 → 点击定位 → GIS 校验 → 地图定位 → 追问来源上下文
```

来源追问已经能够回传原始来源上下文并调用预期工具，但定位链路暴露出三个阻塞点：

1. 评定结果来源携带了被评定路段的 `object_id`，而不是 `assessment_result.id`。
2. 病害来源通过 GIS 校验后，前端重新加载病害图层时没有携带 bbox，病害接口按约定返回空图层。
3. 路线统计来源只有 `routeCode`，无法满足严格对象绑定或路线桩号范围绑定契约。

本设计在不放宽严格来源绑定契约的前提下修复三处问题，并让路线统计定位时临时高亮整条统计路线。

## 2. 目标

1. 评定结果来源绑定到真实 `assessment_result.id`。
2. 病害对象通过验证后能够加载对象附近的病害明细并定位。
3. 路线统计来源绑定到当前项目中的真实路线对象。
4. 点击路线统计来源后，地图适配并临时高亮整条路线。
5. 来源追问继续使用同一份标准化 `followupContext`，不引入定位与追问两套标识。
6. 自动验收和浏览器冒烟覆盖完整链路。

## 3. 非目标

- 不允许仅凭 `routeCode` 直接生成可定位范围。
- 不允许在对象查找失败时回退到当前地图选择或当前视窗。
- 不取消病害图层的 bbox 保护，也不开放无范围的病害全表查询。
- 不把路线统计伪装成病害、评定结果或任意路段对象。
- 不新增长期锁定地图图层的交互。
- 不修改业务数据以绕过来源绑定错误。

## 4. 已确认根因

### 4.1 评定结果标识选错

`gis.queryAssessmentResults` 的结果同时包含：

- `id`：评定结果行 ID；
- `object_type`：被评定对象类型；
- `object_id`：被评定对象 ID。

来源标准化当前优先读取 `object_id`，同时将来源类型设为 `ASSESSMENT_RESULT`。因此 GIS 校验会把路段 ID 当作评定结果 ID 查询并返回 `NOT_FOUND`。

目标环境样本中：

- 错误来源 ID：`9705e534c19f467ab6e68b2731b9bda3`，实际为 `ROAD_SECTION_LINE` ID；
- 正确评定结果 ID：`5117a85290ca49e48d740719de5791bc`；
- 使用正确 ID 调用现有验证接口已返回 `VALID`。

因此本问题只需要修正来源标准化，不需要放宽 GIS 校验。

### 4.2 病害图层缺少 bbox

病害图层接口要求合法 bbox，并按缩放级别返回 summary、cluster 或 detail。前端从来源定位病害时只传递项目、路线、桩号和 `zoom=17`，没有传 bbox。

接口因此返回：

```text
请移动地图到有效范围后查看病害数据
```

GIS 对象校验已经能确认病害存在，但验证结果目前不包含对象 geometry，前端也就无法构造对象附近的查询范围。

### 4.3 路线统计缺少权威地图目标

`gis.queryRegionSummary` 在路线场景返回的 `queryScope` 只有：

```json
{
  "projectId": "...",
  "routeCode": "C001140727"
}
```

严格绑定契约要求：

- 对象绑定必须有 `objectType + objectId`；
- 路线范围绑定必须有 `routeCode + startStake + endStake`。

因此当前输出会产生一个仅参考统计来源和一个不完整的范围来源，二者都不能通过“业务来源必须可定位”的验收。

## 5. 方案比较

### 5.1 病害定位

#### 方案 A：验证接口返回对象 geometry，前端由 geometry 构造 bbox

优点：

- 对象定位仍以 GIS 校验后的权威数据为准；
- 保留病害接口的 bbox 防护；
- geometry 同时可用于准确地图定位；
- 不需要额外对象详情请求。

缺点：

- GIS 验证响应和前端查询参数构造都需要扩展。

#### 方案 B：病害图层支持 `objectId` 且对象查询不要求 bbox

优点：

- 前端改动较少。

缺点：

- 在视窗图层接口中增加特殊绕过；
- 容易让对象查询与常规图层查询的边界变模糊。

#### 方案 C：验证后调用对象详情接口获取 geometry

优点：

- 验证接口保持不变。

缺点：

- 增加一次网络请求；
- 当前对象详情接口也不返回 geometry；
- 校验与定位数据可能来自不同查询。

选择方案 A。

### 5.2 路线统计绑定

#### 方案 A：统计工具解析真实路线对象，输出权威路线目标

优点：

- 来源直接绑定当前项目中的路线 ID；
- 点击后可以复用已有路线图层加载、校验和定位能力；
- 不需要放宽范围契约。

缺点：

- 统计工具需要增加一次轻量路线解析查询。

#### 方案 B：只补齐路线起止桩号，生成路线范围绑定

优点：

- 仍可通过范围校验。

缺点：

- 前端会更倾向于路段范围表达；
- 无法稳定表达“统计对象是整条路线”。

#### 方案 C：允许 `routeCode` 单字段定位

优点：

- 改动最小。

缺点：

- 破坏严格绑定契约；
- 在多项目、重复路线编码或数据变更时容易误定位。

选择方案 A。

## 6. 详细设计

### 6.1 评定来源标准化

编排器为评定业务结果选择对象 ID 时遵循：

1. `gis.queryAssessmentResults` 和附近对象结果中的评定记录优先使用行字段 `id`；
2. `object_id` 保留在 `raw` 中，表示被评定对象，不提升为 `ASSESSMENT_RESULT.objectId`；
3. 输出的 `sourceId`、`mapTarget.objectId` 和 `followupContext.mapTarget.objectId` 使用同一个评定结果 ID；
4. 路线和桩号继续作为对象绑定的辅助校验字段。

普通病害和其他明确对象结果保持现有标识规则。

### 6.2 病害验证与定位

GIS 来源验证查询病害对象时，同时读取病害 geometry。验证成功后：

```json
{
  "bindingStatus": "VALID",
  "resolvedTarget": {
    "objectType": "DISEASE",
    "objectId": "...",
    "routeCode": "...",
    "startStake": 0,
    "endStake": 0,
    "geometry": {}
  },
  "recommendedLayer": "disease"
}
```

前端处理流程：

1. 只使用 `resolvedTarget`，不重新信任原始来源字段；
2. 从验证后的 geometry 计算 bbox；
3. 对 Point 或退化 bbox 增加小范围 padding，保证 `min < max`；
4. 使用 bbox、路线、桩号和明细缩放级别重新加载病害图层；
5. 按对象 ID 匹配并高亮图层对象；
6. 若图层暂时未返回对象，仍可使用验证后的 geometry 显示来源高亮，不回退到其他对象。

geometry 到 bbox 的转换放在独立工具函数中，便于无浏览器单元测试。

### 6.3 路线统计权威目标

`gis.queryRegionSummary` 在没有 geometry、但存在项目和路线编码时：

1. 按租户、项目、路线编码查询唯一有效 `road_route`；
2. 将真实路线 ID、路线编码及起止桩号写入工具返回的权威查询范围；
3. 编排器将区域统计标准化为一个业务来源：

```json
{
  "sourceTitle": "区域统计｜C001140727",
  "bindingType": "OBJECT",
  "bindingOrigin": "BUSINESS_QUERY",
  "bindingStatus": "UNVERIFIED",
  "mapTarget": {
    "objectType": "ROAD_ROUTE",
    "objectId": "...",
    "routeCode": "C001140727",
    "startStake": 0,
    "endStake": 10.972
  }
}
```

路线统计不再同时产生一个不可定位的 summary 来源和一个重复 scope 来源。

区域 geometry 场景保持 `MAP_REGION + RANGE`，优先使用真实 geometry，不强制转换为路线对象。

若路线无法在当前项目解析：

- 不伪造对象 ID；
- 输出不可定位来源并保留明确原因；
- 自动验收继续失败，从而暴露数据或项目上下文问题。

### 6.4 路线统计临时高亮

路线统计验证成功后复用现有路线图层：

1. 加载当前项目和路线编码对应的路线图层；
2. 按验证后的路线对象 ID 精确匹配；
3. 地图适配整条路线范围；
4. 使用来源定位高亮样式描边整条路线；
5. 在反馈信息中说明这是统计范围；
6. 点击其他来源、选择其他地图对象、切换项目或清除选择时恢复原样。

高亮只表达“本次统计覆盖范围”，不锁定图层，也不改变当前路线数据。

## 7. 数据流

```text
Java GIS 工具真实结果
  → 编排器来源标准化
  → AI 回答 source / followupContext
  → 用户点击来源
  → POST /api/gis/source-binding/verify
  → resolvedTarget
  → 加载目标图层
  → 精确匹配并临时高亮
  → 追问时回传同一 followupContext
```

三个场景的关键差异：

- 评定结果：标准化阶段选择正确评定行 ID；
- 病害：验证阶段补充 geometry，加载阶段计算 bbox；
- 路线统计：工具阶段解析真实路线对象，标准化为路线对象来源。

## 8. 错误处理

- 评定结果缺少行 `id`：不使用被评定对象 `object_id` 冒充评定结果 ID。
- 病害 geometry 为空：验证仍可返回对象有效，但前端提示无法获取精确空间位置，不执行无 bbox 查询。
- geometry 非法或无法计算 bbox：停止图层加载并显示验证后的定位失败原因。
- 路线统计解析不到唯一项目路线：不回退到仅 routeCode 定位。
- 项目切换：清理来源验证缓存和来源高亮。
- 图层加载失败：保留 AI 来源内容和追问能力，显示图层加载错误。

## 9. 测试设计

### 9.1 编排器单元测试

- 评定工具结果同时含 `id` 与 `object_id` 时，绑定必须使用 `id`。
- 评定来源的 `sourceId`、`mapTarget` 和 `followupContext` ID 一致。
- 路线统计带权威路线范围时只输出一个 `ROAD_ROUTE` 对象来源。
- geometry 区域统计仍输出 `MAP_REGION + RANGE`。
- 路线统计只有未解析 routeCode 时不能被误判为可定位。

### 9.2 Java 单元测试

- 病害对象验证 SQL 读取 geometry，响应 `resolvedTarget` 包含合法 GeoJSON。
- 路线统计工具按项目和 routeCode 解析真实路线 ID、起止桩号。
- 无路线、重复或无效路线时不生成伪造对象目标。
- 既有对象项目归属和严格 ID 校验保持通过。

### 9.3 前端工具测试

- Point geometry 能生成带 padding 的合法 bbox。
- LineString、Polygon 和 Multi* geometry 能生成正确 bbox。
- 非法坐标不生成查询 bbox。
- 来源目标查询保留 projectId、routeCode、桩号，并写入 geometry bbox。

### 9.4 自动冒烟

运行：

```bash
./scripts/check-map-agent-e2e.sh --case map.route_analysis --json --fail-fast
```

验收：

- 所有业务来源均满足严格可定位契约；
- 评定来源 ID 可通过 GIS 校验；
- 路线统计来源可通过 GIS 校验；
- 必需工具全部成功。

### 9.5 浏览器冒烟

在目标环境按顺序验证：

1. 发起路线 AI 分析；
2. 点击病害来源；
3. 确认验证请求成功、病害请求包含 bbox、地图定位并高亮病害；
4. 点击评定来源；
5. 确认使用真实评定结果 ID、验证成功并定位；
6. 点击路线统计来源；
7. 确认整条路线适配并临时高亮；
8. 点击“追问来源”；
9. 确认回答回显原始来源 ID、类型、路线和桩号；
10. 确认浏览器无控制台错误。

## 10. 完成标准

- 自动测试全部通过。
- `map.route_analysis` 自动冒烟通过。
- 浏览器完整链路通过。
- 目标环境更新后服务健康。
- 不修改目标环境业务数据。
- 不降低来源绑定校验严格度。
