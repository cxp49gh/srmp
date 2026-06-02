package com.smartroad.srmp.agent.service.impl;

import com.smartroad.srmp.agent.dto.AgentAnalysisRequest;
import com.smartroad.srmp.agent.service.AgentDataQueryService;
import com.smartroad.srmp.tenant.context.TenantContextHolder;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 统计查询修复版。
 *
 * <p>当前主分支中的 AgentDataQueryServiceImpl 使用了类似：
 * <pre>
 * nullif(:grade, '') is not distinct from a.grade
 * </pre>
 * 的可选条件写法。当参数为空字符串时，该条件会变成只查询 grade is null，
 * 导致“评定结果总数 0，但低分列表有数据”的矛盾结果。</p>
 *
 * <p>本类使用 @Service("dataQueryService") + @Primary 接管 AgentDataQueryService 注入，
 * 避免直接覆盖旧文件造成补丁冲突。</p>
 */
@Primary
@Service("dataQueryService")
public class AgentDataQueryServiceFixedImpl implements AgentDataQueryService {

    @Resource
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public Map routeSummary(AgentAnalysisRequest request) {
        MapSqlParameterSource params = baseParams(request);
        String sql =
                "select " +
                " count(distinct r.id) as route_count, " +
                routeLengthSubquery() + " as route_length_km, " +
                " count(distinct d.id) as disease_count, " +
                " count(distinct a.id) as assessment_count, " +
                " round(avg(a.mqi),3) as avg_mqi, " +
                " round(avg(a.pqi),3) as avg_pqi, " +
                " round(avg(a.pci),3) as avg_pci " +
                " from road_route r " +
                " left join disease_record d " +
                "        on d.tenant_id=r.tenant_id " +
                "       and d.route_code=r.route_code " +
                "       and d.deleted=false " +
                " left join assessment_result a " +
                "        on a.tenant_id=r.tenant_id " +
                "       and a.route_code=r.route_code " +
                "       and a.deleted=false " +
                " where r.tenant_id=:tenantId " +
                "   and r.deleted=false " +
                optionalRoute("r") +
                optionalYear("a");
        return queryOne(sql, params);
    }

    @Override
    public Map diseaseSummary(AgentAnalysisRequest request) {
        MapSqlParameterSource params = baseParams(request);
        String sql =
                "select " +
                " count(*) as disease_count, " +
                " coalesce(sum(case when severity='HEAVY' then 1 else 0 end),0) as heavy_count, " +
                " coalesce(sum(case when severity='MEDIUM' then 1 else 0 end),0) as medium_count, " +
                " coalesce(sum(case when severity='LIGHT' then 1 else 0 end),0) as light_count, " +
                " coalesce(sum(damage_area),0) as total_damage_area, " +
                " coalesce(sum(damage_length),0) as total_damage_length " +
                " from disease_record d " +
                " where d.tenant_id=:tenantId " +
                "   and d.deleted=false " +
                optionalRoute("d") +
                optionalDiseaseType() +
                optionalSeverity();
        return queryOne(sql, params);
    }

    @Override
    public Map assessmentSummary(AgentAnalysisRequest request) {
        MapSqlParameterSource params = baseParams(request);
        String sql =
                "select " +
                " count(*) as assessment_count, " +
                " round(avg(mqi),3) as avg_mqi, " +
                " round(avg(pqi),3) as avg_pqi, " +
                " round(avg(pci),3) as avg_pci, " +
                " coalesce(sum(case when grade='EXCELLENT' then 1 else 0 end),0) as excellent_count, " +
                " coalesce(sum(case when grade='GOOD' then 1 else 0 end),0) as good_count, " +
                " coalesce(sum(case when grade='MEDIUM' then 1 else 0 end),0) as medium_count, " +
                " coalesce(sum(case when grade='POOR' then 1 else 0 end),0) as poor_count, " +
                " coalesce(sum(case when grade='BAD' then 1 else 0 end),0) as bad_count " +
                " from assessment_result a " +
                " where a.tenant_id=:tenantId " +
                "   and a.deleted=false " +
                optionalRoute("a") +
                optionalYear("a") +
                optionalGrade("a");
        return queryOne(sql, params);
    }

    @Override
    public List<Map<String, Object>> topDiseaseUnits(AgentAnalysisRequest request, int limit) {
        MapSqlParameterSource params = baseParams(request).addValue("limit", limit);
        String sql =
                "select route_code, unit_id, min(start_stake) as start_stake, max(end_stake) as end_stake, " +
                " count(*) as disease_count, " +
                " coalesce(sum(case when severity='HEAVY' then 1 else 0 end),0) as heavy_count " +
                " from disease_record d " +
                " where d.tenant_id=:tenantId " +
                "   and d.deleted=false " +
                optionalRoute("d") +
                optionalDiseaseType() +
                optionalSeverity() +
                " group by route_code, unit_id " +
                " order by heavy_count desc, disease_count desc " +
                " limit :limit";
        return namedParameterJdbcTemplate.queryForList(sql, params);
    }

