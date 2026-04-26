package com.smartroad.srmp.importer.controller;
import com.smartroad.srmp.common.core.R;
import com.smartroad.srmp.importer.service.DataImportService;
import com.smartroad.srmp.importer.vo.ImportResultVO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
@RestController
@RequestMapping("/api/import")
public class DataImportController {
    @Resource private DataImportService dataImportService;

    /**
     * 阶段三简化导入：上传后立即解析、校验并入库。
     */
    @PostMapping("/upload")
    public R<ImportResultVO> upload(@RequestParam("dataType") String dataType,
                                    @RequestParam(value = "importName", required = false) String importName,
                                    @RequestPart("file") MultipartFile file) throws IOException {
        return R.ok(dataImportService.importFile(dataType, importName, file));
    }

    @GetMapping("/tasks/{id}/errors")
    public R<List<?>> errors(@PathVariable("id") String id) {
        return R.ok(dataImportService.listErrors(id));
    }
    @GetMapping("/templates/{dataType}")
    public void template(@PathVariable("dataType") String dataType, HttpServletResponse response) throws IOException {
        dataImportService.downloadTemplate(dataType, response);
    }
}