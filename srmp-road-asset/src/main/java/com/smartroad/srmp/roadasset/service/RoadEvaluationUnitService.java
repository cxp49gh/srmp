package com.smartroad.srmp.roadasset.service;
import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.roadasset.dto.*;
import com.smartroad.srmp.roadasset.vo.RoadEvaluationUnitVO;
import com.smartroad.srmp.roadasset.vo.StakeLocationVO;
import java.util.List;
public interface RoadEvaluationUnitService {
    PageResult<RoadEvaluationUnitVO> page(EvaluationUnitQueryDTO query);
    RoadEvaluationUnitVO getById(String id);
    String create(EvaluationUnitSaveDTO dto);
    void update(String id, EvaluationUnitSaveDTO dto);
    void delete(String id);
    List<RoadEvaluationUnitVO> listForMap(EvaluationUnitQueryDTO query);
    StakeLocationVO locateByStake(StakeLocationQueryDTO query);
}
