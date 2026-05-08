# 历史：曾将分片 SQL 合并为 srmp_full_init.sql；分片已删除，DDL/种子仅维护单一文件。
# 用法：在仓库根目录执行  powershell -ExecutionPolicy Bypass -File scripts/rebuild-srmp-full-init.ps1
$ErrorActionPreference = 'Stop'
Write-Host '[INFO] 数据库初始化已收敛为: srmp-admin/src/main/resources/db/srmp_full_init.sql' -ForegroundColor Cyan
Write-Host '[INFO] 请直接编辑并提交该文件；本脚本不再执行合并。' -ForegroundColor Cyan
