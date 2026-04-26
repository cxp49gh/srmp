package com.smartroad.srmp.base.controller;

import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dicts")
public class DictController {

    @GetMapping("/ping")
    public R<String> ping() {
        return R.ok("srmp-base ok");
    }
}
