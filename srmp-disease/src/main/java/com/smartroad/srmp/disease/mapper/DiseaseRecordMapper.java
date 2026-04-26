package com.smartroad.srmp.disease.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartroad.srmp.disease.dto.DiseaseQueryDTO;
import com.smartroad.srmp.disease.entity.DiseaseRecord;
import com.smartroad.srmp.disease.vo.DiseaseRecordVO;
import com.smartroad.srmp.disease.vo.DiseaseStatisticsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DiseaseRecordMapper extends BaseMapper<DiseaseRecord> {
    Page<DiseaseRecordVO> selectPageVO(Page<?> page, @Param("tenantId") String tenantId, @Param("q") DiseaseQueryDTO query);
    DiseaseRecordVO selectDetail(@Param("tenantId") String tenantId, @Param("id") String id);
    int insertWithGeom(DiseaseRecord record);
    int updateWithGeom(DiseaseRecord record);
    List<DiseaseRecordVO> selectForMap(@Param("tenantId") String tenantId, @Param("q") DiseaseQueryDTO query);
    DiseaseStatisticsVO selectStatistics(@Param("tenantId") String tenantId, @Param("q") DiseaseQueryDTO query);
}
