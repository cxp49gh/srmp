package com.smartroad.srmp.roadasset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartroad.srmp.roadasset.dto.HmCodeIdRow;
import com.smartroad.srmp.roadasset.dto.SectionStakeMatchRow;
import com.smartroad.srmp.roadasset.entity.RoadSectionHm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface RoadSectionHmMapper extends BaseMapper<RoadSectionHm> {

    int insertWithGeom(RoadSectionHm row);

    int updateWithGeom(RoadSectionHm row);

    String selectIdByTenantAndHmCode(@Param("tenantId") String tenantId, @Param("hmCode") String hmCode);

    String selectIdByRouteStake(@Param("tenantId") String tenantId, @Param("routeCode") String routeCode,
                                @Param("direction") String direction, @Param("startStake") BigDecimal startStake,
                                @Param("endStake") BigDecimal endStake);

    List<HmCodeIdRow> selectHmIdsByHmCodes(@Param("tenantId") String tenantId, @Param("codes") List<String> hmCodes);

    List<SectionStakeMatchRow> selectHmIdsByStakes(@Param("tenantId") String tenantId, @Param("rows") List<SectionStakeMatchRow> rows);

    int upsertBatchWithGeom(@Param("tenantId") String tenantId, @Param("list") List<RoadSectionHm> list,
                            @Param("userId") String userId);
}
