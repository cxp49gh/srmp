# 阶段二十六修复版：地图对象 AI 上下文闭环

## 1. 修复背景

最新代码中已经有 `AgentChatRequest.mapObject`、`AgentChatFloat.mapObject` 参数、`MapObjectContextService` 接口，但闭环不完整。

## 2. 本修复内容

```text
1. 新增 MapObjectContextServiceImpl；
2. 支持 ROAD_ROUTE / ROAD_SECTION / EVALUATION_UNIT / DISEASE / ASSESSMENT_RESULT；
3. AgentChatServiceImpl 注入 MapObjectContextService；
4. 自动解析 request.mapObject / context.mapObject / context.selectedMapObject / context.selected；
5. 将地图对象详情拼入 RAG 业务上下文；
6. response.data 返回 mapObjectUsed / mapObject / mapObjectContext；
7. answer 增加【基于当前地图对象】前缀；
8. 提供前端辅助修复脚本；
9. 提供验收脚本。
```

## 3. 应用方式

```bash
git apply srmp-phase26-map-ai-context-fix.patch
```

如果 `AgentChatServiceImpl.java` 冲突，可直接使用完整文件包中的同名文件覆盖。

如果前端文件冲突：

```bash
chmod +x scripts/*.sh
./scripts/apply-phase26-map-ai-context-fix.sh
```

## 4. 验收

```bash
./scripts/check-map-ai-context-fix.sh
```

前端：

```text
1. 打开 /gis/one-map
2. 点击路线、路段、评定单元、病害或评定结果
3. AI 浮窗显示“当前地图上下文”
4. 提问：分析当前对象
5. 响应中 data.mapObjectUsed=true
```
