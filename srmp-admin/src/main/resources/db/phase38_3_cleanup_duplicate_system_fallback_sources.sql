-- Phase38.3：清理历史任务中重复的“系统兜底模板”引用来源。
-- 仅保留同一任务下第一条系统兜底模板来源。
WITH duplicated AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY tenant_id, task_id, coalesce(source_title, '系统兜底模板')
            ORDER BY created_at ASC, id ASC
        ) AS rn
    FROM ai_solution_source
    WHERE source_title LIKE '%兜底模板%'
       OR source_title LIKE '%系统兜底%'
       OR source_type = 'SYSTEM_TEMPLATE'
)
DELETE FROM ai_solution_source s
USING duplicated d
WHERE s.id = d.id
  AND d.rn > 1;
