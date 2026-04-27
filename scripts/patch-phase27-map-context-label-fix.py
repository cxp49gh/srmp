#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

def fail(msg):
    print("[FAIL] " + msg)
    sys.exit(1)

def backup(path: Path):
    bak = path.with_suffix(path.suffix + ".phase27-label.bak")
    if not bak.exists():
        bak.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")

def read(path):
    if not path.exists():
        fail(f"文件不存在：{path}")
    return path.read_text(encoding="utf-8")

def patch_one_map():
    path = ROOT / "srmp-web-ui/src/views/gis/OneMap.vue"
    s = read(path)
    backup(path)
    changed = False

    # 1) 记录点击时的原始 feature.properties，避免 getObjectDetail 返回简化对象后丢失 diseaseName / mqi / id 等。
    if "const selectedFeatureProperties" not in s:
        anchor = "const selectedDetail = ref<Record<string, any> | null>(null)"
        if anchor not in s:
            fail("OneMap.vue 未找到 selectedDetail 定义")
        s = s.replace(
            anchor,
            anchor + "\nconst selectedFeatureProperties = ref<Record<string, any> | null>(null)",
            1,
        )
        changed = True

    # 2) 点击图层对象时先保存原始属性。
    click_anchor = "selectedDetail.value = properties"
    if "selectedFeatureProperties.value = properties" not in s:
        if click_anchor not in s:
            fail("OneMap.vue 未找到 selectedDetail.value = properties")
        s = s.replace(
            click_anchor,
            "selectedFeatureProperties.value = properties\n  selectedDetail.value = properties",
            1,
        )
        changed = True

    # 3) selectedMapObject：合并原始 feature 属性 + 详情属性；object label 不再只依赖 routeCode。
    if "const selectedMapObject = computed" not in s:
        marker = "const agentContext = computed(() => ({"
        if marker not in s:
            fail("OneMap.vue 未找到 agentContext computed")
        block = (
            "\nfunction normalizeMapObjectType(rawType: any) {\n"
            "  const type = String(rawType || '').toUpperCase()\n"
            "  if (type === 'ASSESSMENT') return 'ASSESSMENT_RESULT'\n"
            "  if (type === 'DISEASE_RECORD') return 'DISEASE'\n"
            "  return type\n"
            "}\n\n"
            "const selectedMapObject = computed(() => {\n"
            "  const raw: any = selectedFeatureProperties.value || {}\n"
            "  const detail: any = selectedDetail.value || {}\n"
            "  const detailProps: any = detail.properties || detail\n"
            "  const props: any = { ...raw, ...detailProps }\n"
            "  if (!props || Object.keys(props).length === 0) return null\n\n"
            "  const objectType = normalizeMapObjectType(props.objectType || props.object_type || props.type || props.layerType)\n"
            "  const objectId = props.objectId || props.object_id || props.id || raw.objectId || raw.id\n\n"
            "  return {\n"
            "    objectType,\n"
            "    objectId,\n"
            "    id: objectId,\n"
            "    routeCode: props.routeCode || props.route_code || raw.routeCode || raw.route_code || query.routeCode,\n"
            "    year: Number(props.year || query.year || 2026),\n"
            "    startStake: props.startStake ?? props.start_stake ?? raw.startStake ?? raw.start_stake,\n"
            "    endStake: props.endStake ?? props.end_stake ?? raw.endStake ?? raw.end_stake,\n"
            "    routeName: props.routeName || props.route_name || raw.routeName || raw.route_name,\n"
            "    sectionName: props.sectionName || props.section_name || raw.sectionName || raw.section_name,\n"
            "    sectionCode: props.sectionCode || props.section_code || raw.sectionCode || raw.section_code,\n"
            "    unitCode: props.unitCode || props.unit_code || raw.unitCode || raw.unit_code,\n"
            "    diseaseName: props.diseaseName || props.disease_name || raw.diseaseName || raw.disease_name,\n"
            "    diseaseType: props.diseaseType || props.disease_type || raw.diseaseType || raw.disease_type,\n"
            "    severity: props.severity || raw.severity,\n"
            "    quantity: props.quantity ?? raw.quantity,\n"
            "    measureUnit: props.measureUnit || props.measure_unit || raw.measureUnit || raw.measure_unit,\n"
            "    mqi: props.mqi ?? raw.mqi,\n"
            "    pqi: props.pqi ?? raw.pqi,\n"
            "    pci: props.pci ?? raw.pci,\n"
            "    grade: props.grade || raw.grade,\n"
            "    raw: props\n"
            "  }\n"
            "})\n\n"
        )
        s = s.replace(marker, block + marker, 1)
        changed = True

    # 4) agentContext 带 mapObject / selectedMapObject。
    if "selectedMapObject: selectedMapObject.value" not in s:
        if "selected: selectedDetail.value" not in s:
            fail("OneMap.vue 未找到 selected: selectedDetail.value")
        s = s.replace(
            "selected: selectedDetail.value",
            "selected: selectedDetail.value,\n  mapObject: selectedMapObject.value,\n  selectedMapObject: selectedMapObject.value",
            1,
        )
        changed = True

    # 5) AgentChatFloat 显式传 map-object。
    if "<AgentChatFloat" in s and ':map-object=' not in s and ':mapObject=' not in s:
        if ':context="agentContext"' not in s:
            fail("OneMap.vue 未找到 :context=\"agentContext\"")
        s = s.replace(
            ':context="agentContext"',
            ':context="agentContext"\n      :map-object="selectedMapObject"',
            1,
        )
        changed = True

    # 6) loadObjectDetail 成功/失败后不要丢失原始 feature 属性。
    old_success = "selectedDetail.value = await getObjectDetail({ objectType, id })"
    if old_success in s and "const detail = await getObjectDetail({ objectType, id })" not in s:
        s = s.replace(
            old_success,
            "const detail = await getObjectDetail({ objectType, id })\n  selectedDetail.value = { ...properties, ...(detail || {}), objectType, objectId: id, id }",
            1,
        )
        changed = True

    old_fail = "selectedDetail.value = properties"
    # 第二次出现通常在 catch 中，点击处已经替换为带 selectedFeatureProperties 的版本。
    if "selectedDetail.value = { ...properties, objectType, objectId: id, id }" not in s:
        # 只替换 catch 里的那一处，找 catch block 更稳
        s2 = s.replace(
            "} catch {\n\n  selectedDetail.value = properties",
            "} catch {\n\n  selectedDetail.value = { ...properties, objectType, objectId: id, id }",
            1,
        )
        if s2 != s:
            s = s2
            changed = True

    if changed:
        path.write_text(s, encoding="utf-8")
        print(f"[OK] patched {path}")
    else:
        print(f"[SKIP] {path} 无需修改")

