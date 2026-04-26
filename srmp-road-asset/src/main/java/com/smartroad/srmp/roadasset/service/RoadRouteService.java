package com.smartroad.srmp.roadasset.service;
import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.roadasset.dto.RoadRouteQueryDTO;
import com.smartroad.srmp.roadasset.dto.RoadRouteSaveDTO;
import com.smartroad.srmp.roadasset.vo.RoadRouteVO;
import java.util.List;
public interface RoadRouteService {
    PageResult<RoadRouteVO> page(RoadRouteQueryDTO query);
    RoadRouteVO getById(String id);
    String create(RoadRouteSaveDTO dto);
    void update(String id, RoadRouteSaveDTO dto);
    void delete(String id);
    List<RoadRouteVO> listForMap(RoadRouteQueryDTO query);
}
