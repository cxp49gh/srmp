package com.smartroad.srmp.roadasset.service;
import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.roadasset.dto.RoadSectionQueryDTO;
import com.smartroad.srmp.roadasset.dto.RoadSectionSaveDTO;
import com.smartroad.srmp.roadasset.vo.RoadSectionVO;
import java.util.List;
public interface RoadSectionService {
    PageResult<RoadSectionVO> page(RoadSectionQueryDTO query);
    RoadSectionVO getById(String id);
    String create(RoadSectionSaveDTO dto);
    void update(String id, RoadSectionSaveDTO dto);
    void delete(String id);
    List<RoadSectionVO> listForMap(RoadSectionQueryDTO query);
}
