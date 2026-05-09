package com.smartroad.srmp.disease.service;

import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.disease.dto.DiseaseQueryDTO;
import com.smartroad.srmp.disease.dto.DiseaseReviewDTO;
import com.smartroad.srmp.disease.dto.DiseaseSaveDTO;
import com.smartroad.srmp.disease.vo.DiseaseRecordVO;
import com.smartroad.srmp.disease.vo.DiseaseStatisticsVO;

import java.util.List;

public interface DiseaseRecordService {
    PageResult<DiseaseRecordVO> page(DiseaseQueryDTO query);
    DiseaseRecordVO getById(String id);
    String create(DiseaseSaveDTO dto);

    /**
     * 批量插入（单条 SQL 多 VALUES，内部再按块切分），用于 Excel 等大批量导入，避免逐条 insert 往返。
     */
    void createBatch(List<DiseaseSaveDTO> dtos);

    void update(String id, DiseaseSaveDTO dto);
    void delete(String id);
    void review(DiseaseReviewDTO dto);
    List<DiseaseRecordVO> listForMap(DiseaseQueryDTO query);
    DiseaseStatisticsVO statistics(DiseaseQueryDTO query);
}
