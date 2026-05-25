<template>
  <AgentPageShell
    title="AI 能力治理"
    description="查看 Agent 当前能力、工具目录、编排策略，并用模拟器解释一次请求会命中什么能力和工具。"
  >
    <template #actions>
      <div class="header-actions">
        <el-button :loading="loading" @click="loadGovernance">刷新</el-button>
        <el-button :loading="coverageLoading" @click="loadPolicyCoverage">运行策略样例</el-button>
        <el-button :loading="toolImpactLoading" @click="loadToolImpact">刷新工具影响面</el-button>
        <el-button :loading="readinessLoading" @click="loadReadiness">运行治理体检</el-button>
        <el-button :loading="draftValidationLoading" @click="validateCurrentGovernanceConfigDraft">校验配置草稿</el-button>
        <el-button type="primary" :loading="planning" @click="simulatePlan">运行模拟</el-button>
      </div>
    </template>

    <div class="governance-page">
      <section class="metric-grid">
        <el-card shadow="never" class="metric-card">
          <span>能力数量</span>
          <strong>{{ capabilityCount }}</strong>
          <p>启用 {{ enabledCapabilityCount }} 个</p>
        </el-card>
        <el-card shadow="never" class="metric-card">
          <span>工具数量</span>
          <strong>{{ toolCount }}</strong>
          <p>{{ toolCategories.join(' / ') || '-' }}</p>
        </el-card>
        <el-card shadow="never" class="metric-card">
          <span>策略校验</span>
          <strong>{{ validationStatus }}</strong>
          <p>错误 {{ validationErrorCount }}；告警 {{ validationWarningCount }}</p>
        </el-card>
        <el-card shadow="never" class="metric-card">
          <span>策略样例</span>
          <strong>{{ coverageStatus }}</strong>
          <p>通过 {{ coveragePassedCount }}；失败 {{ coverageFailedCount }}</p>
        </el-card>
        <el-card shadow="never" class="metric-card">
          <span>治理体检</span>
          <strong>{{ readinessStatus }}</strong>
          <p>错误 {{ readinessErrorCount }}；告警 {{ readinessWarningCount }}</p>
        </el-card>
        <el-card shadow="never" class="metric-card">
          <span>配置版本</span>
          <strong>{{ capabilityVersion || '-' }}</strong>
          <p>工具 {{ toolVersion || '-' }}</p>
        </el-card>
      </section>

      <el-alert
        v-if="configPayload.mode && configPayload.publishMode === 'restart-required'"
        class="mb"
        type="info"
        show-icon
        title="治理配置当前采用重启发布模式，草稿校验不会改动运行时配置。"
      />
      <el-alert
        v-if="validationErrorCount > 0"
        class="mb"
        type="error"
        show-icon
        title="治理配置存在错误，Runtime 应阻止生产发布。"
      />
      <el-alert
        v-if="readinessStatus === 'FAIL'"
        class="mb"
        type="error"
        show-icon
        title="治理体检发现阻断项，请先处理错误级问题。"
      />

      <el-tabs v-model="activeTab" class="governance-tabs">
        <el-tab-pane label="治理体检" name="readiness">
          <div class="coverage-head">
            <div>
              <strong>治理体检</strong>
              <span>汇总配置、策略样例、工具注册和影响面问题</span>
            </div>
            <el-button size="small" type="primary" :loading="readinessLoading" @click="loadReadiness">运行体检</el-button>
          </div>
          <section class="plan-summary readiness-summary">
            <div><span>状态</span><strong>{{ readinessStatus }}</strong></div>
            <div><span>问题</span><strong>{{ readinessIssueCount }}</strong></div>
            <div><span>孤立工具</span><strong>{{ readinessSummary.orphanToolCount ?? '-' }}</strong></div>
          </section>
          <el-table :data="readinessIssues" border stripe v-loading="readinessLoading">
            <el-table-column label="级别" width="90">
              <template #default="{ row }">
                <el-tag :type="issueTagType(row.severity)">{{ row.severity || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="code" label="问题码" min-width="190" />
            <el-table-column prop="source" label="来源" width="120" />
            <el-table-column label="对象" min-width="210">
              <template #default="{ row }">
                <span>{{ row.toolName || row.capabilityId || row.caseId || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="说明" min-width="320" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="配置草稿" name="config">
          <div class="coverage-head">
            <div>
              <strong>活动配置</strong>
              <span>当前 Runtime 加载的能力、工具配置和发布方式</span>
            </div>
            <div class="head-buttons">
              <el-button size="small" :loading="configLoading" @click="loadGovernanceConfig">刷新配置</el-button>
              <el-button size="small" @click="resetDraftEditor">重置草稿</el-button>
              <el-button size="small" type="primary" :loading="draftValidationLoading" @click="validateCurrentGovernanceConfigDraft">校验当前草稿</el-button>
            </div>
          </div>
          <section class="plan-summary config-summary" v-loading="configLoading">
            <div><span>发布模式</span><strong>{{ configPayload.publishMode || '-' }}</strong></div>
            <div><span>运行时可改</span><strong>{{ configPayload.runtimeMutable ? '是' : '否' }}</strong></div>
            <div><span>能力 Hash</span><strong>{{ shortHash(configPayload.capabilitiesHash) || '-' }}</strong></div>
            <div><span>工具 Hash</span><strong>{{ shortHash(configPayload.toolsHash) || '-' }}</strong></div>
          </section>
          <div class="guide-list mb">
            <div>
              <span>配置文件</span>
              <code>{{ configFiles.capabilities || '-' }}</code>
              <code>{{ configFiles.tools || '-' }}</code>
            </div>
          </div>

          <div class="config-editor-grid">
            <section class="config-editor-panel">
              <div class="editor-title">
                <strong>能力配置草稿</strong>
                <span>{{ draftCapabilitiesText.length }} 字符</span>
              </div>
              <el-input
                v-model="draftCapabilitiesText"
                type="textarea"
                :rows="14"
                resize="vertical"
                spellcheck="false"
              />
            </section>
            <section class="config-editor-panel">
              <div class="editor-title">
                <strong>工具配置草稿</strong>
                <span>{{ draftToolsText.length }} 字符</span>
              </div>
              <el-input
                v-model="draftToolsText"
                type="textarea"
                :rows="14"
                resize="vertical"
                spellcheck="false"
              />
            </section>
          </div>

          <div class="coverage-head">
            <div>
              <strong>草稿校验结果</strong>
              <span>提交前检查结构、工具引用、孤立工具和阻断项</span>
            </div>
          </div>
          <section class="plan-summary config-summary" v-loading="draftValidationLoading">
            <div><span>状态</span><strong>{{ draftReadiness.status || draftStatus }}</strong></div>
            <div><span>草稿 ID</span><strong>{{ draftValidationPayload.draftId || '-' }}</strong></div>
            <div><span>配置错误</span><strong>{{ draftValidationErrorCount }}</strong></div>
            <div><span>问题总数</span><strong>{{ draftIssueCount }}</strong></div>
          </section>
          <section class="plan-summary diff-summary" v-if="draftDiff.mode || draftDiff.changed !== undefined">
            <div><span>能力变更</span><strong>{{ diffCapabilityChangeCount }}</strong></div>
            <div><span>工具变更</span><strong>{{ diffToolChangeCount }}</strong></div>
            <div><span>根配置变更</span><strong>{{ diffRootChangeCount }}</strong></div>
            <div><span>是否有变更</span><strong>{{ draftDiff.changed ? '是' : '否' }}</strong></div>
          </section>
          <el-table v-if="draftConfigChanges.length" class="mb" :data="draftConfigChanges" border stripe>
            <el-table-column prop="kind" label="类型" width="90" />
            <el-table-column prop="key" label="对象" min-width="220" />
            <el-table-column label="变更" width="110">
              <template #default="{ row }">
                <el-tag :type="changeTagType(row.changeType)">{{ changeTypeLabel(row.changeType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="字段" min-width="260">
              <template #default="{ row }">
                <div class="tag-list">
                  <el-tag v-for="item in arrayValue(row.changedFields)" :key="item" size="small" effect="plain">{{ item }}</el-tag>
                  <span v-if="arrayValue(row.changedFields).length === 0" class="muted">整项</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="label" label="名称" min-width="180" />
          </el-table>
          <el-table :data="draftIssues" border stripe v-loading="draftValidationLoading">
            <el-table-column label="级别" width="90">
              <template #default="{ row }">
                <el-tag :type="issueTagType(row.severity)">{{ row.severity || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="code" label="问题码" min-width="190" />
            <el-table-column prop="source" label="来源" width="120" />
            <el-table-column label="对象" min-width="210">
              <template #default="{ row }">
                <span>{{ row.toolName || row.capabilityId || row.caseId || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="说明" min-width="320" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="能力矩阵" name="capabilities">
          <el-table :data="capabilities" border stripe>
            <el-table-column prop="id" label="能力 ID" min-width="190" />
            <el-table-column prop="name" label="名称" width="130" />
            <el-table-column prop="category" label="分类" width="140" />
            <el-table-column prop="intent" label="Intent" width="150" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled === false ? 'info' : 'success'">{{ row.enabled === false ? '停用' : '启用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="触发摘要" min-width="280">
              <template #default="{ row }">
                <div class="tag-list">
                  <el-tag v-for="item in triggerTags(row)" :key="item" size="small" effect="plain">{{ item }}</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :loading="capabilityDetailLoading && capabilityDetailId === row.id" @click="openCapabilityDetail(row.id)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="工具目录" name="tools">
          <el-table :data="tools" border stripe>
            <el-table-column prop="name" label="工具名" min-width="210" />
            <el-table-column prop="label" label="展示名" width="150" />
            <el-table-column prop="category" label="分类" width="130" />
            <el-table-column label="风险" width="110">
              <template #default="{ row }">
                <el-tag :type="row.writeRisk ? 'danger' : 'success'">{{ row.writeRisk ? '写风险' : '只读' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="280" />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :loading="toolDetailLoading && toolDetailId === row.name" @click="openToolDetail(row.name)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="工具影响面" name="tool-impact">
          <div class="coverage-head">
            <div>
              <strong>工具影响面</strong>
              <span>按工具反查受影响能力和调用约束</span>
            </div>
            <el-button size="small" type="primary" :loading="toolImpactLoading" @click="loadToolImpact">刷新影响面</el-button>
          </div>
          <el-table :data="toolImpactTools" border stripe v-loading="toolImpactLoading">
            <el-table-column prop="name" label="工具名" min-width="210" />
            <el-table-column prop="label" label="展示名" width="150" />
            <el-table-column label="风险" width="90">
              <template #default="{ row }">
                <el-tag :type="riskTagType(row.riskLevel)">{{ row.riskLevel || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="affectedCapabilityCount" label="影响能力" width="100" />
            <el-table-column label="Required" min-width="220">
              <template #default="{ row }">
                <div class="tag-list"><el-tag v-for="item in relationNames(row, 'requiredBy')" :key="item" size="small">{{ item }}</el-tag></div>
              </template>
            </el-table-column>
            <el-table-column label="Optional" min-width="200">
              <template #default="{ row }">
                <div class="tag-list"><el-tag v-for="item in relationNames(row, 'optionalBy')" :key="item" size="small" type="info">{{ item }}</el-tag></div>
              </template>
            </el-table-column>
            <el-table-column label="Adaptive" min-width="200">
              <template #default="{ row }">
                <div class="tag-list"><el-tag v-for="item in relationNames(row, 'adaptiveBy')" :key="item" size="small" type="warning">{{ item }}</el-tag></div>
              </template>
            </el-table-column>
            <el-table-column label="Prohibited" min-width="220">
              <template #default="{ row }">
                <div class="tag-list"><el-tag v-for="item in relationNames(row, 'prohibitedBy')" :key="item" size="small" type="danger">{{ item }}</el-tag></div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :loading="toolDetailLoading && toolDetailId === row.name" @click="openToolDetail(row.name)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="编排策略" name="policy">
          <el-table :data="capabilities" border stripe>
            <el-table-column prop="name" label="能力" width="150" />
            <el-table-column label="Required" min-width="220">
              <template #default="{ row }">
                <div class="tag-list"><el-tag v-for="item in policyList(row, 'required')" :key="item" size="small">{{ item }}</el-tag></div>
              </template>
            </el-table-column>
            <el-table-column label="Optional" min-width="200">
              <template #default="{ row }">
                <div class="tag-list"><el-tag v-for="item in policyList(row, 'optional')" :key="item" size="small" type="info">{{ item }}</el-tag></div>
              </template>
            </el-table-column>
            <el-table-column label="Adaptive" min-width="200">
              <template #default="{ row }">
                <div class="tag-list"><el-tag v-for="item in policyList(row, 'adaptive')" :key="item" size="small" type="warning">{{ item }}</el-tag></div>
              </template>
            </el-table-column>
            <el-table-column label="Prohibited" min-width="240">
              <template #default="{ row }">
                <div class="tag-list"><el-tag v-for="item in policyList(row, 'prohibited')" :key="item" size="small" type="danger">{{ item }}</el-tag></div>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="策略样例" name="coverage">
          <div class="coverage-head">
            <div>
              <strong>样例矩阵</strong>
              <span>用配置中的典型请求校验能力命中和工具边界</span>
            </div>
            <el-button size="small" type="primary" :loading="coverageLoading" @click="loadPolicyCoverage">运行样例</el-button>
          </div>
          <el-alert
            v-if="coverageFailedCount > 0"
            class="mb"
            type="error"
            show-icon
            title="存在失败样例，请检查能力触发条件或工具策略。"
          />
          <el-table :data="coverageCases" border stripe v-loading="coverageLoading">
            <el-table-column prop="id" label="样例 ID" min-width="230" />
            <el-table-column prop="name" label="场景" min-width="150" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'PASS' ? 'success' : 'danger'">{{ row.status || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="能力命中" min-width="240">
              <template #default="{ row }">
                <div class="stacked">
                  <strong>{{ row.actualCapabilityName || row.actualCapabilityId || '-' }}</strong>
                  <span>{{ row.actualCapabilityId || '-' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="计划工具" min-width="320">
              <template #default="{ row }">
                <div class="tag-list">
                  <el-tag v-for="item in arrayValue(row.actualToolNames)" :key="item" size="small">{{ item }}</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="问题" min-width="260">
              <template #default="{ row }">
                <span v-if="arrayValue(row.warnings).length === 0" class="muted">无</span>
                <div v-else class="stacked">
                  <span v-for="item in arrayValue(row.warnings)" :key="item.code || item.message" class="error">{{ item.message || item.code }}</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="Plan 模拟器" name="simulator">
          <div class="simulator-grid">
            <el-card shadow="never">
              <template #header>请求上下文</template>
              <el-form label-width="90px">
                <el-form-item label="问题">
                  <el-input v-model="planForm.message" type="textarea" :rows="3" />
                </el-form-item>
                <el-form-item label="Action">
                  <el-select v-model="planForm.action" clearable placeholder="可选">
                    <el-option label="CHAT" value="CHAT" />
                    <el-option label="ANALYZE_OBJECT" value="ANALYZE_OBJECT" />
                    <el-option label="ANALYZE_ROUTE" value="ANALYZE_ROUTE" />
                    <el-option label="ANALYZE_REGION" value="ANALYZE_REGION" />
                    <el-option label="GENERATE_OBJECT_SOLUTION" value="GENERATE_OBJECT_SOLUTION" />
                    <el-option label="GENERATE_REGION_SOLUTION" value="GENERATE_REGION_SOLUTION" />
                    <el-option label="GENERATE_ROUTE_REPORT" value="GENERATE_ROUTE_REPORT" />
                  </el-select>
                </el-form-item>
                <el-form-item label="Mode">
                  <el-select v-model="planForm.mode">
                    <el-option label="ROUTE" value="ROUTE" />
                    <el-option label="OBJECT" value="OBJECT" />
                    <el-option label="REGION" value="REGION" />
                    <el-option label="FREE" value="FREE" />
                  </el-select>
                </el-form-item>
                <el-form-item label="对象类型">
                  <el-select v-model="planForm.objectType" clearable placeholder="可选">
                    <el-option label="ROAD_ROUTE" value="ROAD_ROUTE" />
                    <el-option label="ROAD_SECTION" value="ROAD_SECTION" />
                    <el-option label="DISEASE" value="DISEASE" />
                    <el-option label="ASSESSMENT_RESULT" value="ASSESSMENT_RESULT" />
                  </el-select>
                </el-form-item>
                <el-form-item label="路线">
                  <el-input v-model="planForm.routeCode" />
                </el-form-item>
                <el-form-item label="年份">
                  <el-input-number v-model="planForm.year" :min="2000" :max="2100" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="planning" @click="simulatePlan">运行模拟</el-button>
                </el-form-item>
              </el-form>
            </el-card>

            <el-card shadow="never">
              <template #header>命中结果</template>
              <el-empty v-if="!planResult" description="暂无模拟结果" />
              <template v-else>
                <section class="plan-summary">
                  <div><span>能力</span><strong>{{ planResult.capabilityId || '-' }}</strong></div>
                  <div><span>Intent</span><strong>{{ planResult.intent || '-' }}</strong></div>
                  <div><span>Trace</span><strong>{{ planResult.traceId || '-' }}</strong></div>
                </section>
                <div class="detail-block">
                  <h3>匹配规则</h3>
                  <div class="tag-list">
                    <el-tag v-for="item in planMatchedRules" :key="item" size="small" effect="plain">{{ item }}</el-tag>
                  </div>
                </div>
                <div class="detail-block">
                  <h3>计划工具</h3>
                  <div v-for="tool in planTools" :key="tool.toolName + tool.reason" class="tool-row">
                    <strong>{{ tool.label || tool.toolName }}</strong>
                    <span>{{ tool.reason || '-' }}</span>
                  </div>
                </div>
                <div class="detail-block">
                  <h3>禁用工具</h3>
                  <div class="tag-list">
                    <el-tag v-for="item in planProhibitedTools" :key="item" size="small" type="danger">{{ item }}</el-tag>
                  </div>
                </div>
              </template>
            </el-card>
          </div>
        </el-tab-pane>
      </el-tabs>

      <el-drawer v-model="capabilityDetailVisible" size="50%" class="capability-drawer">
        <template #header>
          <div class="drawer-title">
            <strong>{{ capabilityDetail.capability?.name || '能力详情' }}</strong>
            <span>{{ capabilityDetail.capability?.id || capabilityDetailId }}</span>
          </div>
        </template>
        <el-skeleton v-if="capabilityDetailLoading" :rows="8" animated />
        <template v-else>
          <section class="drawer-section">
            <h3>触发条件</h3>
            <div class="tag-list">
              <el-tag v-for="item in triggerTags(capabilityDetail.capability || {})" :key="item" size="small" effect="plain">{{ item }}</el-tag>
            </div>
          </section>

          <section class="drawer-section">
            <h3>工具边界</h3>
            <div class="policy-grid">
              <div v-for="item in capabilityPolicyBlocks" :key="item.key" class="policy-block">
                <span>{{ item.label }}</span>
                <div class="tag-list">
                  <el-tag v-for="tool in toolNamesForPolicy(item.key)" :key="tool" size="small" :type="item.type">{{ tool }}</el-tag>
                  <span v-if="toolNamesForPolicy(item.key).length === 0" class="muted">无</span>
                </div>
              </div>
            </div>
          </section>

          <section class="drawer-section">
            <h3>策略样例</h3>
            <el-table :data="capabilityExamples" border stripe size="small">
              <el-table-column prop="id" label="样例 ID" min-width="220" />
              <el-table-column prop="name" label="场景" min-width="150" />
              <el-table-column label="期望能力" min-width="180">
                <template #default="{ row }">{{ row.expect?.capabilityId || '-' }}</template>
              </el-table-column>
              <el-table-column label="必需工具" min-width="240">
                <template #default="{ row }">
                  <div class="tag-list">
                    <el-tag v-for="tool in arrayValue(row.expect?.requiredTools)" :key="tool" size="small">{{ tool }}</el-tag>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </section>

          <section class="drawer-section">
            <h3>开发线索</h3>
            <div class="guide-list">
              <div>
                <span>配置文件</span>
                <code v-for="file in arrayValue(capabilityDetail.developerGuide?.configFiles)" :key="file">{{ file }}</code>
              </div>
              <div>
                <span>新增/调整步骤</span>
                <p v-for="step in arrayValue(capabilityDetail.developerGuide?.steps)" :key="step">{{ step }}</p>
              </div>
            </div>
          </section>
        </template>
      </el-drawer>

      <el-drawer v-model="toolDetailVisible" size="50%" class="tool-drawer">
        <template #header>
          <div class="drawer-title">
            <strong>{{ toolDetail.tool?.label || '工具详情' }}</strong>
            <span>{{ toolDetail.tool?.name || toolDetailId }}</span>
          </div>
        </template>
        <el-skeleton v-if="toolDetailLoading" :rows="8" animated />
        <template v-else>
          <section class="drawer-section">
            <h3>注册状态</h3>
            <div v-if="toolDetail.contract?.checked" class="status-grid">
              <div>
                <span>Java 注册</span>
                <el-tag :type="boolTagType(toolDetail.contract?.javaRegistered)">{{ toolDetail.contract?.javaRegistered ? '已注册' : '未注册' }}</el-tag>
              </div>
              <div>
                <span>Runtime 白名单</span>
                <el-tag :type="boolTagType(toolDetail.contract?.runtimeAllowed)">{{ toolDetail.contract?.runtimeAllowed ? '已开放' : '被拦截' }}</el-tag>
              </div>
              <div>
                <span>写工具拦截</span>
                <el-tag :type="boolTagType(!toolDetail.contract?.writeBlocked)">{{ toolDetail.contract?.writeBlocked ? '已拦截' : '无拦截' }}</el-tag>
              </div>
              <div>
                <span>网关状态</span>
                <el-tag :type="boolTagType(toolDetail.contract?.gatewayOk)">{{ toolDetail.contract?.gatewayOk ? '正常' : '异常' }}</el-tag>
              </div>
            </div>
            <span v-else class="muted">未检查注册状态</span>
          </section>

          <section class="drawer-section">
            <h3>工具契约</h3>
            <div class="guide-list">
              <div>
                <span>说明</span>
                <p>{{ toolDetail.tool?.description || '-' }}</p>
              </div>
              <div>
                <span>输入字段</span>
                <div class="tag-list">
                  <el-tag v-for="item in arrayValue(toolDetail.tool?.inputSchema?.required)" :key="'required-' + item" size="small">{{ item }}</el-tag>
                  <el-tag v-for="item in arrayValue(toolDetail.tool?.inputSchema?.optional)" :key="'optional-' + item" size="small" type="info">{{ item }}</el-tag>
                  <span v-if="!arrayValue(toolDetail.tool?.inputSchema?.required).length && !arrayValue(toolDetail.tool?.inputSchema?.optional).length" class="muted">无</span>
                </div>
              </div>
              <div>
                <span>输出契约</span>
                <code>{{ JSON.stringify(toolDetail.tool?.resultContract || {}) }}</code>
              </div>
            </div>
          </section>

          <section class="drawer-section">
            <h3>影响能力</h3>
            <div class="policy-grid">
              <div v-for="item in toolRelationBlocks" :key="item.key" class="policy-block">
                <span>{{ item.label }}</span>
                <div class="tag-list">
                  <el-tag v-for="name in relationNames(toolDetail, item.key)" :key="name" size="small" :type="item.type">{{ name }}</el-tag>
                  <span v-if="relationNames(toolDetail, item.key).length === 0" class="muted">无</span>
                </div>
              </div>
            </div>
          </section>

          <section class="drawer-section">
            <h3>开发线索</h3>
            <div class="guide-list">
              <div>
                <span>配置文件</span>
                <code v-for="file in arrayValue(toolDetail.developerGuide?.configFiles)" :key="file">{{ file }}</code>
              </div>
              <div>
                <span>新增/调整步骤</span>
                <p v-for="step in arrayValue(toolDetail.developerGuide?.steps)" :key="step">{{ step }}</p>
              </div>
            </div>
          </section>
        </template>
      </el-drawer>
    </div>
  </AgentPageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AgentPageShell from './components/AgentPageShell.vue'
import {
  getAiGovernanceCapability,
  getAiGovernanceCapabilities,
  getAiGovernanceConfig,
  getAiGovernancePolicyCoverage,
  getAiGovernanceReadiness,
  getAiGovernanceTool,
  getAiGovernanceToolImpact,
  getAiGovernanceTools,
  simulateAiGovernancePlan,
  validateAiGovernanceConfigDraft,
  validateAiGovernancePolicies
} from '../../api/orchestrator'

const activeTab = ref('capabilities')
const loading = ref(false)
const planning = ref(false)
const coverageLoading = ref(false)
const toolImpactLoading = ref(false)
const readinessLoading = ref(false)
const configLoading = ref(false)
const draftValidationLoading = ref(false)
const capabilityDetailLoading = ref(false)
const capabilityDetailVisible = ref(false)
const capabilityDetailId = ref('')
const toolDetailLoading = ref(false)
const toolDetailVisible = ref(false)
const toolDetailId = ref('')
const capabilitiesPayload = ref<Record<string, any>>({})
const toolsPayload = ref<Record<string, any>>({})
const validationPayload = ref<Record<string, any>>({})
const coveragePayload = ref<Record<string, any>>({})
const toolImpactPayload = ref<Record<string, any>>({})
const readinessPayload = ref<Record<string, any>>({})
const configPayload = ref<Record<string, any>>({})
const draftValidationPayload = ref<Record<string, any>>({})
const draftCapabilitiesText = ref('')
const draftToolsText = ref('')
const capabilityDetailPayload = ref<Record<string, any>>({})
const toolDetailPayload = ref<Record<string, any>>({})
const planResult = ref<Record<string, any> | null>(null)

const planForm = reactive({
  message: '解释 PCI 指标',
  action: '',
  mode: 'ROUTE',
  objectType: '',
  routeCode: 'Y016140727',
  year: 2026
})

const capabilities = computed(() => arrayValue(capabilitiesPayload.value.capabilities))
const tools = computed(() => arrayValue(toolsPayload.value.tools))
const capabilityCount = computed(() => Number(capabilitiesPayload.value.capabilityCount ?? capabilities.value.length))
const enabledCapabilityCount = computed(() => Number(capabilitiesPayload.value.enabledCapabilityCount ?? capabilities.value.filter((item) => item.enabled !== false).length))
const toolCount = computed(() => Number(toolsPayload.value.toolCount ?? tools.value.length))
const capabilityVersion = computed(() => stringValue(capabilitiesPayload.value.version))
const toolVersion = computed(() => stringValue(toolsPayload.value.version))
const validation = computed(() => objectValue(validationPayload.value.validation || capabilitiesPayload.value.validation))
const validationErrorCount = computed(() => Number(validation.value.errorCount || 0))
const validationWarningCount = computed(() => Number(validation.value.warningCount || 0))
const validationStatus = computed(() => validationErrorCount.value > 0 ? '异常' : '通过')
const coverageCases = computed(() => arrayValue(coveragePayload.value.cases))
const coverageFailedCount = computed(() => Number(coveragePayload.value.failedCount || coverageCases.value.filter((item) => item.status !== 'PASS').length))
const coveragePassedCount = computed(() => Number(coveragePayload.value.passedCount || coverageCases.value.filter((item) => item.status === 'PASS').length))
const coverageStatus = computed(() => coverageFailedCount.value > 0 ? '异常' : coverageCases.value.length ? '通过' : '未运行')
const toolImpactTools = computed(() => arrayValue(toolImpactPayload.value.tools))
const readinessIssues = computed(() => arrayValue(readinessPayload.value.issues))
const readinessSummary = computed(() => objectValue(readinessPayload.value.summary))
const readinessStatus = computed(() => stringValue(readinessPayload.value.status || '未运行'))
const readinessIssueCount = computed(() => Number(readinessSummary.value.issueCount ?? readinessIssues.value.length))
const readinessErrorCount = computed(() => Number(readinessSummary.value.errorCount ?? readinessIssues.value.filter((item) => item.severity === 'ERROR').length))
const readinessWarningCount = computed(() => Number(readinessSummary.value.warningCount ?? readinessIssues.value.filter((item) => item.severity === 'WARN').length))
const configFiles = computed(() => objectValue(configPayload.value.configFiles))
const draftValidation = computed(() => objectValue(draftValidationPayload.value.validation))
const draftReadiness = computed(() => objectValue(draftValidationPayload.value.readiness))
const draftDiff = computed(() => objectValue(draftValidationPayload.value.diff))
const draftDiffSummary = computed(() => objectValue(draftDiff.value.summary))
const diffCapabilityChangeCount = computed(() => Number(draftDiffSummary.value.capabilityAddedCount || 0) + Number(draftDiffSummary.value.capabilityRemovedCount || 0) + Number(draftDiffSummary.value.capabilityModifiedCount || 0))
const diffToolChangeCount = computed(() => Number(draftDiffSummary.value.toolAddedCount || 0) + Number(draftDiffSummary.value.toolRemovedCount || 0) + Number(draftDiffSummary.value.toolModifiedCount || 0))
const diffRootChangeCount = computed(() => Number(draftDiffSummary.value.rootChangedCount || 0))
const draftConfigChanges = computed(() => [
  ...arrayValue(draftDiff.value.capabilities).map((item) => ({
    kind: '能力',
    key: item.id,
    label: item.label,
    changeType: item.changeType,
    changedFields: item.changedFields
  })),
  ...arrayValue(draftDiff.value.tools).map((item) => ({
    kind: '工具',
    key: item.name,
    label: item.label,
    changeType: item.changeType,
    changedFields: item.changedFields
  }))
])
const draftValidationErrorCount = computed(() => Number(draftValidation.value.errorCount || 0))
const draftStatus = computed(() => draftValidationErrorCount.value > 0 ? 'FAIL' : draftValidationPayload.value.mode ? 'PASS' : '未运行')
const draftIssues = computed(() => {
  const readinessIssues = arrayValue(draftReadiness.value.issues)
  if (readinessIssues.length) return readinessIssues
  return arrayValue(draftValidation.value.errors).map((item) => ({
    severity: 'ERROR',
    code: item.code || 'CONFIG_ERROR',
    source: 'CONFIG',
    message: item.message || item.code || '-'
  }))
})
const draftIssueCount = computed(() => Number(objectValue(draftReadiness.value.summary).issueCount ?? draftIssues.value.length))
const capabilityDetail = computed(() => objectValue(capabilityDetailPayload.value))
const capabilityExamples = computed(() => arrayValue(capabilityDetail.value.examples))
const toolDetail = computed(() => objectValue(toolDetailPayload.value))
const toolCategories = computed(() => uniqueStrings(tools.value.map((item) => item.category).filter(Boolean)))
const planTools = computed(() => arrayValue(planResult.value?.toolPlan))
const planMatchedRules = computed(() => arrayValue(planResult.value?.capability?.matchedRules))
const planProhibitedTools = computed(() => arrayValue(planResult.value?.capability?.toolPolicy?.prohibited))
const capabilityPolicyBlocks = [
  { key: 'required', label: 'Required', type: '' },
  { key: 'optional', label: 'Optional', type: 'info' },
  { key: 'adaptive', label: 'Adaptive', type: 'warning' },
  { key: 'prohibited', label: 'Prohibited', type: 'danger' }
]
const toolRelationBlocks = [
  { key: 'requiredBy', label: 'Required By', type: '' },
  { key: 'optionalBy', label: 'Optional By', type: 'info' },
  { key: 'adaptiveBy', label: 'Adaptive By', type: 'warning' },
  { key: 'prohibitedBy', label: 'Prohibited By', type: 'danger' }
]

onMounted(loadGovernance)

async function loadGovernance() {
  loading.value = true
  try {
    const [capabilitiesRes, toolsRes, validationRes] = await Promise.all([
      getAiGovernanceCapabilities(),
      getAiGovernanceTools(),
      validateAiGovernancePolicies(),
      loadGovernanceConfig()
    ])
    capabilitiesPayload.value = extractBody(capabilitiesRes)
    toolsPayload.value = extractBody(toolsRes)
    validationPayload.value = extractBody(validationRes)
  } finally {
    loading.value = false
  }
  await Promise.all([loadPolicyCoverage(), loadToolImpact(), loadReadiness()])
}

async function loadGovernanceConfig() {
  configLoading.value = true
  try {
    const response = await getAiGovernanceConfig()
    configPayload.value = extractBody(response)
    resetDraftEditor()
  } finally {
    configLoading.value = false
  }
}

async function validateCurrentGovernanceConfigDraft() {
  if (!configPayload.value.capabilitiesConfig || !configPayload.value.toolsConfig) {
    await loadGovernanceConfig()
  }
  const capabilitiesConfig = parseDraftJson(draftCapabilitiesText.value, '能力配置')
  const toolsConfig = parseDraftJson(draftToolsText.value, '工具配置')
  if (!capabilitiesConfig || !toolsConfig) return
  draftValidationLoading.value = true
  try {
    const response = await validateAiGovernanceConfigDraft({
      capabilitiesConfig,
      toolsConfig
    })
    draftValidationPayload.value = extractBody(response)
    activeTab.value = 'config'
  } finally {
    draftValidationLoading.value = false
  }
}

function resetDraftEditor() {
  draftCapabilitiesText.value = prettyJson(configPayload.value.capabilitiesConfig || {})
  draftToolsText.value = prettyJson(configPayload.value.toolsConfig || {})
  draftValidationPayload.value = {}
}

function parseDraftJson(value: string, label: string): Record<string, any> | null {
  try {
    const parsed = JSON.parse(value || '{}')
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      ElMessage.error(`${label} 必须是 JSON 对象`)
      return null
    }
    return parsed
  } catch (error: any) {
    ElMessage.error(`${label} JSON 格式错误：${error?.message || '无法解析'}`)
    return null
  }
}

function prettyJson(value: any): string {
  return JSON.stringify(value || {}, null, 2)
}

async function loadPolicyCoverage() {
  coverageLoading.value = true
  try {
    const response = await getAiGovernancePolicyCoverage()
    coveragePayload.value = extractBody(response)
  } finally {
    coverageLoading.value = false
  }
}

async function loadToolImpact() {
  toolImpactLoading.value = true
  try {
    const response = await getAiGovernanceToolImpact()
    toolImpactPayload.value = extractBody(response)
  } finally {
    toolImpactLoading.value = false
  }
}

async function loadReadiness() {
  readinessLoading.value = true
  try {
    const response = await getAiGovernanceReadiness(true, true)
    readinessPayload.value = extractBody(response)
  } finally {
    readinessLoading.value = false
  }
}

async function openCapabilityDetail(capabilityId: string) {
  if (!capabilityId) return
  capabilityDetailId.value = capabilityId
  capabilityDetailVisible.value = true
  capabilityDetailLoading.value = true
  try {
    const response = await getAiGovernanceCapability(capabilityId)
    capabilityDetailPayload.value = extractBody(response)
  } finally {
    capabilityDetailLoading.value = false
  }
}

async function openToolDetail(toolName: string) {
  if (!toolName) return
  toolDetailId.value = toolName
  toolDetailVisible.value = true
  toolDetailLoading.value = true
  try {
    const response = await getAiGovernanceTool(toolName, true)
    toolDetailPayload.value = extractBody(response)
  } finally {
    toolDetailLoading.value = false
  }
}

async function simulatePlan() {
  planning.value = true
  try {
    const mapObject = planForm.objectType ? { objectType: planForm.objectType, routeCode: planForm.routeCode, year: planForm.year } : undefined
    const response = await simulateAiGovernancePlan({
      action: planForm.action || undefined,
      message: planForm.message,
      mapContext: {
        mode: planForm.mode,
        routeCode: planForm.routeCode,
        year: planForm.year,
        mapObject
      },
      options: {
        topK: 3,
        traceId: `governance-plan-${Date.now()}`
      }
    })
    planResult.value = extractBody(response)
    activeTab.value = 'simulator'
  } finally {
    planning.value = false
  }
}

function triggerTags(row: Record<string, any>) {
  const triggers = objectValue(row.triggers)
  return [
    ...arrayValue(triggers.actions).map((item) => `action:${item}`),
    ...arrayValue(triggers.modes).map((item) => `mode:${item}`),
    ...arrayValue(triggers.objectTypes).map((item) => `object:${item}`),
    ...arrayValue(triggers.includeKeywords).slice(0, 5).map((item) => `kw:${item}`)
  ]
}

function policyList(row: Record<string, any>, key: string) {
  return arrayValue(row.toolPolicy?.[key])
}

function relationNames(row: Record<string, any>, key: string) {
  return arrayValue(row[key])
    .map((item) => item?.name && item?.id ? `${item.name} (${item.id})` : item?.name || item?.id)
    .filter(Boolean)
}

function riskTagType(riskLevel: string) {
  if (riskLevel === 'HIGH') return 'danger'
  if (riskLevel === 'MEDIUM') return 'warning'
  if (riskLevel === 'LOW') return 'info'
  return 'success'
}

function boolTagType(value: boolean) {
  return value ? 'success' : 'danger'
}

function issueTagType(severity: string) {
  if (severity === 'ERROR') return 'danger'
  if (severity === 'WARN') return 'warning'
  return 'info'
}

function changeTagType(changeType: string) {
  if (changeType === 'ADDED') return 'success'
  if (changeType === 'REMOVED') return 'danger'
  if (changeType === 'MODIFIED') return 'warning'
  return 'info'
}

function changeTypeLabel(changeType: string) {
  if (changeType === 'ADDED') return '新增'
  if (changeType === 'REMOVED') return '删除'
  if (changeType === 'MODIFIED') return '修改'
  return changeType || '-'
}

function toolNamesForPolicy(key: string) {
  return arrayValue(capabilityDetail.value.tools?.[key])
    .map((item) => item?.label ? `${item.label} (${item.name})` : item?.name)
    .filter(Boolean)
}

function extractBody(value: any): Record<string, any> {
  if (value?.body && typeof value.body === 'object') return value.body
  if (value?.data?.body && typeof value.data.body === 'object') return value.data.body
  if (value && typeof value === 'object') return value
  return {}
}

function arrayValue(value: any): any[] {
  return Array.isArray(value) ? value : []
}

function objectValue(value: any): Record<string, any> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
}

function stringValue(value: any): string {
  return value == null ? '' : String(value)
}

function shortHash(value: any): string {
  const text = stringValue(value)
  return text ? text.slice(0, 12) : ''
}

function uniqueStrings(values: any[]): string[] {
  return Array.from(new Set(values.map((item) => String(item)).filter(Boolean)))
}
</script>

<style scoped>
.governance-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.header-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.metric-card span,
.plan-summary span {
  color: #64748b;
  font-size: 12px;
}

.metric-card strong {
  display: block;
  margin-top: 8px;
  font-size: 24px;
}

.metric-card p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
}

.mb {
  margin-bottom: 12px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.config-editor-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.config-editor-panel {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.editor-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.editor-title strong {
  color: #0f172a;
  font-size: 14px;
}

.editor-title span {
  color: #64748b;
  font-size: 12px;
}

.coverage-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.coverage-head > div {
  display: grid;
  gap: 4px;
}

.coverage-head .head-buttons {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.coverage-head span,
.muted {
  color: #64748b;
  font-size: 12px;
}

.stacked {
  display: grid;
  gap: 4px;
}

.stacked span {
  color: #64748b;
  word-break: break-all;
}

.stacked .error {
  color: #dc2626;
}

.drawer-title {
  display: grid;
  gap: 4px;
}

.drawer-title strong {
  color: #0f172a;
  font-size: 16px;
}

.drawer-title span {
  color: #64748b;
  font-size: 12px;
}

.drawer-section {
  margin-bottom: 18px;
}

.drawer-section h3 {
  margin: 0 0 10px;
  color: #0f172a;
  font-size: 14px;
}

.policy-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.policy-block {
  display: grid;
  gap: 8px;
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f8fafc;
}

.policy-block > span,
.guide-list span {
  color: #64748b;
  font-size: 12px;
}

.guide-list {
  display: grid;
  gap: 12px;
}

.guide-list div {
  display: grid;
  gap: 6px;
}

.guide-list code {
  display: block;
  padding: 6px 8px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  color: #334155;
  background: #f8fafc;
  white-space: normal;
  word-break: break-all;
}

.guide-list p {
  margin: 0;
  color: #334155;
  font-size: 13px;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.status-grid div {
  display: grid;
  gap: 8px;
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f8fafc;
}

.status-grid span {
  color: #64748b;
  font-size: 12px;
}

.simulator-grid {
  display: grid;
  grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
  gap: 16px;
}

.plan-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.config-summary {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.diff-summary {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.plan-summary div {
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f8fafc;
}

.plan-summary strong {
  display: block;
  margin-top: 6px;
  word-break: break-all;
}

.detail-block {
  margin-top: 16px;
}

.detail-block h3 {
  margin: 0 0 10px;
  font-size: 14px;
}

.tool-row {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #e5e7eb;
}

.tool-row span {
  color: #64748b;
}

@media (max-width: 1100px) {
  .metric-grid,
  .simulator-grid,
  .config-editor-grid,
  .plan-summary,
  .policy-grid,
  .status-grid {
    grid-template-columns: 1fr;
  }
}
</style>
