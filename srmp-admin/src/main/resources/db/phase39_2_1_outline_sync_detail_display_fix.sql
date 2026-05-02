-- Phase39.2.1：Outline 同步明细字段增强
--
-- 解决：
-- 1. 明细缺少 url、updatedAt、contentHash、contentChars、oldHash 等定位信息；
-- 2. 前端展示失败时只有 error_message，不知道是哪个文档、什么内容、是否内容变化；
-- 3. 后端返回 snake_case，前端字段混用导致展示错位。

ALTER TABLE outline_sync_task_detail
ADD COLUMN IF NOT EXISTS outline_url VARCHAR(1000),
ADD COLUMN IF NOT EXISTS outline_updated_at VARCHAR(80),
ADD COLUMN IF NOT EXISTS content_chars INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64),
ADD COLUMN IF NOT EXISTS old_content_hash VARCHAR(64),
ADD COLUMN IF NOT EXISTS detail_message TEXT,
ADD COLUMN IF NOT EXISTS document_status VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_outline_sync_task_detail_status_action
ON outline_sync_task_detail(tenant_id, task_id, status, action, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_outline_sync_task_detail_hash
ON outline_sync_task_detail(tenant_id, content_hash);
