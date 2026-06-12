package com.smartroad.srmp.agent.solution.service.impl;

import com.smartroad.srmp.agent.solution.service.AiSolutionTemplateContractService;
import com.smartroad.srmp.agent.solution.template.MarkdownTemplateRenderer;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AiSolutionTemplateContractServiceImpl implements AiSolutionTemplateContractService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Resource
    private MarkdownTemplateRenderer markdownTemplateRenderer;

    @Override
    public Map<String, Object> contracts() {
        List<ContractDefinition> definitions = defaultContracts();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("templateCodes", templateCodes(definitions))
                .addValue("solutionTypes", solutionTypes(definitions));

        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                "select t.id, t.tenant_id, t.template_code, t.template_name, t.solution_type, t.source_type, t.source_id, " +
                        "t.category, t.current_version, t.status, t.origin_type, t.object_type, t.is_default, t.priority, " +
                        "t.created_at, t.updated_at, v.content, v.variables, v.source_url " +
                        "from ai_solution_template t " +
                        "left join ai_solution_template_version v on v.tenant_id=t.tenant_id and v.template_id=t.id and v.version=t.current_version " +
                        "where t.tenant_id=:tenantId and t.deleted=false " +
                        "and (t.template_code in (:templateCodes) or t.solution_type in (:solutionTypes)) " +
                        "order by t.template_code, case when t.status='ENABLED' then 0 else 1 end, t.is_default desc, t.priority desc, t.updated_at desc",
                params
        );
        return evaluateContracts(rows, definitions);
    }

    public Map<String, Object> evaluateContractsForTest(List<Map<String, Object>> templateRows) {
        return evaluateContracts(templateRows, defaultContracts());
    }

    private Map<String, Object> evaluateContracts(List<Map<String, Object>> templateRows,
                                                  List<ContractDefinition> definitions) {
        List<Map<String, Object>> safeRows = templateRows == null ? Collections.<Map<String, Object>>emptyList() : templateRows;
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> contracts = new ArrayList<>();
        int ok = 0;
        int warn = 0;
        int error = 0;

        for (ContractDefinition definition : definitions) {
            Map<String, Object> contract = evaluateContract(definition, safeRows);
            contracts.add(contract);
            String status = stringValue(contract.get("status"));
            if ("ERROR".equals(status)) {
                error++;
            } else if ("WARN".equals(status)) {
                warn++;
            } else {
                ok++;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", contracts.size());
        summary.put("ok", ok);
        summary.put("warn", warn);
        summary.put("error", error);

        result.put("summary", summary);
        result.put("contracts", contracts);
        result.put("generatedAt", LocalDateTime.now().toString());
        return result;
    }

    private Map<String, Object> evaluateContract(ContractDefinition definition,
                                                 List<Map<String, Object>> rows) {
        Map<String, Object> selected = selectTemplate(definition, rows);
        List<Map<String, Object>> checks = new ArrayList<>();
        List<String> missingVariables = new ArrayList<>();

        checks.add(check("TEMPLATE_CODE_DECLARED", isBlank(definition.templateCode) ? "ERROR" : "PASS",
                isBlank(definition.templateCode) ? "能力未声明期望模板编码" : "能力已声明期望模板编码", null));

        if (selected == null || !"ENABLED".equalsIgnoreCase(stringValue(selected.get("status")))) {
            checks.add(check("TEMPLATE_EXISTS", "ERROR", "未找到启用的约定模板：" + definition.templateCode, null));
            checks.add(check("TEMPLATE_SCOPE_MATCH", "SKIP", "模板不存在，跳过范围检查", null));
            checks.add(check("TEMPLATE_DEFAULT", "SKIP", "模板不存在，跳过默认模板检查", null));
            checks.add(check("VARIABLES_RENDER", "SKIP", "模板不存在，跳过变量渲染检查", null));
        } else {
            checks.add(check("TEMPLATE_EXISTS", "PASS", "已找到启用的约定模板：" + definition.templateCode, templateMeta(selected)));

            boolean scopeMatched = equalsIgnoreCase(definition.originType, selected.get("origin_type"))
                    && equalsIgnoreCase(definition.objectType, selected.get("object_type"))
                    && equalsIgnoreCase(definition.solutionType, selected.get("solution_type"));
            checks.add(check("TEMPLATE_SCOPE_MATCH", scopeMatched ? "PASS" : "ERROR",
                    scopeMatched ? "模板范围与能力契约一致" : "模板来源场景、对象类型或方案类型与能力契约不一致",
                    expectedAndActualScope(definition, selected)));

            boolean isDefault = boolValue(selected.get("is_default"));
            checks.add(check("TEMPLATE_DEFAULT", isDefault ? "PASS" : "WARN",
                    isDefault ? "该模板为当前契约范围默认模板" : "该模板已启用，但不是当前契约范围默认模板",
                    templateMeta(selected)));

            String content = stringValue(selected.get("content"));
            if (isBlank(content)) {
                checks.add(check("VARIABLES_RENDER", "ERROR", "模板当前版本内容为空", templateMeta(selected)));
            } else {
                MarkdownTemplateRenderer.RenderResult renderResult = renderer().renderWithCheck(content, sampleVariables(definition));
                missingVariables.addAll(renderResult.getMissingVariables());
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("missingVariables", renderResult.getMissingVariables());
                details.put("warnings", renderResult.getWarnings());
                checks.add(check("VARIABLES_RENDER", renderResult.getMissingVariables().isEmpty() ? "PASS" : "ERROR",
                        renderResult.getMissingVariables().isEmpty() ? "模板变量可用样例数据完整渲染" : "模板存在缺失变量",
                        details));
            }
        }

        List<Map<String, Object>> legacyRows = legacyRows(definition, rows);
        Map<String, Object> legacyDetails = new LinkedHashMap<>();
        legacyDetails.put("templates", legacyRows);
        checks.add(check("LEGACY_ALIAS", legacyRows.isEmpty() ? "PASS" : "WARN",
                legacyRows.isEmpty() ? "未发现同范围非约定启用模板" : "发现同范围非约定启用模板，建议迁移或停用",
                legacyDetails));

        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("capabilityId", definition.capabilityId);
        contract.put("label", definition.label);
        contract.put("action", definition.action);
        contract.put("contextScope", definition.contextScope);
        contract.put("originType", definition.originType);
        contract.put("objectType", definition.objectType);
        contract.put("solutionType", definition.solutionType);
        contract.put("templateCode", definition.templateCode);
        contract.put("status", aggregateStatus(checks));
        contract.put("checks", checks);
        contract.put("template", selected == null ? null : templateMeta(selected));
        contract.put("missingVariables", missingVariables);
        return contract;
    }

    private Map<String, Object> selectTemplate(ContractDefinition definition,
                                               List<Map<String, Object>> rows) {
        Map<String, Object> firstExact = null;
        Map<String, Object> enabledExact = null;
        Map<String, Object> defaultExact = null;
        for (Map<String, Object> row : rows) {
            if (!equalsIgnoreCase(definition.templateCode, row.get("template_code"))) {
                continue;
            }
            if (firstExact == null) {
                firstExact = row;
            }
            if ("ENABLED".equalsIgnoreCase(stringValue(row.get("status")))) {
                if (enabledExact == null) {
                    enabledExact = row;
                }
                if (boolValue(row.get("is_default"))) {
                    defaultExact = row;
                    break;
                }
            }
        }
        if (defaultExact != null) {
            return defaultExact;
        }
        if (enabledExact != null) {
            return enabledExact;
        }
        return firstExact;
    }

    private List<Map<String, Object>> legacyRows(ContractDefinition definition,
                                                 List<Map<String, Object>> rows) {
        List<Map<String, Object>> legacy = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (equalsIgnoreCase(definition.templateCode, row.get("template_code"))) {
                continue;
            }
            if (!"ENABLED".equalsIgnoreCase(stringValue(row.get("status")))) {
                continue;
            }
            if (equalsIgnoreCase(definition.originType, row.get("origin_type"))
                    && equalsIgnoreCase(definition.objectType, row.get("object_type"))
                    && equalsIgnoreCase(definition.solutionType, row.get("solution_type"))) {
                legacy.add(templateMeta(row));
            }
        }
        return legacy;
    }

    private Map<String, Object> sampleVariables(ContractDefinition definition) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("routeCode", "Y016140727");
        variables.put("routeName", "Y016140727 示范路线");
        variables.put("title", definition.label);
        variables.put("year", 2026);
        variables.put("solutionType", definition.solutionType);
        variables.put("originType", definition.originType);
        variables.put("objectType", definition.objectType);
        variables.put("objectId", "demo-object-001");
        variables.put("routeSummary", "路线范围覆盖 14.072 公里，包含评定、病害和路段业务数据。");
        variables.put("assessmentSummary", "评定结果 23 条，MQI 均值 82.4，PCI 均值 76.8。");
        variables.put("diseaseSummary", "病害 12 条，其中裂缝 7 条、坑槽 2 条、沉陷 3 条。");
        variables.put("lowScoreSections", "K0+000-K1+000、K5+200-K6+000 为重点复核区间。");
        variables.put("problemAnalysis", "病害集中区与 PCI 偏低单元重叠，应优先核实结构性损坏和排水条件。");
        variables.put("maintenanceSuggestion", "优先复核重度病害点位，结合低分单元安排处治和预防性养护。");
        variables.put("treatmentAdvice", "建议清缝灌缝、局部铣刨重铺，并复核排水条件。");
        variables.put("riskNotice", "模板体检使用样例变量，实际成果需以业务查询和现场复核为准。");
        variables.put("businessEvidenceSummary", "- gis.queryAssessmentResults：23 条\n- gis.queryDiseases：12 条");
        variables.put("stakeRange", "K0+000-K1+000");
        variables.put("unitCode", "U-DEMO-001");
        variables.put("mqi", "82.4");
        variables.put("pqi", "78.6");
        variables.put("pci", "76.8");
        variables.put("grade", "良");
        variables.put("diseaseName", "裂缝");
        variables.put("severity", "重度");
        variables.put("quantity", "127.52");
        variables.put("measureUnit", "m");
        variables.put("sectionName", "Y016140727 K0-K1");
        variables.put("lengthKm", "1.000");
        variables.put("regionLabel", "框选区域");
        variables.put("areaKm2", "2.65");
        variables.put("routeCount", "1");
        variables.put("sectionCount", "4");
        variables.put("unitCount", "23");
        variables.put("diseaseCount", "12");
        variables.put("heavyDiseaseCount", "3");
        variables.put("mediumDiseaseCount", "5");
        variables.put("avgMqi", "82.4");
        variables.put("avgPqi", "78.6");
        variables.put("avgPci", "76.8");
        variables.put("hotspotSummary", "病害热点集中在 K0+000-K1+000。");
        variables.put("regionSummary", "框选区域覆盖当前路线、4 个路段和 23 个评定单元。");
        return variables;
    }

    private Map<String, Object> check(String code,
                                      String status,
                                      String message,
                                      Map<String, Object> details) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("code", code);
        check.put("status", status);
        check.put("message", message);
        if (details != null && !details.isEmpty()) {
            check.put("details", details);
        }
        return check;
    }

    private Map<String, Object> expectedAndActualScope(ContractDefinition definition,
                                                       Map<String, Object> row) {
        Map<String, Object> details = new LinkedHashMap<>();
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("originType", definition.originType);
        expected.put("objectType", definition.objectType);
        expected.put("solutionType", definition.solutionType);
        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("originType", stringValue(row.get("origin_type")));
        actual.put("objectType", stringValue(row.get("object_type")));
        actual.put("solutionType", stringValue(row.get("solution_type")));
        details.put("expected", expected);
        details.put("actual", actual);
        return details;
    }

    private Map<String, Object> templateMeta(Map<String, Object> row) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", stringValue(row.get("id")));
        meta.put("templateCode", stringValue(row.get("template_code")));
        meta.put("templateName", stringValue(row.get("template_name")));
        meta.put("solutionType", stringValue(row.get("solution_type")));
        meta.put("originType", stringValue(row.get("origin_type")));
        meta.put("objectType", stringValue(row.get("object_type")));
        meta.put("status", stringValue(row.get("status")));
        meta.put("isDefault", boolValue(row.get("is_default")));
        meta.put("currentVersion", stringValue(row.get("current_version")));
        meta.put("priority", row.get("priority"));
        return meta;
    }

    private String aggregateStatus(List<Map<String, Object>> checks) {
        boolean hasWarn = false;
        for (Map<String, Object> check : checks) {
            String status = stringValue(check.get("status"));
            if ("ERROR".equals(status)) {
                return "ERROR";
            }
            if ("WARN".equals(status)) {
                hasWarn = true;
            }
        }
        return hasWarn ? "WARN" : "OK";
    }

    private List<String> templateCodes(List<ContractDefinition> definitions) {
        List<String> values = new ArrayList<>();
        for (ContractDefinition definition : definitions) {
            values.add(definition.templateCode);
        }
        return values;
    }

    private List<String> solutionTypes(List<ContractDefinition> definitions) {
        Set<String> values = new LinkedHashSet<>();
        for (ContractDefinition definition : definitions) {
            values.add(definition.solutionType);
        }
        return new ArrayList<>(values);
    }

    private List<ContractDefinition> defaultContracts() {
        return Arrays.asList(
                contract("solution.route_report", "生成路线养护报告", "GENERATE_ROUTE_REPORT", "ROUTE",
                        "MAP_OBJECT", "ROAD_ROUTE", "ROUTE_REPORT", "route_report_default"),
                contract("solution.section_plan", "生成路段养护计划", "GENERATE_OBJECT_SOLUTION", "OBJECT",
                        "MAP_OBJECT", "ROAD_SECTION", "SECTION_PLAN", "map_object_section_plan_default"),
                contract("solution.disease_treatment", "生成病害处置建议", "GENERATE_OBJECT_SOLUTION", "OBJECT",
                        "MAP_OBJECT", "DISEASE", "DISEASE_TREATMENT", "map_object_disease_treatment_default"),
                contract("solution.disease_review", "生成病害复核意见", "GENERATE_OBJECT_SOLUTION", "OBJECT",
                        "MAP_OBJECT", "DISEASE", "DISEASE_REVIEW", "map_object_disease_review_default"),
                contract("solution.assessment_advice", "生成评定养护建议", "GENERATE_OBJECT_SOLUTION", "OBJECT",
                        "MAP_OBJECT", "ASSESSMENT_RESULT", "EVALUATION_UNIT_ADVICE", "map_object_evaluation_unit_advice_default"),
                contract("solution.region_advice", "生成区域养护建议", "GENERATE_REGION_SOLUTION", "REGION",
                        "MAP_REGION", "MAP_REGION", "REGION_MAINTENANCE_SUGGESTION", "map_region_maintenance_advice_default")
        );
    }

    private ContractDefinition contract(String capabilityId,
                                        String label,
                                        String action,
                                        String contextScope,
                                        String originType,
                                        String objectType,
                                        String solutionType,
                                        String templateCode) {
        ContractDefinition definition = new ContractDefinition();
        definition.capabilityId = capabilityId;
        definition.label = label;
        definition.action = action;
        definition.contextScope = contextScope;
        definition.originType = originType;
        definition.objectType = objectType;
        definition.solutionType = solutionType;
        definition.templateCode = templateCode;
        return definition;
    }

    private MarkdownTemplateRenderer renderer() {
        if (markdownTemplateRenderer == null) {
            markdownTemplateRenderer = new MarkdownTemplateRenderer();
        }
        return markdownTemplateRenderer;
    }

    private boolean equalsIgnoreCase(String expected, Object actual) {
        return stringValue(expected).equalsIgnoreCase(stringValue(actual));
    }

    private boolean boolValue(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return "true".equalsIgnoreCase(stringValue(value));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class ContractDefinition {
        private String capabilityId;
        private String label;
        private String action;
        private String contextScope;
        private String originType;
        private String objectType;
        private String solutionType;
        private String templateCode;
    }
}
