package com.smartroad.srmp.agent.map;

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

    @Resource(name = "namedParameterJdbcTemplate")
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    @SuppressWarnings("unchecked")
    public MapObjectContext resolve(Map context) {
        if (context == null || context.isEmpty()) {
            return MapObjectContext.empty();
        }

        Map raw = null;
        Object mapObject = context.get("mapObject");
        if (mapObject instanceof Map) {
            raw = (Map) mapObject;
        }
        if ((raw == null || raw.isEmpty()) && context.get("selectedMapObject") instanceof Map) {
            raw = (Map) context.get("selectedMapObject");
        }
        if ((raw == null || raw.isEmpty()) && context.get("selected") instanceof Map) {
            raw = (Map) context.get("selected");
        }
        if (raw == null || raw.isEmpty()) {
            return MapObjectContext.empty();
        }

        String objectType = firstString(raw, "objectType", "object_type", "type", "layerType");
        String objectId = firstString(raw, "objectId", "object_id", "id");
        String routeCode = firstString(raw, "routeCode", "route_code");
        Integer year = firstInteger(raw, "year");

        Map detail = getObjectDetail(objectType, objectId, routeCode, year);
        Map merged = new LinkedHashMap();
        merged.putAll(raw);
        if (detail != null) {
            merged.putAll(detail);
        }
        if (objectType != null) {
            merged.put("objectType", normalizeType(objectType));
        }
        if (objectId != null) {
            merged.put("objectId", objectId);
        }
        if (routeCode != null) {
            merged.put("routeCode", routeCode);
        }
        if (year != null) {
            merged.put("year", year);
        }
        return MapObjectContext.of(merged);
    }

    @Override
    public Map getObjectDetail(String objectType, String objectId, String routeCode, Integer year) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId.trim().isEmpty()) {
            tenantId = "default";
        }
        String type = normalizeType(objectType);
        String id = objectId == null ? "" : objectId.trim();
        String rc = routeCode == null ? "" : routeCode.trim();
        Integer y = year == null ? 2026 : year;

        if ((type == null || type.isEmpty()) && !rc.isEmpty()) {
            type = "ROAD_ROUTE";
        }

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", id)
                .addValue("routeCode", rc)
                .addValue("year", y);

        if ("ROAD_ROUTE".equals(type) || "ROUTE".equals(type)) {
            return one("select 'ROAD_ROUTE' object_type,r.id,r.route_code,r.route_name,r.route_type,r.admin_grade,r.technical_grade,r.start_stake,r.end_stake,"
                    + "coalesce(sec.length_km, r.length_km) as length_km,r.adcode,"
                    + "concat('路线 ',r.route_code,' ',coalesce(r.route_name,''),'，里程 ',coalesce(coalesce(sec.length_km, r.length_km),0),' km') context_summary "
                    + "from road_route r "
                    + "left join lateral (select round(sum(coalesce(s.length_km, abs(s.end_stake - s.start_stake))),3) as length_km "
                    + "from road_section_line s where s.tenant_id = r.tenant_id and s.deleted = false "
                    + "and (s.route_id = r.id or (s.route_id is null and s.route_code = r.route_code)) "
                    + "and (r.project_id is null or s.project_id is null or s.project_id = r.project_id)) sec on true "
                    + "where r.tenant_id=:tenantId and coalesce(r.deleted,false)=false and ((:id<>'' and r.id=:id) or (:routeCode<>'' and r.route_code=:routeCode)) order by r.route_code limit 1", p);
        }
        if ("ROAD_SECTION".equals(type) || "SECTION".equals(type)) {
            return one("select 'ROAD_SECTION' object_type,id,route_id,route_code,line_code as section_code,line_name as section_name,direction,start_stake,end_stake,length_km,pavement_type,technical_grade,lane_count,road_width,adcode,concat('路段 ',route_code,' K',coalesce(start_stake,0),'—K',coalesce(end_stake,0),'，长度 ',coalesce(length_km,0),' km') context_summary from road_section_line where tenant_id=:tenantId and coalesce(deleted,false)=false and id=:id", p);
        }
        if ("EVALUATION_UNIT".equals(type) || "UNIT".equals(type) || "ROAD_EVALUATION_UNIT".equals(type)) {
            return one("select 'EVALUATION_UNIT' object_type,id,route_id,line_id as section_id,route_code,ledger_code as unit_code,direction,lane_no,start_stake,end_stake,length_m,pavement_type,technical_grade,road_width,adcode,concat('评定单元 ',route_code,' K',coalesce(start_stake,0),'—K',coalesce(end_stake,0),'，长度 ',coalesce(length_m,0),' m') context_summary from road_section_ledger where tenant_id=:tenantId and coalesce(deleted,false)=false and id=:id", p);
        }
        if ("DISEASE".equals(type) || "DISEASE_RECORD".equals(type)) {
            return one("select 'DISEASE' object_type,id,task_id,route_id,section_id,unit_id,route_code,direction,lane_no,start_stake,end_stake,disease_category,disease_type,disease_name,severity,quantity,measure_unit,damage_area,damage_length,damage_width,damage_depth,source,confidence,status,verified,concat('病害 ',route_code,' K',coalesce(start_stake,0),'，',coalesce(disease_name,disease_type),'，程度 ',coalesce(severity,''),'，数量 ',coalesce(quantity,0),coalesce(measure_unit,'')) context_summary from disease_record where tenant_id=:tenantId and coalesce(deleted,false)=false and id=:id", p);
        }
        if ("ASSESSMENT".equals(type) || "ASSESSMENT_RESULT".equals(type)) {
            return one("select 'ASSESSMENT_RESULT' object_type,id,task_id,object_type assessment_object_type,object_id,route_id,section_id,unit_id,route_code,direction,start_stake,end_stake,year,standard_code,mqi,sci,pqi,bci,tci,pci,rqi,rdi,pbi,pwi,sri,pssi,grade,zero_reason,assessed_at,concat('评定结果 ',route_code,' K',coalesce(start_stake,0),'—K',coalesce(end_stake,0),'，MQI=',coalesce(mqi,0),'，PQI=',coalesce(pqi,0),'，PCI=',coalesce(pci,0),'，等级=',coalesce(grade,'')) context_summary from assessment_result where tenant_id=:tenantId and coalesce(deleted,false)=false and ((:id<>'' and id=:id) or (:id<>'' and object_id=:id)) order by assessed_at desc nulls last limit 1", p);
        }
        return new LinkedHashMap();
    }

    private Map one(String sql, MapSqlParameterSource p) {
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(sql, p);
        return rows.isEmpty() ? new LinkedHashMap() : rows.get(0);
    }

    private String normalizeType(String objectType) {
        if (objectType == null) {
            return "";
        }
        String type = objectType.trim().toUpperCase().replace("-", "_");
        if ("ASSESSMENT".equals(type)) {
            return "ASSESSMENT_RESULT";
        }
        if ("DISEASE_RECORD".equals(type)) {
            return "DISEASE";
        }
        return type;
    }

    private String firstString(Map map, String... keys) {
        Object value = firstObject(map, keys);
        return value == null ? null : String.valueOf(value);
    }

    private Integer firstInteger(Map map, String... keys) {
        Object value = firstObject(map, keys);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private Object firstObject(Map map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && String.valueOf(value).trim().length() > 0) {
                return value;
            }
        }
        return null;
    }
}
