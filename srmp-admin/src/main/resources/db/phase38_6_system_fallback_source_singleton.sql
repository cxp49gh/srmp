-- Phase38.6：系统兜底模板来源单例化。
BEGIN;
WITH fallback_sources AS (
    SELECT id, tenant_id, task_id,
           ROW_NUMBER() OVER (PARTITION BY tenant_id, task_id ORDER BY created_at ASC NULLS LAST, id ASC) AS rn
    FROM ai_solution_source
    WHERE source_type = 'SYSTEM_TEMPLATE'
       OR source_title LIKE '%系统兜底模板%'
       OR source_title LIKE '%兜底模板%'
       OR source_title LIKE '%Fallback Template%'
)
DELETE FROM ai_solution_source s
USING fallback_sources f
WHERE s.id = f.id AND f.rn > 1;

UPDATE ai_solution_source
SET source_type = 'SYSTEM_TEMPLATE', source_title = '系统兜底模板'
WHERE source_type = 'SYSTEM_TEMPLATE'
   OR source_title LIKE '%系统兜底模板%'
   OR source_title LIKE '%兜底模板%'
   OR source_title LIKE '%Fallback Template%';

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_solution_source_one_fallback
ON ai_solution_source(tenant_id, task_id)
WHERE source_type = 'SYSTEM_TEMPLATE'
   OR source_title LIKE '%系统兜底模板%'
   OR source_title LIKE '%兜底模板%'
   OR source_title LIKE '%Fallback Template%';

CREATE OR REPLACE FUNCTION fn_ai_solution_source_normalize_fallback()
RETURNS trigger AS $$
BEGIN
    IF NEW.source_type = 'SYSTEM_TEMPLATE'
       OR NEW.source_title LIKE '%系统兜底模板%'
       OR NEW.source_title LIKE '%兜底模板%'
       OR NEW.source_title LIKE '%Fallback Template%' THEN
        NEW.source_type := 'SYSTEM_TEMPLATE';
        NEW.source_title := '系统兜底模板';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ai_solution_source_normalize_fallback ON ai_solution_source;
CREATE TRIGGER trg_ai_solution_source_normalize_fallback
BEFORE INSERT OR UPDATE ON ai_solution_source
FOR EACH ROW
EXECUTE FUNCTION fn_ai_solution_source_normalize_fallback();
COMMIT;
