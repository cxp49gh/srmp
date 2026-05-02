#!/usr/bin/env bash
set -euo pipefail

echo "==> Maven package"
mvn clean package -DskipTests

echo "==> Frontend build"
npm --prefix srmp-web-ui run build

echo "[OK] Phase38.5 build check passed"
