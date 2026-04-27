#!/usr/bin/env bash
set -euo pipefail

grep -q "sanitizeAssistantAnswer" srmp-agent/src/main/java/com/smartroad/srmp/agent/service/impl/AgentChatServiceImpl.java
grep -q "buildSingleDiseaseObjectAnswer" srmp-agent/src/main/java/com/smartroad/srmp/agent/service/impl/AgentChatServiceImpl.java
grep -q "MAP_OBJECT_LOCAL" srmp-agent/src/main/java/com/smartroad/srmp/agent/service/impl/AgentChatServiceImpl.java
grep -q "【基于当前地图对象】" srmp-agent/src/main/java/com/smartroad/srmp/agent/service/impl/AgentChatServiceImpl.java
grep -q "static MapObjectContext of" srmp-agent/src/main/java/com/smartroad/srmp/agent/map/MapObjectContext.java

echo "[OK] 阶段二十八地图对象回答质量修复代码已存在"

BASE_URL="${BASE_URL:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-default}"

if command -v curl >/dev/null 2>&1; then
  echo "==> 可选接口检查：POST /api/agent/chat"
  curl -s -X POST "$BASE_URL/api/agent/chat"     -H "Content-Type: application/json"     -H "X-Tenant-Id: $TENANT_ID"     -d '{
      "message":"分析当前地图选中对象，说明主要问题、成因判断，并给出养护处置建议",
      "context":{"routeCode":"G210","year":2026},
      "mapObject":{
        "objectType":"DISEASE",
        "routeCode":"G210",
        "startStake":69.007,
        "endStake":69.034,
        "diseaseName":"修补损坏",
        "severity":"MEDIUM",
        "quantity":11.02,
        "measureUnit":"m2",
        "year":2026
      },
      "options":{"useBusinessData":true,"useKnowledge":false,"useOutline":false,"topK":5}
    }' | python3 -m json.tool | head -120 || true
fi
