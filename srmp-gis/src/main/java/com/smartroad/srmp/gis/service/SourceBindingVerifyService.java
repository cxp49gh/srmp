package com.smartroad.srmp.gis.service;

import com.smartroad.srmp.gis.dto.SourceBindingVerifyRequest;

import java.util.Map;

public interface SourceBindingVerifyService {
    Map<String, Object> verify(SourceBindingVerifyRequest request);
}
