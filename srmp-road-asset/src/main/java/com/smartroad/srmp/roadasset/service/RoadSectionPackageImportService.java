package com.smartroad.srmp.roadasset.service;

import com.smartroad.srmp.roadasset.vo.ImportSectionPackageResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface RoadSectionPackageImportService {
    ImportSectionPackageResultVO importPackage(MultipartFile file);
}
