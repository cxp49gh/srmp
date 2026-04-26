package com.smartroad.srmp.importer.service;

import com.smartroad.srmp.importer.vo.ImportResultVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public interface DataImportService {
    ImportResultVO importFile(String dataType, String importName, MultipartFile file) throws IOException;
    List<?> listErrors(String importTaskId);
    void downloadTemplate(String dataType, HttpServletResponse response) throws IOException;
}