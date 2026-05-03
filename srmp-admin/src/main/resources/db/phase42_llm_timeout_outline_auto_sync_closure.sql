-- Phase42：AI 请求超时配置 + Outline 自动同步/Webhook 收口
-- 说明：
-- 1. AI/Outline HTTP 超时为应用配置项，无需数据库字段。
-- 2. 本脚本补齐自动同步运行锁与运维查询需要的索引，幂等可重复执行。

create index if not exists idx_outline_auto_sync_config_due
    on outline_auto_sync_config(enabled, next_run_at, status, updated_at);

create index if not exists idx_outline_auto_sync_config_webhook
    on outline_auto_sync_config(tenant_id, webhook_enabled, webhook_secret, collection_id, updated_at desc);

create index if not exists idx_outline_auto_sync_run_config_created
    on outline_auto_sync_run(tenant_id, config_id, created_at desc);

create index if not exists idx_outline_auto_sync_run_doc
    on outline_auto_sync_run(tenant_id, outline_document_id, created_at desc);

comment on table outline_auto_sync_config is 'Outline 自动同步配置：Phase42 增强运行锁、Webhook 精准同步与向量化闭环';
comment on table outline_auto_sync_run is 'Outline 自动同步运行记录：Phase42 记录 schedule/manual/webhook 触发与向量化状态';
