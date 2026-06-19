# Phase54 AI 证据来源绑定与地图定位治理

Phase54 为一张图 AI 的业务数据、知识、Outline 和模板来源建立统一绑定契约。前端不再根据 `sourceId`、路线字段或当前地图选择猜测位置；只有明确的对象或范围绑定才能发起定位，并且点击后必须在当前租户、当前数据管理项目内验证。

## 来源绑定契约

每条来源统一包含：

- `bindingType`：`OBJECT`、`RANGE` 或 `NONE`；
- `bindingOrigin`：`BUSINESS_QUERY`、`EXPLICIT_METADATA` 或 `NONE`；
- `bindingStatus`：`UNVERIFIED`、`VALID`、`NOT_FOUND` 或 `INVALID`；
- `bindingReason`：绑定判定或验证原因；
- `mapTarget`：仅 `OBJECT/RANGE` 来源允许携带；
- `followupContext`：来源追问使用的唯一标准上下文。

状态含义：

- `OBJECT/RANGE + UNVERIFIED`：来源字段完整，可以点击；点击后按当前项目验证。
- `VALID`：对象或范围已在当前项目验证通过。
- `NOT_FOUND`：当前项目中对象已变更、不存在，或范围内没有业务数据。
- `INVALID`：字段组合非法，或路线、桩号与真实对象冲突。
- `NONE + VALID`：仅参考资料，不提供地图定位。

历史 Trace 或历史消息缺少绑定契约时，前端显示“历史记录未标准化”，默认不可定位。

## GIS 验证接口

`POST /api/gis/source-binding/verify`

请求示例：

```json
{
  "projectId": "project-1",
  "bindingType": "OBJECT",
  "mapTarget": {
    "objectType": "DISEASE",
    "objectId": "disease-1",
    "routeCode": "Y016140727",
    "startStake": 1.2,
    "endStake": 1.25
  }
}
```

响应数据：

```json
{
  "bindingStatus": "VALID",
  "bindingReason": "来源对象已在当前项目验证",
  "resolvedTarget": {
    "objectType": "DISEASE",
    "objectId": "disease-1",
    "routeCode": "Y016140727",
    "startStake": 1.2,
    "endStake": 1.25
  },
  "recommendedLayer": "disease",
  "matchedCount": 1
}
```

接口要求有效的租户上下文和 `projectId`。对象验证只接受精确对象 ID，不按当前选择或相似路线回退；范围验证支持路线桩号、GeoJSON geometry 和 bbox。

## 前端行为

1. 证据卡片按绑定状态展示“地图对象”“地图范围”“待验证”“仅参考资料”等状态。
2. `NONE`、`NOT_FOUND`、`INVALID` 和无契约历史来源不显示可用定位动作。
3. 点击定位先调用验证接口。
4. 验证通过后只使用 `resolvedTarget`，并按 `recommendedLayer` 加载图层和定位。
5. 验证失败时展示 `bindingReason`，不回退到当前地图对象。
6. 来源追问原样发送 `followupContext`，前端不重新拼装对象、路线或桩号。

## Agent 与 E2E 约束

- 普通知识、Outline 和模板来源默认 `NONE + VALID`。
- 知识或 Outline 只有显式 metadata 才能建立地图绑定。
- 纯资料追问只允许计划和执行 `knowledge.retrieve`。
- 病害对象来源追问应计划并执行 `gis.queryNearbyObjects`。
- E2E 验收会检查每条来源的绑定契约，并记录来源追问的计划工具和实际工具。

## 回归命令

```bash
cd srmp-ai-orchestrator
.venv/bin/python -m unittest discover -s tests -v

cd ..
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
PATH=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin:$PATH \
mvn -pl srmp-gis,srmp-agent -am test

cd srmp-web-ui
node tests/mapAiWorkbenchSplit.test.mjs
node tests/aiTracesPage.test.mjs
node --experimental-strip-types tests/gisUnifiedContextSource.test.mjs
npm run build
```

真实环境验收：

```bash
cd srmp-ai-orchestrator
./scripts/check-map-agent-e2e.sh
```

## 手工验收

1. 普通知识资料只显示“仅参考资料”，点击追问后不调用 GIS 工具。
2. 业务对象来源点击定位时，浏览器先请求 `/api/gis/source-binding/verify`。
3. 删除对象或切换项目后，定位提示“对象已变更或不存在”，地图不跳到当前选中对象。
4. 路线桩号、geometry 和 bbox 来源验证通过后按返回范围定位。
5. Trace 的来源行显示绑定类型、绑定来源、状态和原因。
6. 历史无契约来源显示“历史记录未标准化”，且不可定位。

## 排障提示

- `NOT_FOUND`：确认来源对象仍属于当前项目，或范围内仍有业务数据。
- `INVALID`：检查对象类型、对象 ID、路线编码、桩号顺序和 GeoJSON/bbox 结构。
- 纯资料追问出现 GIS 工具：检查 `followupContext.bindingType` 是否被错误改成 `OBJECT/RANGE`。
- 前端验证通过但未定位：检查 `recommendedLayer` 对应图层是否成功加载，以及 `resolvedTarget` 是否能匹配图层要素。
