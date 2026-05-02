#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
TASK_ID="${TASK_ID:-}"
if [ -z "$TASK_ID" ]; then echo "[FAIL] 请传入 TASK_ID"; exit 1; fi
DETAIL="$(curl -fsS "$BASE_URL/api/ai/solution/tasks/$TASK_ID")"
python3 - "$DETAIL" <<'PY'
import json,re,sys
payload=json.loads(sys.argv[1]); data=payload.get('data') or payload
content=data.get('resultContent') or data.get('result_content') or data.get('markdown') or ''
if re.search(r'##\s*(六、)?引用来源\s*\n\s*[-*]\s*系统兜底模板', content, re.S): raise SystemExit('[FAIL] 方案正文仍内嵌“引用来源 - 系统兜底模板”')
print('[OK] 方案正文未发现内嵌引用来源章节')
PY
SOURCES="$(curl -fsS "$BASE_URL/api/ai/solution/tasks/$TASK_ID/sources")"
python3 - "$SOURCES" <<'PY'
import json,sys
payload=json.loads(sys.argv[1]); items=payload.get('data') or payload
fallback=[]
for it in items:
    title=it.get('sourceTitle') or it.get('source_title') or it.get('title') or ''
    stype=it.get('sourceType') or it.get('source_type') or it.get('type') or ''
    sid=it.get('sourceId') or it.get('source_id') or it.get('id') or ''
    if stype=='SYSTEM_TEMPLATE' or '兜底模板' in title or 'SYSTEM_FALLBACK_TEMPLATE' in str(sid): fallback.append(it)
if len(fallback)>1: raise SystemExit(f'[FAIL] sources 中系统兜底模板重复：{len(fallback)} 条')
print(f'[OK] sources 中系统兜底模板数量：{len(fallback)}')
PY
MD="$(curl -fsS "$BASE_URL/api/ai/solution/tasks/$TASK_ID/export/markdown-v2")"
python3 - "$MD" <<'PY'
import re,sys
md=sys.argv[1]
if re.search(r'##\s*(六、)?引用来源\s*\n\s*[-*]\s*系统兜底模板', md, re.S): raise SystemExit('[FAIL] Markdown 导出中方案正文仍内嵌系统兜底模板引用来源')
sections=len(re.findall(r'##\s*[五六]、引用来源', md))
if sections > 1: raise SystemExit(f'[FAIL] Markdown 导出中引用来源章节重复：{sections}')
fallback_count=len(re.findall(r'系统兜底模板', md))
if fallback_count > 2: raise SystemExit(f'[FAIL] Markdown 导出中系统兜底模板出现过多：{fallback_count}')
print(f'[OK] Markdown 导出兜底模板出现次数：{fallback_count}，引用来源章节数：{sections}')
PY
echo "[OK] Phase38.6 系统兜底模板来源单例化验收通过"
