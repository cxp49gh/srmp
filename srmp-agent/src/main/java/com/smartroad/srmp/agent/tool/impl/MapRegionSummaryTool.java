package com.smartroad.srmp.agent.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroad.srmp.agent.mapagent.dto.MapAiContext;
import com.smartroad.srmp.agent.tool.AiToolContext;
import com.smartroad.srmp.agent.tool.AiToolResult;
import com.smartroad.srmp.agent.tool.support.AiBusinessScope;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MapRegionSummaryTool extends AbstractJdbcAiTool {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String name() { return "gis.queryRegionSummary"; }
    @Override
    public String description() { return "查询当前路线或框选区域的路况统计摘要"; }

    @Override
    public AiToolResult execute(AiToolContext context, Map<String, Object> args) {
        long start = System.currentTimeMillis();
        try {
            AiBusinessScope scope = businessScope(context, args);
            MapAiContext map = context == null ? null : context.getMapContext();
            Map<String, Object> geometry = geometry(context, args);
            if (!geometry.isEmpty()) {
                return executeGeometrySummary(context, args, scope, geometry, start);
            }
            if (map != null && map.getRegionSummary() != null && !map.getRegionSummary().isEmpty()) {
                Map<String, Object> data = new LinkedHashMap<>(map.getRegionSummary());
                data.put("queryScope", scope.toQueryScope());
                data.put("scopeWarnings", scope.getScopeWarnings());
                data.put("returnedCount", 1);
                data.put("totalCount", 1);
                data.put("truncated", false);
                return AiToolResult.success(name(), "使用前端已传入的区域统计摘要", data, 1, System.currentTimeMillis() - start);
            }
            MapSqlParameterSource p = baseParams(context, args);
            String routeFilters = routeFilter("r") + directProjectFilter("r");
            String sectionFilters = routeFilter("s") + directProjectFilter("s") + directionFilter("s") + stakeOverlapFilter("s");
            String diseaseFilters = routeFilter("d") + directProjectFilter("d") + directionFilter("d") + stakeOverlapFilter("d");
            String assessmentFilters = routeFilter("a") + yearFilter("a") + assessmentProjectFilter("a") + sectionTierFilter("a") + directionFilter("a") + stakeOverlapFilter("a");
            String sectionTable = sectionTable(scope.getSectionTier());
            String sectionLengthKm = sectionLengthKmExpression("s", scope.getSectionTier());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("routeCount", first("select count(*) from road_route r where r.tenant_id=:tenantId and r.deleted=false " + routeFilters, p));
            data.put("sectionCount", first("select count(*) from " + sectionTable + " s where s.tenant_id=:tenantId and s.deleted=false " + sectionFilters, p));
            data.put("totalLengthKm", first("select round(coalesce(sum(" + sectionLengthKm + "),0),3) from " + sectionTable + " s where s.tenant_id=:tenantId and s.deleted=false " + sectionFilters, p));
            data.put("diseaseCount", first("select count(*) from disease_record d where d.tenant_id=:tenantId and d.deleted=false " + diseaseFilters, p));
            data.put("assessmentCount", first("select count(*) from assessment_result a where a.tenant_id=:tenantId and a.deleted=false " + assessmentFilters, p));
            data.put("avgMqi", first("select round(avg(a.mqi),3) from assessment_result a where a.tenant_id=:tenantId and a.deleted=false " + assessmentFilters, p));
            data.put("avgPqi", first("select round(avg(a.pqi),3) from assessment_result a where a.tenant_id=:tenantId and a.deleted=false " + assessmentFilters, p));
            data.put("avgPci", first("select round(avg(a.pci),3) from assessment_result a where a.tenant_id=:tenantId and a.deleted=false " + assessmentFilters, p));
            data.put("heavyDiseaseCount", first("select count(*) from disease_record d where d.tenant_id=:tenantId and d.deleted=false and d.severity='HEAVY' " + diseaseFilters, p));
            List<Map<String, Object>> diseaseByType = namedParameterJdbcTemplate.queryForList(
                    "select d.disease_name, d.severity, count(*) count from disease_record d where d.tenant_id=:tenantId and d.deleted=false " + diseaseFilters +
                            " group by d.disease_name, d.severity order by count desc limit 10", p);
            data.put("diseaseByType", diseaseByType);
            data.put("queryScope", scope.toQueryScope());
            data.put("scopeWarnings", scope.getScopeWarnings());
            data.put("returnedCount", diseaseByType.size());
            data.put("totalCount", data.get("diseaseCount"));
            data.put("truncated", false);
            return AiToolResult.success(name(), "已生成区域/路线统计摘要", data, diseaseByType.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            return failedResult(e.getMessage(), start);
        }
    }

    private AiToolResult executeGeometrySummary(AiToolContext context,
                                                Map<String, Object> args,
                                                AiBusinessScope scope,
                                                Map<String, Object> geometry,
                                                long start) throws Exception {
        MapSqlParameterSource p = baseParams(context, args);
        p.addValue("geometryGeoJson", OBJECT_MAPPER.writeValueAsString(geometry));
        String routeFilters = routeFilter("r") + directProjectFilter("r");
        String sectionFilters = routeFilter("s") + directProjectFilter("s") + directionFilter("s") + stakeOverlapFilter("s");
        String diseaseFilters = routeFilter("d") + directProjectFilter("d") + directionFilter("d") + stakeOverlapFilter("d");
        String assessmentFilters = routeFilter("a") + yearFilter("a") + assessmentProjectFilter("a") + sectionTierFilter("a") + directionFilter("a") + stakeOverlapFilter("a");
        String sectionTable = sectionTable(scope.getSectionTier());
        String sectionLengthKm = sectionLengthKmExpression("s", scope.getSectionTier());
        String assessmentFrom = " from assessment_result a "
                + "left join road_section_ledger u on u.tenant_id=a.tenant_id and a.unit_id is not null and u.id=a.unit_id and u.deleted=false "
                + "left join road_section_line ln on ln.tenant_id=a.tenant_id and a.section_id is not null and ln.id=a.section_id and ln.deleted=false "
                + "left join road_section_km km on km.tenant_id=a.tenant_id and a.object_type='ROAD_SECTION_KM' and km.id=a.object_id and km.deleted=false "
                + "left join road_section_hm hm on hm.tenant_id=a.tenant_id and a.object_type='ROAD_SECTION_HM' and hm.id=a.object_id and hm.deleted=false "
                + "where a.tenant_id=:tenantId and a.deleted=false " + assessmentFilters
                + " and COALESCE(km.geom, hm.geom, u.geom, ln.geom) is not null "
                + spatialFilter("COALESCE(km.geom, hm.geom, u.geom, ln.geom)");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("geometry", geometry);
        data.put("sourcePrecision", "POSTGIS");
        data.put("routeCount", first("select count(*) from road_route r where r.tenant_id=:tenantId and r.deleted=false " + routeFilters + spatialFilter("r.geom"), p));
        data.put("sectionCount", first("select count(*) from " + sectionTable + " s where s.tenant_id=:tenantId and s.deleted=false " + sectionFilters + spatialFilter("s.geom"), p));
        data.put("totalLengthKm", first("select round(coalesce(sum(" + sectionLengthKm + "),0),3) from " + sectionTable + " s where s.tenant_id=:tenantId and s.deleted=false " + sectionFilters + spatialFilter("s.geom"), p));
        data.put("diseaseCount", first("select count(*) from disease_record d where d.tenant_id=:tenantId and d.deleted=false " + diseaseFilters + spatialFilter("d.geom"), p));
        data.put("assessmentCount", first("select count(*) " + assessmentFrom, p));
        data.put("avgMqi", first("select round(avg(a.mqi),3) " + assessmentFrom, p));
        data.put("avgPqi", first("select round(avg(a.pqi),3) " + assessmentFrom, p));
        data.put("avgPci", first("select round(avg(a.pci),3) " + assessmentFrom, p));
        data.put("heavyDiseaseCount", first("select count(*) from disease_record d where d.tenant_id=:tenantId and d.deleted=false and d.severity='HEAVY' " + diseaseFilters + spatialFilter("d.geom"), p));
        List<Map<String, Object>> diseaseByType = namedParameterJdbcTemplate.queryForList(
                "select d.disease_name, d.severity, count(*) count from disease_record d where d.tenant_id=:tenantId and d.deleted=false " + diseaseFilters + spatialFilter("d.geom") +
                        " group by d.disease_name, d.severity order by count desc limit 10", p);
        data.put("diseaseByType", diseaseByType);
        data.put("queryScope", scope.toQueryScope());
        data.put("scopeWarnings", scope.getScopeWarnings());
        data.put("returnedCount", diseaseByType.size());
        data.put("totalCount", data.get("diseaseCount"));
        data.put("truncated", false);
        return AiToolResult.success(name(), "已按框选区域重新生成统计摘要", data, diseaseByType.size(), System.currentTimeMillis() - start);
    }

    private Object first(String sql, MapSqlParameterSource p) {
        return namedParameterJdbcTemplate.queryForObject(sql, p, Object.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> geometry(AiToolContext context, Map<String, Object> args) {
        Object raw = args == null ? null : args.get("geometry");
        if (raw instanceof Map) {
            return (Map<String, Object>) raw;
        }
        MapAiContext map = context == null ? null : context.getMapContext();
        raw = map == null ? null : map.getGeometry();
        if (raw instanceof Map) {
            return (Map<String, Object>) raw;
        }
        return new LinkedHashMap<>();
    }

    private String spatialFilter(String geomColumn) {
        return " and " + geomColumn + " is not null and ST_Intersects(" + geomColumn + ", ST_SetSRID(ST_GeomFromGeoJSON(:geometryGeoJson), 4326)) ";
    }

    private String sectionTable(String sectionTier) {
        String tier = sectionTier == null ? "" : sectionTier.trim().toUpperCase();
        if ("LEDGER".equals(tier)) {
            return "road_section_ledger";
        }
        if ("KM".equals(tier)) {
            return "road_section_km";
        }
        if ("HM".equals(tier)) {
            return "road_section_hm";
        }
        return "road_section_line";
    }

    private String sectionLengthKmExpression(String alias, String sectionTier) {
        String a = alias == null || alias.trim().isEmpty() ? "" : alias.trim() + ".";
        String tier = sectionTier == null ? "" : sectionTier.trim().toUpperCase();
        if ("LEDGER".equals(tier) || "KM".equals(tier) || "HM".equals(tier)) {
            return "coalesce(" + a + "length_m / 1000.0, abs(" + a + "end_stake - " + a + "start_stake))";
        }
        return "coalesce(" + a + "length_km, abs(" + a + "end_stake - " + a + "start_stake))";
    }
}
