#!/usr/bin/env bash
set -euo pipefail

"$(dirname "$0")/check-demo-data.sh"
echo ""
"$(dirname "$0")/check-demo-ai.sh"

echo ""
echo "[OK] 演示数据 + AI 联调检查完成"