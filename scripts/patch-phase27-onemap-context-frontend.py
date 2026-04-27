from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def patch_one_map():
    path = ROOT / "srmp-web-ui/src/views/gis/OneMap.vue"
    if not path.exists():
        raise FileNotFoundError(path)
    s = path.read_text(encoding="utf-8")

    if "<AgentChatFloat" in s:
        if ':map-object=' not in s and ':mapObject=' not in s:
            s = s.replace(
                ':context="agentContext"',
                ':context="agentContext"\n      :map-object="selectedMapObject"'
            )
        if ':auto-question=' not in s:
            s = s.replace(
                ':context="agentContext"',
                ':context="agentContext"\n      :auto-question="pendingAiQuestion"\n      @auto-question-consumed="pendingAiQuestion = \'\'"'
            )

    if "const pendingAiQuestion" not in s:
        s = s.replace(
            "const agentVisible = ref(false)",
            "const agentVisible = ref(false)\nconst pendingAiQuestion = ref('')"
        )

    if "const selectedMapObject = computed" not in s:
        marker = "const agentContext = computed(() => ({"
        insert = (
            "\nconst selectedMapObject = computed(() => {\n"
            "  const detail: any = selectedDetail.value || {}\n"
            "  const props: any = detail.properties || detail\n"
            "  if (!props || Object.keys(props).length === 0) return null\n\n"
            "  const rawType = props.objectType || props.object_type || props.type || props.layerType\n"
            "  const objectType = rawType === 'ASSESSMENT' ? 'ASSESSMENT_RESULT' : rawType\n\n"
            "  return {\n"
            "    objectType,\n"
            "    objectId: props.objectId || props.object_id || props.id,\n"
            "    routeCode: props.routeCode || props.route_code || query.routeCode,\n"
            "    year: Number(query.year || props.year || 2026),\n"
            "    startStake: props.startStake || props.start_stake,\n"
            "    endStake: props.endStake || props.end_stake,\n"
            "    mqi: props.mqi,\n"
            "    pqi: props.pqi,\n"
            "    pci: props.pci,\n"
            "    grade: props.grade,\n"
            "    diseaseType: props.diseaseType || props.disease_type,\n"
            "    diseaseName: props.diseaseName || props.disease_name,\n"
            "    severity: props.severity,\n"
            "    raw: props\n"
            "  }\n"
            "})\n\n"
        )
        if marker not in s:
            raise RuntimeError("未找到 OneMap.vue 中的 agentContext marker，无法自动插入 selectedMapObject")
        s = s.replace(marker, insert + marker)

    if "selectedMapObject: selectedMapObject.value" not in s:
        s = s.replace(
            "selected: selectedDetail.value",
            "selected: selectedDetail.value,\n  mapObject: selectedMapObject.value,\n  selectedMapObject: selectedMapObject.value"
        )

    pattern = re.compile(r"function openAiForSelected\(\) \{\s*agentVisible\.value = true\s*\}", re.S)
    replacement = (
        "function openAiForSelected() {\n"
        "  if (!selectedDetail.value) {\n"
        "    ElMessage.warning('请先在地图上选择一个对象')\n"
        "    return\n"
        "  }\n"
        "  agentVisible.value = true\n"
        "  pendingAiQuestion.value = '分析当前地图选中对象，说明主要问题、成因判断，并给出养护处置建议'\n"
        "}"
    )
    s, n = pattern.subn(replacement, s)
    if n == 0 and "pendingAiQuestion.value = '分析当前地图选中对象" not in s:
        raise RuntimeError("未找到 openAiForSelected() 的原始实现，请手动检查 OneMap.vue")

    path.write_text(s, encoding="utf-8")
    print(f"[OK] patched {path}")

