package com.smartroad.srmp.roadasset.service;

import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.roadasset.dto.DataMgmtProjectQueryDTO;
import com.smartroad.srmp.roadasset.dto.DataMgmtProjectSaveDTO;
import com.smartroad.srmp.roadasset.vo.DataMgmtProjectVO;

public interface DataMgmtProjectService {

    PageResult<DataMgmtProjectVO> page(DataMgmtProjectQueryDTO query);

    String create(DataMgmtProjectSaveDTO dto);

    void requireExists(String projectId);

    DataMgmtProjectVO getById(String id);

    /**
     * 删除项目：先按 {@link com.smartroad.srmp.roadasset.datamgmt.DataMgmtClearScope#ALL} 物理清除归属数据与导入流水，再软删项目主档（同一事务）。
     */
    void delete(String id);
}
