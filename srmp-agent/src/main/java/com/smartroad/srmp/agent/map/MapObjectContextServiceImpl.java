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
        Object mapObj = context.get("mapObject");
        if (mapObj instanceof Map) {
            return MapObjectContext.of((Map<String, Object>) mapObj);
        }
        Object selMapObj = context.get("selectedMapObject");
        if (selMapObj instanceof Map) {
            return MapObjectContext.of((Map<String, Object>) selMapObj);
        }
        Object selected = context.get("selected");
        if (selected instanceof Map) {
            return MapObjectContext.of((Map<String, Object>) selected);
        }
        return MapObjectContext.empty();
    }

    @Override
    public Map getObjectDetail(String objectType, String objectId, String routeCode, Integer year) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId.trim().isEmpty()) {
            tenantId = "default";
        }
        String type = objectType == null ? "" : objectType.trim().toUpperCase().replace("-", "_");
        String id = objectId == null ? "" : objectId.trim();
        String rc = routeCode == null ? "" : routeCode.trim();
        Integer y = year == null ? 2026 : year;
        if (type.isEmpty() && !rc.isEmpty()) {
            type = "ROAD_ROUTE";
        }
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("id", id)
                .addValue("routeCode", rc)
                .addValue("year", y);
        if ("ROAD_ROUTE".equals(type) || "ROUTE".equals(type)) {
            return one("select 'ROAD_ROUTE' object_type,id,route_code,route_name,route_type,admin_grade,technical_grade,start_stake,end_stake,length_km,adcode,concat('路线 ',route_code,' ',coalesce(route_name,''),'，里程 ',coalesce(length_km,0),' km') context_summary from road_route where tenant_id=:tenantId and coalesce(deleted,false)=false and ((:id<>'' and id=:id) or (:routeCode<>'' and route_code=:routeCode)) order by route_code limit 1", p);
        }
        if ("ROAD_SECTION".equals(type) || "SECTION".equals(type)) {
            return one("select 'ROAD_SECTION' object_type,id,route_id,route_code,section_code,section_name,direction,start_stake,end_stake,length_km,pavement_type,technical_grade,lane_count,road_width,adcode,concat('路段 ',route_code,' K',coalesce(start_stake,0),'—K',coalesce(end_stake,0),'，长度 ',coalesce(length_km,0),' km') context_summary from road_section where tenant_id=:tenantId and coalesce(deleted,false)=false and id=:id", p);
        }
        if ("EVALUATION_UNIT".equals(type) || "UNIT".equals(type) || "ROAD_EVALUATION_UNIT".equals(type)) {
            return one("select 'EVALUATION_UNIT' object_type,id,route_id,section_id,route_code,unit_code,direction,lane_no,start_stake,end_stake,length_m,pavement_type,technical_grade,road_width,adcode,concat('评定单元 ',route_code,' K',coalesce(start_stake,0),'—K',coalesce(end_stake,0),'，长度 ',coalesce(length_m,0),' m') context_summary from road_evaluation_unit where tenant_id=:tenantId and coalesce(deleted,false)=false and id=:id", p);
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
}
