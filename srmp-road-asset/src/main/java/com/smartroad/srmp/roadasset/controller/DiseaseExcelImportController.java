package com.smartroad.srmp.roadasset.controller;

import com.smartroad.srmp.common.core.R;
import com.smartroad.srmp.roadasset.service.DiseaseExcelImportService;
import com.smartroad.srmp.roadasset.vo.ImportDiseaseExcelResultVO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/road-assets/diseases")
public class DiseaseExcelImportController {

    @Resource
    private DiseaseExcelImportService diseaseExcelImportService;

    @PostMapping(value = "/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<ImportDiseaseExcelResultVO> importExcel(@RequestPart("file") MultipartFile file) {
        return R.ok(diseaseExcelImportService.importExcel(file));
    }
}
