package com.smartroad.srmp.roadasset.controller;

import com.smartroad.srmp.common.exception.BizException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/data-mgmt/templates")
public class DataMgmtTemplateController {

    private static final Map<String, String> FILES;

    static {
        Map<String, String> files = new HashMap<>();
        files.put("road-network", "data-mgmt-templates/road_template.tar");
        files.put("section", "data-mgmt-templates/road_section_template.tar");
        files.put("disease", "data-mgmt-templates/disease.csv");
        FILES = Collections.unmodifiableMap(files);
    }

    @GetMapping("/{type}/download")
    public ResponseEntity<byte[]> download(@PathVariable String type) throws IOException {
        String path = FILES.get(type);
        if (path == null) {
            throw new BizException("未知模板类型：" + type);
        }
        ClassPathResource res = new ClassPathResource(path);
        if (!res.exists()) {
            throw new BizException("模板文件不存在");
        }
        byte[] body = readAllBytes(res.getInputStream());
        String filename = path.substring(path.lastIndexOf('/') + 1);
        String encoded = URLEncoder.encode(filename, "UTF-8").replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    private byte[] readAllBytes(InputStream input) throws IOException {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }
}
