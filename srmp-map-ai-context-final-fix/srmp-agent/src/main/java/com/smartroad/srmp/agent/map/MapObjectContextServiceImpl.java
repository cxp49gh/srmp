package com.smartroad.srmp.agent.map.impl;

import com.smartroad.srmp.agent.map.MapObjectContext;
import com.smartroad.srmp.agent.map.MapObjectContextService;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MapObjectContextServiceImpl implements MapObjectContextService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public MapObjectContext resolve(Map context) {
        MapObjectContext result = new MapObjectContext();
        Map mapObject = extractMapObject(context);
        if (mapObject == null || mapObject.isEmpty()) {
            return result;
        }

        String objectType = asString(firstNonNull(mapObject.get("objectType"), mapObject.get("type")));
        String objectId = asString(firstNonNull(mapObject.get("id"), mapObject.get("objectId")));
        if (isBlank(objectType)) {
            return result;
        }

        String normalizedType = objectType.trim().toUpperCase();
        Map<String, Object> detail = new LinkedHashMap<>();
        if (!isBlank(objectId)) {
            detail = loadDetail(normalizedType, objectId);
        }
        if (detail.isEmpty()) {
            for (Object key : mapObject.keySet()) {
                detail.put(String.valueOf(key), mapObject.get(key));
            }
        }

        result.setPresent(true);
        result.setObjectType(normalizedType);
        result.setObjectId(objectId);
        result.setDetail(detail);
        result.setRouteCode(asString(firstNonNull(detail.get("route_code"), detail.get("routeCode"), mapObject.get("routeCode"))));
        result.setYear(asInteger(firstNonNull(detail.get("year"), mapObject.get("year"), context == null ? null : context.get("year"))));
        result.setMarkdown(buildMarkdown(normalizedType, objectId, detail));
        return result;
    }

    @Override
    public Map<String, Object> getObjectDetail(String objectType, String objectId, String routeCode, Integer year) {
        if (isBlank(objectType)) {
            return new LinkedHashMap<>();
        }
        String normalized = objectType.trim().toUpperCase();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("id", objectId)
                .addValue("routeCode", routeCode);
        String sql = sqlByType(normalized);
        if (sql == null) {
            return new LinkedHashMap<>();
        }
        List<Map<String, Object>> list = namedParameterJdbcTemplate.queryForList(sql, params);
        return list.isEmpty() ? new LinkedHashMap<>() : list.get(0);
    }

    private Map extractMapObject(Map context) {
        if (context == null) {
            return null;
        }
        Object value = firstNonNull(
                context.get("mapObject"),
                context.get("selectedMapObject"),
                context.get("selected")
        );
        return value instanceof Map ? (Map) value : null;
    }

    private Map<String, Object> loadDetail(String objectType, String id) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", TenantContextHolder.getTenantId())
                .addValue("id", id);
        String sql = sqlByType(objectType);
        if (sql == null) {
            return new LinkedHashMap<>();
        }
        List<Map<String, Object>> list = namedParameterJdbcTemplate.queryForList(sql, params);
        return list.isEmpty() ? new LinkedHashMap<>() : list.get(0);
    }

    private String sqlByType(String objectType) {
        if ("ROAD_ROUTE".equals(objectType)) {
            return "select id, route_code, route_name, route_type, admin_grade, technical_grade, start_stake, end_stake, length_km, adcode " +
                    "from road_route where tenant_id=:tenantId and id=:id and coalesce(deleted,false)=false";
        }
        if ("ROAD_SECTION".equals(objectType)) {
            return "select id, route_id, route_code, section_code, section_name, direction, start_stake, end_stake, length_km, pavement_type, lane_count, road_width " +
                    "from road_section where tenant_id=:tenantId and id=:id and coalesce(deleted,false)=false";
        }
        if ("EVALUATION_UNIT".equals(objectType)) {
            return "select id, route_id, section_id, route_code, unit_code, direction, lane_no, start_stake, end_stake, length_m, pavement_type, road_width " +
                    "from road_evaluation_unit where tenant_id=:tenantId and id=:id and coalesce(deleted,false)=false";
        }
        if ("DISEASE".equals(objectType)) {
            return "select id, route_id, section_id, unit_id, route_code, direction, lane_no, start_stake, end_stake, disease_category, disease_type, disease_name, severity, quantity, measure_unit, damage_area, damage_length, status, verified " +
                    "from disease_record where tenant_id=:tenantId and id=:id and coalesce(deleted,false)=false";
        }
        if ("ASSESSMENT".equals(objectType) || "ASSESSMENT_RESULT".equals(objectType)) {
            return "select id, object_type, object_id, route_id, section_id, unit_id, route_code, direction, start_stake, end_stake, year, mqi, sci, pqi, bci, tci, pci, rqi, rdi, pbi, pwi, sri, pssi, grade " +
                    "from assessment_result where tenant_id=:tenantId and id=:id and coalesce(deleted,false)=false";
        }
        return null;
    }

    private String buildMarkdown(String objectType, String objectId, Map<String, Object> detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("【当前地图选中对象】\n");
        sb.append("对象类型：").append(objectType).append("\n");
        if (!isBlank(objectId)) {
            sb.append("对象ID：").append(objectId).append("\n");
        }
        append(sb, "路线编号", firstNonNull(detail.get("route_code"), detail.get("routeCode")));
        append(sb, "路线名称", firstNonNull(detail.get("route_name"), detail.get("routeName")));
        append(sb, "路段名称", firstNonNull(detail.get("section_name"), detail.get("sectionName")));
        append(sb, "评定单元", firstNonNull(detail.get("unit_code"), detail.get("unitCode"), detail.get("unit_id")));
        append(sb, "方向", firstNonNull(detail.get("direction")));
        append(sb, "起点桩号", firstNonNull(detail.get("start_stake"), detail.get("startStake")));
        append(sb, "终点桩号", firstNonNull(detail.get("end_stake"), detail.get("endStake")));
        append(sb, "年度", detail.get("year"));
        append(sb, "MQI", detail.get("mqi"));
        append(sb, "PQI", detail.get("pqi"));
        append(sb, "PCI", detail.get("pci"));
        append(sb, "等级", detail.get("grade"));
        append(sb, "病害类型", firstNonNull(detail.get("disease_name"), detail.get("diseaseName"), detail.get("disease_type")));
        append(sb, "严重程度", firstNonNull(detail.get("severity")));
        append(sb, "数量", firstNonNull(detail.get("quantity")));
        append(sb, "单位", firstNonNull(detail.get("measure_unit"), detail.get("measureUnit")));
        sb.append("请优先围绕该地图对象回答；如果用户说\"这个/当前/该路段/该病害\"，均指当前地图选中对象。\n");
        return sb.toString();
    }

    private void append(StringBuilder sb, String label, Object value) {
        if (value != null && String.valueOf(value).trim().length() > 0) {
            sb.append(label).append("：").append(value).append("\n");
        }
    }

    private Object firstNonNull(Object... values) {
        if (values == null) return null;
        for (Object value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer asInteger(Object value) {
        if (value == null) return null;
        try { return Integer.valueOf(String.valueOf(value)); } catch (Exception e) { return null; }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}