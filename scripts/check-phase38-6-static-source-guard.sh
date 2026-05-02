#!/usr/bin/env bash
set -euo pipefail
echo "==> 静态检查系统兜底模板重复风险"
if grep -R "## 六、引用来源" srmp-agent/src/main/java srmp-web-ui/src; then
  echo "[FAIL] 发现硬编码“## 六、引用来源”，请确认不是在 fallback content 中内嵌引用来源。"; exit 1
fi
echo "[OK] 未发现代码中硬编码“## 六、引用来源”"
