export type ToolPolicyKey = 'required' | 'optional' | 'adaptive' | 'prohibited'

export interface CapabilityMatrixRow {
  id: string
  name: string
  category: string
  intent: string
  enabled: boolean
  required: string[]
  optional: string[]
  adaptive: string[]
  prohibited: string[]
  raw: Record<string, any>
}

export interface EvaluationCaseRow {
  id: string
  name: string
  capabilityId: string
  capabilityName: string
  request: Record<string, any>
  expect: Record<string, any>
  expectedCapabilityId: string
  requiredTools: string[]
  prohibitedTools: string[]
  exactToolNames: string[]
}

const POLICY_KEYS: ToolPolicyKey[] = ['required', 'optional', 'adaptive', 'prohibited']

export function buildCapabilityMatrixRows(capabilitiesConfig: Record<string, any>): CapabilityMatrixRow[] {
  return arrayValue(capabilitiesConfig?.capabilities).map((capability) => {
    const policy = objectValue(capability.toolPolicy)
    return {
      id: stringValue(capability.id),
      name: stringValue(capability.name || capability.id),
      category: stringValue(capability.category),
      intent: stringValue(capability.intent),
      enabled: capability.enabled !== false,
      required: stringList(policy.required),
      optional: stringList(policy.optional),
      adaptive: stringList(policy.adaptive),
      prohibited: stringList(policy.prohibited),
      raw: capability
    }
  })
}

export function updateCapabilityToolPolicy(
  capabilitiesConfig: Record<string, any>,
  capabilityId: string,
  policyKey: ToolPolicyKey,
  toolNames: string[]
): Record<string, any> {
  const next = cloneObject(capabilitiesConfig)
  const selected = uniqueStrings(toolNames)
  next.capabilities = arrayValue(next.capabilities).map((capability) => {
    if (stringValue(capability.id) !== capabilityId) return capability
    const nextPolicy: Record<string, string[]> = {}
    for (const key of POLICY_KEYS) {
      const current = key === policyKey ? selected : stringList(capability.toolPolicy?.[key])
      nextPolicy[key] = key === policyKey ? current : current.filter((tool) => !selected.includes(tool))
    }
    return {
      ...capability,
      toolPolicy: nextPolicy
    }
  })
  return next
}

export function extractEvaluationCases(capabilitiesConfig: Record<string, any>): EvaluationCaseRow[] {
  const rows: EvaluationCaseRow[] = []
  for (const capability of arrayValue(capabilitiesConfig?.capabilities)) {
    for (const example of arrayValue(capability.examples)) {
      const expect = objectValue(example.expect)
      rows.push({
        id: stringValue(example.id),
        name: stringValue(example.name || example.id),
        capabilityId: stringValue(capability.id),
        capabilityName: stringValue(capability.name || capability.id),
        request: objectValue(example.request),
        expect,
        expectedCapabilityId: stringValue(expect.capabilityId || capability.id),
        requiredTools: stringList(expect.requiredTools),
        prohibitedTools: stringList(expect.prohibitedTools),
        exactToolNames: stringList(expect.exactToolNames)
      })
    }
  }
  return rows
}

export function writeEvaluationCases(
  capabilitiesConfig: Record<string, any>,
  cases: EvaluationCaseRow[]
): Record<string, any> {
  const next = cloneObject(capabilitiesConfig)
  const byCapability = new Map<string, EvaluationCaseRow[]>()
  for (const item of cases) {
    const key = stringValue(item.capabilityId)
    if (!key) continue
    byCapability.set(key, [...(byCapability.get(key) || []), item])
  }
  next.capabilities = arrayValue(next.capabilities).map((capability) => {
    const capabilityId = stringValue(capability.id)
    const rows = byCapability.get(capabilityId) || []
    return {
      ...capability,
      examples: rows.map((row) => ({
        id: row.id,
        name: row.name,
        request: row.request || {},
        expect: {
          capabilityId: row.expectedCapabilityId || row.capabilityId,
          requiredTools: row.requiredTools || [],
          prohibitedTools: row.prohibitedTools || [],
          exactToolNames: row.exactToolNames || []
        }
      }))
    }
  })
  return next
}

export function derivePublishBlockers(validationPayload: Record<string, any>, coveragePayload: Record<string, any>): string[] {
  const blockers: string[] = []
  const validation = objectValue(validationPayload.validation)
  const readiness = objectValue(validationPayload.readiness)
  const coverage = Object.keys(coveragePayload || {}).length ? coveragePayload : objectValue(validationPayload.policyCoverage)
  if (Number(validation.errorCount || 0) > 0) blockers.push('配置校验存在错误')
  if (stringValue(readiness.status) === 'FAIL') blockers.push('治理体检存在阻断项')
  if (Number(coverage.failedCount || 0) > 0) blockers.push(`策略样例失败 ${Number(coverage.failedCount || 0)} 个`)
  return blockers
}

export function parseJsonObject(text: string): Record<string, any> | null {
  try {
    const parsed = JSON.parse(text || '{}')
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null
  } catch {
    return null
  }
}

function cloneObject(value: Record<string, any>): Record<string, any> {
  return JSON.parse(JSON.stringify(value || {}))
}

function arrayValue(value: any): any[] {
  return Array.isArray(value) ? value : []
}

function objectValue(value: any): Record<string, any> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
}

function stringList(value: any): string[] {
  return uniqueStrings(arrayValue(value).map((item) => stringValue(item)).filter(Boolean))
}

function stringValue(value: any): string {
  return value == null ? '' : String(value)
}

function uniqueStrings(values: string[]): string[] {
  return Array.from(new Set(values.map((item) => item.trim()).filter(Boolean)))
}
