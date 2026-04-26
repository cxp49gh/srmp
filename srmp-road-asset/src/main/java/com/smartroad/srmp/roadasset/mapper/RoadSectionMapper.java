package com.smartroad.srmp.roadasset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartroad.srmp.roadasset.dto.RoadSectionQueryDTO;
import com.smartroad.srmp.roadasset.entity.RoadSection;
import com.smartroad.srmp.roadasset.vo.RoadSectionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoadSectionMapper extends BaseMapper<RoadSection> {
    Page<RoadSectionVO> selectPageVO(Page<?> page, @Param("tenantId") String tenantId, @Param("q") RoadSectionQueryDTO query);
    RoadSectionVO selectDetail(@Param("tenantId") String tenantId, @Param("id") String id);
    int insertWithGeom(RoadSection section);
    int updateWithGeom(RoadSection section);
    List<RoadSectionVO> selectForMap(@Param("tenantId") String tenantId, @Param("q") RoadSectionQueryDTO query);
}
