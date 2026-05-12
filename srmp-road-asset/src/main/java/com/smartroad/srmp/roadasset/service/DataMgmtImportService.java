package com.smartroad.srmp.roadasset.service;

import com.smartroad.srmp.roadasset.vo.ImportDiseaseExcelResultVO;
import com.smartroad.srmp.roadasset.vo.ImportNetworkResultVO;
import com.smartroad.srmp.roadasset.vo.ImportSectionPackageResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface DataMgmtImportService {

    ImportNetworkResultVO importRoadNetwork(String projectId, MultipartFile file);

    ImportSectionPackageResultVO importSectionPackage(String projectId, MultipartFile file);

    ImportDiseaseExcelResultVO importDiseaseExcel(String projectId, MultipartFile file);
}
