package com.smartroad.srmp.gis.controller;

import com.smartroad.srmp.common.core.R;
import com.smartroad.srmp.gis.dto.SourceBindingVerifyRequest;
import com.smartroad.srmp.gis.service.SourceBindingVerifyService;
import com.smartroad.srmp.gis.support.GisQueryPreconditions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/api/gis/source-binding")
public class GisSourceBindingController {

    @Resource
    private SourceBindingVerifyService sourceBindingVerifyService;

    @PostMapping("/verify")
    public R<Map<String, Object>> verify(
            @RequestBody(required = false) SourceBindingVerifyRequest request
    ) {
        GisQueryPreconditions.requireProjectId(
                request == null ? null : request.getProjectId()
        );
        return R.ok(sourceBindingVerifyService.verify(request));
    }
}
