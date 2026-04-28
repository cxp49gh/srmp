#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

echo "==> 检查关键源码是否存在"
required_files=(
  "srmp-agent/src/main/java/com/smartroad/srmp/agent/service/impl/AgentChatServiceImpl.java"
  "srmp-agent/src/main/java/com/smartroad/srmp/agent/map/MapObjectContext.java"
  "srmp-agent/src/main/java/com/smartroad/srmp/agent/map/MapObjectContextService.java"
  "srmp-agent/src/main/java/com/smartroad/srmp/agent/map/MapObjectContextServiceImpl.java"
  "srmp-web-ui/src/views/gis/OneMap.vue"
  "srmp-web-ui/src/views/gis/components/AgentChatFloat.vue"
  "srmp-gis/src/main/java/com/smartroad/srmp/gis/controller/GisMapController.java"
  "srmp-admin/src/main/java/com/smartroad/srmp/demo/controller/DemoDataController.java"
)
for f in "${required_files[@]}"; do
  test -f "$f" || { echo "[FAIL] 缺少关键文件：$f"; exit 1; }
done

AGENT="srmp-agent/src/main/java/com/smartroad/srmp/agent/service/impl/AgentChatServiceImpl.java"
MAP_IMPL="srmp-agent/src/main/java/com/smartroad/srmp/agent/map/MapObjectContextServiceImpl.java"
MAP_CTX="srmp-agent/src/main/java/com/smartroad/srmp/agent/map/MapObjectContext.java"

echo "==> 检查后端地图上下文能力"
grep -q "mapObjectContextService" "$AGENT" || { echo "[FAIL] AgentChatServiceImpl 未使用 mapObjectContextService"; exit 1; }
grep -Eq "resolve\(buildContextMap\(request\)\)|resolveRawMapObject|enrichMapObject" "$AGENT" || { echo "[FAIL] AgentChatServiceImpl 未发现地图上下文解析逻辑"; exit 1; }
grep -q "sanitizeAssistantAnswer" "$AGENT" || echo "[WARN] AgentChatServiceImpl 未发现 sanitizeAssistantAnswer，可能仍会暴露 <think>"
grep -Eq "MAP_OBJECT_LOCAL|buildSingleDiseaseObjectAnswer|isSingleDiseaseObject" "$AGENT" || echo "[WARN] 未发现单病害对象本地分析逻辑，点单个病害可能仍输出路线级统计"
grep -q "基于当前地图对象" "$AGENT" || echo "[WARN] answer 可能没有地图对象前缀"

echo "==> 检查 MapObjectContextServiceImpl 编译风险"
if grep -q "List>" "$MAP_IMPL"; then
  echo "[FAIL] MapObjectContextServiceImpl 存在 List> 泛型错误"
  exit 1
fi
grep -q "NamedParameterJdbcTemplate" "$MAP_IMPL" || echo "[WARN] MapObjectContextServiceImpl 未看到 NamedParameterJdbcTemplate"
grep -Eq "object_type|objectType" "$MAP_IMPL" || echo "[WARN] MapObjectContextServiceImpl 未看到 objectType/object_type 处理"

echo "==> 检查 MapObjectContext 字段兼容"
grep -q "boolean present" "$MAP_CTX" || echo "[WARN] MapObjectContext 未看到 present 字段"
grep -Eq "object_type|objectType|firstString" "$MAP_CTX" || echo "[WARN] MapObjectContext 可能不兼容下划线字段"

echo "[OK] 源码一致性检查完成"
