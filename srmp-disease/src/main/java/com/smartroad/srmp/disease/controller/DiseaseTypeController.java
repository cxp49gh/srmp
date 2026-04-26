package com.smartroad.srmp.disease.controller;

import com.smartroad.srmp.common.core.PageResult;
import com.smartroad.srmp.common.core.R;
import com.smartroad.srmp.disease.dto.DiseaseTypeQueryDTO;
import com.smartroad.srmp.disease.dto.DiseaseTypeSaveDTO;
import com.smartroad.srmp.disease.service.DiseaseTypeService;
import com.smartroad.srmp.disease.vo.DiseaseTypeVO;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/disease-types")
public class DiseaseTypeController {
    @Resource private DiseaseTypeService service;

    @PostMapping("/page")
    public R<PageResult<DiseaseTypeVO>> page(@RequestBody DiseaseTypeQueryDTO query) { return R.ok(service.page(query)); }
    @GetMapping("/{id}")
    public R<DiseaseTypeVO> get(@PathVariable String id) { return R.ok(service.getById(id)); }
    @PostMapping
    public R<String> create(@RequestBody DiseaseTypeSaveDTO dto) { return R.ok(service.create(dto)); }
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable String id, @RequestBody DiseaseTypeSaveDTO dto) { service.update(id, dto); return R.ok(); }
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) { service.delete(id); return R.ok(); }
}
