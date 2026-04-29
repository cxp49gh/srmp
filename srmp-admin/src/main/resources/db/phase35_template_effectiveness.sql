-- Phase 35: AI solution template effectiveness and unified template metadata
-- psql -h 127.0.0.1 -U srmp -d srmp -f srmp-admin/src/main/resources/db/phase35_template_effectiveness.sql

ALTER TABLE ai_solution_template
ADD COLUMN IF NOT EXISTS object_type VARCHAR(100),
ADD COLUMN IF NOT EXISTS origin_type VARCHAR(100),
ADD COLUMN IF NOT EXISTS is_default BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS priority INTEGER DEFAULT 0;

ALTER TABLE ai_solution_template_version
ADD COLUMN IF NOT EXISTS change_note VARCHAR(500),
ADD COLUMN IF NOT EXISTS created_by VARCHAR(64);

ALTER TABLE ai_solution_task
ADD COLUMN IF NOT EXISTS template_meta JSONB;

ALTER TABLE ai_trace_step
ADD COLUMN IF NOT EXISTS step_data JSONB;

CREATE INDEX IF NOT EXISTS idx_ai_solution_template_match
ON ai_solution_template(tenant_id, origin_type, object_type, solution_type, status, priority DESC, updated_at DESC)
WHERE deleted = false;

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_solution_template_default
ON ai_solution_template(
    tenant_id,
    COALESCE(origin_type, ''),
    COALESCE(object_type, ''),
    solution_type
)
WHERE is_default = true AND deleted = false;

CREATE INDEX IF NOT EXISTS idx_ai_solution_task_template
ON ai_solution_task(tenant_id, template_id, template_version);

UPDATE ai_solution_template
SET origin_type = COALESCE(origin_type, 'ROUTE_REPORT'),
    object_type = COALESCE(object_type, 'ROAD_ROUTE'),
    priority = COALESCE(priority, 0),
    is_default = COALESCE(is_default, false)
WHERE solution_type = 'ROAD_ASSESSMENT_REPORT'
  AND deleted = false;
