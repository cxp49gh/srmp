package com.smartroad.srmp.disease.service;

import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.disease.dto.DiseaseTypeQueryDTO;
import com.smartroad.srmp.disease.dto.DiseaseTypeSaveDTO;
import com.smartroad.srmp.disease.vo.DiseaseTypeVO;

import java.util.List;

public interface DiseaseTypeService {
    PageResult<DiseaseTypeVO> page(DiseaseTypeQueryDTO query);
    DiseaseTypeVO getById(String id);
    String create(DiseaseTypeSaveDTO dto);
    void update(String id, DiseaseTypeSaveDTO dto);
    void delete(String id);

    /** 租户下已启用的病害类型字典，供 Excel 导入等场景在内存中匹配 */
    List<DiseaseTypeVO> listEnabledForImport();
}
