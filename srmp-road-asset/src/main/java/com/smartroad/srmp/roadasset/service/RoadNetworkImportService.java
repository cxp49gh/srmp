package com.smartroad.srmp.roadasset.service;

import com.smartroad.srmp.roadasset.vo.ImportNetworkResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface RoadNetworkImportService {

    ImportNetworkResultVO importNetwork(MultipartFile file);

    /** @param projectId 数据管理项目 id；非编排入口传 null */
    ImportNetworkResultVO importNetwork(MultipartFile file, String projectId);
}
