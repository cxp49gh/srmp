package com.smartroad.srmp.disease.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartroad.srmp.disease.dto.DiseaseTypeQueryDTO;
import com.smartroad.srmp.disease.entity.DiseaseTypeDict;
import com.smartroad.srmp.disease.vo.DiseaseTypeVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DiseaseTypeDictMapper extends BaseMapper<DiseaseTypeDict> {
    Page<DiseaseTypeVO> selectPageVO(Page<?> page, @Param("tenantId") String tenantId, @Param("q") DiseaseTypeQueryDTO query);
    DiseaseTypeVO selectDetail(@Param("tenantId") String tenantId, @Param("id") String id);
}
