package com.smartroad.srmp.roadasset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartroad.srmp.roadasset.dto.KmCodeIdRow;
import com.smartroad.srmp.roadasset.dto.SectionStakeMatchRow;
import com.smartroad.srmp.roadasset.entity.RoadSectionKm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface RoadSectionKmMapper extends BaseMapper<RoadSectionKm> {

    int insertWithGeom(RoadSectionKm row);

    int updateWithGeom(RoadSectionKm row);

    String selectIdByTenantAndKmCode(@Param("tenantId") String tenantId, @Param("kmCode") String kmCode);

    String selectIdByRouteStake(@Param("tenantId") String tenantId, @Param("routeCode") String routeCode,
                                @Param("direction") String direction, @Param("startStake") BigDecimal startStake,
                                @Param("endStake") BigDecimal endStake);

    List<KmCodeIdRow> selectKmIdsByKmCodes(@Param("tenantId") String tenantId, @Param("codes") List<String> kmCodes);

    List<SectionStakeMatchRow> selectKmIdsByStakes(@Param("tenantId") String tenantId, @Param("rows") List<SectionStakeMatchRow> rows);

    int upsertBatchWithGeom(@Param("tenantId") String tenantId, @Param("list") List<RoadSectionKm> list,
                            @Param("userId") String userId);
}