def patch_agent_chat_float():
    path = ROOT / "srmp-web-ui/src/views/gis/components/AgentChatFloat.vue"
    if not path.exists():
        raise FileNotFoundError(path)
    s = path.read_text(encoding="utf-8")

    s = s.replace(
        "import { computed, reactive, ref } from 'vue'",
        "import { computed, nextTick, reactive, ref, watch } from 'vue'"
    )

    if "autoQuestion?: string" not in s:
        s = s.replace(
            "mapObject?: Record<string, any>",
            "mapObject?: Record<string, any> | null\n\n  autoQuestion?: string"
        )

    if "const emit = defineEmits" not in s:
        s = s.replace("defineEmits<{", "const emit = defineEmits<{")
    if "auto-question-consumed" not in s:
        s = s.replace(
            "(e: 'update:visible', value: boolean): void",
            "(e: 'update:visible', value: boolean): void\n\n  (e: 'auto-question-consumed'): void"
        )

    if "const activeMapObject = computed" not in s:
        marker = "const contextText = computed(() => {"
        insert = (
            "\nconst activeMapObject = computed(() => {\n"
            "  return props.mapObject || props.context?.mapObject || props.context?.selectedMapObject || props.context?.selected || null\n"
            "})\n\n"
            "const mapContextLabel = computed(() => {\n"
            "  const obj: any = activeMapObject.value || {}\n"
            "  const type = obj.objectType || obj.object_type || '地图对象'\n"
            "  const route = obj.routeCode || obj.route_code || ''\n"
            "  const stake = obj.startStake || obj.start_stake\n"
            "  return route ? `${route} | ${type}${stake ? ` | K${stake}` : ''}` : type\n"
            "})\n\n"
        )
        if marker not in s:
            raise RuntimeError("未找到 AgentChatFloat.vue 中的 contextText marker")
        s = s.replace(marker, insert + marker)

    s = s.replace("const selected = props.context?.selected", "const selected = activeMapObject.value")
    s = s.replace(
        "const selectedText = selected?.objectType ? `｜已选 ${selected.objectType}` : ''",
        "const selectedText = selected ? `｜已选 ${selected.objectType || selected.object_type || selected.routeCode || selected.route_code || '地图对象'}` : ''"
    )
    s = s.replace("mapObject: props.mapObject", "mapObject: activeMapObject.value")

    if "watch(\n  () => props.autoQuestion" not in s and "watch(() => props.autoQuestion" not in s:
        marker = "function quickAsk(text: string) {"
        insert = (
            "\nwatch(\n"
            "  () => props.autoQuestion,\n"
            "  async (question) => {\n"
            "    const text = String(question || '').trim()\n"
            "    if (!props.visible || !text || loading.value) return\n"
            "    input.value = text\n"
            "    await nextTick()\n"
            "    await send()\n"
            "    emit('auto-question-consumed')\n"
            "  },\n"
            "  { immediate: true }\n"
            ")\n\n"
            "function analyzeCurrentObject() {\n"
            "  if (!activeMapObject.value) return\n"
            "  quickAsk('分析当前地图选中对象，说明主要问题、成因判断，并给出养护处置建议')\n"
            "}\n\n"
            "function suggestForCurrentObject() {\n"
            "  if (!activeMapObject.value) return\n"
            "  quickAsk('基于当前地图选中对象，生成养护处置建议和优先级判断')\n"
            "}\n\n"
        )
        if marker not in s:
            raise RuntimeError("未找到 quickAsk marker")
        s = s.replace(marker, insert + marker)

    if "当前地图上下文" not in s:
        marker = '<div class="option-row">'
        banner = (
            "\n  <div v-if=\"activeMapObject\" class=\"map-context-banner\">\n"
            "    <div class=\"map-context-main\">\n"
            "      <strong>当前地图上下文</strong>\n"
            "      <span>{{ mapContextLabel }}</span>\n"
            "    </div>\n"
            "    <div class=\"map-context-actions\">\n"
            "      <el-button size=\"small\" type=\"primary\" plain :loading=\"loading\" @click=\"analyzeCurrentObject\">重新分析当前对象</el-button>\n"
            "      <el-button size=\"small\" plain :loading=\"loading\" @click=\"suggestForCurrentObject\">生成处置建议</el-button>\n"
            "    </div>\n"
            "  </div>\n"
        )
        if marker not in s:
            raise RuntimeError("未找到 option-row marker")
        s = s.replace(marker, banner + "\n  " + marker)

    if ".map-context-banner" not in s:
        css = (
            "\n.map-context-banner {\n"
            "  display: flex;\n"
            "  justify-content: space-between;\n"
            "  align-items: center;\n"
            "  gap: 10px;\n"
            "  margin-bottom: 8px;\n"
            "  padding: 8px 10px;\n"
            "  border-radius: 10px;\n"
            "  background: #eff6ff;\n"
            "  color: #1d4ed8;\n"
            "  font-size: 12px;\n"
            "}\n\n"
            ".map-context-main {\n"
            "  display: flex;\n"
            "  flex-direction: column;\n"
            "  gap: 2px;\n"
            "  min-width: 0;\n"
            "}\n\n"
            ".map-context-main span {\n"
            "  overflow: hidden;\n"
            "  text-overflow: ellipsis;\n"
            "  white-space: nowrap;\n"
            "}\n\n"
            ".map-context-actions {\n"
            "  display: flex;\n"
            "  gap: 6px;\n"
            "  flex-shrink: 0;\n"
            "}\n"
        )
        s = s.replace("</style>", css + "\n</style>")

    path.write_text(s, encoding="utf-8")
    print(f"[OK] patched {path}")

def patch_agent_api_type():
    path = ROOT / "srmp-web-ui/src/api/agent.ts"
    if not path.exists():
        return
    s = path.read_text(encoding="utf-8")
    if "mapObject?" not in s:
        s = s.replace(
            "context?: Record<string, any>",
            "context?: Record<string, any>\n  mapObject?: Record<string, any> | null"
        )
    path.write_text(s, encoding="utf-8")
    print(f"[OK] patched {path}")

patch_one_map()
patch_agent_chat_float()
patch_agent_api_type()
print("[OK] phase27 one-map current context frontend fix applied")
