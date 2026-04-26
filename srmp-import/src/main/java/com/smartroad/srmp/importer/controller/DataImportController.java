package com.smartroad.srmp.importer.controller;

import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/import")
public class DataImportController {

    @GetMapping("/ping")
    public R<String> ping() {
        return R.ok("srmp-import ok");
    }
}
