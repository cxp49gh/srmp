# 阶段二十九：AI/GIS 收口完整替换文件包

基于 GitHub main `3d946e0` 的源码问题，提供可直接替换的完整文件。

## 替换文件

```text
srmp-agent/src/main/java/com/smartroad/srmp/agent/service/impl/AgentChatServiceImpl.java
srmp-agent/src/main/java/com/smartroad/srmp/agent/map/MapObjectContext.java
srmp-agent/src/main/java/com/smartroad/srmp/agent/map/MapObjectContextService.java
srmp-agent/src/main/java/com/smartroad/srmp/agent/map/MapObjectContextServiceImpl.java
srmp-web-ui/src/views/gis/OneMap.vue
srmp-web-ui/src/views/gis/components/AgentChatFloat.vue
srmp-web-ui/src/api/agent.ts
```

## 修复点

1. 修复 `MapObjectContextServiceImpl` 中 `List>` 泛型错误。
2. 统一地图对象解析入口为 `mapObjectContextService.resolve(...)`。
3. `MapObjectContext` 兼容驼峰字段和下划线字段。
4. 后端统一过滤 `<think>...</think>` 和 `<thinking>...</thinking>`。
5. 单个 `DISEASE` 地图对象优先返回单病害对象分析，不再展开为整条路线 TOP10 聚合。
6. 前缀统一为 `【基于当前地图对象】` / `【当前地图选中对象】`。
7. `OneMap.vue` 使用 `selectedMapObject` 作为 `AgentChatFloat` 的 `mapObject`。
8. `selectedMapObject` 合并 `selectedFeatureProperties + selectedDetail`，避免详情覆盖导致病害名、评分、桩号丢失。
9. `AgentChatFloat.vue` 的标题与上下文卡片优先显示当前对象标签，不再只显示 G210。
10. `AgentChatFloat.vue` 简单支持 Markdown 风格展示。

## 使用方式

```bash
unzip srmp-phase29-replacement-files.zip -d /tmp/srmp-phase29
cp -r /tmp/srmp-phase29/srmp-phase29-replacement-files/* /path/to/srmp/

cd /path/to/srmp
mvn clean package -DskipTests
npm --prefix srmp-web-ui run build
```

## 验收

1. 点击病害对象后，当前地图上下文应显示：`病害｜病害名称｜严重程度｜G210｜K...`。
2. 请求 `/api/agent/chat` 的 body 应包含 `mapObject.objectType=DISEASE`、桩号、病害名、严重程度。
3. 返回内容不应出现 `<think>`。
4. 单个病害不应再回答成“G210 全线病害总数 / TOP10 热点”。
5. 返回应包含 `【基于当前地图对象】`。
