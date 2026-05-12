package com.smartroad.srmp.roadasset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartroad.srmp.roadasset.dto.RoadRouteQueryDTO;
import com.smartroad.srmp.roadasset.dto.RouteCodeIdRow;
import com.smartroad.srmp.roadasset.entity.RoadRoute;
import com.smartroad.srmp.roadasset.vo.RoadRouteVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoadRouteMapper extends BaseMapper<RoadRoute> {
    Page<RoadRouteVO> selectPageVO(Page<?> page, @Param("tenantId") String tenantId, @Param("q") RoadRouteQueryDTO query);
    RoadRouteVO selectDetail(@Param("tenantId") String tenantId, @Param("id") String id);
    int insertWithGeom(RoadRoute route);
    int updateWithGeom(RoadRoute route);
    List<RoadRouteVO> selectForMap(@Param("tenantId") String tenantId, @Param("q") RoadRouteQueryDTO query);

    String selectIdByTenantAndRouteCode(@Param("tenantId") String tenantId, @Param("routeCode") String routeCode);

    /** 精确匹配一条路网（含库内规范 route_code） */
    RouteCodeIdRow selectRowByTenantAndRouteCode(@Param("tenantId") String tenantId, @Param("routeCode") String routeCode);

    /** 批量解析路网 id 与 route_code，未命中路线不会出现在结果中 */
    List<RouteCodeIdRow> selectIdsByRouteCodes(@Param("tenantId") String tenantId, @Param("codes") List<String> codes);
}
