package com.smartroad.srmp.file.controller;

import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
public class FileResourceController {

    @GetMapping("/ping")
    public R<String> ping() {
        return R.ok("srmp-file ok");
    }
}
