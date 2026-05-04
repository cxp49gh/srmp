package com.smartroad.srmp.gis.service.impl;

import com.smartroad.srmp.gis.dto.MapRegionAnalysisRequest;
import com.smartroad.srmp.gis.service.GisMapSupportService;
import com.smartroad.srmp.gis.service.MapRegionAnalysisService;
import com.smartroad.srmp.gis.vo.GeoJsonFeatureCollectionVO;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GisMapSupportServiceImpl implements GisMapSupportService {

    private static final String DEFAULT_YEAR = "";

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Resource
    private MapRegionAnalysisService mapRegionAnalysisService;

    @Override
    public Map<String, Object> mapStatistics(Map<String, Object> request) {
        MapSqlParameterSource params = baseParams(request);
        Set<String> enabledLayers = enabledLayers(request);
        String routeFilter = optionalRoute("");
        String yearFilter = optionalYear("");
        String gradeFilter = optionalGrade("");
        String bboxFilter = bboxSpatialFilter("geom");

        boolean routeLayerEnabled = isLayerEnabled(enabledLayers, "ROAD_ROUTE");
        boolean sectionLayerEnabled = isLayerEnabled(enabledLayers, "ROAD_SECTION");
        boolean unitLayerEnabled = isLayerEnabled(enabledLayers, "EVALUATION_UNIT");
        boolean diseaseLayerEnabled = isLayerEnabled(enabledLayers, "DISEASE");
        boolean assessmentLayerEnabled = isLayerEnabled(enabledLayers, "ASSESSMENT_RESULT") || isLayerEnabled(enabledLayers, "ASSESSMENT");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabledLayers", new ArrayList<>(enabledLayers));
        result.put("bbox", Boolean.TRUE.equals(params.getValue("hasBbox")) ? bboxResult(params) : Collections.emptyList());
        result.put("filteredByBbox", Boolean.TRUE.equals(params.getValue("hasBbox")));
        result.put("selectedMetricCode", params.getValue("indexCode"));

        result.put("totalLengthKm", routeLayerEnabled ? firstValue(
                "select coalesce(round(sum(length_km)::numeric,3),0) from road_route where tenant_id=:tenantId and deleted=false " + routeFilter + bboxFilter,
                params
        ) : BigDecimal.ZERO);
        result.put("routeCount", routeLayerEnabled ? firstValue(
                "select count(*) from road_route where tenant_id=:tenantId and deleted=false " + routeFilter + bboxFilter,
                params
        ) : 0);
        result.put("sectionCount", sectionLayerEnabled ? firstValue(
                "select count(*) from road_section where tenant_id=:tenantId and deleted=false " + routeFilter + bboxFilter,
                params
        ) : 0);
        result.put("unitCount", unitLayerEnabled ? firstValue(
                "select count(*) from road_evaluation_unit where tenant_id=:tenantId and deleted=false " + routeFilter + bboxFilter,
                params
        ) : 0);

        result.put("diseaseCount", diseaseLayerEnabled ? firstValue(
                "select count(*) from disease_record where tenant_id=:tenantId and deleted=false " +
                        routeFilter + optionalDiseaseType("") + optionalSeverity("") + bboxFilter,
                params
        ) : 0);
        result.put("heavyDiseaseCount", diseaseLayerEnabled ? firstValue(
                "select count(*) from disease_record where tenant_id=:tenantId and deleted=false and severity='HEAVY' " +
                        routeFilter + optionalDiseaseType("") + bboxFilter,
                params
        ) : 0);

        Map<String, Object> assessment = assessmentLayerEnabled ? queryOne(
                "select count(*) as assessment_count, " +
                        "round(avg(ar.mqi)::numeric,3) as avg_mqi, " +
                        "round(avg(ar.sci)::numeric,3) as avg_sci, " +
                        "round(avg(ar.pqi)::numeric,3) as avg_pqi, " +
                        "round(avg(ar.bci)::numeric,3) as avg_bci, " +
                        "round(avg(ar.tci)::numeric,3) as avg_tci, " +
                        "round(avg(ar.pci)::numeric,3) as avg_pci, " +
                        "round(avg(ar.rqi)::numeric,3) as avg_rqi, " +
                        "round(avg(ar.rdi)::numeric,3) as avg_rdi, " +
                        "round(avg(ar.pbi)::numeric,3) as avg_pbi, " +
                        "round(avg(ar.pwi)::numeric,3) as avg_pwi, " +
                        "round(avg(ar.sri)::numeric,3) as avg_sri, " +
                        "round(avg(ar.pssi)::numeric,3) as avg_pssi, " +
                        "sum(case when ar.grade in ('EXCELLENT','GOOD') then 1 else 0 end) as good_count, " +
                        "sum(case when ar.grade in ('POOR','BAD') then 1 else 0 end) as poor_count " +
                        "from assessment_result ar " +
                        "left join road_evaluation_unit u on u.tenant_id=ar.tenant_id and u.id=ar.unit_id and u.deleted=false " +
                        "where ar.tenant_id=:tenantId and ar.deleted=false " +
                        optionalRoute("ar") + yearFilter + gradeFilter + bboxSpatialFilter("u.geom"),
                params
        ) : emptyAssessment();

        putAssessmentMetrics(result, assessment, safe(params.getValue("indexCode")));
        return result;
    }

    @Override
    public Map<String, Object> objectDetail(String objectType, String id) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("id", id);
        String type = objectType == null ? "" : objectType.trim().toUpperCase();
        if ("ROAD_ROUTE".equals(type)) {
            return queryOne("select id, route_code, route_name, route_type, technical_grade, start_stake, end_stake, length_km from road_route where tenant_id=:tenantId and id=:id and deleted=false", params);
        }
        if ("ROAD_SECTION".equals(type)) {
            return queryOne("select id, route_code, section_code, section_name, direction, start_stake, end_stake, length_km, pavement_type from road_section where tenant_id=:tenantId and id=:id and deleted=false", params);
        }
        if ("EVALUATION_UNIT".equals(type)) {
            return queryOne("select id, route_code, unit_code, direction, lane_no, start_stake, end_stake, length_m from road_evaluation_unit where tenant_id=:tenantId and id=:id and deleted=false", params);
        }
        if ("DISEASE".equals(type)) {
            return queryOne("select id, route_code, disease_type, disease_name, severity, start_stake, end_stake, quantity, measure_unit, status, verified from disease_record where tenant_id=:tenantId and id=:id and deleted=false", params);
        }
        if ("ASSESSMENT".equals(type) || "ASSESSMENT_RESULT".equals(type)) {
            return queryOne("select id, route_code, unit_id, start_stake, end_stake, year, mqi, sci, pqi, bci, tci, pci, rqi, rdi, pbi, pwi, sri, pssi, grade from assessment_result where tenant_id=:tenantId and id=:id and deleted=false", params);
        }
        return Collections.emptyMap();
    }

    @Override
    @SuppressWarnings("unchecked")
    public GeoJsonFeatureCollectionVO spatialQuery(Map<String, Object> query) {
        MapRegionAnalysisRequest request = new MapRegionAnalysisRequest();
        request.setGeometry(query == null ? null : (Map<String, Object>) query.get("geometry"));
        request.setQuery(query == null ? null : (Map<String, Object>) query.get("query"));
        request.setLayers(query == null ? null : (List<String>) query.get("layers"));
        request.setOptions(query == null ? null : (Map<String, Object>) query.get("options"));
        mapRegionAnalysisService.analyze(request);
        return new GeoJsonFeatureCollectionVO();
    }

    private MapSqlParameterSource baseParams(Map<String, Object> request) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("tenantId", TenantContextHolder.getTenantId());
        params.addValue("routeCode", getString(request, "routeCode") == null ? "" : getString(request, "routeCode"));
        params.addValue("year", getString(request, "year") == null ? DEFAULT_YEAR : getString(request, "year"));
        params.addValue("grade", getString(request, "grade") == null ? "" : getString(request, "grade"));
        params.addValue("indexCode", normalizeIndexCode(getString(request, "indexCode")));
        params.addValue("diseaseType", getString(request, "diseaseType") == null ? "" : getString(request, "diseaseType"));
        params.addValue("severity", getString(request, "severity") == null ? "" : getString(request, "severity"));
        addBboxParams(params, request);
        return params;
    }

    private String optionalRoute(String alias) {
        String prefix = alias == null || alias.isEmpty() ? "" : alias + ".";
        return " and (nullif(:routeCode, '') is null or " + prefix + "route_code = nullif(:routeCode, '')) ";
    }

    private String optionalYear(String alias) {
        String prefix = alias == null || alias.isEmpty() ? "" : alias + ".";
        return " and (nullif(:year, '') is null or cast(" + prefix + "year as text) = nullif(:year, '')) ";
    }

    private String optionalGrade(String alias) {
        String prefix = alias == null || alias.isEmpty() ? "" : alias + ".";
        return " and (nullif(:grade, '') is null or " + prefix + "grade = nullif(:grade, '')) ";
    }

    private String optionalDiseaseType(String alias) {
        String prefix = alias == null || alias.isEmpty() ? "" : alias + ".";
        return " and (nullif(:diseaseType, '') is null or " + prefix + "disease_type = nullif(:diseaseType, '')) ";
    }

    private String optionalSeverity(String alias) {
        String prefix = alias == null || alias.isEmpty() ? "" : alias + ".";
        return " and (nullif(:severity, '') is null or " + prefix + "severity = nullif(:severity, '')) ";
    }

    private String bboxSpatialFilter(String column) {
        return " and (:hasBbox = false or (" + column + " is not null and ST_Intersects(" + column + ", ST_MakeEnvelope(:west,:south,:east,:north,4326)))) ";
    }

    @SuppressWarnings("unchecked")
    private Set<String> enabledLayers(Map<String, Object> request) {
        Object value = request == null ? null : request.get("enabledLayers");
        if (value == null && request != null) {
            value = request.get("layers");
        }
        Set<String> layers = new LinkedHashSet<>();
        if (value instanceof Iterable) {
            for (Object item : (Iterable<Object>) value) {
                addLayer(layers, item);
            }
        } else if (value instanceof String) {
            String[] parts = String.valueOf(value).split(",");
            for (String part : parts) {
                addLayer(layers, part);
            }
        }
        if (layers.isEmpty()) {
            layers.add("ROAD_ROUTE");
            layers.add("ROAD_SECTION");
            layers.add("EVALUATION_UNIT");
            layers.add("DISEASE");
            layers.add("ASSESSMENT_RESULT");
        }
        return layers;
    }

    private void addLayer(Set<String> layers, Object item) {
        if (item == null) {
            return;
        }
        String layer = String.valueOf(item).trim().toUpperCase();
        if (layer.length() == 0) {
            return;
        }
        if ("ASSESSMENT".equals(layer)) {
            layer = "ASSESSMENT_RESULT";
        }
        layers.add(layer);
    }

    private boolean isLayerEnabled(Set<String> layers, String layer) {
        return layers.contains(layer == null ? "" : layer.toUpperCase());
    }

    @SuppressWarnings("unchecked")
    private void addBboxParams(MapSqlParameterSource params, Map<String, Object> request) {
        double[] bbox = parseBbox(request == null ? null : request.get("bbox"));
        if (bbox == null && request != null && request.get("viewport") instanceof Map) {
            bbox = parseBbox(((Map<String, Object>) request.get("viewport")).get("bbox"));
        }
        params.addValue("hasBbox", bbox != null);
        if (bbox != null) {
            params.addValue("west", bbox[0]);
            params.addValue("south", bbox[1]);
            params.addValue("east", bbox[2]);
            params.addValue("north", bbox[3]);
        } else {
            params.addValue("west", 0D);
            params.addValue("south", 0D);
            params.addValue("east", 0D);
            params.addValue("north", 0D);
        }
    }

    @SuppressWarnings("unchecked")
    private double[] parseBbox(Object bbox) {
        if (!(bbox instanceof List) || ((List<Object>) bbox).size() != 4) {
            return null;
        }
        List<Object> list = (List<Object>) bbox;
        Double a = toDouble(list.get(0));
        Double b = toDouble(list.get(1));
        Double c = toDouble(list.get(2));
        Double d = toDouble(list.get(3));
        if (a == null || b == null || c == null || d == null) {
            return null;
        }
        double west = Math.min(a, c);
        double east = Math.max(a, c);
        double south = Math.min(b, d);
        double north = Math.max(b, d);
        if (west < -180 || east > 180 || south < -90 || north > 90 || west == east || south == north) {
            return null;
        }
        return new double[]{west, south, east, north};
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(String.valueOf(value));
            return Double.isFinite(parsed) ? parsed : null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<Double> bboxResult(MapSqlParameterSource params) {
        List<Double> bbox = new ArrayList<>();
        bbox.add((Double) params.getValue("west"));
        bbox.add((Double) params.getValue("south"));
        bbox.add((Double) params.getValue("east"));
        bbox.add((Double) params.getValue("north"));
        return bbox;
    }

    private Object firstValue(String sql, MapSqlParameterSource params) {
        return namedParameterJdbcTemplate.queryForObject(sql, params, Object.class);
    }

    private Map<String, Object> queryOne(String sql, MapSqlParameterSource params) {
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(sql, params);
        return rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
    }

    private String getString(Map<String, Object> request, String key) {
        if (request == null || request.get(key) == null) {
            return null;
        }
        return String.valueOf(request.get(key)).trim();
    }

    private void putAssessmentMetrics(Map<String, Object> result, Map<String, Object> assessment, String selectedMetric) {
        result.put("assessmentCount", valueOrZero(assessment.get("assessment_count")));
        result.put("avgMqi", assessment.get("avg_mqi"));
        result.put("avgSci", assessment.get("avg_sci"));
        result.put("avgPqi", assessment.get("avg_pqi"));
        result.put("avgBci", assessment.get("avg_bci"));
        result.put("avgTci", assessment.get("avg_tci"));
        result.put("avgPci", assessment.get("avg_pci"));
        result.put("avgRqi", assessment.get("avg_rqi"));
        result.put("avgRdi", assessment.get("avg_rdi"));
        result.put("avgPbi", assessment.get("avg_pbi"));
        result.put("avgPwi", assessment.get("avg_pwi"));
        result.put("avgSri", assessment.get("avg_sri"));
        result.put("avgPssi", assessment.get("avg_pssi"));
        result.put("excellentGoodRate", rate(assessment.get("good_count"), assessment.get("assessment_count")));
        result.put("poorBadRate", rate(assessment.get("poor_count"), assessment.get("assessment_count")));
        Object selectedMetricAvg = selectedMetricAverage(assessment, selectedMetric);
        result.put("selectedMetricAvg", selectedMetricAvg);
        result.put("selectedMetricGrade", gradeFromScore(selectedMetricAvg));
    }

    private Map<String, Object> emptyAssessment() {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("assessment_count", 0);
        empty.put("good_count", 0);
        empty.put("poor_count", 0);
        return empty;
    }

    private Object selectedMetricAverage(Map<String, Object> assessment, String metric) {
        String key = "avg_" + normalizeIndexCode(metric).toLowerCase();
        return assessment.get(key);
    }

    private String normalizeIndexCode(String indexCode) {
        String code = indexCode == null || indexCode.trim().isEmpty() ? "MQI" : indexCode.trim().toUpperCase();
        if ("ASSESSMENT".equals(code) || "ASSESSMENT_RESULT".equals(code)) {
            return "MQI";
        }
        if ("MQI".equals(code) || "SCI".equals(code) || "PQI".equals(code) || "BCI".equals(code) || "TCI".equals(code)
                || "PCI".equals(code) || "RQI".equals(code) || "RDI".equals(code) || "PBI".equals(code)
                || "PWI".equals(code) || "SRI".equals(code) || "PSSI".equals(code)) {
            return code;
        }
        return "MQI";
    }

    private Object valueOrZero(Object value) {
        return value == null ? 0 : value;
    }

    private String gradeFromScore(Object value) {
        if (value == null) {
            return null;
        }
        BigDecimal score = toBigDecimal(value);
        if (score.compareTo(new BigDecimal("90")) >= 0) {
            return "EXCELLENT";
        }
        if (score.compareTo(new BigDecimal("80")) >= 0) {
            return "GOOD";
        }
        if (score.compareTo(new BigDecimal("70")) >= 0) {
            return "FAIR";
        }
        if (score.compareTo(new BigDecimal("60")) >= 0) {
            return "POOR";
        }
        return "BAD";
    }

    private BigDecimal rate(Object numerator, Object denominator) {
        BigDecimal n = toBigDecimal(numerator);
        BigDecimal d = toBigDecimal(denominator);
        if (d.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return n.multiply(new BigDecimal("100")).divide(d, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
