#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ONE_MAP="$ROOT/srmp-web-ui/src/views/gis/OneMap.vue"
AGENT="$ROOT/srmp-web-ui/src/views/gis/components/AgentChatFloat.vue"

need() {
  local file="$1"
  local pattern="$2"
  local message="$3"
  if ! grep -q "$pattern" "$file"; then
    echo "[FAIL] $message" >&2
    exit 1
  fi
}

need "$ONE_MAP" "function isBlankQueryValue" "OneMap 缺少空查询值过滤"
need "$ONE_MAP" "Object.entries(query)" "OneMap layerQuery 未统一清理查询参数"
need "$ONE_MAP" "next\[key\] = typeof value === 'string' ? value.trim() : value" "OneMap layerQuery 未 trim 查询条件"
need "$ONE_MAP" "query.indexCode || 'MQI'" "OneMap 指标渲染缺少默认 MQI 兜底"
need "$AGENT" "objectScopeMetricItems" "AI 一张图分析缺少对象范围统计"
need "$AGENT" "type === 'DISEASE' ? '1'" "病害对象统计未按单对象收口"
need "$AGENT" "type === 'EVALUATION_UNIT' ? '1'" "评定单元对象统计未按单对象收口"
need "$AGENT" "type === 'ASSESSMENT_RESULT' ? '1'" "评定结果对象统计未按单对象收口"
need "$AGENT" "hasSectionContext" "路段上下文识别缺失"

echo "[OK] Phase49J 查询空条件与对象范围统计结构检查通过"
