package com.smartroad.srmp.roadasset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartroad.srmp.roadasset.dto.EvaluationUnitQueryDTO;
import com.smartroad.srmp.roadasset.dto.StakeLocationQueryDTO;
import com.smartroad.srmp.roadasset.dto.UnitCodeIdRow;
import com.smartroad.srmp.roadasset.entity.RoadEvaluationUnit;
import com.smartroad.srmp.roadasset.vo.RoadEvaluationUnitVO;
import com.smartroad.srmp.roadasset.vo.StakeLocationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoadEvaluationUnitMapper extends BaseMapper<RoadEvaluationUnit> {
    Page<RoadEvaluationUnitVO> selectPageVO(Page<?> page, @Param("tenantId") String tenantId, @Param("q") EvaluationUnitQueryDTO query);
    RoadEvaluationUnitVO selectDetail(@Param("tenantId") String tenantId, @Param("id") String id);
    int insertWithGeom(RoadEvaluationUnit unit);
    int updateWithGeom(RoadEvaluationUnit unit);
    List<RoadEvaluationUnitVO> selectForMap(@Param("tenantId") String tenantId, @Param("q") EvaluationUnitQueryDTO query);
    StakeLocationVO locateByStake(@Param("tenantId") String tenantId, @Param("q") StakeLocationQueryDTO query);

    String selectIdByTenantAndUnitCode(@Param("tenantId") String tenantId, @Param("unitCode") String unitCode);

    List<UnitCodeIdRow> selectLedgerIdsByUnitCodes(@Param("tenantId") String tenantId, @Param("codes") List<String> unitCodes);

    int upsertBatchWithGeom(@Param("tenantId") String tenantId, @Param("list") List<RoadEvaluationUnit> list,
                            @Param("userId") String userId);
}
