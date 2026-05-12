package com.smartroad.srmp.roadasset.service;

import com.smartroad.srmp.roadasset.vo.ImportSectionPackageResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface RoadSectionPackageImportService {
    ImportSectionPackageResultVO importPackage(MultipartFile file);

    /** @param projectId 数据管理项目 id；非编排入口传 null */
    ImportSectionPackageResultVO importPackage(MultipartFile file, String projectId);
}
