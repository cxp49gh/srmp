#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

echo "==> 编译后端"
mvn clean package -DskipTests

echo "==> 启动后端"
java -jar srmp-admin/target/srmp-admin-1.0.0.jar
