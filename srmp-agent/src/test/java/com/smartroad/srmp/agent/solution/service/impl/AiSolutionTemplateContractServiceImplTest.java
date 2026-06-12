package com.smartroad.srmp.agent.solution.service.impl;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AiSolutionTemplateContractServiceImplTest {

    @Test
    public void defaultContractsDeclareExplicitTemplateCodes() {
        AiSolutionTemplateContractServiceImpl service = new AiSolutionTemplateContractServiceImpl();

        Map<String, Object> result = service.evaluateContractsForTest(Collections.emptyList());
        List<Map<String, Object>> contracts = contracts(result);

        assertEquals(6, contracts.size());
        assertContract(contracts, "solution.route_report", "route_report_default");
        assertContract(contracts, "solution.section_plan", "map_object_section_plan_default");
        assertContract(contracts, "solution.disease_treatment", "map_object_disease_treatment_default");
        assertContract(contracts, "solution.disease_review", "map_object_disease_review_default");
        assertContract(contracts, "solution.assessment_advice", "map_object_evaluation_unit_advice_default");
        assertContract(contracts, "solution.region_advice", "map_region_maintenance_advice_default");
    }

    @Test
    public void missingEnabledTemplateMarksContractAsError() {
        AiSolutionTemplateContractServiceImpl service = new AiSolutionTemplateContractServiceImpl();

        Map<String, Object> result = service.evaluateContractsForTest(Collections.emptyList());
        Map<String, Object> routeReport = contract(result, "solution.route_report");

        assertEquals("ERROR", routeReport.get("status"));
        assertEquals("ERROR", check(routeReport, "TEMPLATE_EXISTS").get("status"));
    }

    @Test
    public void matchingEnabledDefaultTemplatePassesContract() {
        AiSolutionTemplateContractServiceImpl service = new AiSolutionTemplateContractServiceImpl();

        Map<String, Object> result = service.evaluateContractsForTest(Collections.singletonList(
                template("map_object_disease_treatment_default", "MAP_OBJECT", "DISEASE", "DISEASE_TREATMENT", true,
                        "# {{routeCode}} 病害处置建议\n\n{{diseaseSummary}}\n\n{{maintenanceSuggestion}}\n")
        ));
        Map<String, Object> diseaseTreatment = contract(result, "solution.disease_treatment");

        assertEquals("OK", diseaseTreatment.get("status"));
        assertEquals("PASS", check(diseaseTreatment, "TEMPLATE_EXISTS").get("status"));
        assertEquals("PASS", check(diseaseTreatment, "VARIABLES_RENDER").get("status"));
    }

    @Test
    public void templateWithMissingVariablesMarksContractAsError() {
        AiSolutionTemplateContractServiceImpl service = new AiSolutionTemplateContractServiceImpl();

        Map<String, Object> result = service.evaluateContractsForTest(Collections.singletonList(
                template("route_report_default", "MAP_OBJECT", "ROAD_ROUTE", "ROUTE_REPORT", true,
                        "# {{routeCode}} 路线养护报告\n\n{{unknownVariable}}\n")
        ));
        Map<String, Object> routeReport = contract(result, "solution.route_report");

        assertEquals("ERROR", routeReport.get("status"));
        assertEquals("ERROR", check(routeReport, "VARIABLES_RENDER").get("status"));
        assertTrue(list(routeReport.get("missingVariables")).contains("unknownVariable"));
    }

    @Test
    public void scopeMismatchMarksContractAsError() {
        AiSolutionTemplateContractServiceImpl service = new AiSolutionTemplateContractServiceImpl();

        Map<String, Object> result = service.evaluateContractsForTest(Collections.singletonList(
                template("map_object_disease_review_default", "MAP_OBJECT", "ROAD_ROUTE", "DISEASE_REVIEW", true,
                        "# {{routeCode}} 病害复核意见\n\n{{maintenanceSuggestion}}\n")
        ));
        Map<String, Object> diseaseReview = contract(result, "solution.disease_review");

        assertEquals("ERROR", diseaseReview.get("status"));
        assertEquals("ERROR", check(diseaseReview, "TEMPLATE_SCOPE_MATCH").get("status"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> contracts(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("contracts");
    }

    private void assertContract(List<Map<String, Object>> contracts, String capabilityId, String templateCode) {
        Map<String, Object> contract = byId(contracts, capabilityId);
        assertEquals(templateCode, contract.get("templateCode"));
        assertFalse(String.valueOf(contract.get("templateCode")).trim().isEmpty());
    }

    private Map<String, Object> contract(Map<String, Object> result, String capabilityId) {
        return byId(contracts(result), capabilityId);
    }

    private Map<String, Object> byId(List<Map<String, Object>> contracts, String capabilityId) {
        for (Map<String, Object> contract : contracts) {
            if (capabilityId.equals(contract.get("capabilityId"))) {
                return contract;
            }
        }
        throw new AssertionError("missing contract: " + capabilityId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> check(Map<String, Object> contract, String code) {
        List<Map<String, Object>> checks = (List<Map<String, Object>>) contract.get("checks");
        for (Map<String, Object> check : checks) {
            if (code.equals(check.get("code"))) {
                return check;
            }
        }
        throw new AssertionError("missing check: " + code);
    }

    @SuppressWarnings("unchecked")
    private List<String> list(Object value) {
        return (List<String>) value;
    }

    private Map<String, Object> template(String templateCode,
                                         String originType,
                                         String objectType,
                                         String solutionType,
                                         boolean isDefault,
                                         String content) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", templateCode + "-id");
        row.put("template_code", templateCode);
        row.put("template_name", templateCode);
        row.put("origin_type", originType);
        row.put("object_type", objectType);
        row.put("solution_type", solutionType);
        row.put("status", "ENABLED");
        row.put("is_default", isDefault);
        row.put("current_version", "v1");
        row.put("content", content);
        return row;
    }
}
