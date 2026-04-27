from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[0]

def patch_one_map():
    path = ROOT / "srmp-web-ui/src/views/gis/OneMap.vue"
    s = path.read_text(encoding="utf-8")

    if "<AgentChatFloat" in s:
        if ':map-object=' not in s and ':mapObject=' not in s:
            s = s.replace(':context="agentContext"', ':context="agentContext"\n      :map-object="selectedMapObject"')
        if ':auto-question=' not in s:
            s = s.replace(
                ':context="agentContext"',
                ':context="agentContext"\n      :auto-question="pendingAiQuestion"\n      @auto-question-consumed="pendingAiQuestion = \'\'"'
            )

    if "const pendingAiQuestion" not in s:
        s = s.replace("const agentVisible = ref(false)", "const agentVisible = ref(false)\nconst pendingAiQuestion = ref('')")

    if "const selectedMapObject = computed" not in s:
        marker = "const agentContext = computed(() => ({"
        insert = '''
const selectedMapObject = computed(() => {
  const detail: any = selectedDetail.value || {}
  const props: any = detail.properties || detail
  if (!props || Object.keys(props).length === 0) return null

  const rawType = props.objectType || props.object_type || props.type || props.layerType
  const objectType = rawType === 'ASSESSMENT' ? 'ASSESSMENT_RESULT' : rawType

  return {
    objectType,
    objectId: props.objectId || props.object_id || props.id,
    routeCode: props.routeCode || props.route_code || query.routeCode,
    year: Number(query.year || props.year || 2026),
    startStake: props.startStake || props.start_stake,
    endStake: props.endStake || props.end_stake,
    mqi: props.mqi,
    pqi: props.pqi,
    pci: props.pci,
    grade: props.grade,
    diseaseType: props.diseaseType || props.disease_type,
    diseaseName: props.diseaseName || props.disease_name,
    severity: props.severity,
    raw: props
  }
})

'''
        if marker in s:
            s = s.replace(marker, insert + marker)
        else:
            print("[WARN] agentContext marker not found")

    if "selectedMapObject: selectedMapObject.value" not in s:
        s = s.replace(
            "selected: selectedDetail.value",
            "selected: selectedDetail.value,\n  mapObject: selectedMapObject.value,\n  selectedMapObject: selectedMapObject.value"
        )

    pattern = re.compile(r"function openAiForSelected\(\) \{\s*agentVisible\.value = true\s*\}", re.S)
    replacement = '''function openAiForSelected() {
  if (!selectedDetail.value) {
    ElMessage.warning('请先在地图上选择一个对象')
    return
  }
  agentVisible.value = true
  pendingAiQuestion.value = '分析当前地图选中对象，说明主要问题、成因判断，并给出养护处置建议'
}'''
    s, n = pattern.subn(replacement, s)
    if n == 0 and "pendingAiQuestion.value = '分析当前地图选中对象" not in s:
        print("[WARN] openAiForSelected pattern not found")

    path.write_text(s, encoding="utf-8")
    print(f"[OK] patched {path}")

def patch_agent_chat_float():
    path = ROOT / "srmp-web-ui/src/views/gis/components/AgentChatFloat.vue"
    s = path.read_text(encoding="utf-8")

    s = s.replace("import { computed, reactive, ref } from 'vue'", "import { computed, nextTick, reactive, ref, watch } from 'vue'")

    if "autoQuestion?: string" not in s:
        s = s.replace("mapObject?: Record<string, any>", "mapObject?: Record<string, any>\n\n  autoQuestion?: string")

    if "auto-question-consumed" not in s:
        s = s.replace(
            "(e: 'update:visible', value: boolean): void",
            "(e: 'update:visible', value: boolean): void\n\n  (e: 'auto-question-consumed'): void"
        )

    if "const emit = defineEmits" not in s:
        s = s.replace("defineEmits<{", "const emit = defineEmits<{")

    if "const activeMapObject = computed" not in s:
        insert = '''
const activeMapObject = computed(() => {
  return props.mapObject || props.context?.mapObject || props.context?.selectedMapObject || props.context?.selected || null
})

'''
        s = s.replace("const contextText = computed(() => {", insert + "const contextText = computed(() => {")

    s = s.replace("const selected = props.context?.selected", "const selected = activeMapObject.value")
    s = s.replace(
        "const selectedText = selected?.objectType ? `｜已选 ${selected.objectType}` : ''",
        "const selectedText = selected ? `｜已选 ${selected.objectType || selected.object_type || selected.routeCode || selected.route_code || '地图对象'}` : ''"
    )
    s = s.replace("mapObject: props.mapObject", "mapObject: activeMapObject.value")

    if "watch(\n  () => props.autoQuestion" not in s and "watch(() => props.autoQuestion" not in s:
        watcher = '''
watch(
  () => props.autoQuestion,
  async (question) => {
    const text = String(question || '').trim()
    if (!props.visible || !text || loading.value) return
    input.value = text
    await nextTick()
    await send()
    emit('auto-question-consumed')
  },
  { immediate: true }
)

'''
        s = s.replace("function quickAsk(text: string) {", watcher + "function quickAsk(text: string) {")

    if "当前地图上下文" not in s:
        banner = '''
  <div v-if="activeMapObject" class="map-context-banner">
    <strong>当前地图上下文</strong>
    <span>{{ activeMapObject.routeCode || activeMapObject.route_code || activeMapObject.objectType || activeMapObject.object_type || '已选中对象' }}</span>
  </div>
'''
        s = s.replace('<div class="option-row">', banner + '\n  <div class="option-row">')

    if ".map-context-banner" not in s:
        css = '''
.map-context-banner {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
  padding: 8px 10px;
  border-radius: 10px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
}
'''
        s = s.replace("</style>", css + "\n</style>")

    path.write_text(s, encoding="utf-8")
    print(f"[OK] patched {path}")

def patch_agent_api_type():
    path = ROOT / "srmp-web-ui/src/api/agent.ts"
    if not path.exists():
        return
    s = path.read_text(encoding="utf-8")
    if "mapObject?" not in s:
        s = s.replace("context?: Record<string, any>", "context?: Record<string, any>\n  mapObject?: Record<string, any> | null")
    path.write_text(s, encoding="utf-8")
    print(f"[OK] patched {path}")

patch_one_map()
patch_agent_chat_float()
patch_agent_api_type()
print("[OK] phase26 AI analyze auto-run patch applied")
