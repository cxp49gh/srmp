package com.smartroad.srmp.assessment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartroad.srmp.assessment.dto.IndexResultQueryDTO;
import com.smartroad.srmp.assessment.entity.IndexResult;
import com.smartroad.srmp.assessment.vo.IndexResultVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IndexResultMapper extends BaseMapper<IndexResult> {
    Page<IndexResultVO> selectPageVO(Page<?> page, @Param("tenantId") String tenantId, @Param("q") IndexResultQueryDTO query);
    IndexResultVO selectDetail(@Param("tenantId") String tenantId, @Param("id") String id);
    int insertWithJson(IndexResult record);
    int updateWithJson(IndexResult record);
}