def patch_agent_chat_float():
    path = ROOT / "srmp-web-ui/src/views/gis/components/AgentChatFloat.vue"
    s = read(path)
    backup(path)
    changed = False

    # 1) activeMapObject fallback。
    if "const activeMapObject = computed" not in s:
        marker = "const contextText = computed(() => {"
        if marker not in s:
            fail("AgentChatFloat.vue 未找到 contextText computed")
        block = (
            "\nconst activeMapObject = computed(() => {\n"
            "  return props.mapObject || props.context?.mapObject || props.context?.selectedMapObject || props.context?.selected || null\n"
            "})\n\n"
            "function mapObjectTypeLabel(type: any) {\n"
            "  const value = String(type || '').toUpperCase()\n"
            "  const map: Record<string, string> = {\n"
            "    ROAD_ROUTE: '路线',\n"
            "    ROAD_SECTION: '路段',\n"
            "    EVALUATION_UNIT: '评定单元',\n"
            "    ASSESSMENT: '评定结果',\n"
            "    ASSESSMENT_RESULT: '评定结果',\n"
            "    DISEASE: '病害',\n"
            "    DISEASE_RECORD: '病害'\n"
            "  }\n"
            "  return map[value] || value || '地图对象'\n"
            "}\n\n"
            "function formatStake(start: any, end?: any) {\n"
            "  if (start === undefined || start === null || start === '') return ''\n"
            "  const s = `K${start}`\n"
            "  return end !== undefined && end !== null && end !== '' ? `${s}—K${end}` : s\n"
            "}\n\n"
            "const mapContextLabel = computed(() => {\n"
            "  const obj: any = activeMapObject.value || {}\n"
            "  const type = String(obj.objectType || obj.object_type || '').toUpperCase()\n"
            "  const typeLabel = mapObjectTypeLabel(type)\n"
            "  const route = obj.routeCode || obj.route_code || ''\n"
            "  const stake = formatStake(obj.startStake ?? obj.start_stake, obj.endStake ?? obj.end_stake)\n"
            "  if (type === 'DISEASE' || type === 'DISEASE_RECORD') {\n"
            "    const name = obj.diseaseName || obj.disease_name || obj.diseaseType || obj.disease_type || '病害'\n"
            "    const sev = obj.severity ? `｜${obj.severity}` : ''\n"
            "    return `${typeLabel}｜${name}${sev}${route ? `｜${route}` : ''}${stake ? `｜${stake}` : ''}`\n"
            "  }\n"
            "  if (type === 'ASSESSMENT' || type === 'ASSESSMENT_RESULT') {\n"
            "    const score = obj.mqi !== undefined && obj.mqi !== null ? `｜MQI ${obj.mqi}` : (obj.pci !== undefined && obj.pci !== null ? `｜PCI ${obj.pci}` : '')\n"
            "    return `${typeLabel}${route ? `｜${route}` : ''}${stake ? `｜${stake}` : ''}${score}`\n"
            "  }\n"
            "  if (type === 'EVALUATION_UNIT') {\n"
            "    const unit = obj.unitCode || obj.unit_code || ''\n"
            "    return `${typeLabel}${route ? `｜${route}` : ''}${stake ? `｜${stake}` : ''}${unit ? `｜${unit}` : ''}`\n"
            "  }\n"
            "  if (type === 'ROAD_SECTION') {\n"
            "    const section = obj.sectionName || obj.section_name || obj.sectionCode || obj.section_code || ''\n"
            "    return `${typeLabel}${section ? `｜${section}` : ''}${route ? `｜${route}` : ''}${stake ? `｜${stake}` : ''}`\n"
            "  }\n"
            "  if (type === 'ROAD_ROUTE') {\n"
            "    const name = obj.routeName || obj.route_name || route || '路线'\n"
            "    return `${typeLabel}｜${name}${route && name !== route ? `｜${route}` : ''}`\n"
            "  }\n"
            "  return `${typeLabel}${route ? `｜${route}` : ''}${stake ? `｜${stake}` : ''}`\n"
            "})\n\n"
        )
        s = s.replace(marker, block + marker, 1)
        changed = True

    # 2) contextText 用 activeMapObject，不直接用 query.routeCode 作为“当前地图上下文”。
    if "const selected = props.context?.selected" in s:
        s = s.replace("const selected = props.context?.selected", "const selected = activeMapObject.value", 1)
        changed = True
    if "const selectedText = selected?.objectType ? `｜已选 ${selected.objectType}` : ''" in s:
        s = s.replace(
            "const selectedText = selected?.objectType ? `｜已选 ${selected.objectType}` : ''",
            "const selectedText = selected ? `｜已选 ${mapContextLabel.value}` : ''",
            1,
        )
        changed = True

    # 3) 请求体 mapObject 使用 activeMapObject。
    if "mapObject: props.mapObject" in s:
        s = s.replace("mapObject: props.mapObject", "mapObject: activeMapObject.value", 1)
        changed = True

    # 4) 当前地图上下文卡片如果还没有，则加入。
    if "当前地图上下文" not in s:
        marker = '<div class="option-row">'
        if marker not in s:
            fail("AgentChatFloat.vue 未找到 option-row")
        banner = (
            "\n  <div v-if=\"activeMapObject\" class=\"map-context-banner\">\n"
            "    <strong>当前地图上下文</strong>\n"
            "    <span>{{ mapContextLabel }}</span>\n"
            "  </div>\n"
        )
        s = s.replace(marker, banner + "\n  " + marker, 1)
        changed = True

    if ".map-context-banner" not in s:
        css = (
            "\n.map-context-banner {\n"
            "  margin-bottom: 8px;\n"
            "  padding: 8px 10px;\n"
            "  border-radius: 10px;\n"
            "  background: #eff6ff;\n"
            "  color: #1d4ed8;\n"
            "  font-size: 12px;\n"
            "  display: flex;\n"
            "  gap: 8px;\n"
            "  align-items: center;\n"
            "}\n"
            ".map-context-banner span {\n"
            "  overflow: hidden;\n"
            "  text-overflow: ellipsis;\n"
            "  white-space: nowrap;\n"
            "}\n"
        )
        if "</style>" not in s:
            fail("AgentChatFloat.vue 未找到 </style>")
        s = s.replace("</style>", css + "\n</style>", 1)
        changed = True

    if changed:
        path.write_text(s, encoding="utf-8")
        print(f"[OK] patched {path}")
    else:
        print(f"[SKIP] {path} 无需修改")

patch_one_map()
patch_agent_chat_float()
print("[OK] phase27 map context label fix applied")
