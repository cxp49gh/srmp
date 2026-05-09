package com.smartroad.srmp.roadasset.service;

import com.smartroad.srmp.roadasset.vo.ImportDiseaseExcelResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface DiseaseExcelImportService {
    ImportDiseaseExcelResultVO importExcel(MultipartFile file);
}