    @Override
    public List<Map<String, Object>> poorAssessmentResults(AgentAnalysisRequest request, int limit) {
        MapSqlParameterSource params = baseParams(request).addValue("limit", limit);
        String sql =
                "select id, route_code, unit_id, start_stake, end_stake, mqi, pqi, pci, grade " +
                " from assessment_result a " +
                " where a.tenant_id=:tenantId " +
                "   and a.deleted=false " +
                optionalRoute("a") +
                optionalYear("a") +
                " and (grade in ('POOR','BAD') or coalesce(mqi,100) < 70 or coalesce(pci,100) < 70) " +
                " order by coalesce(mqi, 999), coalesce(pci, 999) " +
                " limit :limit";
        return namedParameterJdbcTemplate.queryForList(sql, params);
    }

    @Override
    public List<String> findAssessmentIdsForMapQuery(String routeCode, Integer year, String grade, String indexCode) {
        AgentAnalysisRequest request = new AgentAnalysisRequest();
        request.setRouteCode(routeCode);
        request.setYear(year);
        request.setGrade(grade);
        request.setIndexCode(indexCode);

        MapSqlParameterSource params = baseParams(request);
        String sql =
                "select id from assessment_result a " +
                " where a.tenant_id=:tenantId " +
                "   and a.deleted=false " +
                optionalRoute("a") +
                optionalYear("a") +
                optionalGrade("a") +
                optionalIndexThreshold("a") +
                " order by start_stake limit 500";
        return namedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getString("id"));
    }

    @Override
    public List<String> findDiseaseIdsForMapQuery(String routeCode, String diseaseType, String severity) {
        AgentAnalysisRequest request = new AgentAnalysisRequest();
        request.setRouteCode(routeCode);
        request.setDiseaseType(diseaseType);
        request.setSeverity(severity);

        MapSqlParameterSource params = baseParams(request);
        String sql =
                "select id from disease_record d " +
                " where d.tenant_id=:tenantId " +
                "   and d.deleted=false " +
                optionalRoute("d") +
                optionalDiseaseType() +
                optionalSeverity() +
                " order by start_stake limit 500";
        return namedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getString("id"));
    }

    private Map queryOne(String sql, MapSqlParameterSource params) {
        List<Map<String, Object>> list = namedParameterJdbcTemplate.queryForList(sql, params);
        return list.isEmpty() ? new LinkedHashMap<>() : list.get(0);
    }

    private MapSqlParameterSource baseParams(AgentAnalysisRequest request) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("tenantId", TenantContextHolder.getTenantId());
        params.addValue("routeCode", normalize(request == null ? null : request.getRouteCode()));
        params.addValue("year", request == null || request.getYear() == null ? "" : String.valueOf(request.getYear()));
        params.addValue("grade", normalize(request == null ? null : request.getGrade()));
        params.addValue("diseaseType", normalize(request == null ? null : request.getDiseaseType()));
        params.addValue("severity", normalize(request == null ? null : request.getSeverity()));
        params.addValue("indexCode", normalize(request == null ? null : request.getIndexCode()));
        return params;
    }

    /**
     * 参数为空时不过滤；参数不为空时按参数过滤。
     */
    private String optionalRoute(String alias) {
        return " and (:routeCode = '' or " + alias + ".route_code = :routeCode) ";
    }

    private String optionalYear(String alias) {
        return " and (:year = '' or cast(" + alias + ".year as text) = :year) ";
    }

    private String optionalGrade(String alias) {
        return " and (:grade = '' or " + alias + ".grade = :grade) ";
    }

    private String optionalDiseaseType() {
        return " and (:diseaseType = '' or d.disease_type = :diseaseType) ";
    }

    private String optionalSeverity() {
        return " and (:severity = '' or d.severity = :severity) ";
    }

    private String routeLengthSubquery() {
        return " coalesce((select round(sum(coalesce(s.length_km, abs(s.end_stake - s.start_stake))),3) "
                + "from road_section_line s "
                + "where s.tenant_id=:tenantId and s.deleted=false "
                + "and exists (select 1 from road_route rr "
                + "where rr.tenant_id=:tenantId and rr.deleted=false and rr.route_code=s.route_code "
                + optionalRoute("rr")
                + ")),0) ";
    }

    /**
     * 地图联动中如果传入 indexCode=PCI/MQI/PQI/RQI/RDI，
     * 默认返回该指标小于 70 的对象；如果没有传 indexCode，则不过滤。
     */
    private String optionalIndexThreshold(String alias) {
        return " and (:indexCode = '' " +
                " or (:indexCode = 'MQI' and coalesce(" + alias + ".mqi,100) < 70) " +
                " or (:indexCode = 'PQI' and coalesce(" + alias + ".pqi,100) < 70) " +
                " or (:indexCode = 'PCI' and coalesce(" + alias + ".pci,100) < 70) " +
                " or (:indexCode = 'RQI' and coalesce(" + alias + ".rqi,100) < 70) " +
                " or (:indexCode = 'RDI' and coalesce(" + alias + ".rdi,100) < 70) " +
                " or (:indexCode = 'SCI' and coalesce(" + alias + ".sci,100) < 70) " +
                " or (:indexCode = 'BCI' and coalesce(" + alias + ".bci,100) < 70) " +
                " or (:indexCode = 'TCI' and coalesce(" + alias + ".tci,100) < 70) " +
                " ) ";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
