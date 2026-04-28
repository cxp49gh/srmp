#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

APPLY="${1:-}"
ARCHIVE_DIR="docs/patch-history/phase31-archive"

echo "==> 检查仓库根目录补丁残留"

candidates=()
while IFS= read -r line; do
  candidates+=("$line")
done < <(
  {
    find . -maxdepth 1 -type f \( -name 'patch-*.py' -o -name 'phase*-*.md' -o -name 'srmp-phase*.patch' -o -name 'srmp-phase*.zip' \) -print
    find . -maxdepth 1 -type d \( -name 'srmp-*-fix' -o -name 'srmp-phase*-*' \) -print
  } | sed 's#^\./##' | sort
)

if [ "${#candidates[@]}" -eq 0 ]; then
  echo "[OK] 未发现明显补丁残留"
  exit 0
fi

echo "[WARN] 发现疑似补丁残留："
printf '  - %s\n' "${candidates[@]}"

if [ "$APPLY" != "--apply" ]; then
  echo
  echo "默认仅提示，不移动文件。确认后可执行："
  echo "  ./scripts/check-phase31-repo-clean.sh --apply"
  exit 1
fi

mkdir -p "$ARCHIVE_DIR"
for item in "${candidates[@]}"; do
  if [ -e "$item" ]; then
    echo "move $item -> $ARCHIVE_DIR/"
    mv "$item" "$ARCHIVE_DIR/"
  fi
done

echo "[OK] 已移动补丁残留到 $ARCHIVE_DIR"
