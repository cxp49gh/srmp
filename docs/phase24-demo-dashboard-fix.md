# 阶段二十四：演示看板无数据显示修复

## 修复内容

1. 后端自动探测 `default/demo` 哪个租户有演示数据。
2. `/api/demo/status` 和 `/api/demo/dashboard` 返回 `requestedTenantId`、`tenantId`、`availableTenants`。
3. 路线统计 SQL 改为子查询，避免多表 join 导致统计放大。
4. 前端页面展示实际读取租户、数据计数、接口错误和空数据提示。
5. 新增调试脚本 `scripts/debug-demo-dashboard.sh`。

## 验证

```bash
./scripts/debug-demo-dashboard.sh
TENANT_ID=demo ./scripts/debug-demo-dashboard.sh
```

## 页面

```text
http://localhost:5173/demo/dashboard
```

右上角可切换 `default` / `demo`。