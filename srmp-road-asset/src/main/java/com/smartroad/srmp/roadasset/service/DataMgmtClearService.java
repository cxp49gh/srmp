package com.smartroad.srmp.roadasset.service;

import com.smartroad.srmp.roadasset.vo.DataMgmtClearResultVO;

public interface DataMgmtClearService {

    /**
     * 按租户 + 项目清除业务数据：物理删除表中 {@code tenant_id + project_id} 与参数匹配的行；
     * {@code scope} 含义见 {@link com.smartroad.srmp.roadasset.datamgmt.DataMgmtClearScope}。
     */
    DataMgmtClearResultVO clearByProject(String projectId, String scope);
}
